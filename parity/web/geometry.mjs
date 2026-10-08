/**
 * The Gadget — measured geometry reference.
 *
 * Runs the production build in headless Chromium at each parity viewport, walks to every screen,
 * and dumps the *measured* box of every element that the native layout has to reproduce, plus the
 * computed styles that the native design tokens are derived from.
 *
 * Output: parity/out/web/<viewport>/geometry.json
 *
 * This file is the numeric contract for the Compose port: `verification/` compares the Kotlin
 * layout maths against these numbers, so "24dp padding" is never guessed.
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
const OUT = process.env.GEOMETRY_OUT || path.join(ROOT, "parity/out/web");
const FROZEN_DATE = "2026-10-07T16:20:00";

const VIEWPORTS = {
  "390x844": { width: 390, height: 844, dsf: 3 },
  "393x873": { width: 393, height: 873, dsf: 3 },
  "412x915": { width: 412, height: 915, dsf: 3 },
  "844x390": { width: 844, height: 390, dsf: 3 },
};

/* fonts as in capture.mjs */
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
  FONTS.map((f) => `@font-face{font-family:'${f.family}';font-style:normal;font-weight:${f.weight};font-display:block;src:url(${f.url}) format('woff2');}`).join("\n");

const MIME = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg", ".webmanifest": "application/manifest+json" };
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

const SEED = {
  /* plugin record injected below (kept out of the literal for readability) */
  "gadget:onboarded": true,
  "gadget:profile": { name: "naz", tagline: "player one", avatar: null, status: "online", mascot: "grin" },
  "gadget:settings": {
    sounds: false, haptics: false, hour24: false, ambient: true, parallax: false, chainSway: true,
    reduceMotion: true, y2k: false, autoImmersive: true, notify: false, keepAwake: true, artwork: true,
    orbScale: 1, hubScale: 1, chainScale: 1, labelScale: 1, glow: 1,
  },
  "gadget:playlists": [{ id: "p1", name: "late night", ids: [] }],
  "gadget:games": [
    { id: "g1", name: "2048", category: "puzzle", url: "https://play2048.co/", cover: null, favorite: true, launches: 12, lastPlayed: 1759000000000, added: 1758000000000 },
    { id: "g2", name: "Slither", category: "arcade", url: "https://slither.io/", cover: null, favorite: false, launches: 4, lastPlayed: null, added: 1758100000000 },
  ],
  "gadget:homies": [
    { id: "h1", name: "kaya", note: "neighbour", link: "", phone: "", status: "online", avatar: null, added: 1758000000000, kind: "local" },
    { id: "h2", name: "dee", note: "band", link: "", phone: "", status: "away", avatar: null, added: 1758000001000, kind: "local" },
    { id: "h3", name: "rio", note: "", link: "", phone: "", status: "busy", avatar: null, added: 1758000002000, kind: "local" },
    { id: "h4", name: "sable", note: "linked homie", link: "", phone: "", status: "online", avatar: null, added: 1758000003000, kind: "linked", code: "hpabc234" },
  ],
  "gadget:hostcode": "hpk7m2x9",
};

SEED["gadget:plugins"] = [PLUGIN_RECORD];

/** Everything the native layout needs numbers for, grouped per screen. */
const PLAN = {
  hub: [
    ".up", ".pill", ".pill-avatar", ".pill-name", ".pill-time",
    ".hub-mascot", ".node", ".node-body", ".node-label",
    ".wire", ".bg-photo", ".bg-vignette",
  ],
  playlist: [".page-head", ".page-title", ".page-sub", ".hero-orb", ".quad", ".qnode", ".empty", ".empty-orb", ".empty-title", ".empty-text"],
  "hub-theme": [".node", ".node-label", ".bg-photo", ".bg-vignette"],
  onboarding: [".onboard", ".onboard-card", ".onboard-mascot", ".onboard-kicker", ".onboard-title", ".onboard-field", ".onboard-start", ".onboard-note"],
  music: [".page-head", ".page-title", ".page-sub", ".hero-orb", ".quad", ".qnode", ".surface", ".empty-orb"],
  "music-empty": [".page-head", ".page-title", ".empty", ".empty-orb", ".empty-title", ".empty-text", ".picker", ".picker-note"],
  albums: [".agrid", ".acard", ".acard-art", ".acard-name", ".acard-sub"],
  album: [".chero", ".chero-orb", ".chero-title", ".chero-sub", ".chero-actions", ".trow", ".trow-art", ".trow-title", ".trow-sub"],
  playlists: [".plist", ".prow", ".prow-art", ".prow-name", ".inline-form"],
  search: [".field", ".field-label", ".field-input", ".sresult", ".acard--sm"],
  "homie-detail": [".gdetail", ".hdetail-orb", ".gdetail-name", ".gdetail-tags", ".tag", ".stats", ".contact"],
  chat: [".chat", ".chat-head", ".chat-log", ".bubble", ".chat-form", ".field"],
  "homie-edit": [".form", ".field", ".chips", ".form-actions"],
  "game-detail": [".gdetail", ".gdetail-cover", ".gdetail-name", ".gdetail-tags", ".tag", ".stats", ".launch", ".cover"],
  "game-edit": [".form", ".cover-pick", ".cover-pick-row", ".field", ".chips", ".form-actions"],
  "game-browser": [".webview", ".webview-bar", ".webview-name", ".webview-body"],
  songs: [".songs-list", ".trow", ".trow-art", ".rail", ".lhead"],
  "now-playing": [".np", ".np-stage", ".np-ring", ".np-art", ".np-title", ".np-ctl", ".scrub", ".mini"],
  games: [".ggrid", ".tile", ".cover", ".gfeature", ".chips", ".chip", ".g2048-scores", ".board", ".board-cells span", ".t48-face"],
  homies: [".const", ".hnode", ".hnode-orb", ".const-you", ".inline-form", ".info-row"],
  account: [".acct-orb", ".acct-face", ".orbstats", ".orbstat", ".seg"],
  config: [".aset", ".gslider", ".gslider-rail", ".gslider-thumb", ".switch-track", ".switch-thumb", ".plugin"],
  plugins: [".plugin-actions", ".plugin-url", ".plugin-list", ".plugin"],
  onboarding: [".onboard-card", ".onboard-mascot", ".onboard-title", ".onboard-field", ".onboard-start"],
  "game-browser": [".node[aria-label=games]", ".ggrid .tile:nth-child(2)"],
};

