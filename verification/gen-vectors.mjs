/**
 * Reference vectors from the *real* web app sources.
 *
 * Every vector is produced by executing the TypeScript that ships in `src/` — bundled with the
 * same esbuild/vite toolchain the app is built with — not by re-typing the algorithms. The two
 * exceptions are called out where they happen:
 *
 *   - `Game2048.tsx` keeps its engine inside the component file, so the harness slices the
 *     module-level functions out of the source text and evaluates that slice verbatim.
 *   - `social.ts` does not export its topic helper, so a *copy* of the module gets one extra
 *     `export` line appended before bundling (the app source itself is never touched).
 *
 * Output: verification/vectors/*.json — consumed by verification/RunVectors.kt, which runs the
 * Kotlin port against the same inputs and diffs the results.
 *
 * Usage: node verification/gen-vectors.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

/* ------------------------------------------------------------------ DOM shim
 * Some modules compute their initial value at import time (metrics.ts reads window.innerWidth).
 * A minimal, inert stand-in keeps those modules importable without a browser; nothing the vectors
 * exercise depends on it. */
function installDomShim() {
  const noop = () => {};
  const element = () => ({
    style: {}, dataset: {}, classList: { add: noop, remove: noop, toggle: noop },
    setAttribute: noop, removeAttribute: noop, appendChild: noop, remove: noop,
    addEventListener: noop, removeEventListener: noop, getBoundingClientRect: () => ({ x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 }),
  });
  globalThis.window = {
    innerWidth: 390, innerHeight: 844, devicePixelRatio: 3,
    addEventListener: noop, removeEventListener: noop, dispatchEvent: noop,
    matchMedia: () => ({ matches: false, addEventListener: noop, removeEventListener: noop, addListener: noop, removeListener: noop }),
    setTimeout, clearTimeout, setInterval, clearInterval,
    localStorage: undefined, document: undefined,
  };
  globalThis.document = {
    documentElement: element(), body: element(), head: element(),
    createElement: element, querySelector: () => null, querySelectorAll: () => [],
    addEventListener: noop, removeEventListener: noop,
  };
  const store = new Map();
  globalThis.localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    clear: () => store.clear(),
    key: (i) => [...store.keys()][i] ?? null,
    get length() { return store.size; },
  };
  window.localStorage = globalThis.localStorage;
  window.document = globalThis.document;
}
installDomShim();

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..");
const SRC = path.join(REPO, "src");
const TMP = path.join(HERE, ".tmp");
const OUT = path.join(HERE, "vectors");
const ESBUILD = path.join(REPO, "node_modules", "esbuild", "bin", "esbuild");

fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(TMP, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

/** Bundle a generated entry file and import the result. */
async function bundle(name, source, { aliases = [], external = [] } = {}) {
  const entry = path.join(TMP, `${name}.ts`);
  const outfile = path.join(TMP, `${name}.mjs`);
  fs.writeFileSync(entry, source);
  execFileSync(
    ESBUILD,
    [
      entry, "--bundle", "--format=esm", "--platform=node", "--outfile=" + outfile, "--log-level=error",
      ...aliases.flatMap(([from, to]) => [`--alias:${from}=${to}`]),
      ...external.map((m) => `--external:${m}`),
    ],
    { cwd: REPO },
  );
  return import("file://" + outfile);
}

const write = (name, data) => {
  fs.writeFileSync(path.join(OUT, `${name}.json`), JSON.stringify(data, null, 1) + "\n");
  console.log(`${name}.json  ${(JSON.stringify(data).length / 1024).toFixed(1)} kB`);
};

const rel = (p) => path.relative(TMP, p).replace(/\\/g, "/");

/* ------------------------------------------------------------------ metrics */
{
  const mod = await bundle("metrics", `export * from "${rel(path.join(SRC, "utils/metrics.ts"))}";`);
  const sizes = [];
  for (const w of [320, 360, 375, 390, 393, 412, 428, 480, 519, 520, 560, 700, 736, 799, 800, 900, 1000, 1024, 1180, 1280]) {
    for (const h of [360, 390, 412, 480, 640, 736, 780, 844, 873, 915, 1000, 1024, 1180, 1366, 1600]) {
      sizes.push([w, h]);
    }
  }
  const vectors = sizes.map(([w, h]) => {
    const m = mod.computeMetrics(w, h);
    return { w, h, sw: m.sw, sh: m.sh, fx: m.fx, fy: m.fy, u: m.u };
  });
  write("metrics", vectors);
}

/* ------------------------------------------------------------------ text/format/links */
{
  const mod = await bundle(
    "text",
    `export { titleFromFile, formatTime } from "${rel(path.join(SRC, "utils/musicLibrary.ts"))}";
     export { normaliseLink, ago, duration } from "${rel(path.join(SRC, "utils/link.ts"))}";
     export { fmtDuration, topTracks } from "${rel(path.join(SRC, "utils/stats.ts"))}";
     export { letterOf, byTitle, LETTERS } from "${rel(path.join(SRC, "utils/albums.ts"))}";`,
  );
  const names = [
    "01 Chrome Sigh.mp3", "02 Glass Teeth.mp3", "10 Late Room.mp3", "11 Fade_Out.mp3",
    "Ámbar.flac", "no-extension", "TRACK.WAV", "a.b.c.m4a", "  spaced   name  .mp3",
    "drums.ogg", "x_y-z (mix)!.opus", ".hidden.mp3", "2048 theme.aiff",
  ];
  const secs = [0, 0.4, 1, 59.4, 59.5, 61, 3599, 3600, 3661, 7200, 7325, 86_400];
  const links = ["", "  ", "example.com", "https://a.b/c", "tel:+15551234", "steam://run/1", "HTTP://X", "mailto:a@b", "not a link", "//weird"];
  const now = 1_760_000_000_000;
  const stamps = [null, 0, now - 500, now - 5_000, now - 65_000, now - 3_600_000, now - 90_000_000, now - 3_000_000_000];
  write("text", {
    titles: names.map((n) => ({ in: n, out: mod.titleFromFile(n) })),
    formatTime: secs.map((s) => ({ in: s, out: mod.formatTime(s) })),
    durations: secs.map((s) => ({ in: s, out: mod.duration(s) })),
    fmtDuration: secs.map((s) => ({ in: s, out: mod.fmtDuration(s) })),
    links: links.map((l) => ({ in: l, out: mod.normaliseLink(l) })),
    // `ago` is relative to Date.now(); the harness freezes the clock so both sides agree.
    ago: stamps.map((t) => ({ in: t, now, out: (() => { const real = Date.now; Date.now = () => now; try { return mod.ago(t); } finally { Date.now = real; } })() })),
    letters: ["Aerosmith", "adam", "Ámbar", "2048", "#hash", "the gadget"].map((t) => ({ in: t, out: mod.letterOf(t) })),
    byTitle: (() => {
      const list = ["Track 10", "track 2", "Apple", "apple", "Ápple", "Zebra", "10", "2"].map((title) => ({ id: title, title, folder: "f" }));
      return { in: list.map((t) => t.title), out: [...list].sort(mod.byTitle).map((t) => t.title) };
    })(),
    topTracks: [
      { lib: [{ id: "a", title: "Alpha" }, { id: "b", title: "Beta" }, { id: "c", title: "Gamma" }, { id: "d", title: "Delta" }], stats: { a: 12, b: 120, c: 90, d: 300 } },
      { lib: [{ id: "z", title: "Zed" }], stats: { z: 30, missing: 500 } },
    ].map((v) => ({ ...v, out: mod.topTracks(v.lib, v.stats) })),
  });
}

/* ------------------------------------------------------------------ plugins */
{
  const mod = await bundle("plugins", `export * from "${rel(path.join(SRC, "plugins/schema.ts"))}";`);
  const docs = [];
  const base = () => ({ id: "example.arcade", name: "Arcade Shelf" });
  docs.push(["minimal", base()]);
  docs.push(["version+author", { ...base(), version: "2.0.1", author: "Someone" }]);
  docs.push(["nodes", { ...base(), nodes: [{ id: "n1", label: "arc", x: 9, y: 46, d: 96, ly: 16, icon: "games", page: "games" }] }]);
  docs.push(["node defaults", { ...base(), nodes: [{ id: "n2", label: "x" }] }]);
  docs.push(["theme full", { ...base(), theme: { accent: "#FFC94D", orb: ["#FFE59B", "#C08A32", "#573614", "#1A1308"], orbScale: 1.4, hubScale: 0.7, chainScale: 2, labelScale: 1.7, glow: 0.3 } }]);
  docs.push(["page blocks", { ...base(), pages: [{ id: "p1", label: "arcade", blocks: [{ type: "text", value: "hi" }, { type: "header", value: "h" }, { type: "note", value: "n" }, { type: "link", label: "g", url: "https://g.co" }, { type: "button", label: "b", page: "games" }, { type: "tiles", items: [{ label: "t", icon: "star", page: "games" }] }] }] }]);
  // invalid documents — every branch of the validator
  docs.push(["no id", { name: "x" }]);
  docs.push(["bad id", { id: "A_B", name: "x" }]);
  docs.push(["short id", { id: "a", name: "x" }]);
  docs.push(["no name", { id: "ok.id" }]);
  docs.push(["name too long", { id: "ok.id", name: "x".repeat(41) }]);
  docs.push(["version not semver", { ...base(), version: "2.0" }]);
  docs.push(["empty", {}]);
  docs.push(["nodes not array", { ...base(), nodes: {} }]);
  docs.push(["node no id", { ...base(), nodes: [{ label: "x" }] }]);
  docs.push(["node bad x", { ...base(), nodes: [{ id: "n", label: "x", x: "abc" }] }]);
  docs.push(["node x range", { ...base(), nodes: [{ id: "n", label: "x", x: 111 }] }]);
  docs.push(["node d range", { ...base(), nodes: [{ id: "n", label: "x", d: 20 }] }]);
  docs.push(["node label long", { ...base(), nodes: [{ id: "n", label: "x".repeat(25) }] }]);
  docs.push(["node dup id", { ...base(), nodes: [{ id: "n", label: "a" }, { id: "n", label: "b" }] }]);
  docs.push(["node page clash", { ...base(), nodes: [{ id: "n", label: "a", page: "music" }] }]);
  docs.push(["too many nodes", { ...base(), nodes: Array.from({ length: 13 }, (_, i) => ({ id: "n" + i, label: "n" })) }]);
  docs.push(["pages not array", { ...base(), pages: [] }]);
  docs.push(["page no id", { ...base(), pages: [{ label: "x" }] }]);
  docs.push(["page no label", { ...base(), pages: [{ id: "p" }] }]);
  docs.push(["page title long", { ...base(), pages: [{ id: "p", label: "x", title: "t".repeat(61) }] }]);
  docs.push(["page too many blocks", { ...base(), pages: [{ id: "p", label: "x", blocks: Array.from({ length: 41 }, () => ({ type: "text", value: "t" })) }] }]);
  docs.push(["block unknown", { ...base(), pages: [{ id: "p", label: "x", blocks: [{ type: "video", value: "v" }] }] }]);
  docs.push(["block text long", { ...base(), pages: [{ id: "p", label: "x", blocks: [{ type: "text", value: "t".repeat(601) }] }] }]);
  docs.push(["link not http", { ...base(), pages: [{ id: "p", label: "x", blocks: [{ type: "link", label: "l", url: "ftp://x" }] }] }]);
  docs.push(["button both", { ...base(), pages: [{ id: "p", label: "x", blocks: [{ type: "button", label: "b", page: "games", url: "https://x.co" }] }] }]);
  docs.push(["tiles empty", { ...base(), pages: [{ id: "p", label: "x", blocks: [{ type: "tiles", items: [] }] }] }]);
  docs.push(["theme bad accent", { ...base(), theme: { accent: "red" } }]);
  docs.push(["theme orb 3", { ...base(), theme: { orb: ["#fff", "#000", "#111"] } }]);
  docs.push(["theme glow range", { ...base(), theme: { glow: 2 } }]);
  docs.push(["empty theme", { ...base(), theme: {} }]);
  docs.push(["not json", "__raw__:{oops"]);
  docs.push(["json array", "__raw__:[]"]);
  docs.push(["json number", "__raw__:42"]);
  const out = docs.map(([label, doc]) => {
    const raw = typeof doc === "string" && doc.startsWith("__raw__:") ? doc.slice(8) : JSON.stringify(doc, null, 2);
    try {
      const rec = mod.parsePlugin(raw);
      return { label, ok: true, id: rec.id, name: rec.name, version: rec.version, author: rec.author, nodes: rec.doc.nodes?.length ?? 0, pages: rec.doc.pages?.length ?? 0, themeKeys: Object.keys(rec.doc.theme ?? {}).sort(), sourceIsPretty: rec.source === raw };
    } catch (err) {
      return { label, ok: false, error: err.message, isPluginError: err instanceof mod.PluginError };
    }
  });
  write("plugins", out);
}

/* ------------------------------------------------------------------ social topics/codes */
{
  // social.ts keeps its topic helper private, so a *copy* of the module gets one extra export
  // line. The copy lives beside the original (so its relative imports keep working) and is
  // removed again as soon as the bundle is built — the app source is never modified.
  const copy = path.join(SRC, "utils/vectors-social.tmp.ts");
  fs.writeFileSync(copy, fs.readFileSync(path.join(SRC, "utils/social.ts"), "utf8") + "\nexport { chatTopic, pTopic };\n");
  let mod;
  try {
    // `mqtt` is a Node/Browser package; leave it external so Node loads the real thing.
    mod = await bundle("social", `export * from "${rel(copy)}";`, { external: ["mqtt", "react"] });
  } finally {
    fs.rmSync(copy, { force: true });
  }
  const codes = ["hpabc234", "HPABC234", " hp abc 234 ", "ab", "abcdefghijklmnopq", "a-b", "!!!!", "", "  ", "hp1"];
  const pairs = [["hpabc234", "hpzzz999"], ["hpzzz999", "hpabc234"], ["aaa", "aaa"], ["zzz", "aaa"]];
  write("social", {
    normalize: codes.map((c) => ({ in: c, out: mod.normalizeCode(c) })),
    valid: codes.map((c) => ({ in: c, out: mod.isValidCode(c) })),
    chatTopic: pairs.map(([a, b]) => ({ a, b, out: mod.chatTopic(a, b) })),
    presenceTopic: ["hpabc234", "x y"].map((c) => ({ in: c, out: mod.pTopic(c) })),
  });
}

/* ------------------------------------------------------------------ 2048 engine */
{
  const source = fs.readFileSync(path.join(SRC, "pages/games/Game2048.tsx"), "utf8");
  // The engine is the module-level block between `let uid = 1;` and the colour table.
  const start = source.indexOf("let uid = 1;");
  const end = source.indexOf("const TONE: Record<number");
  if (start < 0 || end < 0) throw new Error("2048 engine block not found — the source moved; update the harness");
  const block = source.slice(start + "let uid = 1;".length, end);
  // esbuild (the toolchain the app itself is built with) strips the type annotations.
  const engine = await bundle("engine2048", `let uid = 1;${block}\nexport { spawn, fresh, move };`);

  // Deterministic LCG — the Kotlin side replays exactly this sequence.
  let state = 123456789;
  const nextRandom = () => {
    state = (Math.imul(state, 1103515245) + 12345) & 0x7fffffff;
    return state / 0x80000000;
  };
  const realRandom = Math.random;
  Math.random = nextRandom;
  const key = (t) => `${t.r},${t.c},${t.v}`;
  const brief = (tiles) => tiles.map(key).sort();
  try {
    const trace = [];
    let board = engine.fresh();
    trace.push({ op: "fresh", tiles: brief(board) });
    for (const dir of ["left", "up", "right", "down", "left", "left", "down", "right", "up", "right", "down", "left", "up", "right", "down", "left", "up", "right", "down", "left"]) {
      const r = engine.move(board, dir);
      trace.push({ op: `move:${dir}`, moved: r.moved, gained: r.gained, tiles: brief(r.tiles) });
      if (r.moved) {
        board = engine.spawn(r.tiles);
        trace.push({ op: "spawn", tiles: brief(board) });
      } else {
        board = r.tiles;
      }
    }
    // stuck detection over a known-full board
    const full = [];
    let id = 1;
    for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) full.push({ id: id++, v: 1 << ((r + c) % 11) >= 2048 ? 2048 : 2 ** (1 + ((r * 4 + c) % 9)), r, c });
    const stuckBoards = [full, engine.fresh()];
    const stuck = stuckBoards.map((b) => !["up", "down", "left", "right"].some((d) => engine.move(b, d).moved));
    trace.push({ op: "stuck", values: stuck, boards: stuckBoards.map(brief) });
    write("game2048", { trace, randomSequenceConsumed: state });
  } finally {
    Math.random = realRandom;
  }
}

