#!/usr/bin/env bash
set -euo pipefail

APP_NAME="notify-relay"
APP_PORT_INTERNAL="8000"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LANG_CHOICE="en"
RECONFIGURE="${1:-}"

say() {
  local ru="$1"
  local en="$2"
  if [ "$LANG_CHOICE" = "ru" ]; then
    printf '%s\n' "$ru"
  else
    printf '%s\n' "$en"
  fi
}

ask() {
  local ru="$1"
  local en="$2"
  local default="${3:-}"
  local prompt value
  if [ "$LANG_CHOICE" = "ru" ]; then
    prompt="$ru"
  else
    prompt="$en"
  fi
  if [ -n "$default" ]; then
    read -r -p "$prompt [$default]: " value
    printf '%s' "${value:-$default}"
  else
    read -r -p "$prompt: " value
    printf '%s' "$value"
  fi
}

ask_secret() {
  local ru="$1"
  local en="$2"
  local prompt value
  if [ "$LANG_CHOICE" = "ru" ]; then
    prompt="$ru"
  else
    prompt="$en"
  fi
  read -r -s -p "$prompt: " value
  printf '\n' >&2
  printf '%s' "$value"
}

require_root_for_system_changes() {
  if [ "$(id -u)" -ne 0 ]; then
    say "Для установки пакетов нужен sudo/root." "Installing packages requires sudo/root."
    SUDO="sudo"
  else
    SUDO=""
  fi
}

install_docker_if_needed() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    say "Docker уже установлен." "Docker is already installed."
    return
  fi

  require_root_for_system_changes
  say "Устанавливаю Docker через официальный convenience script." "Installing Docker with the official convenience script."
  curl -fsSL https://get.docker.com | $SUDO sh
}

install_caddy_if_needed() {
  if command -v caddy >/dev/null 2>&1; then
    say "Caddy уже установлен." "Caddy is already installed."
    return
  fi

  require_root_for_system_changes
  if command -v apt-get >/dev/null 2>&1; then
    say "Устанавливаю Caddy через apt." "Installing Caddy with apt."
    $SUDO apt-get update
    $SUDO apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | $SUDO gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | $SUDO tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
    $SUDO apt-get update
    $SUDO apt-get install -y caddy
  else
    say "Не удалось автоматически установить Caddy: поддержан только apt. Установите Caddy вручную и запустите скрипт снова." "Could not install Caddy automatically: only apt is supported. Install Caddy manually and run this script again."
    exit 1
  fi
}

random_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32 | tr -d '=+/\n' | cut -c1-32
  else
    date +%s%N | sha256sum | cut -c1-32
  fi
}

find_free_local_port() {
  local port
  for port in $(seq 18000 18999); do
    if is_local_port_free "$port"; then
      printf '%s' "$port"
      return
    fi
  done
  say "Не удалось найти свободный локальный порт." "Could not find a free local port."
  exit 1
}

is_local_port_free() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ! ss -ltn | awk '{print $4}' | grep -q ":$port$"
    return
  fi
  if command -v netstat >/dev/null 2>&1; then
    ! netstat -ltn | awk '{print $4}' | grep -q ":$port$"
    return
  fi
  return 0
}

write_env_file() {
  local bot_token="$1"
  local chat_ids="$2"
  local postgres_password="$3"
  cat > .env <<EOF
PORT=$APP_PORT_INTERNAL
TELEGRAM_BOT_TOKEN=$bot_token
TELEGRAM_CHAT_ID=
ALLOWED_TELEGRAM_CHAT_IDS=$chat_ids
INSTALL_MODE=
PUBLIC_URL=
POSTGRES_DB=notify_relay
POSTGRES_USER=notify_relay
POSTGRES_PASSWORD=$postgres_password
SEEN_EVENT_TTL_DAYS=14
STALE_DEVICE_TTL_DAYS=180
EOF
  chmod 600 .env
}

get_env_value() {
  local key="$1"
  if [ ! -f .env ]; then
    return 0
  fi
  grep -E "^${key}=" .env | tail -n1 | cut -d= -f2-
}

