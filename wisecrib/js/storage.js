// localStorage adapter: namespaced keys, JSON, try/catch, availability check.
// If storage is unavailable (private mode, blocked site data) values live in memory for the session.

const NS = 'wisecrib:';
const memory = new Map();
let availability = null;

export class CorruptDataError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CorruptDataError';
  }
}

export function available() {
  if (availability !== null) return availability;
  try {
    const probe = `${NS}__probe`;
    localStorage.setItem(probe, '1');
    localStorage.removeItem(probe);
    availability = true;
  } catch {
    availability = false;
  }
  return availability;
}

function readRaw(key) {
  if (available()) {
    try {
      return localStorage.getItem(NS + key);
    } catch {
      return memory.get(key) ?? null;
    }
  }
  return memory.get(key) ?? null;
}

// The full localStorage key for a logical key (used to match `storage` events from other tabs).
export const storageKey = (key) => NS + key;

// Raw string as currently stored (null when absent).
export const getRaw = (key) => readRaw(key);

export function get(key) {
  const raw = readRaw(key);
  if (raw == null) return null;
  try {
    return JSON.parse(raw);
  } catch {
    throw new CorruptDataError(`Saved data for "${key}" is not valid JSON.`);
  }
}

// Returns false when localStorage is available but the write failed (quota exceeded or blocked);
// the value is still kept in memory for this session.
export function setRaw(key, raw) {
  memory.set(key, raw);
  if (!available()) return false;
  try {
    localStorage.setItem(NS + key, raw);
    return true;
  } catch {
    return false;
  }
}

export const set = (key, value) => setRaw(key, JSON.stringify(value));

export function remove(key) {
  memory.delete(key);
  if (!available()) return;
  try {
    localStorage.removeItem(NS + key);
  } catch {
    /* ignore */
  }
}
