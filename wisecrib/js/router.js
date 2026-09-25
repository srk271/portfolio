// Hash router: route table with params, onboarding guard, deterministic back targets, scroll reset, cleanup.
// Views are modules exporting render(container, ctx) -> optional cleanup function.
// When another tab changes the saved state ('store:external'), the current view is re-rendered in place
// (scroll kept, focus not moved). Views that hold in-progress input export keepOnExternalChange = true;
// they re-check state when the user acts, and pick up the new data on the next navigation.
import { getState } from './store.js';
import { emit, on } from './bus.js';
import { html, mount } from './dom.js';
import { closeActiveSheet, hasActiveSheet } from './ui/sheet.js';
import * as Onboarding from './views/onboarding.js';
import * as Feed from './views/feed.js';
import * as Article from './views/article.js';
import * as ArticleQuiz from './views/quiz.js';
import * as Practice from './views/practice.js';
import * as Wallet from './views/wallet.js';
import * as Redeem from './views/redeem.js';
import * as Premium from './views/premium.js';
import * as Profile from './views/profile.js';
import * as Settings from './views/settings.js';
import * as Partners from './views/partners.js';
import * as HowRewards from './views/howRewards.js';
import * as NotFound from './views/notfound.js';

const ROUTES = [
  { path: 'onboarding', view: Onboarding, title: 'Welcome', chrome: false },
  { path: 'feed', view: Feed, tab: 'feed', title: 'Feed' },
  { path: 'article/:id', view: Article, tab: 'feed', back: '#/feed', title: 'Article' },
  { path: 'quiz/:id', view: ArticleQuiz, tab: 'feed', back: (p) => `#/article/${encodeURIComponent(p.id)}`, title: 'Article quiz' },
  { path: 'practice', view: Practice, tab: 'practice', title: 'Practice' },
  { path: 'practice/daily', view: Practice.daily, tab: 'practice', back: '#/practice', title: 'Daily quiz' },
  { path: 'practice/mock', view: Practice.mock, tab: 'practice', back: '#/practice', title: 'Mock test' },
  { path: 'wallet', view: Wallet, tab: 'wallet', title: 'Wallet' },
  { path: 'redeem', view: Redeem, tab: 'wallet', back: '#/wallet', title: 'Redeem' },
  { path: 'premium', view: Premium, tab: 'wallet', back: '#/wallet', title: 'Premium' },
  { path: 'profile', view: Profile, tab: 'profile', title: 'Profile' },
  { path: 'settings', view: Settings, tab: 'profile', back: '#/profile', title: 'Settings' },
  { path: 'partners', view: Partners, tab: 'profile', back: (p, q, s) => (s.profile.onboarded ? '#/profile' : '#/onboarding'), title: 'Partners & investors', requiresOnboarding: false },
  {
    path: 'how-rewards-work', view: HowRewards, tab: 'wallet',
    back: (p, q, s) => (q.from === 'onboarding' || !s.profile.onboarded ? '#/onboarding' : q.from === 'redeem' ? '#/redeem' : '#/wallet'),
    title: 'How rewards work', requiresOnboarding: false
  }
].map((r) => ({ ...r, name: r.path, parts: r.path.split('/') }));

const NOT_FOUND = { path: '*', name: 'notfound', view: NotFound, title: 'Not found', back: '#/feed', requiresOnboarding: false, parts: [] };

let viewEl = null;
let cleanup = null;
let current = null;
let renders = 0;
let externalQueued = false;