set_env_value() {
  local key="$1"
  local value="$2"
  if grep -qE "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

write_domain_override() {
  local local_port="$1"
  cat > docker-compose.override.yml <<EOF
services:
  app:
    ports:
      - "127.0.0.1:$local_port:$APP_PORT_INTERNAL"
EOF
}

remove_domain_override() {
  rm -f docker-compose.override.yml
}

append_caddy_site() {
  local domain="$1"
  local local_port="$2"
  local caddyfile="/etc/caddy/Caddyfile"
  require_root_for_system_changes

  if [ ! -f "$caddyfile" ]; then
    $SUDO mkdir -p /etc/caddy
    $SUDO touch "$caddyfile"
  fi

  if $SUDO grep -q "# $APP_NAME:$domain" "$caddyfile"; then
    say "Блок Caddy для этого домена уже есть, не дублирую." "Caddy block for this domain already exists; not duplicating it."
    return
  fi

  cat <<EOF | $SUDO tee -a "$caddyfile" >/dev/null

# $APP_NAME:$domain
$domain {
    reverse_proxy 127.0.0.1:$local_port
}
EOF

  $SUDO caddy validate --config "$caddyfile"
  $SUDO systemctl enable --now caddy
  $SUDO systemctl reload caddy
}

show_existing_status() {
  local mode public_url tunnel_url caddy_status
  mode="$(get_env_value INSTALL_MODE)"
  public_url="$(get_env_value PUBLIC_URL)"

  say "Найдена существующая установка Notify Relay." "Existing Notify Relay installation found."

  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose ps
  else
    say "Docker не найден, статус контейнеров недоступен." "Docker was not found, container status is unavailable."
  fi

  if command -v caddy >/dev/null 2>&1; then
    caddy_status="installed"
  else
    caddy_status="not installed"
  fi
  if [ "$caddy_status" = "installed" ]; then
    say "Caddy: установлен" "Caddy: installed"
  else
    say "Caddy: не установлен" "Caddy: not installed"
  fi

  if [ "$mode" = "domain" ] && [ -n "$public_url" ]; then
    say "Адрес: $public_url" "URL: $public_url"
    return
  fi

  tunnel_url=""
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    tunnel_url="$(docker compose logs --no-color tunnel 2>/dev/null | grep -Eo 'https://[-a-zA-Z0-9.]+\.trycloudflare\.com' | tail -n1 || true)"
  fi

  if [ -n "$tunnel_url" ]; then
    set_env_value PUBLIC_URL "$tunnel_url"
    say "Адрес tunnel: $tunnel_url" "Tunnel URL: $tunnel_url"
    say "Важно: quick tunnel URL может измениться после перезапуска tunnel/container/server." "Important: the quick tunnel URL may change after restarting the tunnel/container/server."
    return
  fi

  if [ -n "$public_url" ]; then
    say "Последний сохранённый адрес: $public_url" "Last saved URL: $public_url"
  else
    say "Адрес не найден. Для tunnel выполните: docker compose logs -f tunnel" "URL was not found. For tunnel mode, run: docker compose logs -f tunnel"
  fi
}

start_domain_mode() {
  local domain="$1"
  local local_port="$2"
  write_domain_override "$local_port"
  docker compose up -d --build
  append_caddy_site "$domain" "$local_port"
  set_env_value INSTALL_MODE "domain"
  set_env_value PUBLIC_URL "https://$domain"
  say "Готово. Backend URL: https://$domain" "Done. Backend URL: https://$domain"
}

start_tunnel_mode() {
  remove_domain_override
  docker compose --profile tunnel up -d --build
  set_env_value INSTALL_MODE "tunnel"
  say "Cloudflare Tunnel запущен. Посмотрите URL командой:" "Cloudflare Tunnel is running. View the URL with:"
  printf 'docker compose logs -f tunnel\n'
  say "Важно: quick tunnel URL нужно записать в Android-приложение. Он может измениться после перезапуска tunnel/container/server." "Important: write the quick tunnel URL into the Android app. It may change after restarting the tunnel/container/server."
}

printf 'Choose language / Выберите язык:\n'
printf '1) Русский\n'
printf '2) English\n'
read -r -p '> ' language_number
case "$language_number" in
  1) LANG_CHOICE="ru" ;;
  *) LANG_CHOICE="en" ;;
esac

if [ -f .env ] && [ "$RECONFIGURE" != "--reconfigure" ]; then
  show_existing_status
  say "Для повторной настройки запустите: bash install.sh --reconfigure" "To reconfigure, run: bash install.sh --reconfigure"
  exit 0
fi

say "Установка Notify Relay Server" "Notify Relay Server installer"
install_docker_if_needed

BOT_TOKEN="$(ask_secret "Введите TELEGRAM_BOT_TOKEN" "Enter TELEGRAM_BOT_TOKEN")"
while [ -z "$BOT_TOKEN" ]; do
  say "TELEGRAM_BOT_TOKEN обязателен." "TELEGRAM_BOT_TOKEN is required."
  BOT_TOKEN="$(ask_secret "Введите TELEGRAM_BOT_TOKEN" "Enter TELEGRAM_BOT_TOKEN")"
done

ALLOWED_IDS="$(ask "Разрешённые Telegram chat id через запятую, пусто = любой /start" "Allowed Telegram chat ids, comma-separated; empty = any /start" "")"
POSTGRES_PASSWORD="$(random_password)"
write_env_file "$BOT_TOKEN" "$ALLOWED_IDS" "$POSTGRES_PASSWORD"

say "Выберите режим доступа:" "Choose access mode:"
say "1) Свой домен через Caddy на сервере" "1) Own domain with host Caddy"
say "2) Нет домена, Cloudflare quick tunnel" "2) No domain, Cloudflare quick tunnel"
read -r -p '> ' mode

case "$mode" in
  1)
    DOMAIN="$(ask "Введите домен" "Enter domain")"
    while [ -z "$DOMAIN" ]; do
      DOMAIN="$(ask "Введите домен" "Enter domain")"
    done
    install_caddy_if_needed
    LOCAL_PORT="$(find_free_local_port)"
    start_domain_mode "$DOMAIN" "$LOCAL_PORT"
    ;;
  *)
    start_tunnel_mode
    ;;
esac

say "Проверить контейнеры: docker compose ps" "Check containers: docker compose ps"
