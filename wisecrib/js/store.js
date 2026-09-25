// Single JSON state with write-through persistence.
// API: load(), getState(), update(mutator), subscribe(fn), reset(), select(fn).
// Services are the only modules that call update(); each keeps its signature so a backend can replace it.
import * as storage from './storage.js';
import { emit } from './bus.js';
import { APP } from './config.js';
import { dateKey, addDays } from './format.js';

export const CURRENT = APP.schemaVersion; // 1

// Migrations run in order while state.schemaVersion < CURRENT. Pattern for a future v2:
//   1: (s) => ({ ...s, schemaVersion: 2, bookmarks: [] }),
const MIGRATIONS = {};

export function defaults() {
  return {
    schemaVersion: CURRENT,
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
const subscribers = new Set();

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

function migrate(input) {
  let s = input;
  while ((s.schemaVersion || 0) < CURRENT) {
    const step = MIGRATIONS[s.schemaVersion || 0];
    if (!step) {
      s = { ...s, schemaVersion: CURRENT };
      break;
    }
    s = step(s);
  }
  return s;
}

function validate(input) {
  if (!isObj(input)) throw new storage.CorruptDataError('Saved state is not an object.');
  const base = defaults();
  const s = { ...base, schemaVersion: CURRENT };
  for (const key of Object.keys(base)) {
    if (key === 'schemaVersion') continue;
    const def = base[key];
    const got = input[key];
    if (Array.isArray(def)) s[key] = Array.isArray(got) ? got : [];
    else if (isObj(def)) s[key] = isObj(got) ? { ...def, ...got } : def; // shallow fill per section
  }
  // Nested arrays that must be arrays.
  if (!Array.isArray(s.profile.interests)) s.profile.interests = [];
  if (!Array.isArray(s.activity.rewardedReadTs)) s.activity.rewardedReadTs = [];
  if (!Array.isArray(s.stats.openedIds)) s.stats.openedIds = [];
  if (!isObj(s.stats.categoryCounts)) s.stats.categoryCounts = {};

  // The ledger is the source of truth for the balance.
  let sum = 0;
  let earned = 0;
  let redeemed = 0;
  for (const entry of s.ledger) {
    const amount = Number(entry?.amount) || 0;
    sum += amount;
    if (amount > 0) earned += amount;
    else redeemed -= amount;
  }
  if (sum !== s.wallet.balance) {
    console.warn(`[store] wallet balance ${s.wallet.balance} did not match ledger ${sum}; recomputed.`);
    s.wallet = { ...s.wallet, balance: sum, lifetimeEarned: earned, lifetimeRedeemed: redeemed };
  }

  // Keep only the last 30 days of daily quiz / mock test records.
  const cutoff = addDays(dateKey(), -30);
  for (const key of ['dailyQuiz', 'mockTest']) {
    for (const day of Object.keys(s[key])) if (day < cutoff) delete s[key][day];
  }
  return s;
}

export function load() {
  const saved = storage.get('state'); // throws CorruptDataError on invalid JSON
  state = saved ? validate(migrate(saved)) : defaults();
  if (saved) storage.set('state', state);
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

// The mutator receives a draft copy; whatever it returns is returned to the caller.
// All of a mutation happens in one call, so checks and awards are atomic.
export function update(mutator) {
  const draft = structuredClone(state);
  const result = mutator(draft);
  state = draft;
  storage.set('state', state);
  notify();
  return result;
}

export function subscribe(fn) {
  subscribers.add(fn);
  return () => subscribers.delete(fn);
}

// Clears persisted data. Defaults are kept in memory and only persisted on the next update.
export function reset() {
  storage.remove('state');
  state = defaults();
  emit('store:reset');
  notify();
}
