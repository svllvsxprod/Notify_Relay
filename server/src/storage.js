import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import pg from 'pg';

export async function createStorage({ dataFile, seenEventTtlMs, staleDeviceTtlMs }) {
  if (process.env.DATABASE_URL) {
    const storage = new PostgresStorage(process.env.DATABASE_URL, seenEventTtlMs, staleDeviceTtlMs);
    await storage.init();
    return storage;
  }

  return new JsonStorage(resolve(dataFile || './data/notify-relay.json'), seenEventTtlMs, staleDeviceTtlMs);
}

class JsonStorage {
  constructor(dbPath, seenEventTtlMs, staleDeviceTtlMs) {
    this.kind = 'json';
    this.dbPath = dbPath;
    this.seenEventTtlMs = seenEventTtlMs;
    this.staleDeviceTtlMs = staleDeviceTtlMs;
    this.db = this.loadDb();
  }

  async cleanup() {
    const now = Date.now();
    for (const [code, pairing] of Object.entries(this.db.pairingCodes)) {
      if (pairing.expiresAt < now) delete this.db.pairingCodes[code];
    }

    for (const [idempotencyKey, seenAt] of Object.entries(this.db.seenEvents)) {
      if (now - Number(seenAt) > this.seenEventTtlMs) delete this.db.seenEvents[idempotencyKey];
    }

    for (const [deviceId, device] of Object.entries(this.db.devices)) {
      const lastSeenAt = Number(device.lastSeenAt || device.createdAt || 0);
      if ((!device.active || now - lastSeenAt > this.staleDeviceTtlMs) && lastSeenAt > 0) {
        delete this.db.devices[deviceId];
      }
    }

    this.saveDb();
  }

  async getPairing(code) {
    return this.db.pairingCodes[code] || null;
  }

  async createPairing(code, pairing) {
    this.db.pairingCodes[code] = pairing;
    this.saveDb();
  }

  async deletePairing(code) {
    delete this.db.pairingCodes[code];
    this.saveDb();
  }

  async createDevice(device) {
    this.db.devices[device.deviceId] = device;
    this.saveDb();
  }

  async getDevice(deviceId) {
    return this.db.devices[deviceId] || null;
  }

  async updateDevice(deviceId, patch) {
    if (!this.db.devices[deviceId]) return;
    this.db.devices[deviceId] = { ...this.db.devices[deviceId], ...patch };
    this.saveDb();
  }

  async hasSeenEvent(idempotencyKey) {
    return Boolean(this.db.seenEvents[idempotencyKey]);
  }

  async markSeenEvent(idempotencyKey) {
    this.db.seenEvents[idempotencyKey] = Date.now();
    this.saveDb();
  }

  loadDb() {
    if (!existsSync(this.dbPath)) return { devices: {}, pairingCodes: {}, seenEvents: {} };
    const loaded = JSON.parse(readFileSync(this.dbPath, 'utf8'));
    return {
      devices: loaded.devices || {},
      pairingCodes: loaded.pairingCodes || {},
      seenEvents: loaded.seenEvents || {},
    };
  }

  saveDb() {
    mkdirSync(dirname(this.dbPath), { recursive: true });
    writeFileSync(this.dbPath, JSON.stringify(this.db, null, 2));
  }
}

class PostgresStorage {
  constructor(databaseUrl, seenEventTtlMs, staleDeviceTtlMs) {
    this.kind = 'postgres';
    this.pool = new pg.Pool({ connectionString: databaseUrl });
    this.seenEventTtlMs = seenEventTtlMs;
    this.staleDeviceTtlMs = staleDeviceTtlMs;
  }

