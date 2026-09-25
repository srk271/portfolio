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

export function activate(plan = 'monthly') {
  const now = new Date();
  const renews = new Date(now);
  if (plan === 'yearly') renews.setFullYear(renews.getFullYear() + 1);
  else renews.setMonth(renews.getMonth() + 1);
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
