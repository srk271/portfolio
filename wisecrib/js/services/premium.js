// Premium membership (simulated checkout). Multiplier applies to reads + article quizzes only.
import { update, getState } from '../store.js';
import { emit } from '../bus.js';
import { ECONOMY } from '../config.js';
import { capFor as walletCapFor } from './wallet.js';

export const isPremium = (state = getState()) => Boolean(state.premium.active);
export const multiplier = (state = getState()) => (isPremium(state) ? ECONOMY.premium.multiplier : 1);
export const capFor = (state = getState()) => walletCapFor(state);

export function priceFor(plan) {
  return plan === 'yearly' ? ECONOMY.premium.yearlyPrice : ECONOMY.premium.monthlyPrice;
}

export function yearlySavingPct() {
  const full = ECONOMY.premium.monthlyPrice * 12;
  return Math.round((1 - ECONOMY.premium.yearlyPrice / full) * 100);
}

// Same day-of-month n months later, clamped to that month's last day (31 Jan + 1 -> 28/29 Feb,
// not 3 Mar; 29 Feb + 12 -> 28 Feb). Local time of day is kept.
export function addMonthsClamped(date, n) {
  const d = new Date(date);
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + n);
  const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, lastDay));
  return d;
}

export function activate(plan = 'monthly') {
  const now = new Date();
  const renews = addMonthsClamped(now, plan === 'yearly' ? 12 : 1);
  update((s) => {
    s.premium = { active: true, plan, since: now.getTime(), renewsAt: renews.getTime() };
  });
  emit('premium:changed', { active: true, plan });
}

export function cancel() {
  update((s) => {
    s.premium = { active: false, plan: null, since: null, renewsAt: null };
    s.settings.newsletter = false;
  });
  emit('premium:changed', { active: false });
}