  async init() {
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS pairing_codes (
        code text PRIMARY KEY,
        chat_id text NOT NULL,
        expires_at bigint NOT NULL,
        created_at bigint NOT NULL
      );

      CREATE TABLE IF NOT EXISTS devices (
        device_id text PRIMARY KEY,
        device_token text NOT NULL,
        device_name text NOT NULL,
        platform text NOT NULL,
        app_version text NOT NULL,
        chat_id text,
        telegram_linked boolean NOT NULL DEFAULT false,
        active boolean NOT NULL DEFAULT true,
        created_at bigint NOT NULL,
        last_seen_at bigint NOT NULL,
        last_event_at bigint NOT NULL DEFAULT 0,
        revoked_at bigint
      );

      CREATE TABLE IF NOT EXISTS seen_events (
        idempotency_key text PRIMARY KEY,
        seen_at bigint NOT NULL
      );
    `);
  }

  async cleanup() {
    const now = Date.now();
    await this.pool.query('DELETE FROM pairing_codes WHERE expires_at < $1', [now]);
    await this.pool.query('DELETE FROM seen_events WHERE $1 - seen_at > $2', [now, this.seenEventTtlMs]);
    await this.pool.query(
      'DELETE FROM devices WHERE (active = false OR $1 - COALESCE(last_seen_at, created_at) > $2) AND COALESCE(last_seen_at, created_at) > 0',
      [now, this.staleDeviceTtlMs],
    );
  }

  async getPairing(code) {
    const result = await this.pool.query('SELECT code, chat_id, expires_at, created_at FROM pairing_codes WHERE code = $1', [code]);
    const row = result.rows[0];
    if (!row) return null;
    return { chatId: row.chat_id, expiresAt: Number(row.expires_at), createdAt: Number(row.created_at) };
  }

  async createPairing(code, pairing) {
    await this.pool.query(
      'INSERT INTO pairing_codes (code, chat_id, expires_at, created_at) VALUES ($1, $2, $3, $4)',
      [code, pairing.chatId, pairing.expiresAt, pairing.createdAt],
    );
  }

  async deletePairing(code) {
    await this.pool.query('DELETE FROM pairing_codes WHERE code = $1', [code]);
  }

  async createDevice(device) {
    await this.pool.query(
      `INSERT INTO devices (
        device_id, device_token, device_name, platform, app_version, chat_id, telegram_linked,
        active, created_at, last_seen_at, last_event_at, revoked_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)`,
      [
        device.deviceId,
        device.deviceToken,
        device.deviceName,
        device.platform,
        device.appVersion,
        device.chatId,
        device.telegramLinked,
        device.active,
        device.createdAt,
        device.lastSeenAt,
        device.lastEventAt,
        device.revokedAt || null,
      ],
    );
  }

  async getDevice(deviceId) {
    const result = await this.pool.query('SELECT * FROM devices WHERE device_id = $1', [deviceId]);
    return result.rows[0] ? mapDevice(result.rows[0]) : null;
  }

  async updateDevice(deviceId, patch) {
    const current = await this.getDevice(deviceId);
    if (!current) return;
    await this.pool.query(
      `UPDATE devices SET
        device_token = $2,
        device_name = $3,
        platform = $4,
        app_version = $5,
        chat_id = $6,
        telegram_linked = $7,
        active = $8,
        created_at = $9,
        last_seen_at = $10,
        last_event_at = $11,
        revoked_at = $12
      WHERE device_id = $1`,
      [
        deviceId,
        patch.deviceToken ?? current.deviceToken,
        patch.deviceName ?? current.deviceName,
        patch.platform ?? current.platform,
        patch.appVersion ?? current.appVersion,
        patch.chatId ?? current.chatId,
        patch.telegramLinked ?? current.telegramLinked,
        patch.active ?? current.active,
        patch.createdAt ?? current.createdAt,
        patch.lastSeenAt ?? current.lastSeenAt,
        patch.lastEventAt ?? current.lastEventAt,
        patch.revokedAt ?? current.revokedAt ?? null,
      ],
    );
  }

  async hasSeenEvent(idempotencyKey) {
    const result = await this.pool.query('SELECT 1 FROM seen_events WHERE idempotency_key = $1', [idempotencyKey]);
    return result.rowCount > 0;
  }

  async markSeenEvent(idempotencyKey) {
    await this.pool.query(
      'INSERT INTO seen_events (idempotency_key, seen_at) VALUES ($1, $2) ON CONFLICT (idempotency_key) DO NOTHING',
      [idempotencyKey, Date.now()],
    );
  }
}

function mapDevice(row) {
  return {
    deviceId: row.device_id,
    deviceToken: row.device_token,
    deviceName: row.device_name,
    platform: row.platform,
    appVersion: row.app_version,
    chatId: row.chat_id,
    telegramLinked: row.telegram_linked,
    active: row.active,
    createdAt: Number(row.created_at),
    lastSeenAt: Number(row.last_seen_at),
    lastEventAt: Number(row.last_event_at),
    revokedAt: row.revoked_at ? Number(row.revoked_at) : undefined,
  };
}
