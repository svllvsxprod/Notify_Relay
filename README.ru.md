<p align="center">
  <img src="screens/logo.svg" alt="Логотип Notify Relay" width="150" />
</p>

<h1 align="center">Notify Relay</h1>

<p align="center">
  Приватная пересылка Android-уведомлений и SMS в Telegram через ваш собственный сервер.
</p>

<p align="center">
  <a href="README.md">English version</a>
</p>

<table align="center">
  <tr>
    <td align="center" width="760">
      <h3>Помогите Notify Relay оставаться независимым</h3>
      <p>Если проект оказался полезен, можно поддержать дальнейшую разработку, тестирование и серверные инструменты.</p>
      <a href="https://nowpayments.io/donation/svllvsx">
        <img alt="Поддержать через NOWPayments" src="https://img.shields.io/badge/Поддержать%20Notify%20Relay-NOWPayments-7C3AED?style=for-the-badge&logo=bitcoin&logoColor=white&labelColor=111827">
      </a>
    </td>
  </tr>
</table>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Server" src="https://img.shields.io/badge/Server-Node.js-339933?style=for-the-badge&logo=node.js&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/Database-PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Deploy-Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
  <img alt="Telegram" src="https://img.shields.io/badge/Telegram-Bot-26A5E4?style=for-the-badge&logo=telegram&logoColor=white">
</p>

<p align="center">
  <a href="#скриншоты">Скриншоты</a> ·
  <a href="#что-делает-приложение">Возможности</a> ·
  <a href="#установка-сервера">Установка сервера</a> ·
  <a href="#установка-android-приложения">Android</a> ·
  <a href="#модель-безопасности">Безопасность</a>
</p>

## Скриншоты

<p align="center">
  <img src="screens/first_launch.png" width="150" alt="Первый запуск" />
  <img src="screens/main.png" width="150" alt="Главный экран" />
  <img src="screens/app_screen.png" width="150" alt="Выбор приложений" />
  <img src="screens/settings.png" width="150" alt="Настройки" />
  <img src="screens/diagnostic.png" width="150" alt="Диагностика" />
</p>

## Что Делает Приложение

Notify Relay пересылает выбранные Android-уведомления и SMS в Telegram, не сохраняя Telegram bot token на телефоне.

Android-приложение отправляет события только на ваш сервер. Сервер хранит токен бота, привязывает устройство к Telegram-чату через короткий код и доставляет события в привязанный чат.

## Возможности

- Пересылка уведомлений только от выбранных приложений.
- Пересылка SMS после выдачи Android-разрешения.
- Привязка Telegram через `/start` и короткий 6-значный код.
- Приватный режим бота через allowlist `ALLOWED_TELEGRAM_CHAT_IDS`.
- Выбор приложений с поиском и переключателем системных приложений.
- Режимы приватности: полный текст, маскировка чувствительных данных или только факт события.
- RU/EN интерфейс Android-приложения с выбором языка.
- PostgreSQL на сервере при Docker-установке.
- JSON fallback для локальной разработки без PostgreSQL.
- Установка сервера одной командой: Docker, Postgres, Caddy и Cloudflare quick tunnel.

## Архитектура

```text
Android-телефон
  -> Notify Relay Android app
  -> ваш Notify Relay server
  -> Telegram Bot API
  -> ваш Telegram-чат
```

Telegram bot token никогда не попадает в Android-приложение.

## Требования

Для Android:

- Android 8.0+.
- Доступ к уведомлениям для пересылки уведомлений.
- SMS-разрешение только если нужна пересылка SMS.

Для сервера:

- Рекомендуется Linux VPS/сервер.
- `curl` и `tar` для standalone installer.
- Docker установится автоматически, если отсутствует.
- Caddy устанавливается автоматически на apt-based системах только при выборе режима своего домена.

## Создание Telegram-Бота

1. Откройте Telegram и напишите `@BotFather`.
2. Выполните `/newbot`.
3. Выберите имя и username бота.
4. Скопируйте bot token. Он выглядит как `<number>:<secret>`.
5. Никому не показывайте токен. Он вводится только в server installer.

## Как Узнать Telegram Chat ID

Если нужен приватный режим бота, нужен числовой Telegram chat id.

Простой вариант:

1. Временно запустите сервер без `ALLOWED_TELEGRAM_CHAT_IDS`.
2. Отправьте `/start` боту.
3. Посмотрите chat id в логах сервера или Telegram updates.
4. Запустите installer с `--reconfigure` и укажите `ALLOWED_TELEGRAM_CHAT_IDS`.

Если chat id уже известен, введите его во время установки. Несколько id указываются через запятую.

## Установка Сервера