function parseHash(hash) {
  const h = String(hash || '').replace(/^#\/?/, '');
  const [pathPart, queryPart = ''] = h.split('?');
  const segs = pathPart.split('/').filter(Boolean);
  const query = Object.fromEntries(new URLSearchParams(queryPart));
  return { segs, query };
}

function match(segs) {
  for (const route of ROUTES) {
    if (route.parts.length !== segs.length) continue;
    const params = {};
    let ok = true;
    for (let i = 0; i < segs.length; i += 1) {
      const part = route.parts[i];
      if (part.startsWith(':')) {
        try {
          params[part.slice(1)] = decodeURIComponent(segs[i]);
        } catch {
          params[part.slice(1)] = segs[i];
        }
      } else if (part !== segs[i]) {
        ok = false;
        break;
      }
    }
    if (ok) return { route, params };
  }
  return null;
}

export const currentRoute = () => current;

export function navigate(hash, { replace = false } = {}) {
  if (replace) {
    history.replaceState(history.state, '', hash);
    render();
  } else if (location.hash === hash) {
    render();
  } else {
    location.hash = hash;
  }
}

export function refresh() {
  render();
}

// Another tab changed the saved state: re-render the current view once the current task finishes.
function onExternalChange() {
  if (externalQueued || !viewEl) return;
  externalQueued = true;
  queueMicrotask(() => {
    externalQueued = false;
    if (viewEl) render({ external: true });
  });
}

function render({ external = false } = {}) {
  if (!viewEl) return undefined; // stopped
  const { segs, query } = parseHash(location.hash);
  const state = getState();
  if (!segs.length) return navigate(state.profile.onboarded ? '#/feed' : '#/onboarding', { replace: true });
  const m = match(segs) || { route: NOT_FOUND, params: {} };
  const { route, params } = m;
  if (route.requiresOnboarding !== false && !state.profile.onboarded && route.name !== 'onboarding') {
    return navigate('#/onboarding', { replace: true });
  }
  if (state.profile.onboarded && route.name === 'onboarding') return navigate('#/feed', { replace: true });
  // Don't tear down an open sheet or a view with in-progress input for a change made elsewhere.
  if (external && (hasActiveSheet() || route.view.keepOnExternalChange || current?.name !== route.name)) return undefined;

  if (cleanup) {
    try {
      cleanup();
    } catch (err) {
      console.error('[router] cleanup failed', err);
    }
    cleanup = null;
  }
  closeActiveSheet();

  document.body.dataset.chrome = route.chrome === false ? 'none' : 'full';
  document.body.dataset.tabbar = state.profile.onboarded ? 'show' : 'none';
  document.body.dataset.onboarded = String(state.profile.onboarded);
  document.body.dataset.route = route.name;

  const resolvedRoute = { ...route, back: typeof route.back === 'function' ? route.back(params, query, state) : route.back };
  current = { route: resolvedRoute, params, query, name: route.name };
  emit('route:changed', { ...current, title: route.title });
  document.title = route.name === 'feed' ? 'WiSeCRiB — Read. Quiz. Earn.' : `${route.title} · WiSeCRiB`;

  const scrollTop = viewEl.scrollTop;
  viewEl.scrollTop = 0;
  const ctx = {
    params,
    query,
    route: resolvedRoute,
    external, // true when re-rendering because another tab changed the state
    navigate,
    refresh: () => render(),
    setTitle: (text) => emit('header:title', text)
  };
  try {
    const result = route.view.render(viewEl, ctx);
    cleanup = typeof result === 'function' ? result : null;
  } catch (err) {
    console.error('[router] view failed to render', err);
    mount(viewEl, html`<div class="page"><div class="card card-pad stack">
      <h1 class="h-section">Something went wrong</h1>
      <p class="muted">This screen could not be displayed.</p>
      <a class="btn btn-primary" href="#/feed">Back to feed</a>
    </div></div>`);
  }
  if (external) viewEl.scrollTop = scrollTop;
  emit('view:rendered', viewEl);
  renders += 1;
  if (renders > 1 && !external) viewEl.focus({ preventScroll: true });
  return undefined;
}

const onHashChange = () => render();
let offExternal = null;

export function start(el) {
  viewEl = el;
  window.addEventListener('hashchange', onHashChange);
  offExternal = on('store:external', onExternalChange);
  render();
}

// Stops routing (used when saved data can no longer be written, e.g. a newer version took over).
export function stop() {
  window.removeEventListener('hashchange', onHashChange);
  offExternal?.();
  if (cleanup) {
    try {
      cleanup();
    } catch (err) {
      console.error('[router] cleanup failed', err);
    }
    cleanup = null;
  }
  closeActiveSheet();
  viewEl = null;
}
