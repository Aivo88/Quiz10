/* Pub Quiz Multiplayer — service worker (PWA shell) */
const CACHE = 'pubquiz-mp-v00.08.08';
const ASSETS = [
  './', './index.html', './manifest.json',
  './icon-192.png', './icon-512.png', './icon-maskable-512.png', './apple-touch-icon.png',
  '../questions.json', '../bullseye_1000.json', '../background.jpg'
];
self.addEventListener('install', e=>{
  self.skipWaiting();
  e.waitUntil(caches.open(CACHE).then(c=> Promise.all(ASSETS.map(u=> c.add(u).catch(()=>{})))));
});
self.addEventListener('activate', e=>{
  e.waitUntil(
    caches.keys().then(ks=> Promise.all(ks.filter(k=>k!==CACHE).map(k=>caches.delete(k))))
      .then(()=> self.clients.claim())
  );
});
self.addEventListener('fetch', e=>{
  const req = e.request;
  if(req.method!=='GET') return;
  // network-first: fresh when online, cache fallback offline
  e.respondWith(
    fetch(req).then(res=>{
      const copy = res.clone();
      caches.open(CACHE).then(c=> c.put(req, copy).catch(()=>{}));
      return res;
    }).catch(()=> caches.match(req).then(r=> r || caches.match('./index.html')))
  );
});
