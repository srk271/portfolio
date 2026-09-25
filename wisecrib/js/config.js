// WiSeCRiB configuration: ALL demo economics, app constants and unit-economics defaults.
// The "How rewards work" screen is generated from these values so the copy always matches the rules.
//
// Local testing knob: on localhost / 127.0.0.1 only, a JSON object stored in
// localStorage['wisecrib:dev-config'] is deep-merged over ECONOMY (e.g. {"read":{"minDwellSeconds":2}}).
// It is inert on any deployed host.

const BASE_ECONOMY = {
  currency: { code: 'INR', symbol: '₹', locale: 'en-IN' },
  coinsPerRupee: 100, // 1 coin = 1 paisa; 100 coins = ₹1
  welcomeBonus: 100,
  read: { baseCoins: 3, wpm: 200, dwellFraction: 0.4, minDwellSeconds: 20, maxRewardedPer10Min: 3 },
  articleQuiz: { questions: 3, passMark: 2, passCoins: 5 },
  dailyQuiz: { questions: 8, coinsPerCorrect: 1, perfectBonus: 2 },
  mockTest: { questions: 15, coinsPerCorrect: 1, minutes: 10 },
  dailyCap: { free: 60, premium: 90 }, // activity coins/day: reads + quizzes + rewarded video
  premium: {
    monthlyPrice: 49,
    yearlyPrice: 399,
    multiplier: 1.5, // multiplier on reads + article quizzes only
    streakProtectionEveryDays: 7 // one missed day forgiven, at most once per 7 days
  },
  dailyBonus: { base: 3, perStreakDay: 1, maxFree: 10, premiumMultiplier: 2 },
  rewardedVideo: { coins: 2, maxPerDay: 3, seconds: 5 },
  referral: { codePrefix: 'WSC-', reward: 50, requiredVerifiedReads: 3 },
  redemption: {
    free: { minRupees: 50, feeRate: 0.10 },
    premium: { minRupees: 20, feeRate: 0 },
    charityFeeRate: 0,
    cashPresetsRupees: [20, 50, 100],
    maxCashRupees: 5000
  },
  funding: { adRevenueShareToRewards: 0.5 }, // explanatory only (How rewards work)
  demoCredit: 10000
};

const BASE_UNIT_ECONOMICS = {
  mau: 100000,
  readsPerUserPerDay: 3,
  adImpressionsPerRead: 2,
  ecpmRupees: 30,
  payoutPerVerifiedReadRupees: 0.08,
  brandFundedShare: 0.30,
  premiumConversion: 0.03,
  premiumPriceRupees: 49
};

const BASE_APP = {
  name: 'WiSeCRiB',
  tagline: 'Read the news. Prove it with a quiz. Earn rewards.',
  version: '1.0.0',
  cacheVersion: 'wisecrib-v2', // mirror of CACHE in sw.js, shown in Settings
  schemaVersion: 1,
  feedAdEvery: 5,
  // Demo article dates are written relative to this anchor, then shifted so it maps to "now" at load
  // (see data/articles.js): dates stay current and the recency ranking stays stable.
  contentAnchor: '2026-09-24T21:00:00+05:30',
  otp: { digits: 4, maxAttempts: 3 },
  planTargets: { usersYear1: 100000, revenueYear1Usd: 300000, marketCapUsd: 10000000 }
};

function isPlainObject(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

// Merge only keys that already exist in the base, so a typo cannot invent new config.
function deepMerge(base, patch) {
  const out = Array.isArray(base) ? [...base] : { ...base };
  if (!isPlainObject(patch)) return out;
  for (const [key, value] of Object.entries(patch)) {
    if (!(key in out)) continue;
    if (isPlainObject(value) && isPlainObject(out[key])) out[key] = deepMerge(out[key], value);
    else if (typeof value === typeof out[key] || Array.isArray(out[key])) out[key] = value;
  }
  return out;
}

function deepFreeze(obj) {
  for (const value of Object.values(obj)) {
    if (value && typeof value === 'object' && !Object.isFrozen(value)) deepFreeze(value);
  }
  return Object.freeze(obj);
}

function readDevOverrides() {
  try {
    const host = globalThis.location?.hostname;
    if (host !== 'localhost' && host !== '127.0.0.1') return null;
    const raw = globalThis.localStorage?.getItem('wisecrib:dev-config');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

const dev = readDevOverrides();

export const ECONOMY = deepFreeze(dev ? deepMerge(BASE_ECONOMY, dev) : structuredClone(BASE_ECONOMY));
export const CONFIG = ECONOMY;
export const UNIT_ECONOMICS_DEFAULTS = deepFreeze({ ...BASE_UNIT_ECONOMICS });
export const APP = deepFreeze({ ...structuredClone(BASE_APP), devOverridesActive: Boolean(dev) });
