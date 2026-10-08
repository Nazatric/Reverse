/**
 * The Gadget — shared harness driver.
 *
 * The web app keeps its route stack in `history.state.stack` and reacts to `popstate`
 * (see src/state/nav.tsx). Driving it that way is *the app's own contract*, so the harness can
 * place it on any screen — including ones with no click affordance in the reference build —
 * without poking at internals or inventing selectors that can silently drift.
 *
 * Anything that genuinely needs a click (like the folder picker, which must run inside a user
 * gesture) is expressed as a "#selector" step instead.
 */

/** Wait for fonts + the app's own transition timers to finish. */
export async function settle(page, ms = 700) {
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
  await new Promise((r) => setTimeout(r, ms));
}

/** Put the app on a route stack, exactly as back/forward navigation would. */
export async function setRoute(page, routes) {
  await page.evaluate((stack) => {
    window.history.replaceState({ stack }, "");
    window.dispatchEvent(new PopStateEvent("popstate", { state: { stack } }));
  }, routes);
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
  // PageHost keeps the outgoing route mounted for 480ms after the new one appears.
  await new Promise((r) => setTimeout(r, 620));
}

/**
 * Run a screen recipe.
 * @param {{routes?: Array<object>, clicks?: string[]}} spec
 *   routes — the navigation stack to impose (the screen is its last entry)
 *   clicks — "#sel" steps executed afterwards (for app-side gestures the harness must trigger)
 */
export async function run(page, spec) {
  const phases = spec.phases ?? [spec];
  for (const phase of phases) await runPhase(page, phase);
}

async function runPhase(page, phase) {
  if (phase.routes) await setRoute(page, phase.routes);
  for (const step of phase.clicks ?? []) {
    if (step === "@lib") {
      await page.waitForSelector(".picker .btn", { timeout: 8000 });
      await page.click(".picker .btn");
      await page.waitForSelector(".hero-orb", { timeout: 10000 });
      await new Promise((r) => setTimeout(r, 500));
      continue;
    }
    const sel = step.startsWith("#") ? step.slice(1) : step;
    await page.waitForSelector(sel, { timeout: 8000 });
    await page.click(sel);
    await new Promise((r) => setTimeout(r, 300));
  }
}

/* ------------------------------------------------------------------ routes */
/** Screen → history stack, mirroring src/state/nav.tsx. */
/** Fixtures installed in both harnesses: a linked homie (enables chat) and an installed plugin. */
export const PLUGIN_DOC = {
  id: "example.arcade",
  name: "Arcade Shelf",
  version: "1.0.0",
  author: "The Gadget",
  description: "Adds a hub orb with shortcuts to your library and 2048.",
  nodes: [{ id: "arcade.shelf", label: "arcade", x: 9, y: 46, d: 96, ly: 16, icon: "games", page: "games" }],
  pages: [
    {
      id: "arcade.page",
      label: "arcade",
      title: "Arcade",
      subtitle: "Quick links around your library",
      blocks: [
        { type: "text", value: "A small page added by a plugin. It opens your library, the player and 2048." },
        {
          type: "tiles",
          items: [
            { label: "2048", icon: "star", page: "2048" },
            { label: "albums", icon: "note", page: "albums" },
            { label: "now playing", icon: "music", page: "now" },
          ],
        },
        { type: "button", label: "open games", page: "games" },
      ],
    },
  ],
};

export const PLUGIN_RECORD = {
  id: PLUGIN_DOC.id,
  name: PLUGIN_DOC.name,
  version: PLUGIN_DOC.version,
  author: PLUGIN_DOC.author,
  description: PLUGIN_DOC.description,
  source: JSON.stringify(PLUGIN_DOC, null, 2),
  doc: PLUGIN_DOC,
  enabled: true,
  installed: 1758000000000,
};

export const ROUTES = {
  hub: [],
  onboarding: [],
  music: [{ n: "music" }],
  "music-empty": [{ n: "music" }],
  albums: [{ n: "music" }, { n: "albums" }],
  album: [{ n: "music" }, { n: "albums" }, { n: "album", id: "PLACEHOLDER" }],
  songs: [{ n: "music" }, { n: "songs" }],
  playlists: [{ n: "music" }, { n: "playlists" }],
  search: [{ n: "music" }, { n: "search" }],
  "now-playing": [{ n: "music" }, { n: "now" }],
  games: [{ n: "games" }],
  "game-detail": [{ n: "games" }, { n: "game", id: "PLACEHOLDER" }],
  "game-edit": [{ n: "games" }, { n: "game-edit" }],
  "game-browser": [{ n: "games" }, { n: "game", id: "PLACEHOLDER" }],
  g2048: [{ n: "games" }, { n: "g2048" }],
  homies: [{ n: "homies" }],
  "homie-detail": [{ n: "homies" }, { n: "homie", id: "PLACEHOLDER" }],
  "homie-edit": [{ n: "homies" }, { n: "homie-edit" }],
  chat: [{ n: "homies" }, { n: "chat", id: "PLACEHOLDER" }],
  account: [{ n: "account" }],
  config: [{ n: "config" }],
  plugins: [{ n: "plugins" }],
  "plugin-page": [{ n: "plugins" }, { n: "plugin-page", id: "PLACEHOLDER" }],
};

/**
 * Resolve the `PLACEHOLDER` ids in a screen's stack against what the running app actually has:
 * the first album, the built-in game, the first homie, the first plugin page. Falls back to the
 * literal value so a missing entity surfaces as an empty screen instead of a crash.
 */
export async function resolveRoutes(page, screen) {
  const routes = JSON.parse(JSON.stringify(ROUTES[screen] ?? []));
  const needs = (n) => routes.some((r) => r.n === n);
  const ids = await page.evaluate(() => {
    const read = (k, d) => {
      try {
        const raw = localStorage.getItem(k);
        return raw ? JSON.parse(raw) : d;
      } catch {
        return d;
      }
    };
    const games = read("gadget:games", []);
    const homies = read("gadget:homies", []);
    const plugins = read("gadget:plugins", []);
    const pages = [];
    for (const p of plugins) for (const pg of p?.pages ?? []) pages.push(`plugin:${p.id}:${pg.id}`);
    return {
      game: games.find((g) => !g.builtIn)?.id ?? games[0]?.id ?? "built-in-2048",
      homie: homies[0]?.id ?? "h1",
      homieCode: homies[0]?.code ?? "hpabc123",
      album: localStorage.getItem("__gadgetAlbumId") ?? "0",
      page: pages[0] ?? "missing",
    };
  });
  for (const r of routes) {
    if (r.id !== "PLACEHOLDER") continue;
    if (r.n === "game") r.id = ids.game;
    else if (r.n === "homie") r.id = ids.homie;
    else if (r.n === "chat") r.id = ids.homieCode;
    else if (r.n === "album") r.id = ids.album;
    else if (r.n === "plugin-page") r.id = ids.page;
  }
  void needs;
  return routes;
}
