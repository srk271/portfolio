// Boot: load the store, apply theme, mount header/tabbar, start the router, register the service worker,
// capture the install prompt, and wire global bus listeners (coins:awarded -> coin burst + toast).
import * as store from './store.js';
import { on } from './bus.js';
import { html, mount } from './dom.js';
import { start, stop } from './router.js';
import { mountHeader } from './ui/header.js';
import { mountTabbar } from './ui/tabbar.js';
import { toast } from './ui/toast.js';
import { burst } from './ui/coinburst.js';
import { icon } from './ui/icons.js';
import { reasonText } from './services/wallet.js';
import { captureInstallPrompt, registerServiceWorker } from './pwa.js';
import * as storage from './storage.js';

const root = document.documentElement;
const mqDark = window.matchMedia('(prefers-color-scheme: dark)');
const mqMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const THEME_COLORS = { light: '#F6F6FB', dark: '#0F1020' };

function applyTheme(settings = {}) {
  const pref = settings.theme || 'system';
  const theme = pref === 'light' || pref === 'dark' ? pref : mqDark.matches ? 'dark' : 'light';
  if (root.dataset.theme !== theme) root.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
  root.dataset.motion = settings.reduceMotion === 'on' || mqMotion.matches ? 'reduce' : 'full';
}

function renderFatal(err) {
  document.body.dataset.chrome = 'none';
  document.body.dataset.tabbar = 'none';
  const view = document.getElementById('view');
  const newer = err instanceof store.NewerDataError;
  mount(view, html`<div class="page fatal">
    <div class="card card-pad stack" data-testid="fatal-error" data-kind="${newer ? 'newer' : 'corrupt'}">
      <div class="fatal-ico">${icon('alert')}</div>
      ${newer
        ? html`<h1 class="h-section">WiSeCRiB was updated</h1>
          <p class="muted">${err.message} Reload to use the latest version; your progress is kept.</p>
          <button type="button" class="btn btn-primary btn-block" data-testid="fatal-reload">Reload</button>`
        : html`<h1 class="h-section">Saved data couldn't be read</h1>
          <p class="muted">${err?.message || 'Unknown error'} Resetting clears this device's demo progress.</p>
          <button type="button" class="btn btn-primary btn-block" data-testid="fatal-reset">Reset demo data</button>`}
    </div>
  </div>`);
  view.querySelector('[data-testid="fatal-reset"]')?.addEventListener('click', () => {
    storage.remove('state');
    location.hash = '#/onboarding';
    location.reload();
  });
  view.querySelector('[data-testid="fatal-reload"]')?.addEventListener('click', () => location.reload());
}

function wireGlobalEvents() {
  // quiet: the screen already announced this outcome (e.g. a quiz result), so the toast isn't re-read.
  on('coins:awarded', ({ amount, from = null, message = null, quiet = false }) => {
    if (!amount) return;
    burst({ from, amount });
    toast({ message: message || `+${amount} coins`, kind: 'coin', announceIt: !quiet });
  });
  on('reward:rejected', ({ reason, message = null, quiet = false }) => {
    toast({ message: message || `Reward not applied — ${reasonText(reason)}`, kind: 'warn', duration: 5000, announceIt: !quiet });
  });
  on('toast', (opts) => toast(opts));
  // Another tab saved data this version can't read or must not overwrite: stop and explain (after the
  // current task, so a view finishing an action can't draw over the message).
  on('store:halted', (err) => {
    setTimeout(() => {
      stop();
      renderFatal(err);
    }, 0);
  });
  on('theme:changed', () => applyTheme(store.getState().settings));

  const skip = document.querySelector('[data-skip-link]');
  skip?.addEventListener('click', (e) => {
    e.preventDefault();
    document.getElementById('view').focus();
  });
}

function boot() {
  captureInstallPrompt();
  wireGlobalEvents(); // before load(), so a storage warning raised while loading is shown
  try {
    store.load();
  } catch (err) {
    console.warn('[wisecrib] could not load saved state', err);
    applyTheme({});
    renderFatal(err);
    registerServiceWorker(); // so a newer version can still install
    return;
  }
  applyTheme(store.getState().settings);
  store.subscribe((s) => applyTheme(s.settings));
  mqDark.addEventListener?.('change', () => applyTheme(store.getState().settings));
  mqMotion.addEventListener?.('change', () => applyTheme(store.getState().settings));
  if (!store.storageAvailable()) document.body.dataset.storage = 'memory';

  mountHeader(document.getElementById('app-header'));
  mountTabbar(document.getElementById('tabbar'));
  start(document.getElementById('view'));
  registerServiceWorker();
}

boot();
