'use strict';
/*
 * Arsenic chat relay.
 * TCP server (optional TLS) exchanging newline-delimited JSON with Arsenic clients.
 * No dependencies: requires Node >= 22 (global WebSocket for the Discord bridge).
 *
 * The relay never receives the Minecraft username, UUID or session: only a random token
 * that it generated itself, bound to an anonymous nickname.
 */
const net = require('net');
const tls = require('tls');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const readline = require('readline');
const { createBridge } = require('./discord');

try { process.loadEnvFile(path.join(__dirname, '.env')); } catch (e) { /* .env is optional */ }

const env = process.env;
const PORT = parseInt(env.PORT || '25600', 10);
const BIND = env.BIND || '0.0.0.0';
const DATA_FILE = path.resolve(__dirname, env.DATA_FILE || 'data.json');
const MAX_LEN = parseInt(env.MAX_LEN || '256', 10);
const MAX_LINE = 4096;
const MAX_PER_IP = parseInt(env.MAX_PER_IP || '3', 10);
const RATE = 0.5;   // messages regained per second
const BURST = 5;    // max burst
const IDLE_MS = 90000;
const RENAME_COOLDOWN_MS = 60000;
const RESERVED = /^(admin|server|serveur|arsenic|system|moderat|console)|discord|clyde/i;
const NICK_RE = /^[A-Za-z0-9_]{3,16}$/;

const ADJ = ['Agile', 'Brave', 'Calm', 'Silent', 'Electric', 'Fierce', 'Frozen', 'Swift', 'Lucky', 'Happy',
    'Lunar', 'Clever', 'Dark', 'Shadow', 'Pale', 'Rapid', 'Wild', 'Stubborn', 'Bold', 'Zen', 'Red', 'Blue',
    'Cosmic', 'Sneaky', 'Toxic', 'Grim', 'Solar', 'Sly', 'Mystic', 'Ancient'];
const ANIMALS = ['Wolf', 'Fox', 'Owl', 'Lynx', 'Raven', 'Panda', 'Tiger', 'Cobra', 'Falcon', 'Bear', 'Shark',
    'Lizard', 'Badger', 'Ferret', 'Gecko', 'Jaguar', 'Koala', 'Rabbit', 'Sparrow', 'Orca', 'Puma', 'Raccoon', 'Scorpion',
    'Mole', 'Viper', 'Yak', 'Zebra', 'Axolotl', 'Phoenix', 'Golem'];

const log = (...a) => console.log(new Date().toISOString(), ...a);

/* ---------- Persistence ---------- */
const store = { users: {} }; // sha256(token) -> { nick, created, banned, renamedAt }
try { Object.assign(store, JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'))); }
catch (e) { if (e.code !== 'ENOENT') { console.error('Unreadable data.json:', e.message); process.exit(1); } }

let saveTimer = null;
function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(saveNow, 300);
}
function saveNow() {
    clearTimeout(saveTimer);
    const tmp = DATA_FILE + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(store, null, 2), { mode: 0o600 });
    fs.renameSync(tmp, DATA_FILE);
}

const hash = t => crypto.createHash('sha256').update(t).digest('hex');
const pick = arr => arr[crypto.randomInt(arr.length)];
const findUser = nick => {
    const l = String(nick).toLowerCase();
    return Object.values(store.users).find(u => u.nick.toLowerCase() === l);
};
function genNick() {
    for (let i = 0; i < 1000; i++) {
        const n = pick(ADJ) + pick(ANIMALS) + crypto.randomInt(10, 100);
        if (n.length <= 16 && !findUser(n)) return n;
    }
    return 'Anon' + crypto.randomInt(100000, 1000000);
}

