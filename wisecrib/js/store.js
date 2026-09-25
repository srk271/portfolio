// Single JSON state with write-through persistence, kept consistent across tabs.
// API: load(), getState(), update(mutator), subscribe(fn), reset(), select(fn).
// Services are the only modules that call update(); each keeps its signature so a backend can replace it.
//
// Several tabs share one localStorage copy. Every write stamps state.rev (+1). Before applying a mutator,
// update() re-reads storage and adopts the persisted copy if another tab wrote since this tab last read or
// wrote it, so a stale tab never overwrites newer data. A `storage` listener adopts other tabs' writes
// eagerly, notifies subscribers and emits 'store:external' so the router re-renders the current view.
import * as storage from './storage.js';
import { emit } from './bus.js';
import { APP } from './config.js';
import { dateKey, addDays } from './format.js';

export const CURRENT = APP.schemaVersion; // 1
const KEY = 'state';

// Saved data written by a newer version of the app (higher schemaVersion). It is never downgraded:
// the app stops writing and asks for a reload instead.
export class NewerDataError extends Error {
  constructor(message) {
    super(message);
    this.name = 'NewerDataError';
  }
}

// Migrations run in order while state.schemaVersion < CURRENT. Pattern for a future v2:
//   1: (s) => ({ ...s, schemaVersion: 2, bookmarks: [] }),
const MIGRATIONS = {};

export function defaults() {
  return {
    schemaVersion: CURRENT,
    rev: 0, // write counter, stamped by update()
    profile: { id: null, name: '', goal: null, interests: [], createdAt: null, onboarded: false },
    premium: { active: false, plan: null, since: null, renewsAt: null },
    wallet: { balance: 0, lifetimeEarned: 0, lifetimeRedeemed: 0 },
    ledger: [],
    reads: {},
    quizzes: {},
    dailyQuiz: {},
    mockTest: {},
    bonus: { lastClaimDate: null, totalClaims: 0 },
    streak: { current: 0, best: 0, lastReadDate: null, protectedOn: null },
    referral: { code: null, enteredCode: null, enteredAt: null, status: 'none', verifiedReadsAtEntry: 0, paidAt: null, shares: 0 },
    payout: { verified: false, upiId: null, phoneMasked: null, verifiedAt: null },
    activity: { date: null, coinsEarned: 0, rewardedReadTs: [], rewardedAdsToday: 0 },
    redemptions: [],
    stats: {
      readingMs: 0,
      verifiedReads: 0,
      categoryCounts: {},
      adImpressions: 0,
      sponsoredReads: 0,
      quizAnswered: 0,
      quizCorrect: 0,
      openedIds: []
    },
    settings: { theme: 'system', reduceMotion: 'system', newsletter: false }
  };
}

let state = defaults();
let stateRaw = JSON.stringify(state); // serialized `state`, for cheap no-op detection
let lastRaw = null; // the exact string this tab last read from or wrote to storage
let halted = null; // error that stopped persistence (unreadable or newer data written by another tab)
let writeWarned = false;
let watching = false;
const subscribers = new Set();

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const isFiniteNum = (v) => typeof v === 'number' && Number.isFinite(v);

function migrate(input) {
  if (!isObj(input)) throw new storage.CorruptDataError('Saved state is not an object.');
  const version = Number.isInteger(input.schemaVersion) && input.schemaVersion >= 0 ? input.schemaVersion : 0;
  if (version > CURRENT) {
    throw new NewerDataError(`Your saved progress was written by a newer version of ${APP.name} (data version ${version}; this version reads ${CURRENT}).`);
  }
  let s = { ...input, schemaVersion: version };
  while (s.schemaVersion < CURRENT) {
    const step = MIGRATIONS[s.schemaVersion];
    if (!step) {
      s = { ...s, schemaVersion: CURRENT };
      break;
    }
    s = step(s);
  }
  return s;
}

