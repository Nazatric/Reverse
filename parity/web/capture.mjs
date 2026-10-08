/**
 * The Gadget — web reference capture.
 *
 * Renders the *production build* of the web app in headless Chromium at the exact
 * viewports used for parity testing, drives it to each screen the way a user would,
 * and writes a PNG per screen into parity/out/web/.
 *
 * Determinism:
 *   - local Orbitron / Exo 2 (fontsource) replace the Google Fonts CDN,
 *   - the app's own "reduce motion" flag is on, so every animation is 0.001ms,
 *   - Date is frozen, so the clock in the status pill never changes between runs,
 *   - localStorage is seeded for every screen that needs a pre-existing library/game.
 *
 * Usage:  node parity/web/capture.mjs [--out DIR] [--only name,name] [--keep-alive]
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import puppeteer from "puppeteer-core";
import chromium from "@sparticuz/chromium";
import { fakeFsScript } from "./seed.mjs";
import { run, settle, PLUGIN_RECORD } from "./drive.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "../..");
const DIST = path.join(ROOT, "dist");

/* ------------------------------------------------------------------ args */
const argv = process.argv.slice(2);
const argOf = (name, dflt) => {
  const i = argv.indexOf(name);
  return i >= 0 ? argv[i + 1] : dflt;
};
const OUT = path.resolve(argOf("--out", path.join(ROOT, "parity/out/web")));
const ONLY = argOf("--only", "") ? argOf("--only", "").split(",") : null;

/* ------------------------------------------------------- reference viewports */
export const VIEWPORTS = {
  "390x844": { width: 390, height: 844, dsf: 3 },
  "393x873": { width: 393, height: 873, dsf: 3 },
  "412x915": { width: 412, height: 915, dsf: 3 },
  "844x390": { width: 844, height: 390, dsf: 3 },
};

const FROZEN_DATE = "2026-10-07T16:20:00";

/* --------------------------------------------------------------- font sink */
const FONTS = [];
for (const [family, slug, weights] of [
  ["Orbitron", "orbitron", [400, 500, 600, 700, 800, 900]],
  ["Exo 2", "exo-2", [300, 400, 500, 600, 700]],
]) {
  for (const w of weights) {
    const file = path.join(HERE, "fonts", `${slug}-${w}.woff2`);
    if (fs.existsSync(file)) FONTS.push({ family, weight: w, file, url: `https://fonts.gstatic.com/gadget-local/${slug}-${w}.woff2` });
  }
}
const fontCss = () =>
  FONTS.map(
    (f) => `@font-face{font-family:'${f.family}';font-style:normal;font-weight:${f.weight};font-display:block;src:url(${f.url}) format('woff2');}`,
  ).join("\n");

/* ------------------------------------------------------------- static server */
const MIME = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg", ".webmanifest": "application/manifest+json", ".svg": "image/svg+xml" };

function serve(rootDir) {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = decodeURIComponent((req.url || "/").split("?")[0]);
      let file = path.join(rootDir, url === "/" ? "index.html" : url);
      if (!file.startsWith(rootDir)) return res.writeHead(403).end();
      if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(rootDir, "index.html");
      res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream", "cache-control": "no-store" });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(0, "127.0.0.1", () => resolve({ server, port: server.address().port }));
  });
}

