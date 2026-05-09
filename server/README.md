# Notify Relay Server

Backend для Android-приложения Notify Relay. Сервер принимает события от устройства, привязывает устройство к Telegram через pairing code и отправляет выбранные уведомления/SMS в Telegram.

## Быстрая установка на сервер

```bash
bash install.sh
```

Скрипт интерактивно спросит язык, `TELEGRAM_BOT_TOKEN`, разрешённые Telegram chat id и режим доступа.

Если установка уже есть, повторный запуск покажет статус контейнеров, наличие Caddy и текущий адрес. Для полной повторной настройки используйте:

```bash
bash install.sh --reconfigure
```

Режимы доступа:

- Свой домен: приложение и Postgres запускаются в Docker, Caddy ставится/используется на хосте, `Caddyfile` только дописывается. Docker-порт пробрасывается только на `127.0.0.1:<free_port>`, не наружу.
- Без домена: приложение, Postgres и `cloudflared` запускаются в Docker. Внешний порт не нужен. Quick tunnel URL нужно посмотреть в `docker compose logs -f tunnel` и записать в Android-приложение. Этот URL может измениться после перезапуска tunnel/container/server.

## Docker

```bash
docker compose up -d --build
```

По умолчанию compose не публикует порт наружу. Для Caddy installer создаёт локальный `docker-compose.override.yml` с binding вида:

```yaml
ports:
  - "127.0.0.1:18000:8000"
```

Для Cloudflare quick tunnel:

```bash
docker compose --profile tunnel up -d --build
docker compose logs -f tunnel
```

## Локальная разработка

```bash
npm install
npm run dev
```

Без `DATABASE_URL` сервер использует JSON fallback `data/notify-relay.json`. Если `DATABASE_URL` задан, используется Postgres.

Для Android emulator используйте URL:

```text
http://10.0.2.2:8000
```

## Environment

Минимально нужен `TELEGRAM_BOT_TOKEN`.

```env
PORT=8000
TELEGRAM_BOT_TOKEN=<telegram-bot-token>
ALLOWED_TELEGRAM_CHAT_IDS=123456789
POSTGRES_DB=notify_relay
POSTGRES_USER=notify_relay
POSTGRES_PASSWORD=<generated-password>
```

`ALLOWED_TELEGRAM_CHAT_IDS` включает приватный режим. Можно указать один или несколько Telegram chat id через запятую. Если переменная пустая, бот работает для любого пользователя, который напишет `/start`.

`TELEGRAM_CHAT_ID` можно задать как fallback destination, но обычный flow использует chat id из `/start`.

## Endpoints

- `GET /health`
- `POST /v1/devices/register`
- `POST /v1/events/batch`
- `POST /v1/devices/me/test`
- `GET /v1/devices/me`
- `DELETE /v1/devices/me`
