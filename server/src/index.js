import 'dotenv/config';
import express from 'express';
import { nanoid } from 'nanoid';
import { createStorage } from './storage.js';

const app = express();
const port = Number(process.env.PORT || 8000);
const telegramBotToken = process.env.TELEGRAM_BOT_TOKEN || '';
const fallbackTelegramChatId = process.env.TELEGRAM_CHAT_ID || '';
const allowedTelegramChatIds = parseChatIdList(process.env.ALLOWED_TELEGRAM_CHAT_IDS || '');
const pairingTtlMs = 10 * 60 * 1000;
const seenEventTtlMs = Number(process.env.SEEN_EVENT_TTL_DAYS || 14) * 24 * 60 * 60 * 1000;
const staleDeviceTtlMs = Number(process.env.STALE_DEVICE_TTL_DAYS || 180) * 24 * 60 * 60 * 1000;
const cleanupIntervalMs = 60 * 60 * 1000;

const storage = await createStorage({
  dataFile: process.env.DATA_FILE,
  seenEventTtlMs,
  staleDeviceTtlMs,
});
let telegramOffset = 0;

app.use(express.json({ limit: '1mb' }));

app.get('/health', (_req, res) => {
  res.json({ ok: true, version: '1.0.0' });
});

app.post('/v1/devices/register', async (req, res) => {
  await cleanupDb();
  const { pairing_code: rawPairingCode, device_name: deviceName, platform, app_version: appVersion } = req.body || {};
  const pairingCode = normalizePairingCode(rawPairingCode);
  const pairing = await storage.getPairing(pairingCode);

  if (!pairing) return res.status(400).json({ error: 'invalid_pairing_code' });
  if (pairing.expiresAt < Date.now()) {
    await storage.deletePairing(pairingCode);
    return res.status(410).json({ error: 'expired_pairing_code' });
  }

  const deviceId = `dev_${nanoid(10)}`;
  const deviceToken = nanoid(40);
  await storage.createDevice({
    deviceId,
    deviceToken,
    deviceName: deviceName || 'Android device',
    platform: platform || 'android',
    appVersion: appVersion || 'dev',
    chatId: pairing.chatId,
    telegramLinked: Boolean(pairing.chatId),
    active: true,
    createdAt: Date.now(),
    lastSeenAt: Date.now(),
    lastEventAt: 0,
  });
  await storage.deletePairing(pairingCode);

  void sendTelegramMessage(pairing.chatId, formatServiceMessage('Устройство привязано', deviceName || 'Android device')).catch((error) => console.error(error.message));
  res.json({ device_id: deviceId, device_token: deviceToken, telegram_linked: true });
});

app.post('/v1/events/batch', requireDevice, async (req, res) => {
  const events = Array.isArray(req.body?.events) ? req.body.events : [];
  const accepted = [];
  const duplicates = [];
  const rejected = [];

  for (const event of events) {
    if (!event?.event_id || !event?.idempotency_key || !event?.type || !event?.timestamp) {
      rejected.push({ event_id: event?.event_id || 'unknown', reason: 'invalid_payload' });
      continue;
    }
    if (await storage.hasSeenEvent(event.idempotency_key)) {
      duplicates.push(event.event_id);
      continue;
    }

    const message = formatTelegramMessage(event);
    console.log(message);
    try {
      await sendTelegramMessage(req.device.chatId, message);
    } catch (error) {
      rejected.push({ event_id: event.event_id, reason: 'telegram_send_failed' });
      console.error(error.message);
      continue;
    }

    await storage.markSeenEvent(event.idempotency_key);
    accepted.push(event.event_id);
  }

  if (accepted.length) {
    await storage.updateDevice(req.device.deviceId, { lastEventAt: Date.now(), lastSeenAt: Date.now() });
  }
  res.json({ accepted, duplicates, rejected });
});

app.post('/v1/devices/me/test', requireDevice, async (req, res) => {
  const message = formatServiceMessage('Тестовое сообщение', `${req.device.deviceName}: ${req.body?.message || 'Тест из Android-приложения'}`);
  console.log(message);
  try {
    await sendTelegramMessage(req.device.chatId, message);
  } catch (error) {
    return res.status(502).json({ error: 'telegram_send_failed' });
  }
  res.json({ ok: true });
});

app.get('/v1/devices/me', requireDevice, (req, res) => {
  res.json({ device_id: req.device.deviceId, telegram_linked: req.device.telegramLinked, active: req.device.active });
});

app.delete('/v1/devices/me', requireDevice, async (req, res) => {
  await storage.updateDevice(req.device.deviceId, { active: false, revokedAt: Date.now(), lastSeenAt: Date.now() });
  res.json({ ok: true });
});

async function requireDevice(req, res, next) {
  const deviceId = req.header('x-device-id');
  const auth = req.header('authorization') || '';
  const token = auth.startsWith('Bearer ') ? auth.slice('Bearer '.length) : '';
  const device = deviceId ? await storage.getDevice(deviceId) : null;

  if (!device || !token) return res.status(401).json({ error: 'unauthorized' });
  if (device.deviceToken !== token || !device.active) return res.status(403).json({ error: 'forbidden' });
  await storage.updateDevice(device.deviceId, { lastSeenAt: Date.now() });
  req.device = device;
  next();
}

