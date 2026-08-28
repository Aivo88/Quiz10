// Pub Quiz Editor service worker. Bump CACHE on every editor release.
const CACHE = 'quiz10-editor-v11';
const ASSETS = ['./quiz10-editor.html','./editor-manifest.json',
  './icon-editor-192.png','./icon-editor-512.png','./icon-editor-512-maskable.png',
  './apple-touch-icon-editor.png','./favicon-editor-32.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE)
    .then(c => Promise.all(ASSETS.map(u => c.add(new Request(u, { cache: 'reload' })).catch(()=>{}))))
    .then(()=>self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(ks => Promise.all(ks.filter(k => k!==CACHE).map(k => caches.delete(k))))
    .then(()=>self.clients.claim()));
});
// Network-first: fresh when online (keeps the in-app update working), cached when offline.
self.addEventListener('fetch', e => {
  if(e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request).then(r => {
      const cp = r.clone(); caches.open(CACHE).then(c => c.put(e.request, cp).catch(()=>{}));
      return r;
    }).catch(() => caches.match(e.request).then(m => m || caches.match('./quiz10-editor.html')))
  );
});
