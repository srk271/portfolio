// WiSeCRiB service worker: precache the app shell, versioned cache.
// Bump CACHE on every deploy (and APP.cacheVersion in js/config.js, shown in Settings).
// When you add a module or asset, add it to PRECACHE too, or the app will not work offline.
const CACHE = 'wisecrib-v1';

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

// Navigations: network first (fresh shell when online), cached index.html when offline.
async function handleNavigation(request) {
  const cache = await caches.open(CACHE);
  try {
    const response = await withTimeout(fetch(request), 4000);
    if (response && response.ok) cache.put(INDEX_URL, response.clone());
    return response;
  } catch {
    return (await cache.match(INDEX_URL)) || (await cache.match(new URL('./', self.registration.scope).href)) || Response.error();
  }
}

// Precached assets: cache first.
async function cacheFirst(request) {
  const cached = await caches.match(request, { ignoreSearch: true });
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
    event.respondWith(handleNavigation(request));
    return;
  }
  const clean = url.origin + url.pathname;
  if (PRECACHE_URLS.has(clean)) {
    event.respondWith(cacheFirst(request));
    return;
  }
  event.respondWith(staleWhileRevalidate(request));
});