/* --------------------------------------------------------------- the screens */
/** Each entry: how to get there from a cold start, plus the state to seed first. */
const SEED_BASE = {
  "gadget:onboarded": true,
  "gadget:profile": { name: "naz", tagline: "player one", avatar: null, status: "online", mascot: "grin" },
  "gadget:settings": {
    sounds: false, haptics: false, hour24: false, ambient: true, parallax: false,
    chainSway: true, reduceMotion: true, y2k: false, autoImmersive: true, notify: false,
    keepAwake: true, artwork: true, orbScale: 1, hubScale: 1, chainScale: 1, labelScale: 1, glow: 1,
  },
  "gadget:playlists": [
    { id: "p1", name: "late night", ids: [] },
    { id: "p2", name: "chrome", ids: [] },
  ],
  "gadget:games": [
    { id: "g1", name: "2048", category: "puzzle", url: "https://play2048.co/", cover: null, favorite: true, launches: 12, lastPlayed: 1759000000000, added: 1758000000000 },
    { id: "g2", name: "Slither", category: "arcade", url: "https://slither.io/", cover: null, favorite: false, launches: 4, lastPlayed: null, added: 1758100000000 },
    { id: "g3", name: "Krunker", category: "shooter", url: "https://krunker.io/", cover: null, favorite: false, launches: 1, lastPlayed: null, added: 1758200000000 },
  ],
  "gadget:homies": [
    { id: "h1", name: "kaya", note: "neighbour", link: "", phone: "", status: "online", avatar: null, added: 1758000000000, kind: "local" },
    { id: "h2", name: "dee", note: "band", link: "", phone: "", status: "away", avatar: null, added: 1758000001000, kind: "local" },
    { id: "h3", name: "rio", note: "", link: "", phone: "", status: "busy", avatar: null, added: 1758000002000, kind: "local" },
    { id: "h4", name: "sable", note: "linked homie", link: "", phone: "", status: "online", avatar: null, added: 1758000003000, kind: "linked", code: "hpabc234" },
  ],
  "gadget:plugins": [PLUGIN_RECORD],
  "gadget:hostcode": "hpk7m2x9",
};

/** Gold theme, used by the hub-theme screen (a plugin restyling the hub in place). */
const GOLD_THEME = {
  accent: "#FFC94D",
  orb: ["#FFE59B", "#C08A32", "#573614", "#1A1308"],
  glow: 1.1,
};

const SCREENS = [
  { name: "onboarding", seed: { "gadget:onboarded": false, "gadget:profile": { name: "", tagline: "", avatar: null, status: "online", mascot: "grin" } } },
  { name: "hub" },
  { name: "hub-playing", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "songs" }], clicks: ["#.trow-main"] }, { routes: [] }] },
  // A single playlist, opened from the list. (This slot used to be `hub-plugin`, which captured the
  // hub a second time: plugins do not add hub nodes, so it was a duplicate of `hub`.)
  { name: "playlist", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "playlists" }], clicks: [".prow"] }] },
  { name: "hub-theme", plugins: "gold" },
  { name: "music-empty", noLibrary: true, routes: [{ n: "music" }] },
  { name: "music", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }] },
  { name: "albums", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "albums" }] }] },
  { name: "album", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "albums" }], clicks: ["#.agrid .acard"] }] },
  { name: "songs", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "songs" }] }] },
  { name: "playlists", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "playlists" }], clicks: ["#.page-head .orb"] }] },
  { name: "search", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "search" }], clicks: ["#.field-input"] }], after: (page) => page.type(".field-input", "chrome", { delay: 12 }) },
  { name: "now-playing", phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "songs" }], clicks: ["#.trow-main"] }, { routes: [{ n: "music" }, { n: "now" }] }] },
  { name: "games", routes: [{ n: "games" }] },
  { name: "game-detail", routes: [{ n: "games" }, { n: "game", id: "g1" }] },
  { name: "game-edit", routes: [{ n: "games" }, { n: "game-edit" }] },
  { name: "game-browser", routes: [{ n: "games" }, { n: "game", id: "g2" }], clicks: ["#.launch"] },
  { name: "g2048", routes: [{ n: "games" }, { n: "g2048" }] },
  { name: "homies", routes: [{ n: "homies" }], clicks: ["#.page-head .orb"] },
  { name: "homie-detail", routes: [{ n: "homies" }, { n: "homie", id: "h4" }] },
  { name: "homie-edit", routes: [{ n: "homies" }, { n: "homie-edit", id: "h1" }] },
  { name: "chat", routes: [{ n: "homies" }, { n: "chat", id: "hpabc234" }] },
  { name: "account", routes: [{ n: "account" }] },
  { name: "config", routes: [{ n: "config" }] },
  { name: "plugins", routes: [{ n: "config" }, { n: "plugins" }] },
  { name: "plugin-page", routes: [{ n: "config" }, { n: "plugins" }, { n: "plugin-page", id: "plugin:example.arcade:arcade.page" }] },
];