Выполните на Linux-сервере:

```bash
curl -fsSL https://raw.githubusercontent.com/svllvsxprod/Notify_Relay/main/server/install.sh | bash
```

Скрипт спросит:

- язык: русский или английский;
- `TELEGRAM_BOT_TOKEN` от BotFather;
- разрешённые Telegram chat id. Пустое значение означает, что любой пользователь, отправивший `/start`, сможет привязаться;
- режим доступа: свой домен через Caddy или Cloudflare quick tunnel.

Installer скачивает серверные файлы из GitHub в:

```text
/opt/notify-relay-server
```

Папку можно переопределить:

```bash
NOTIFY_RELAY_DIR=$HOME/notify-relay-server curl -fsSL https://raw.githubusercontent.com/svllvsxprod/Notify_Relay/main/server/install.sh | bash
```

### Режим Своего Домена

Выбирайте этот режим, если домен уже направлен на сервер.

Что делает installer:

- запускает Node.js приложение и PostgreSQL в Docker;
- ищет свободный локальный порт в диапазоне `18000..18999`;
- публикует приложение только на `127.0.0.1:<free_port>`;
- устанавливает Caddy на хост, если он не установлен и доступен apt;
- дописывает блок в `/etc/caddy/Caddyfile`, не перезаписывая файл;
- перезагружает Caddy.

Backend URL будет:

```text
https://your-domain.example
```

### Режим Cloudflare Quick Tunnel

Выбирайте этот режим, если домена нет.

Что делает installer:

- запускает Node.js приложение и PostgreSQL в Docker;
- запускает `cloudflared` в Docker;
- не публикует внешний порт;
- показывает, как посмотреть tunnel URL.

Получить URL:

```bash
cd /opt/notify-relay-server
docker compose logs -f tunnel
```

Важно: URL Cloudflare quick tunnel может измениться после перезапуска tunnel/container/server. Запишите текущий URL в Android-приложение и проверьте его снова, если приложение перестало подключаться к серверу.

## Статус Сервера И Повторная Настройка

Повторный запуск installer покажет статус и текущий адрес:

```bash
curl -fsSL https://raw.githubusercontent.com/svllvsxprod/Notify_Relay/main/server/install.sh | bash
```

Полная повторная настройка:

```bash
curl -fsSL https://raw.githubusercontent.com/svllvsxprod/Notify_Relay/main/server/install.sh | bash -s -- --reconfigure
```

Полезные команды после установки:

```bash
cd /opt/notify-relay-server
docker compose ps
docker compose logs -f app
docker compose logs -f tunnel
docker compose pull
docker compose up -d --build
```

## Установка Android-Приложения

1. Откройте последний GitHub Release.
2. Скачайте APK с именем вида `Notify-Relay-v1.5.3-release.apk`.
3. Установите APK на Android-устройство.
4. Откройте приложение.
5. Введите URL сервера.
6. Нажмите проверку сервера.
7. Откройте Telegram-бота и отправьте `/start`.
8. Введите 6 цифр из сообщения бота в приложении.
9. Выдайте доступ к уведомлениям.
10. Выберите приложения, уведомления которых нужно пересылать.
11. Опционально: включите SMS и выдайте SMS-разрешение.

## Настройки Android-Приложения

- Уведомления: включает или выключает пересылку уведомлений глобально.
- SMS: включает или выключает пересылку SMS глобально.
- Приватность: определяет, сколько текста отправлять в Telegram.
- Язык: системный, русский или английский.
- Показывать системные приложения: управляет отображением системных приложений в списке.

## Режимы Приватности

- Полный текст: отправляет содержимое уведомлений и SMS без маскировки.
- Маскировка: оставляет полезный контекст, но скрывает коды, номера телефонов, карты и email-like значения.
- Только факт события: отправляет только факт события без текста.

## Модель Безопасности

- Telegram bot token хранится только на вашем сервере.
- Android хранит только device id и device token.
- Сервер поддерживает приватный режим бота через `ALLOWED_TELEGRAM_CHAT_IDS`.
- `.env`, runtime database, локальные Android SDK paths и build outputs игнорируются git.
- В публичном репозитории используются только placeholders.

## Структура Проекта

```text
android-app/  Android-приложение на Kotlin и Jetpack Compose
server/       Node.js backend, Docker deployment, PostgreSQL storage
screens/      Публичные скриншоты и логотип для документации
```

## Разработка

Собрать debug APK локально:

```bash
cd android-app
./gradlew :app:assembleDebug
```

Запустить сервер локально без Postgres:

```bash
cd server
npm install
npm run dev
```

Для Android emulator используйте:

```text
http://10.0.2.2:8000
```
