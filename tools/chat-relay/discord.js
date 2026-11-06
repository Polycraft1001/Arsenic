'use strict';
/*
 * Discord <-> relay bridge, no dependencies (global fetch + WebSocket from Node >= 22).
 *  - Discord -> Minecraft: the bot reads the DISCORD_CHANNEL_ID channel through the Gateway.
 *  - Minecraft -> Discord: posted through a webhook under the nickname (otherwise through the bot).
 * Every post disables mentions (@everyone, roles, users).
 */
const API = 'https://discord.com/api/v10';
const GATEWAY = 'wss://gateway.discord.gg/?v=10&encoding=json';
const INTENTS = (1 << 0) | (1 << 9) | (1 << 15); // GUILDS | GUILD_MESSAGES | MESSAGE_CONTENT
const FATAL_CLOSE = new Set([4004, 4010, 4011, 4012, 4013, 4014]);
const NO_MENTIONS = { parse: [] };

function createBridge(opts) {
    const { token, channelId, webhookUrl, adminIds = [], log, onMessage, onCommand } = opts;
    const enabled = Boolean(token && channelId);
    if (!enabled) {
        if (token || channelId || webhookUrl) log('Discord: DISCORD_TOKEN and DISCORD_CHANNEL_ID are required, bridge disabled.');
        return { enabled: false, toDiscord() {}, stop() {} };
    }

    let ws = null, hbTimer = null, seq = null, acked = true, stopped = false, botId = null;

    /* ---------- REST send queue (honours 429s) ---------- */
    const queue = [];
    let busy = false;
    async function pump() {
        if (busy) return;
        busy = true;
        while (queue.length && !stopped) {
     const { url, body, auth } = queue[0];
     try {
         const res = await fetch(url, {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, auth ? { Authorization: 'Bot ' + token } : {}),
      body: JSON.stringify(body),
         });
         if (res.status === 429) {
      const j = await res.json().catch(() => ({}));
      await sleep(Math.ceil((j.retry_after || 1) * 1000));
      continue;
         }
  if (!res.ok) log('Discord: send rejected', res.status, (await res.text()).slice(0, 200));
     } catch (e) {
         log('Discord: network error', e.message);
  await sleep(2000);
     }
     queue.shift();
     if (queue.length > 50) queue.splice(0, queue.length - 50); // avoid piling up during an outage
 }
        busy = false;
    }
    const post = (url, body, auth) => { queue.push({ url, body, auth }); pump(); };
    const say = content => post(`${API}/channels/${channelId}/messages`, { content: content.slice(0, 2000), allowed_mentions: NO_MENTIONS }, true);

    function toDiscord(nick, text) {
 if (webhookUrl) post(webhookUrl + (webhookUrl.includes('?') ? '&' : '?') + 'wait=false',
     { username: `${nick} [MC]`, content: text, allowed_mentions: NO_MENTIONS }, false);
        else say(`**${nick}**: ${text.replace(/([*_`~|>\\])/g, '\\$1')}`);
    }

    /* ---------- Gateway ---------- */
    function connect() {
        if (stopped) return;
        ws = new WebSocket(GATEWAY);
 ws.onmessage = ev => {
     let p;
     try { p = JSON.parse(ev.data); } catch (e) { return; }
     if (p.s != null) seq = p.s;
     switch (p.op) {
  case 10: // Hello
      clearInterval(hbTimer);
      acked = true;
      hbTimer = setInterval(() => {
   if (!acked) { try { ws.close(4000); } catch (e) {} return; } // zombie connection
   acked = false;
   sendWs({ op: 1, d: seq });
      }, p.d.heartbeat_interval);
      sendWs({ op: 2, d: { token, intents: INTENTS, properties: { os: process.platform, browser: 'arsenic-relay', device: 'arsenic-relay' } } });
      break;
  case 11: acked = true; break;
  case 1: sendWs({ op: 1, d: seq }); break;
         case 7: case 9: try { ws.close(4000); } catch (e) {} break; // reconnect / invalid session
  case 0: dispatch(p.t, p.d); break;
            }
        };
 ws.onclose = ev => {
     clearInterval(hbTimer);
     if (stopped) return;
     if (FATAL_CLOSE.has(ev.code)) {
  log(`Discord: closed permanently (${ev.code}). ` + (ev.code === 4014
      ? "Enable Message Content Intent in the developer portal."
      : ev.code === 4004 ? 'Invalid token.' : ''));
         return;
     }
     setTimeout(connect, 5000);
        };
        ws.onerror = () => {};
    }
    const sendWs = obj => { if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj)); };

    function dispatch(type, d) {
        if (type === 'READY') {
     botId = d.user.id;
     log(`Discord: logged in as ${d.user.username}.`);
     return;
 }
        if (type !== 'MESSAGE_CREATE' || d.channel_id !== channelId) return;
        if (d.webhook_id || d.author.bot || d.author.id === botId) return; // no loops
 let text = d.content || '';
        for (const u of d.mentions || []) text = text.split(`<@${u.id}>`).join('@' + (u.global_name || u.username)).split(`<@!${u.id}>`).join('@' + (u.global_name || u.username));
 text = text.replace(/<a?:(\w+):\d+>/g, ':$1:').replace(/<#\d+>/g, '#channel').replace(/<@&\d+>/g, '@role');
 if (d.attachments && d.attachments.length) text += (text ? ' ' : '') + '[attachment]';
 if (!text.trim()) return;

        if (text.startsWith('!')) {
     const [cmd, ...rest] = text.slice(1).trim().split(/\s+/);
     const reply = onCommand(cmd.toLowerCase(), rest.join(' '), adminIds.includes(d.author.id));
     if (reply != null) return say(reply);
        }
        const name = (d.member && d.member.nick) || d.author.global_name || d.author.username;
        onMessage(name, text);
    }

    connect();
    return {
        enabled: true,
 toDiscord,
 stop() { stopped = true; clearInterval(hbTimer); try { ws && ws.close(1000); } catch (e) {} },
    };
}

const sleep = ms => new Promise(r => setTimeout(r, ms));
module.exports = { createBridge };
