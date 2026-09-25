// Bottom navigation: Feed, Practice, Wallet, Profile. aria-current on the active tab; small dots for
// "daily quiz available" and "daily bonus ready".
import { html, mount } from '../dom.js';
import { on } from '../bus.js';
import { getState, subscribe } from '../store.js';
import { icon } from './icons.js';
import { canClaim } from '../services/bonus.js';
import { dailyQuizRecord } from '../services/quiz.js';

const TABS = [
  { key: 'feed', href: '#/feed', label: 'Feed', icon: 'newspaper' },
  { key: 'practice', href: '#/practice', label: 'Practice', icon: 'target' },
  { key: 'wallet', href: '#/wallet', label: 'Wallet', icon: 'wallet' },
  { key: 'profile', href: '#/profile', label: 'Profile', icon: 'user' }
];

export function mountTabbar(el) {
  mount(el, html`${TABS.map((t) => html`<a class="tab" href="${t.href}" data-tab="${t.key}" data-testid="tab-${t.key}">${icon(t.icon)}<span>${t.label}</span><span class="tab-dot" hidden></span></a>`)}`);
  const links = [...el.querySelectorAll('.tab')];

  const setActive = (tab) => {
    for (const a of links) {
      if (a.dataset.tab === tab) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    }
  };

  const setDots = (state) => {
    if (!state.profile.onboarded) return;
    const quizDone = Boolean(dailyQuizRecord(state)?.completedAt);
    el.querySelector('[data-tab="practice"] .tab-dot').hidden = quizDone;
    el.querySelector('[data-tab="wallet"] .tab-dot').hidden = !canClaim(state);
  };

  on('route:changed', ({ route }) => setActive(route.tab));
  subscribe(setDots);
  setDots(getState());
}