/* --------------------------------------------------------------- run helper */
async function withPage(browser, viewport, seed, fn, files) {
  const page = await browser.newPage();
  const { width, height, dsf } = VIEWPORTS[viewport];
  await page.setViewport({ width, height, deviceScaleFactor: dsf, isMobile: true, hasTouch: true });
  await page.setRequestInterception(true);
  page.on("request", (req) => {
    const url = req.url();
    if (url.includes("fonts.googleapis.com")) {
      return req.respond({ status: 200, contentType: "text/css", body: fontCss() });
    }
    const hit = FONTS.find((f) => f.url === url);
    if (hit) return req.respond({ status: 200, contentType: "font/woff2", body: fs.readFileSync(hit.file) });
    if (url.startsWith("http") && !url.startsWith(SERVER_ORIGIN)) return req.abort();
    return req.continue();
  });
  await page.evaluateOnNewDocument(fakeFsScript(files));
  await page.evaluateOnNewDocument(
    (seedJson, frozen) => {
      localStorage.clear();
      for (const [k, v] of Object.entries(JSON.parse(seedJson))) localStorage.setItem(k, JSON.stringify(v));
      const RealDate = Date;
      const fixed = new RealDate(frozen).getTime();
      // eslint-disable-next-line no-global-assign
      const Patched = class extends RealDate {
        constructor(...a) {
          if (!a.length) super(fixed);
          else super(...a);
        }
        static now() {
          return fixed;
        }
      };
      window.Date = Patched;
    },
    JSON.stringify(seed),
    FROZEN_DATE,
  );
  await page.goto(SERVER_ORIGIN + "/", { waitUntil: "networkidle2" });
  await page.evaluate(() => document.fonts.ready);
  await fn(page);
  return page;
}

let SERVER_ORIGIN = "";

/* ------------------------------------------------------------------- main */
let browser;
let server;
try {
  const { server: srv, port } = await serve(DIST);
  server = srv;
  SERVER_ORIGIN = `http://127.0.0.1:${port}`;
  process.env.LD_LIBRARY_PATH = "/tmp/lib";
  browser = await puppeteer.launch({
    args: [...chromium.args, "--font-render-hinting=none", "--disable-lcd-text"],
    executablePath: await chromium.executablePath(),
    headless: true,
    env: { ...process.env, LD_LIBRARY_PATH: "/tmp/lib" },
  });

  const wanted = SCREENS.filter((s) => !ONLY || ONLY.includes(s.name));
  for (const viewport of Object.keys(VIEWPORTS)) {
    fs.mkdirSync(path.join(OUT, viewport), { recursive: true });
    for (const screen of wanted) {
      const seed = { ...SEED_BASE, ...(screen.seed || {}) };
      // An empty library has to be in place *before* the app boots: stripping the picker afterwards
      // and reloading used to land the screenshot back on the hub.
      const page = await withPage(browser, viewport, seed, async (p) => {
        if (screen.plugins === "gold") {
          await p.evaluate((theme) => {
            const rec = JSON.parse(localStorage.getItem("gadget:plugins"));
            const doc = { id: "example.gold", name: "Gold Chrome", version: "1.0.0", author: "The Gadget", description: "Warm gold orbs and a warmer accent.", theme };
            localStorage.setItem("gadget:plugins", JSON.stringify([
              ...rec,
              { ...rec[0], id: doc.id, name: doc.name, description: doc.description, doc, source: JSON.stringify(doc, null, 2), enabled: true, installed: 1758000001000 },
            ]));
          }, GOLD_THEME);
          await p.reload({ waitUntil: "networkidle2" });
        }
        if (screen.name === "hub") await p.evaluate(() => localStorage.setItem("gadget:plugins", "[]"));
        if (screen.routes || screen.phases) await run(p, { routes: screen.routes, phases: screen.phases });
        if (screen.after) await screen.after(p);
        await settle(p, 400);
      }, screen.noLibrary ? [] : undefined);

      await page.screenshot({ path: path.join(OUT, viewport, `${screen.name}.png`) });
      console.log(`captured ${screen.name} @ ${viewport}`);
      await page.close();
    }
  }
} finally {
  if (browser) await browser.close().catch(() => {});
  if (server) server.close();
}
