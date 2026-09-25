// Daily bonus and reading streak. Streak = consecutive local days with at least one verified read.
// Premium streak protection forgives one missed day, at most once per ECONOMY.premium.streakProtectionEveryDays.
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { dateKey, daysBetween, addDays } from '../format.js';
import { addEntry } from './wallet.js';

function canProtect(s, today) {
  if (!s.premium?.active) return false;
  const last = s.streak.protectedOn;
  return !last || daysBetween(last, today) >= ECONOMY.premium.streakProtectionEveryDays;
}

// Streak as the user should see it today (a broken streak shows 0 until the next verified read).
export function streakDisplay(state = getState(), today = dateKey()) {
  const { lastReadDate, current, best } = state.streak;
  if (!lastReadDate) return { current: 0, best, readToday: false, protectedDay: false };
  const gap = daysBetween(lastReadDate, today);
  if (gap <= 0) return { current, best, readToday: true, protectedDay: false };
  if (gap === 1) return { current, best, readToday: false, protectedDay: false };
  if (gap === 2 && canProtect(state, today)) return { current, best, readToday: false, protectedDay: true };
  return { current: 0, best, readToday: false, protectedDay: false };
}

// Draft helper, called from recordVerifiedRead inside the same update.
export function updateStreakOnRead(s, now = Date.now()) {
  const today = dateKey(new Date(now));
  const last = s.streak.lastReadDate;
  let protectedUsed = false;
  if (last) {
    const gap = daysBetween(last, today);
    if (gap <= 0) return { current: s.streak.current, changed: false, protectedUsed };
    if (gap === 1) s.streak.current += 1;
    else if (gap === 2 && canProtect(s, today)) {
      s.streak.current += 1;
      s.streak.protectedOn = addDays(today, -1);
      protectedUsed = true;
    } else s.streak.current = 1;
  } else {
    s.streak.current = 1;
  }
  s.streak.lastReadDate = today;
  s.streak.best = Math.max(s.streak.best || 0, s.streak.current);
  return { current: s.streak.current, changed: true, protectedUsed };
}

export function dailyBonusAmount(state = getState()) {
  const { base, perStreakDay, maxFree, premiumMultiplier } = ECONOMY.dailyBonus;
  const streak = streakDisplay(state).current;
  const amount = Math.min(base + perStreakDay * Math.max(0, streak - 1), maxFree);
  return state.premium.active ? amount * premiumMultiplier : amount;
}

export function canClaim(state = getState()) {
  return state.bonus.lastClaimDate !== dateKey();
}

export function claim() {
  return update((s) => {
    const today = dateKey();
    if (s.bonus.lastClaimDate === today) return { already: true, amount: 0 };
    const amount = dailyBonusAmount(s);
    const streak = streakDisplay(s, today).current;
    addEntry(s, { type: 'daily_bonus', amount, title: streak > 1 ? `Daily bonus · ${streak}-day streak` : 'Daily bonus', meta: { streak } });
    s.bonus.lastClaimDate = today;
    s.bonus.totalClaims += 1;
    return { already: false, amount, streak };
  });
}
