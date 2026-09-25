// Top bar: back button (deterministic, per route), title, premium crown, coin pill with count-up.
import { html, mount } from '../dom.js';
import { on } from '../bus.js';
import { getState, subscribe } from '../store.js';
import { fmtCoins } from '../format.js';
import { icon, coinIcon, logoMark, wordmark, crownIcon } from './icons.js';

let leftEl;
let titleEl;
let premiumEl;
let pillEl;
let valueEl;
let shown = 0;
let target = 0;
let held = false;
let rafId = 0;
let viewEl = null;
let scrollRaf = 0;

const reduced = () => document.documentElement.dataset.motion === 'reduce';

function paint(value) {
  valueEl.textContent = fmtCoins(value);
}

function tweenTo(value) {
  cancelAnimationFrame(rafId);
  if (value === shown) return paint(value);
  if (reduced() || document.hidden) {
    shown = value;
    return paint(value);
  }
  const from = shown;
  const start = performance.now();
  const duration = 600;
  const step = (now) => {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - (1 - t) ** 3;
    shown = Math.round(from + (value - from) * eased);
    paint(shown);
    if (t < 1) rafId = requestAnimationFrame(step);
    else shown = value;
  };
  rafId = requestAnimationFrame(step);
}

export function holdBalance() {
  held = true;
}

export function releaseBalance() {
  held = false;
  tweenTo(target);
}

export function pulsePill() {
  if (!pillEl) return;
  pillEl.classList.remove('pulse');
  void pillEl.offsetWidth; // restart the animation
  pillEl.classList.add('pulse');
}

function renderPremium(state) {
  const active = state.premium.active;
  if (premiumEl.dataset.active === String(active)) return;
  premiumEl.dataset.active = String(active);
  mount(premiumEl, active
    ? html`<a class="crown-badge" href="#/premium" data-testid="premium-badge" aria-label="Premium active" title="Premium active">${crownIcon()}</a>`
    : html``);
}

// Large-title pattern: when a page shows its own big h1 ([data-large-title]), the header title stays
// hidden until that heading scrolls out of view.
function syncLargeTitle() {
  scrollRaf = 0;
  if (!viewEl || !titleEl) return;
  const big = viewEl.querySelector('[data-large-title]');
  const hidden = Boolean(big) && big.getBoundingClientRect().bottom > viewEl.getBoundingClientRect().top + 4;
  titleEl.classList.toggle('is-hidden', hidden);
}

function onViewScroll() {
  if (!scrollRaf) scrollRaf = requestAnimationFrame(syncLargeTitle);
}

function setTitle(text) {
  if (text) titleEl.textContent = text;
}

function renderRoute({ route, params, title }) {
  const back = typeof route.back === 'function' ? route.back(params) : route.back;
  if (back) {
    mount(leftEl, html`<a class="icon-btn" href="${back}" aria-label="Back" data-testid="header-back">${icon('back')}</a>`);
  } else {
    mount(leftEl, html`<a class="brand-lockup" href="#/feed" aria-label="WiSeCRiB home">${logoMark()}</a>`);
  }
  if (route.name === 'feed') {
    mount(titleEl, html`${wordmark()}`);
  } else {
    titleEl.textContent = title || route.title || '';
  }
}

export function mountHeader(el) {
  mount(el, html`<div class="hdr">
    <div class="hdr-left" id="hdr-left"></div>
    <div class="hdr-title" id="hdr-title"></div>
    <div class="hdr-right">
      <span id="hdr-premium" data-active=""></span>
      <a class="coin-pill" id="coin-pill" href="#/wallet" aria-label="Wallet">${coinIcon()}<span id="coin-balance" data-testid="coin-balance" data-value="0">0</span></a>
    </div>
  </div>`);
  leftEl = el.querySelector('#hdr-left');
  titleEl = el.querySelector('#hdr-title');
  premiumEl = el.querySelector('#hdr-premium');
  pillEl = el.querySelector('#coin-pill');
  valueEl = el.querySelector('#coin-balance');
  pillEl.addEventListener('animationend', () => pillEl.classList.remove('pulse'));

  const sync = (state, animate) => {
    target = state.wallet.balance;
    valueEl.dataset.value = String(target);
    pillEl.setAttribute('aria-label', `Wallet: ${fmtCoins(target)} coins`);
    renderPremium(state);
    if (!animate) {
      shown = target;
      paint(target);
      return;
    }
    // Deferred so a coins:awarded handler emitted right after the update can hold the count-up.
    queueMicrotask(() => {
      if (!held) tweenTo(target);
    });
  };
  sync(getState(), false);
  subscribe((state) => sync(state, true));
  on('store:reset', () => sync(getState(), false));
  on('route:changed', renderRoute);
  on('header:title', setTitle);
  on('view:rendered', (el) => {
    if (viewEl !== el) {
      viewEl?.removeEventListener('scroll', onViewScroll);
      viewEl = el;
      viewEl.addEventListener('scroll', onViewScroll, { passive: true });
    }
    syncLargeTitle();
  });
}