// ---- Validation: every known field is coerced to its expected type, so hand-edited or partially
// written data can't turn into NaN / string arithmetic later (e.g. "3" + 1 === "31").
const DATE_KEY_RE = /^\d{4}-\d{2}-\d{2}$/;
const DATE_FIELDS = new Set(['bonus.lastClaimDate', 'streak.lastReadDate', 'streak.protectedOn', 'activity.date']);
const TIMESTAMP_FIELDS = new Set(['profile.createdAt', 'premium.since', 'premium.renewsAt', 'referral.enteredAt', 'referral.paidAt', 'payout.verifiedAt']);
const ENUMS = {
  'premium.plan': [null, 'monthly', 'yearly'],
  'referral.status': ['none', 'pending', 'paid'],
  'settings.theme': ['system', 'light', 'dark'],
  'settings.reduceMotion': ['system', 'on']
};

// Non-negative integer (numbers or numeric strings), else the fallback.
function toCount(v, fallback) {
  const n = typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN;
  return Number.isFinite(n) ? Math.max(0, Math.trunc(n)) : fallback;
}

function coerceField(path, def, v) {
  if (ENUMS[path]) return ENUMS[path].includes(v) ? v : def;
  if (DATE_FIELDS.has(path)) return typeof v === 'string' && DATE_KEY_RE.test(v) ? v : null;
  if (TIMESTAMP_FIELDS.has(path)) return isFiniteNum(v) ? v : null;
  if (typeof def === 'number') return toCount(v, def);
  if (typeof def === 'boolean') return typeof v === 'boolean' ? v : def;
  if (typeof def === 'string') return typeof v === 'string' ? v : def;
  if (def === null) return typeof v === 'string' ? v : null; // ids, codes, names
  if (Array.isArray(def)) return Array.isArray(v) ? v : [];
  if (isObj(def)) return isObj(v) ? v : {};
  return v;
}

// Own enumerable keys only; '__proto__' keys from JSON are dropped.
function objectEntries(v, keep) {
  const out = {};
  if (!isObj(v)) return out;
  for (const [k, val] of Object.entries(v)) {
    if (k !== '__proto__' && keep(val)) out[k] = val;
  }
  return out;
}

function validate(input) {
  if (!isObj(input)) throw new storage.CorruptDataError('Saved state is not an object.');
  const base = defaults();
  const s = { schemaVersion: CURRENT, rev: toCount(input.rev, 0) };
  for (const key of Object.keys(base)) {
    if (key === 'schemaVersion' || key === 'rev') continue;
    const def = base[key];
    const got = input[key];
    if (Array.isArray(def)) {
      s[key] = Array.isArray(got) ? got.filter(isObj) : [];
    } else if (isObj(def)) {
      const section = objectEntries(got, () => true);
      for (const field of Object.keys(def)) section[field] = coerceField(`${key}.${field}`, def[field], section[field]);
      s[key] = section;
    }
  }
  // Record maps keyed by article id / date: keep object-valued entries only.
  for (const key of ['reads', 'quizzes', 'dailyQuiz', 'mockTest']) s[key] = objectEntries(s[key], isObj);

  s.profile.interests = s.profile.interests.filter((k) => typeof k === 'string');
  s.activity.rewardedReadTs = s.activity.rewardedReadTs.filter(isFiniteNum);
  s.stats.openedIds = [...new Set(s.stats.openedIds.filter((id) => typeof id === 'string' && id))];
  s.stats.categoryCounts = Object.fromEntries(Object.entries(objectEntries(s.stats.categoryCounts, () => true)).map(([k, v]) => [k, toCount(v, 0)]));

  // The ledger is the source of truth for the balance and lifetime totals.
  let sum = 0;
  let earned = 0;
  let redeemed = 0;
  for (const entry of s.ledger) {
    const amount = Math.trunc(Number(entry.amount)) || 0;
    entry.amount = amount;
    sum += amount;
    if (amount > 0) earned += amount;
    else redeemed -= amount;
  }
  if (sum !== s.wallet.balance) console.warn(`[store] wallet balance ${s.wallet.balance} did not match ledger ${sum}; recomputed.`);
  s.wallet = { ...s.wallet, balance: sum, lifetimeEarned: earned, lifetimeRedeemed: redeemed };

  // Keep only the last 30 days of daily quiz / mock test records.
  const cutoff = addDays(dateKey(), -30);
  for (const key of ['dailyQuiz', 'mockTest']) {
    for (const day of Object.keys(s[key])) if (day < cutoff) delete s[key][day];
  }
  return s;
}

