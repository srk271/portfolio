// Profile, onboarding completion and settings. (Kept as a service so views never call store.update directly.)
import { update, getState, reset as resetStore } from '../store.js';
import { emit } from '../bus.js';
import { ECONOMY } from '../config.js';
import { uid } from '../format.js';
import { addEntry } from './wallet.js';
import { generateCode } from './referral.js';

export function completeOnboarding({ name, goal, interests, referralCode = null }) {
  return update((s) => {
    if (s.profile.onboarded) return { welcome: 0, already: true }; // e.g. finished in another tab
    const now = Date.now();
    s.profile = { id: uid('u'), name: name.trim(), goal, interests: [...interests], createdAt: now, onboarded: true };
    s.referral.code = generateCode(referralCode);
    addEntry(s, { type: 'welcome', amount: ECONOMY.welcomeBonus, title: 'Welcome bonus', ts: now });
    if (referralCode) {
      Object.assign(s.referral, { enteredCode: referralCode, enteredAt: now, status: 'pending', verifiedReadsAtEntry: s.stats.verifiedReads });
    }
    return { welcome: ECONOMY.welcomeBonus };
  });
}

export function updateProfile(patch) {
  update((s) => {
    if (typeof patch.name === 'string' && patch.name.trim()) s.profile.name = patch.name.trim().slice(0, 24);
    if (patch.goal) s.profile.goal = patch.goal;
    if (Array.isArray(patch.interests)) s.profile.interests = [...patch.interests];
  });
}

export function setTheme(theme) {
  update((s) => {
    s.settings.theme = theme;
  });
  emit('theme:changed', theme);
}

export function setReduceMotion(value) {
  update((s) => {
    s.settings.reduceMotion = value ? 'on' : 'system';
  });
  emit('theme:changed');
}

export function setNewsletter(on) {
  update((s) => {
    s.settings.newsletter = Boolean(on) && s.premium.active;
  });
}

export function recordAdImpressions(count) {
  if (!count || !getState().profile.onboarded) return;
  update((s) => {
    s.stats.adImpressions += count;
  });
}

export function exportData() {
  return JSON.stringify({ exportedAt: new Date().toISOString(), app: 'WiSeCRiB demo', state: getState() }, null, 2);
}

export function resetDemoData() {
  resetStore();
}
