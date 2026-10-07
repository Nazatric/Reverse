/* The Gadget service worker — offline app shell + real lock-screen notifications. */
const CACHE = "gadget-v1";
const SHELL = ["./", "./index.html", "./manifest.webmanifest", "./images/atmosphere.jpg", "./images/mascot.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches
      .open(CACHE)
      .then((c) => c.addAll(SHELL))
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // media + CDN traffic passes straight through

  if (req.mode === "navigate") {
    e.respondWith(fetch(req).catch(() => caches.match("./index.html")));
    return;
  }

  e.respondWith(
    caches.match(req).then(
      (hit) =>
        hit ||
        fetch(req).then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => undefined);
          }
          return res;
        }),
    ),
  );
});

/* ---- lock-screen media notification ---- */

async function focusApp(message) {
  const list = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  for (const c of list) {
    if ("focus" in c) {
      c.focus();
      if (message) c.postMessage(message);
      return c;
    }
  }
  const opened = await self.clients.openWindow("./");
  if (opened && message) setTimeout(() => opened.postMessage(message), 600);
  return opened;
}

self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  if (e.action === "toggle") {
    e.waitUntil(focusApp({ type: "media", action: "toggle" }));
    return;
  }
  if (e.action === "next") {
    e.waitUntil(focusApp({ type: "media", action: "next" }));
    return;
  }
  e.waitUntil(focusApp(null));
});