// raw string (or null) -> validated state. Throws CorruptDataError / NewerDataError.
function parse(raw) {
  if (raw == null) return defaults();
  let data;
  try {
    data = JSON.parse(raw);
  } catch {
    throw new storage.CorruptDataError('Saved data is not valid JSON.');
  }
  return validate(migrate(data));
}

function setState(next) {
  state = next;
  stateRaw = JSON.stringify(next);
}

function persist() {
  if (halted) return;
  const ok = storage.setRaw(KEY, stateRaw);
  lastRaw = storage.getRaw(KEY); // what storage holds now (the previous copy if this write failed)
  if (!ok && storage.available() && !writeWarned) {
    writeWarned = true;
    emit('toast', { message: "Couldn't save progress on this device (storage full or blocked). Changes last until you close this tab.", kind: 'warn', duration: 7000 });
  }
}

function halt(err) {
  halted = err;
  emit('store:halted', err);
}

// Adopts the persisted copy if another tab wrote it since this tab last read or wrote storage.
// Returns 'reset' (another tab cleared the data), 'changed', or null (nothing new).
function syncFromStorage() {
  if (halted) return null;
  const raw = storage.getRaw(KEY);
  if (raw === lastRaw) return null;
  let next;
  try {
    next = parse(raw);
  } catch (err) {
    halt(err);
    return null;
  }
  lastRaw = raw;
  setState(next);
  return raw == null ? 'reset' : 'changed';
}

function announceExternal(kind) {
  if (kind === 'reset') emit('store:reset');
  notify();
  emit('store:external', { reset: kind === 'reset' });
}

function onStorageEvent(e) {
  if (e.key !== null && e.key !== storage.storageKey(KEY)) return; // null: localStorage.clear()
  const kind = syncFromStorage();
  if (kind) announceExternal(kind);
}

export function load() {
  const raw = storage.getRaw(KEY);
  setState(parse(raw)); // throws CorruptDataError / NewerDataError
  lastRaw = raw;
  if (raw != null && raw !== stateRaw) persist(); // write back migrated / normalized data
  if (!watching && typeof window !== 'undefined') {
    watching = true;
    window.addEventListener('storage', onStorageEvent);
  }
  return state;
}

export const getState = () => state;
export const select = (fn) => fn(state);
export const storageAvailable = () => storage.available();

function notify() {
  for (const fn of [...subscribers]) {
    try {
      fn(state);
    } catch (err) {
      console.error('[store] subscriber failed', err);
    }
  }
  emit('state:changed', state);
}

// The mutator receives a draft copy of the latest state (re-read from storage if another tab wrote);
// whatever it returns is returned to the caller. All of a mutation happens in one call, so checks and
// awards are atomic. A mutation that changes nothing is not written.
export function update(mutator) {
  const external = syncFromStorage();
  if (external) announceExternal(external); // adopt the other tab's change first, even if the mutator throws
  const draft = structuredClone(state);
  const result = mutator(draft);
  if (JSON.stringify(draft) !== stateRaw) {
    draft.rev = state.rev + 1;
    setState(draft);
    persist();
    notify();
  }
  return result;
}

export function subscribe(fn) {
  subscribers.add(fn);
  return () => subscribers.delete(fn);
}

// Clears persisted data. Defaults are kept in memory and only persisted on the next update.
export function reset() {
  storage.remove(KEY);
  lastRaw = storage.getRaw(KEY);
  setState(defaults());
  emit('store:reset');
  notify();
}