/* ------------------------------------------------------------------ metadata (real files) */
{
  const mod = await bundle("tags", `export { readTags } from "${rel(path.join(SRC, "utils/tags.ts"))}";`);
  URL.createObjectURL = () => "blob:fixture";
  const b64 = (u8) => Buffer.from(u8).toString("base64");
  const fixtures = buildMediaFixtures();
  const out = [];
  for (const [label, bytes, name] of fixtures) {
    const file = {
      name,
      size: bytes.length,
      slice: (a, b) => {
        const view = bytes.subarray(a, b === undefined ? bytes.length : b);
        return { arrayBuffer: async () => view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength) };
      },
    };
    const meta = await mod.readTags(file);
    out.push({ label, name, bytes: b64(bytes), expect: { title: meta.title ?? null, artist: meta.artist ?? null, album: meta.album ?? null, hasCover: !!meta.coverUrl } });
  }
  write("metadata", out);
}

function buildMediaFixtures() {
  const enc = new TextEncoder();
  const out = [];
  const png = Uint8Array.from(atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="), (c) => c.charCodeAt(0));

  const id3Frame = (id, text, version) => {
    const body = new Uint8Array(1 + text.length);
    body[0] = 3;
    for (let i = 0; i < text.length; i++) body[i + 1] = text.charCodeAt(i) & 0xff;
    const head = new Uint8Array(10);
    for (let i = 0; i < 4; i++) head[i] = id.charCodeAt(i);
    const size = body.length;
    if (version === 4) {
      head[4] = (size >> 21) & 0x7f; head[5] = (size >> 14) & 0x7f; head[6] = (size >> 7) & 0x7f; head[7] = size & 0x7f;
    } else {
      head[4] = (size >> 24) & 0xff; head[5] = (size >> 16) & 0xff; head[6] = (size >> 8) & 0xff; head[7] = size & 0xff;
    }
    return [head, body];
  };
  const apic = (version) => {
    const mime = "image/png";
    const body = new Uint8Array(1 + mime.length + 1 + 1 + 1 + png.length);
    for (let i = 0; i < mime.length; i++) body[1 + i] = mime.charCodeAt(i);
    let o = 1 + mime.length + 1;
    body[o++] = 3;
    body[o++] = 0;
    body.set(png, o);
    const head = new Uint8Array(10);
    "APIC".split("").forEach((c, i) => (head[i] = c.charCodeAt(0)));
    const size = body.length;
    if (version === 4) {
      head[4] = (size >> 21) & 0x7f; head[5] = (size >> 14) & 0x7f; head[6] = (size >> 7) & 0x7f; head[7] = size & 0x7f;
    } else {
      head[4] = (size >> 24) & 0xff; head[5] = (size >> 16) & 0xff; head[6] = (size >> 8) & 0xff; head[7] = size & 0xff;
    }
    return [head, body];
  };
  const id3 = (version, frames) => {
    const parts = frames.flatMap((f) => f(version));
    const total = parts.reduce((a, p) => a + p.length, 0);
    const head = new Uint8Array(10);
    head[0] = 0x49; head[1] = 0x44; head[2] = 0x33; head[3] = version;
    head[4] = 0; head[5] = 0;
    head[6] = (total >> 21) & 0x7f; head[7] = (total >> 14) & 0x7f; head[8] = (total >> 7) & 0x7f; head[9] = total & 0x7f;
    const body = new Uint8Array(total);
    let o = 0;
    for (const p of parts) { body.set(p, o); o += p.length; }
    return [head, body];
  };
  const audio = new Uint8Array(64);

  const v24 = id3(4, [(v) => id3Frame("TIT2", "Chrome Sigh", v), (v) => id3Frame("TPE1", "Naz & the Gloss", v), (v) => id3Frame("TALB", "Chrome Dreams", v), (v) => apic(v)]);
  out.push(["id3v2.4", Buffer.concat([...v24.map(Buffer.from), Buffer.from(audio)]), "id3v24.mp3"]);
  const v23 = id3(3, [(v) => id3Frame("TIT2", "Glass Teeth", v), (v) => id3Frame("TPE1", "Naz", v), (v) => id3Frame("TALB", "Chrome Dreams", v)]);
  out.push(["id3v2.3", Buffer.concat([...v23.map(Buffer.from), Buffer.from(audio)]), "id3v23.mp3"]);

  // ID3v1.1 trailer
  const v1 = new Uint8Array(128);
  v1.set(enc.encode("TAG"), 0);
  const put = (s, off, len) => { const b = enc.encode(s); v1.set(b.slice(0, len), off); };
  put("Late Room", 3, 30);
  put("Naz", 33, 30);
  put("Chrome Dreams", 63, 30);
  put("1999", 93, 4);
  put("comment", 97, 28);
  v1[125] = 0;
  v1[126] = 7;
  out.push(["id3v1.1", Buffer.concat([Buffer.from(audio), Buffer.from(v1)]), "id3v1.mp3"]);

  // FLAC: fLaC + STREAMINFO + VORBIS_COMMENT + PICTURE
  const block = (type, data) => {
    const head = new Uint8Array(4);
    head[0] = type;
    head[1] = (data.length >> 16) & 0xff; head[2] = (data.length >> 8) & 0xff; head[3] = data.length & 0xff;
    return Buffer.concat([Buffer.from(head), Buffer.from(data)]);
  };
  const streaminfo = new Uint8Array(34);
  const vc = () => {
    const vendor = enc.encode("reference libFLAC");
    const comments = ["TITLE=Neon Yard", "ARTIST=Naz & the Gloss", "ALBUM=Late Night"].map((c) => enc.encode(c));
    const size = 4 + vendor.length + 4 + comments.reduce((a, c) => a + 4 + c.length, 0);
    const b = new Uint8Array(size);
    const dv = new DataView(b.buffer);
    let o = 0;
    dv.setUint32(o, vendor.length, true); o += 4;
    b.set(vendor, o); o += vendor.length;
    dv.setUint32(o, comments.length, true); o += 4;
    for (const c of comments) { dv.setUint32(o, c.length, true); o += 4; b.set(c, o); o += c.length; }
    return b;
  };
  const picture = () => {
    const desc = enc.encode("");
    const mime = "image/png";
    const b = new Uint8Array(4 + 4 + mime.length + 4 + desc.length + 4 + 4 + 4 + 4 + 4 + png.length);
    const dv = new DataView(b.buffer);
    let o = 0;
    dv.setUint32(o, 3, false); o += 4;             // picture type 3 = front cover
    dv.setUint32(o, mime.length, false); o += 4;
    b.set(enc.encode(mime), o); o += mime.length;
    dv.setUint32(o, desc.length, false); o += 4;
    b.set(desc, o); o += desc.length;
    dv.setUint32(o, 1, false); o += 4;             // width
    dv.setUint32(o, 1, false); o += 4;             // height
    dv.setUint32(o, 24, false); o += 4;            // depth
    dv.setUint32(o, 0, false); o += 4;             // palette
    dv.setUint32(o, png.length, false); o += 4;
    b.set(png, o);
    return b;
  };
  out.push([
    "flac",
    Buffer.concat([
      Buffer.from("fLaC"),
      block(0, streaminfo),
      block(4, vc()),
      block(6, picture()),
      Buffer.from(audio),
    ]),
    "late.flac",
  ]);

  // MP4/M4A: ftyp + moov(udta(meta(hdlr,ilst)))
  const atom = (type, payload) => {
    const head = new Uint8Array(8);
    new DataView(head.buffer).setUint32(0, 8 + payload.length, false);
    for (let i = 0; i < 4; i++) head[4 + i] = type.charCodeAt(i);
    return Buffer.concat([Buffer.from(head), Buffer.from(payload)]);
  };
  // key atom → `data` atom: [version+flags][locale][payload]. Flag 14 marks a PNG cover.
  const dataAtom = (value, flags) => atom("data", Buffer.concat([
    Buffer.from([0, 0, 0, flags]),
    Buffer.from(new Uint8Array(4)),
    typeof value === "string" ? Buffer.from(enc.encode(value)) : Buffer.from(value),
  ]));
  const key = (name, value, flags = 1) => atom(name, dataAtom(value, flags));
  const ilst = atom("ilst", Buffer.concat([
    key("©nam", "Amber"),
    key("©ART", "Naz"),
    key("©alb", "Chrome Dreams"),
    key("covr", png, 14),
  ]));
  const hdlr = atom("hdlr", Buffer.concat([Buffer.from(new Uint8Array(8)), Buffer.from("mdir"), Buffer.from("appl"), Buffer.from(new Uint8Array(9))]));
  const meta = atom("meta", Buffer.concat([Buffer.from(new Uint8Array(4)), hdlr, ilst]));
  const moov = atom("moov", atom("udta", meta));
  const ftyp = atom("ftyp", Buffer.from(enc.encode("M4A ")).length ? Buffer.concat([Buffer.from(enc.encode("M4A ")), Buffer.from(new Uint8Array(4)), Buffer.from(enc.encode("M4A mp42isom"))]) : new Uint8Array());
  out.push(["mp4", Buffer.concat([ftyp, moov, Buffer.from(audio)]), "amber.m4a"]);

  return out;
}

console.log("\nvectors written to", path.relative(REPO, OUT));