const PROPS = [
  "position", "display", "width", "height", "padding", "margin", "borderRadius", "gap",
  "backgroundImage", "backgroundColor", "boxShadow", "color", "fontFamily", "fontSize", "fontWeight",
  "lineHeight", "letterSpacing", "textTransform", "transform", "opacity", "zIndex", "overflow",
  "maskImage", "filter", "mixBlendMode", "top", "left", "right", "bottom", "flexDirection",
  "alignItems", "justifyContent", "gridTemplateColumns", "aspectRatio", "textShadow", "borderWidth",
];

const NAV = {
  hub: { routes: [] },
  playlist: { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "playlists" }], clicks: [".prow"] }] },
  "hub-theme": { routes: [] },
  "music-empty": { routes: [{ n: "music" }] },
  music: { routes: [{ n: "music" }], clicks: ["@lib"] },
  albums: { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "albums" }] }] },
  album: { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "albums" }], clicks: ["#.agrid .acard"] }] },
  songs: { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "songs" }] }] },
  playlists: { routes: [{ n: "music" }, { n: "playlists" }] },
  search: { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "search" }] }] },
  "now-playing": { phases: [{ routes: [{ n: "music" }], clicks: ["@lib"] }, { routes: [{ n: "music" }, { n: "now" }] }] },
  games: { routes: [{ n: "games" }] },
  "game-detail": { routes: [{ n: "games" }, { n: "game", id: "g1" }] },
  "game-edit": { routes: [{ n: "games" }, { n: "game-edit" }] },
  g2048: { routes: [{ n: "games" }, { n: "g2048" }] },
  homies: { routes: [{ n: "homies" }] },
  "homie-detail": { routes: [{ n: "homies" }, { n: "homie", id: "h1" }] },
  "homie-edit": { routes: [{ n: "homies" }, { n: "homie-edit" }] },
  chat: { routes: [{ n: "homies" }, { n: "chat", id: "hpabc234" }] },
  account: { routes: [{ n: "account" }] },
  config: { routes: [{ n: "config" }] },
  plugins: { routes: [{ n: "config" }, { n: "plugins" }] },
  onboarding: { routes: [] },
  "plugin-page": { routes: [{ n: "config" }, { n: "plugins" }, { n: "plugin-page", id: "plugin:example.arcade:arcade.page" }] },
};

async function collect(page, selectors) {
  return page.evaluate(
    (sels, props) => {
      const out = {};
      for (const sel of sels) {
        const el = document.querySelector(sel);
        if (!el) {
          out[sel] = null;
          continue;
        }
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        const styles = {};
        for (const p of props) styles[p] = cs[p];
        out[sel] = {
          x: +r.x.toFixed(2), y: +r.y.toFixed(2), w: +r.width.toFixed(2), h: +r.height.toFixed(2),
          cx: +(r.x + r.width / 2).toFixed(2), cy: +(r.y + r.height / 2).toFixed(2),
          styles,
          text: (el.textContent || "").trim().slice(0, 60),
        };
      }
      return out;
    },
    selectors,
    PROPS,
  );
}

