# Arsenic Chat Relay

This relay lets Arsenic users chat with each other anonymously. An optional two-way bridge with a Discord channel is available.
It has no dependencies: you only need **Node.js 22 or newer**.

## What the host needs to know

- The relay **never** receives the Minecraft username, UUID or session. It only receives a random token that it generated itself. That token is bound to a nickname such as `FierceScorpion17`.
- The host does, however, see the **IP address** of every connected client and **every message**, which appear in the logs. Users are only anonymous towards each other, not towards the host.
- `data.json` only contains the **SHA-256 hash** of the tokens and the nicknames.
- The Discord bot token goes in `.env`, on the host's machine only. It must never end up on GitHub or in the client.

## Installation

```sh
# 1. Copy the tools/chat-relay folder to the hosting machine
node -v                # must print v22 or newer
cd chat-relay
cp .env.example .env   # on Windows: copy .env.example .env
npm test               # optional: local end-to-end test
npm start
```

By default the relay listens on TCP port **25600**. To make it reachable from the Internet:

1. **Port forwarding** on the router: TCP 25600 to the machine's local IP (for example 192.168.1.20).
2. **Firewall**: allow Node.js or port 25600 (Windows asks on first launch).
3. Give players the **public IP** (https://ifconfig.me) or a DynDNS / DuckDNS name, recommended if the IP changes.

If the connection is behind CGNAT, which is common on 4G/5G and with some ISPs, port forwarding will not work. Use a small VPS, a tunnel (playit.gg, ngrok TCP) or ask the ISP for a full-stack IP instead.

## For players

In Arsenic, enable the **ArsenicChat** module (Client category), then enter the address in the **Server** setting (`ip:port`):

```
.ArsenicChat Server 203.0.113.7:25600
```

Available commands:

- `#message`, `.irc message`, `.ac message` or `.achat message`: send a message. (`.chat` is already an alias of Arsenic's `.say` command.)
- Messages from other Arsenic users show a small `✦` icon before their nickname; Discord users are tagged as such.
- `.irc nick`: show your nickname. `.irc nick NewName`: change it (3 to 16 characters, at most once per minute).
- `.irc nick random`: let the relay generate a fresh random nickname (same cooldown), so you never have to reveal your Minecraft name.
- `.irc online`, `.irc ignore <name>`, `.irc ignored`.

The nickname is kept in `Arsenic/chat-token.txt`. Deleting that file gives you a new anonymous nickname.

## Discord bridge (optional)

1. On https://discord.com/developers/applications, create an application and then a **Bot**. Copy its token into `DISCORD_TOKEN`.
2. In the Bot tab, enable **Message Content Intent**.
3. Invite the bot to the server (OAuth2 > URL Generator, scope `bot`, permissions *View Channel*, *Send Messages* and *Read Message History*).
4. Enable Discord developer mode, right-click the channel, choose *Copy Channel ID* and put it in `DISCORD_CHANNEL_ID`.
5. Recommended: in the channel settings, open Integrations > Webhooks, create a webhook and copy its URL into `DISCORD_WEBHOOK_URL`. Messages coming from Minecraft will then show under their author's nickname.
6. `DISCORD_ADMIN_IDS`: comma-separated Discord user IDs allowed to use `!ban`, `!unban` and `!say`. Anyone can use `!online`.

Mentions (@everyone, roles, members) are always disabled in messages posted by the relay.

## Administration

In the console running `npm start`: `online`, `ban <name>`, `unban <name>`, `say <announcement>`.

## Running as a service (auto restart)

**Linux (systemd)**: create `/etc/systemd/system/arsenic-chat.service`.

```ini
[Unit]
Description=Arsenic chat relay
After=network-online.target

[Service]
WorkingDirectory=/home/USER/chat-relay
ExecStart=/usr/bin/node server.js
Restart=always
User=USER

[Install]
WantedBy=multi-user.target
```

Then run `sudo systemctl enable --now arsenic-chat`. To follow the logs, run `journalctl -u arsenic-chat -f`.

**Windows / macOS / Linux with pm2**:

```sh
npm i -g pm2
pm2 start server.js --name arsenic-chat
pm2 save
pm2 startup   # follow the printed instruction
```

## `.env` settings

| Variable | Default | Purpose |
|---|---|---|
| PORT | 25600 | TCP port |
| BIND | 0.0.0.0 | Listen interface |
| DATA_FILE | data.json | Nicknames and bans |
| MAX_LEN | 256 | Maximum message length |
| MAX_PER_IP | 3 | Maximum simultaneous connections per IP |
| TLS_CERT / TLS_KEY | (empty) | Server-side TLS. The current client connects over plain TCP, so do not enable it. |
| DISCORD_TOKEN | (empty) | Discord bot token |
| DISCORD_CHANNEL_ID | (empty) | ID of the bridged channel |
| DISCORD_WEBHOOK_URL | (empty) | Webhook used to post Minecraft messages |
| DISCORD_ADMIN_IDS | (empty) | Discord IDs allowed to use the admin commands |