async function startTelegramPolling() {
  if (!telegramBotToken) return;
  console.log('Telegram pairing bot polling enabled');
  while (true) {
    try {
      const updates = await telegramApi('getUpdates', { offset: telegramOffset + 1, timeout: 25, allowed_updates: ['message'] });
      for (const update of updates.result || []) {
        telegramOffset = update.update_id;
        const message = update.message;
        const text = message?.text?.trim() || '';
        if (!message?.chat?.id || !text.startsWith('/start')) continue;
        const chatId = String(message.chat.id);
        if (!isAllowedTelegramChat(chatId)) {
          await sendTelegramMessage(chatId, formatServiceMessage('Доступ запрещён', 'Этот бот работает в приватном режиме. Ваш chat id не разрешён.'));
          continue;
        }
        const code = await createPairingCode(chatId);
        await sendTelegramMessage(chatId, formatServiceMessage('Код привязки', `<code>${escapeHtml(code)}</code>\nВведите код в Notify Relay в течение 10 минут.`, true));
      }
    } catch (error) {
      console.error(`Telegram polling failed: ${error.message}`);
      await sleep(5000);
    }
  }
}

async function createPairingCode(chatId) {
  if (!isAllowedTelegramChat(chatId)) throw new Error('Telegram chat is not allowed');
  await cleanupDb();
  let code;
  do {
    code = `${randomDigits(3)}-${randomDigits(3)}`;
  } while (await storage.getPairing(code));
  await storage.createPairing(code, { chatId, expiresAt: Date.now() + pairingTtlMs, createdAt: Date.now() });
  return code;
}

async function cleanupDb() {
  await storage.cleanup();
}

function normalizePairingCode(value) {
  const digits = String(value || '').replace(/\D/g, '');
  if (digits.length !== 6) return '';
  return `${digits.slice(0, 3)}-${digits.slice(3)}`;
}

function randomDigits(length) {
  return String(Math.floor(Math.random() * 10 ** length)).padStart(length, '0');
}

function parseChatIdList(value) {
  return new Set(
    String(value || '')
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean),
  );
}

function isAllowedTelegramChat(chatId) {
  return allowedTelegramChatIds.size === 0 || allowedTelegramChatIds.has(String(chatId));
}

function formatTelegramMessage(event) {
  const typeLabel = event.type === 'sms' ? 'SMS' : 'Уведомление';
  const icon = event.type === 'sms' ? '✉️' : '🔔';
  const sourceLabel = event.type === 'sms' ? 'Отправитель' : 'Приложение';
  const source = event.source?.app_label || event.source?.sender || event.source?.package_name || 'Неизвестно';
  const title = event.content?.title || event.content?.sub_text || '';
  const text = event.content?.big_text || event.content?.text || 'Новое событие';
  const timestamp = formatTimestamp(event.timestamp);

  const lines = [
    `${icon} <b>${escapeHtml(typeLabel)}</b>`,
    `<b>${sourceLabel}:</b> ${escapeHtml(source)}`,
    `<b>Время:</b> ${escapeHtml(timestamp)}`,
  ];

  if (title && title !== text) {
    lines.push('', `<b>${escapeHtml(title)}</b>`);
  }

  lines.push('', escapeHtml(text));

  return lines.join('\n');
}

async function sendTelegramMessage(chatId, text) {
  const destinationChatId = chatId || fallbackTelegramChatId;
  if (!telegramBotToken || !destinationChatId) return;
  const response = await telegramApi('sendMessage', {
    chat_id: destinationChatId,
    text,
    parse_mode: 'HTML',
    disable_web_page_preview: true,
  });
  if (!response.ok) throw new Error(response.description || 'Telegram send failed');
}

function formatServiceMessage(title, body, bodyIsHtml = false) {
  return `✅ <b>${escapeHtml(title)}</b>\n\n${bodyIsHtml ? body : escapeHtml(body)}`;
}

function formatTimestamp(value) {
  const date = new Date(Number(value) || Date.now());
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

async function telegramApi(method, payload) {
  const response = await fetch(`https://api.telegram.org/bot${telegramBotToken}/${method}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error(`Telegram API ${method} failed with ${response.status}`);
  return response.json();
}

function sleep(ms) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, ms));
}

app.listen(port, '0.0.0.0', () => {
  console.log(`Notify Relay server listening on http://0.0.0.0:${port}`);
  console.log(telegramBotToken ? 'Telegram delivery enabled' : 'Telegram delivery disabled; using console preview');
  console.log(allowedTelegramChatIds.size > 0 ? `Telegram private mode enabled for ${allowedTelegramChatIds.size} chat(s)` : 'Telegram private mode disabled');
  console.log(`Storage: ${storage.kind}`);
  void cleanupDb();
  setInterval(() => void cleanupDb(), cleanupIntervalMs).unref();
  void startTelegramPolling();
});