let SERVER_ORIGIN = "";
let browser;
let server;
try {
  const s = await serve(DIST);
  server = s.server;
  SERVER_ORIGIN = `http://127.0.0.1:${s.port}`;
  process.env.LD_LIBRARY_PATH = "/tmp/lib";
  browser = await puppeteer.launch({
    args: [...chromium.args, "--font-render-hinting=none", "--disable-lcd-text"],
    executablePath: await chromium.executablePath(),
    headless: true,
    env: { ...process.env, LD_LIBRARY_PATH: "/tmp/lib" },
  });

  for (const viewport of Object.keys(VIEWPORTS)) {
    const { width, height, dsf } = VIEWPORTS[viewport];
    const page = await browser.newPage();
    await page.setViewport({ width, height, deviceScaleFactor: dsf, isMobile: true, hasTouch: true });
    await page.setRequestInterception(true);
    page.on("request", (req) => {
      const url = req.url();
      if (url.includes("fonts.googleapis.com")) return req.respond({ status: 200, contentType: "text/css", body: fontCss() });
      const hit = FONTS.find((f) => f.url === url);
      if (hit) return req.respond({ status: 200, contentType: "font/woff2", body: fs.readFileSync(hit.file) });
      if (url.startsWith("http") && !url.startsWith(SERVER_ORIGIN)) return req.abort();
      return req.continue();
    });
    await page.evaluateOnNewDocument(fakeFsScript());
    await page.evaluateOnNewDocument(
      (seedJson, frozen) => {
        localStorage.clear();
        for (const [k, v] of Object.entries(JSON.parse(seedJson))) localStorage.setItem(k, JSON.stringify(v));
        const RealDate = Date;
        const fixed = new RealDate(frozen).getTime();
        // eslint-disable-next-line no-global-assign
        window.Date = class extends RealDate {
          constructor(...a) {
            if (!a.length) super(fixed);
            else super(...a);
          }
          static now() {
            return fixed;
          }
        };
      },
      JSON.stringify(SEED),
      FROZEN_DATE,
    );

    const result = { viewport: { width, height, dsf }, screens: {} };

    for (const [screen, spec] of Object.entries(NAV)) {
      await page.goto(SERVER_ORIGIN + "/", { waitUntil: "networkidle2" });
      await page.evaluate((name) => {
        const set = (k, v) => localStorage.setItem(k, JSON.stringify(v));
        if (name === "hub") set("gadget:plugins", []);
        if (name === "hub-theme") {
          const rec = JSON.parse(localStorage.getItem("gadget:plugins"));
          const gold = {
            id: "example.gold", name: "Gold Chrome", version: "1.0.0", author: "The Gadget",
            description: "Warm gold orbs and a warmer accent.",
            theme: { accent: "#FFC94D", orb: ["#FFE59B", "#C08A32", "#573614", "#1A1308"], glow: 1.1 },
          };
          rec.push({ ...rec[0], id: gold.id, name: gold.name, description: gold.description, doc: gold, source: JSON.stringify(gold, null, 2), enabled: true, installed: 1758000001000 });
          set("gadget:plugins", rec);
        }
      }, screen);
      await page.reload({ waitUntil: "networkidle2" });
      await page.evaluate(() => document.fonts.ready);
      if (screen === "onboarding") {
        await page.evaluate(() => {
          const raw = JSON.parse(localStorage.getItem("gadget:onboarded") ?? "true");
          void raw;
          localStorage.setItem("gadget:onboarded", "false");
        });
        await page.reload({ waitUntil: "networkidle2" });
        await page.evaluate(() => document.fonts.ready);
      }
      await run(page, spec);

      const stage = await page.evaluate(() => {
        const root = getComputedStyle(document.documentElement);
        return {
          sw: root.getPropertyValue("--sw").trim(),
          sh: root.getPropertyValue("--sh").trim(),
          fx: root.getPropertyValue("--fx").trim(),
          fy: root.getPropertyValue("--fy").trim(),
          u: root.getPropertyValue("--u").trim(),
          motion: document.documentElement.dataset.motion,
          sw2: root.getPropertyValue("--orb-scale").trim(),
        };
      });
      const boxes = await collect(page, PLAN[screen] ?? []);
      result.screens[screen] = { stage, boxes };
    }

    // the raw deterministic backdrop values, computed in-page from the same formulas
    result.derived = await page.evaluate(() => {
      const rnd = (n) => {
        const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return x - Math.floor(x);
      };
      return Array.from({ length: 16 }, (_, i) => ({
        left: +(rnd(i + 1) * 100).toFixed(4),
        top: +(18 + rnd(i + 31) * 78).toFixed(4),
        size: +(1.4 + rnd(i + 61) * 2.2).toFixed(4),
        dur: +(18 + rnd(i + 91) * 20).toFixed(4),
        delay: +(-rnd(i + 121) * 36).toFixed(4),
        dx: +((rnd(i + 151) - 0.5) * 70).toFixed(4),
      }));
    });

    fs.mkdirSync(path.join(OUT, viewport), { recursive: true });
    fs.writeFileSync(path.join(OUT, viewport, "geometry.json"), JSON.stringify(result, null, 2));
    console.log(`geometry ${viewport}`);
    await page.close();
  }
} finally {
  if (browser) await browser.close().catch(() => {});
  if (server) server.close();
}
