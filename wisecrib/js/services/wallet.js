// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Wallet: ledger entries, balance, the daily activity cap and rejected-event logging.
// Draft helpers (addEntry, awardCapped, ...) run inside a store.update() so checks and awards stay atomic.
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { dateKey, uid, fmtCoins } from '../format.js';

export const SOURCE_LABELS = Object.freeze({
  welcome: 'Welcome bonus',
  read: 'Verified reading',
  quiz: 'Article quiz',
  sponsor_bonus: 'Brand-funded bonus',
  daily_quiz: 'Daily quiz',
  mock_test: 'Mock test',
  daily_bonus: 'Daily bonus',
  referral: 'Referral',
  rewarded_ad: 'Sponsored video',
  demo_credit: 'Demo credit — not earnable',
  redeem_cash: 'UPI payout (simulated)',
  redeem_voucher: 'Voucher (simulated)',
  rejected: 'Not applied'
});

// Activity types count toward the daily cap; everything else is outside it.
export const CAPPED_TYPES = Object.freeze(['read', 'quiz', 'daily_quiz', 'mock_test', 'rewarded_ad']);

export function reasonText(reason) {
  switch (reason) {
    case 'velocity':
      return `Velocity limit: max ${ECONOMY.read.maxRewardedPer10Min} rewarded reads per 10 min`;
    case 'daily_cap':
      return `Daily activity cap reached (${ECONOMY.dailyCap.free} coins free · ${ECONOMY.dailyCap.premium} premium)`;
    case 'ad_limit':
      return `Sponsored video limit: ${ECONOMY.rewardedVideo.maxPerDay} per day`;
    default:
      return 'Reward not applied';
  }
}

const REJECTED_TITLES = {
  read: 'Reading reward not applied',
  quiz: 'Quiz reward not applied',
  daily_quiz: 'Daily quiz reward not applied',
  mock_test: 'Mock test reward not applied',
  rewarded_ad: 'Sponsored video reward not applied'
};

export const isPremiumState = (s) => Boolean(s?.premium?.active);
export const capFor = (s) => (isPremiumState(s) ? ECONOMY.dailyCap.premium : ECONOMY.dailyCap.free);

// Rolls the per-day activity counters when the local date changes; prunes the 10-minute velocity window.
export function rollActivity(s, now = Date.now()) {
  const today = dateKey(new Date(now));
  if (s.activity.date !== today) {
    s.activity.date = today;
    s.activity.coinsEarned = 0;
    s.activity.rewardedAdsToday = 0;
  }
  s.activity.rewardedReadTs = (s.activity.rewardedReadTs || []).filter((ts) => now - ts < 10 * 60 * 1000);
}

export function addEntry(s, { type, amount, title, reason = null, meta = null, ts = Date.now() }) {
  const entry = { id: uid('lg'), ts, type, amount, title };
  if (reason) entry.reason = reason;
  if (meta) entry.meta = meta;
  s.ledger.push(entry);
  s.wallet.balance += amount;
  if (amount > 0) s.wallet.lifetimeEarned += amount;
  else if (amount < 0) s.wallet.lifetimeRedeemed += -amount;
  return entry;
}

export function logRejected(s, { source, reason, meta = null, ts = Date.now() }) {
  return addEntry(s, {
    type: 'rejected',
    amount: 0,
    title: REJECTED_TITLES[source] || 'Reward not applied',
    reason,
    meta: { ...(meta || {}), source },
    ts
  });
}

// Activity coins pass through the daily cap. Partial awards are allowed; zero room logs a rejection.
export function awardCapped(s, { type, amount, title, meta = null, now = Date.now() }) {
  rollActivity(s, now);
  const room = Math.max(0, capFor(s) - s.activity.coinsEarned);
  const coins = Math.max(0, Math.min(amount, room));
  if (coins > 0) {
    const capped = coins < amount;
    const entry = addEntry(s, { type, amount: coins, title, meta: capped ? { ...(meta || {}), cappedFrom: amount } : meta, ts: now });
    s.activity.coinsEarned += coins;
    return { coins, reason: null, capped, entry };
  }
  const entry = logRejected(s, { source: type, reason: 'daily_cap', meta: { ...(meta || {}), wanted: amount }, ts: now });
  return { coins: 0, reason: 'daily_cap', capped: true, entry };
}

export function spend(s, { type, amount, title, meta = null }) {
  if (amount <= 0) throw new Error('spend() needs a positive amount');
  if (amount > s.wallet.balance) throw new Error('Insufficient balance');
  return addEntry(s, { type, amount: -amount, title, meta });
}

export function capProgress(state = getState()) {
  const earned = state.activity.date === dateKey() ? state.activity.coinsEarned : 0;
  const cap = capFor(state);
  return { earned, cap, remaining: Math.max(0, cap - earned), ratio: cap ? Math.min(1, earned / cap) : 0 };
}

export function addDemoCredit() {
  return update((s) => addEntry(s, { type: 'demo_credit', amount: ECONOMY.demoCredit, title: `Demo credit — not earnable (${fmtCoins(ECONOMY.demoCredit)} coins)` }));
}

export function rewardedVideosLeft(state = getState()) {
  const used = state.activity.date === dateKey() ? state.activity.rewardedAdsToday : 0;
  return Math.max(0, ECONOMY.rewardedVideo.maxPerDay - used);
}

// Opt-in sponsored video (AdMob Rewarded format, simulated). Counts toward the daily cap.
export function completeRewardedVideo() {
  return update((s) => {
    const now = Date.now();
    rollActivity(s, now);
    if (s.activity.rewardedAdsToday >= ECONOMY.rewardedVideo.maxPerDay) {
      logRejected(s, { source: 'rewarded_ad', reason: 'ad_limit', ts: now });
      return { coins: 0, reason: 'ad_limit' };
    }
    s.activity.rewardedAdsToday += 1;
    const r = awardCapped(s, { type: 'rewarded_ad', amount: ECONOMY.rewardedVideo.coins, title: 'Sponsored video watched (simulated)', now });
    return { coins: r.coins, reason: r.reason };
  });
}
