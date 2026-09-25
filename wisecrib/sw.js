// WiSeCRiB service worker: precache the app shell, versioned cache.
// Bump CACHE on every deploy (and APP.cacheVersion in js/config.js, shown in Settings).
// When you add a module or asset, add it to PRECACHE too, or the app will not work offline.
//
// Update model: the shell (index.html) and its modules are both served cache-first from the SAME
// versioned cache, so a page never pairs a new index.html with old modules (or the reverse). A deploy
// installs a new worker, which precaches the new version into a new cache and takes over; the page
// then shows "Update available · Reload" (js/pwa.js) and the reload loads the new version as a whole.
const CACHE = 'wisecrib-v2';

const PRECACHE = [
  './',
  './index.html',
  './manifest.json',
  './css/tokens.css',
  './css/base.css',
  './css/components.css',
  './css/views.css',
  './js/main.js',
  './js/config.js',
  './js/router.js',
  './js/store.js',
  './js/storage.js',
  './js/bus.js',
  './js/dom.js',
  './js/format.js',
  './js/pwa.js',
  './js/services/wallet.js',
  './js/services/reading.js',
  './js/services/quiz.js',
  './js/services/recommend.js',
  './js/services/premium.js',
  './js/services/bonus.js',
  './js/services/referral.js',
  './js/services/payout.js',
  './js/services/redeem.js',
  './js/services/stats.js',
  './js/services/economics.js',
  './js/services/profile.js',
  './js/ui/header.js',
  './js/ui/tabbar.js',
  './js/ui/toast.js',
  './js/ui/coinburst.js',
  './js/ui/sheet.js',
  './js/ui/adslot.js',
  './js/ui/quizEngine.js',
  './js/ui/icons.js',
  './js/ui/artwork.js',
  './js/views/onboarding.js',
  './js/views/feed.js',
  './js/views/article.js',
  './js/views/quiz.js',
  './js/views/practice.js',
  './js/views/wallet.js',
  './js/views/redeem.js',
  './js/views/premium.js',
  './js/views/profile.js',
  './js/views/settings.js',
  './js/views/partners.js',
  './js/views/howRewards.js',
  './js/views/notfound.js',
  './js/data/categories.js',
  './js/data/goals.js',
  './js/data/articles.js',
  './js/data/vouchers.js',
  './js/data/partners.js',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-192.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png'
];

const PRECACHE_URLS = new Set(PRECACHE.map((p) => new URL(p, self.registration.scope).href));
const SCOPE_URL = new URL('./', self.registration.scope).href;
const INDEX_URL = new URL('./index.html', self.registration.scope).href;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(PRECACHE.map((p) => new Request(p, { cache: 'reload' }))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('wisecrib-') && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms);
    promise.then((v) => { clearTimeout(t); resolve(v); }, (e) => { clearTimeout(t); reject(e); });
  });
}

// Only a real HTML page may ever be stored as the shell.
const isShellResponse = (response) =>
  Boolean(response) && response.ok && !response.redirected && (response.headers.get('content-type') || '').toLowerCase().startsWith('text/html');

// Navigations to the app itself (scope root or index.html, any query): the cached shell of this version.
// It is only (re)stored from the network if missing, and only when the response is an OK HTML page.
async function handleShellNavigation(request) {
  const cache = await caches.open(CACHE);
  const cached = (await cache.match(INDEX_URL)) || (await cache.match(SCOPE_URL));
  if (cached) return cached;
  const response = await withTimeout(fetch(request), 4000);
  if (isShellResponse(response)) await cache.put(INDEX_URL, response.clone());
  return response;
}

// Precached assets: cache first, from this version's cache.
async function cacheFirst(request) {
  const cached = await (await caches.open(CACHE)).match(request, { ignoreSearch: true });
  if (cached) return cached;
  const response = await fetch(request);
  if (response && response.ok) (await caches.open(CACHE)).put(request, response.clone());
  return response;
}

// Other same-origin GETs: stale-while-revalidate.
async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request);
  const network = fetch(request)
    .then((response) => {
      if (response && response.ok) cache.put(request, response.clone());
      return response;
    })
    .catch(() => cached);
  return cached || network;
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return; // cross-origin: passthrough
  if (!url.href.startsWith(self.registration.scope)) return; // outside our folder (e.g. the portfolio)
  if (request.mode === 'navigate') {
    // Other in-scope pages (README.md, manifest.json, an icon...) go straight to the network and never
    // touch the cached shell.
    const path = url.origin + url.pathname;
    if (path === SCOPE_URL || path === INDEX_URL) event.respondWith(handleShellNavigation(request));
    return;
  }
  const clean = url.origin + url.pathname;
  if (PRECACHE_URLS.has(clean)) {
    event.respondWith(cacheFirst(request));
    return;
  }
  event.respondWith(staleWhileRevalidate(request));
});
