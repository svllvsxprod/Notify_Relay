<p align="center">
  <img src="screens/main.png" alt="Notify Relay" width="180" />
</p>

<h1 align="center">Notify Relay</h1>

<p align="center">
  Private Android notification and SMS relay to Telegram through your own server.
</p>

<p align="center">
  <a href="android-app"><img alt="Android" src="https://img.shields.io/badge/Android-Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="server"><img alt="Node.js" src="https://img.shields.io/badge/Server-Node.js-339933?style=for-the-badge&logo=node.js&logoColor=white"></a>
  <img alt="Postgres" src="https://img.shields.io/badge/Database-PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Deploy-Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
</p>

<p align="center">
  <a href="#screens">Screens</a> ·
  <a href="#features">Features</a> ·
  <a href="#quick-start">Quick Start</a> ·
  <a href="#security">Security</a>
</p>

## Screens

<p align="center">
  <img src="screens/first_launch.png" width="180" alt="First launch" />
  <img src="screens/main.png" width="180" alt="Dashboard" />
  <img src="screens/app_screen.png" width="180" alt="Apps" />
  <img src="screens/settings.png" width="180" alt="Settings" />
  <img src="screens/diagnostic.png" width="180" alt="Diagnostics" />
</p>

## Features

- Forward selected Android notifications to Telegram.
- Forward SMS when permission is granted.
- Pair Android with Telegram using a short code from the bot.
- Private bot mode with `ALLOWED_TELEGRAM_CHAT_IDS` allowlist.
- Per-app selection with search and optional system apps visibility.
- Privacy modes: full text, masked sensitive values, or event-only.
- RU/EN app interface with system-language default.
- Server deployment with Docker, Postgres, optional Cloudflare Tunnel, and optional host Caddy reverse proxy.

## Quick Start

### Server

```bash
cd server
bash install.sh
```

The installer asks for language, Telegram bot token, allowed Telegram chat ids, and access mode.

- Own domain: app and Postgres run in Docker; Caddy runs on the host and reverse-proxies to `127.0.0.1:<free_port>`.
- No domain: app, Postgres, and Cloudflare quick tunnel run in Docker; no external port is published.

Run again to show status and current URL:

```bash
bash install.sh
```

Reconfigure from scratch:

```bash
bash install.sh --reconfigure
```

### Android

```bash
cd android-app
./gradlew :app:assembleDebug
```

APK path:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Security

- Telegram bot token is stored only on the server.
- Android stores only its device token and sends events to your backend.
- `.env`, runtime database files, local Android SDK paths, build outputs, and dependencies are intentionally ignored by git.
- Public examples use placeholders only. Do not commit real tokens, chat ids, tunnel URLs, private Caddy config, or runtime data.

## Project Layout

```text
android-app/  Android app built with Kotlin and Compose
server/       Node.js backend with Postgres/JSON storage
screens/      Public screenshots for README
```
