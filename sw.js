/* QUIZ 10 service worker — network-first, offline-capable.
   Bump CACHE (e.g. quiz10-v2) when you want to force a clean update,
   or just use the in-app "Check for updates" button. */
const CACHE = 'quiz10-v3';

self.addEventListener('install', e => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll([
      './', './questions.json', './manifest.json',
      './icon-192.png', './icon-512.png', './icon-512-maskable.png',
      './apple-touch-icon.png', './favicon-32.png'
    ]).catch(() => {}))
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', e => {
  if (e.data === 'skipWaiting') self.skipWaiting();
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request)
      .then(res => {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(e.request, copy)).catch(() => {});
        return res;
      })
      .catch(() => caches.match(e.request).then(r => r || caches.match('./')))
  );
});