/** Strips Minecraft colour codes, control characters and bidi/invisible characters. */
function clean(s, max) {
    return String(s)
        .replace(/§./gs, '').replace(/§/g, '')
        .replace(/[\u0000-\u001f\u007f-\u009f\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/g, '')
        .replace(/\s+/g, ' ')
        .trim().slice(0, max);
}

/* ---------- Clients ---------- */
const clients = new Set();
const online = () => [...new Set([...clients].filter(c => c.user).map(c => c.user.nick))].sort();
const send = (c, obj) => { if (!c.sock.destroyed) c.sock.write(JSON.stringify(obj) + '\n'); };
const broadcast = obj => { for (const c of clients) if (c.user) send(c, obj); };

function take(c) {
    const now = Date.now();
    c.tokens = Math.min(BURST, c.tokens + (now - c.last) / 1000 * RATE);
    c.last = now;
    if (c.tokens < 1) return false;
    c.tokens -= 1;
    return true;
}

function onConnection(sock) {
    const ip = sock.remoteAddress;
    if ([...clients].filter(c => c.ip === ip).length >= MAX_PER_IP) {
        sock.end(JSON.stringify({ t: 'err', text: 'Too many connections from your address.' }) + '\n');
        return;
    }
    const c = { sock, ip, user: null, tokens: BURST, last: Date.now(), seen: Date.now() };
    clients.add(c);
    sock.setEncoding('utf8');
    sock.setNoDelay(true);
    sock.setKeepAlive(true, 30000);
    const authTimer = setTimeout(() => { if (!c.user) sock.destroy(); }, 10000);

    let buf = '';
    sock.on('data', d => {
        buf += d;
        let i;
        while ((i = buf.indexOf('\n')) >= 0) {
            const line = buf.slice(0, i).trim();
            buf = buf.slice(i + 1);
            if (line) handle(c, line);
            if (sock.destroyed) return;
        }
        if (buf.length > MAX_LINE) sock.destroy();
    });
    sock.on('close', () => {
        clearTimeout(authTimer);
        clients.delete(c);
        if (c.user && !online().includes(c.user.nick)) broadcast({ t: 'leave', nick: c.user.nick });
    });
    sock.on('error', () => {});
}

function handle(c, line) {
    let m;
    try { m = JSON.parse(line); } catch (e) { return send(c, { t: 'err', text: 'Invalid JSON.' }); }
    if (!m || typeof m.t !== 'string') return;
    c.seen = Date.now();
    if (!c.user) {
        if (m.t !== 'hello') return send(c, { t: 'err', text: 'Authentication required.' });
        return hello(c, m);
    }
    switch (m.t) {
        case 'ping': return send(c, { t: 'pong' });
        case 'msg': return chat(c, m.text);
        case 'nick': return rename(c, m.name);
        case 'online': return send(c, { t: 'online', users: online() });
    }
}

function hello(c, m) {
    let token = typeof m.token === 'string' && /^[a-f0-9]{64}$/.test(m.token) ? m.token : null;
    let user = token && store.users[hash(token)];
    if (!user) {
        token = crypto.randomBytes(32).toString('hex');
        user = { nick: genNick(), created: Date.now(), banned: false, renamedAt: 0 };
        store.users[hash(token)] = user;
        save();
        log('new nickname', user.nick);
    }
    if (user.banned) {
        send(c, { t: 'err', text: 'You are banned from this chat.', fatal: true });
        return c.sock.end();
    }
    const wasOnline = online().includes(user.nick);
    c.user = user;
    send(c, { t: 'welcome', nick: user.nick, token, online: online().length });
    if (!wasOnline) broadcast({ t: 'join', nick: user.nick });
}

function chat(c, text) {
    if (typeof text !== 'string') return;
    text = clean(text, MAX_LEN);
    if (!text) return;
    if (!take(c)) return send(c, { t: 'err', text: 'Slow down, you are sending too fast.' });
    broadcast({ t: 'msg', from: c.user.nick, text, src: 'mc' });
    bridge.toDiscord(c.user.nick, text);
    log(`<${c.user.nick}> ${text}`);
}

function rename(c, name) {
    if (typeof name !== 'string') return;
    if (!take(c)) return send(c, { t: 'err', text: 'Slow down, you are sending too fast.' });
    if (!NICK_RE.test(name)) return send(c, { t: 'err', text: 'Invalid name: 3 to 16 characters, letters, digits or _.' });
    if (RESERVED.test(name)) return send(c, { t: 'err', text: 'This name is reserved.' });
    const other = findUser(name);
    if (other && other !== c.user) return send(c, { t: 'err', text: 'This name is already taken.' });
    const wait = c.user.renamedAt + RENAME_COOLDOWN_MS - Date.now();
    if (wait > 0) return send(c, { t: 'err', text: `Wait ${Math.ceil(wait / 1000)} s before renaming again.` });
    const old = c.user.nick;
    c.user.nick = name;
    c.user.renamedAt = Date.now();
    save();
    for (const o of clients) if (o.user === c.user) send(o, { t: 'nick', nick: name });
    broadcast({ t: 'rename', from: old, to: name });
    log('rename', old, '->', name);
}

/* ---------- Admin (console + Discord) ---------- */
function admin(cmd, arg) {
    switch ((cmd || '').toLowerCase()) {
        case 'online': {
            const list = online();
            return `${list.length} online: ${list.join(', ') || 'nobody'}`;
        }
        case 'ban':
        case 'unban': {
            const u = arg && findUser(arg);
            if (!u) return `Unknown name: ${arg || '(empty)'}`;
            u.banned = cmd.toLowerCase() === 'ban';
            saveNow();
            if (u.banned) for (const c of clients) if (c.user === u) { send(c, { t: 'err', text: 'You have been banned from this chat.', fatal: true }); c.sock.end(); }
            return `${u.nick} ${u.banned ? 'banned' : 'unbanned'}.`;
        }
        case 'say': {
            const text = clean(arg || '', MAX_LEN);
            if (!text) return 'Empty message.';
            broadcast({ t: 'sys', text });
            return 'Announcement sent.';
        }
        default:
            return 'Commands: online, ban <name>, unban <name>, say <text>';
    }
}

const bridge = createBridge({
    token: env.DISCORD_TOKEN,
    channelId: env.DISCORD_CHANNEL_ID,
    webhookUrl: env.DISCORD_WEBHOOK_URL,
    adminIds: (env.DISCORD_ADMIN_IDS || '').split(',').map(s => s.trim()).filter(Boolean),
    log,
    onMessage(name, text) {
        name = clean(name, 32);
        text = clean(text, MAX_LEN);
        if (name && text) broadcast({ t: 'msg', from: name, text, src: 'discord' });
    },
    onCommand(cmd, arg, isAdmin) {
        if (cmd === 'online') return admin('online');
        if (!isAdmin) return null;
        return admin(cmd, arg);
    },
});

/* ---------- Startup ---------- */
setInterval(() => {
    const now = Date.now();
    for (const c of clients) if (now - c.seen > IDLE_MS) c.sock.destroy();
}, 30000).unref();

const server = env.TLS_CERT && env.TLS_KEY
    ? tls.createServer({ cert: fs.readFileSync(env.TLS_CERT), key: fs.readFileSync(env.TLS_KEY) }, onConnection)
    : net.createServer(onConnection);
server.on('error', e => { console.error('Server error:', e.message); process.exit(1); });
server.listen(PORT, BIND, () => log(`Arsenic relay on ${BIND}:${PORT}${env.TLS_CERT ? ' (TLS)' : ''}, ${Object.keys(store.users).length} known names, Discord ${bridge.enabled ? 'enabled' : 'disabled'}.`));

if (process.stdin.isTTY) {
    readline.createInterface({ input: process.stdin }).on('line', l => {
        const [cmd, ...rest] = l.trim().split(' ');
        if (cmd) console.log(admin(cmd, rest.join(' ')));
    });
}

function shutdown() {
    log('Shutting down.');
    saveNow();
    for (const c of clients) c.sock.destroy();
    bridge.stop();
    process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
