/**
 * The Gadget — native side of the visual parity pipeline.
 *
 * This script takes the screenshots produced by the Compose instrumented test
 * (`android/app/src/androidTest/.../ParityCaptureTest.kt`, which writes one PNG per route into
 * `parity/out/native/<viewport>/`) and compares them against the web reference PNGs captured by
 * `web/capture.mjs`, producing `reference.png` / `native.png` / `diff.png` / `overlay.png` for
 * every screen and printing a per-screen mismatch table.
 *
 * Usage (from parity/):
 *   node compare.mjs                     # all screens, default viewport
 *   node compare.mjs --viewport 390x844
 *   node compare.mjs --only hub,music    # subset
 *   node compare.mjs --threshold 0.02    # allowed fraction of differing pixels (default 0.02)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { PNG } from "pngjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const WEB = path.join(HERE, "out/web");
const NATIVE = path.join(HERE, "out/native");

const argv = process.argv.slice(2);
const argOf = (name, dflt) => {
  const i = argv.indexOf(name);
  return i >= 0 ? argv[i + 1] : dflt;
};
const VIEWPORT = argOf("--viewport", "390x844");
const ONLY = argOf("--only", "") ? argOf("--only", "").split(",") : null;
const THRESHOLD = Number(argOf("--threshold", "0.02"));
const DIFF_DIR = path.join(HERE, "out/diff", VIEWPORT);

/** Perceptual-ish distance: channel delta with a floor, so antialiasing noise does not count. */
const PIXEL_TOLERANCE = Number(argOf("--pixel-tolerance", "24"));

function readPng(file) {
  return PNG.sync.read(fs.readFileSync(file));
}

function compare(refPng, nativePng) {
  const w = Math.min(refPng.width, nativePng.width);
  const h = Math.min(refPng.height, nativePng.height);
  const diff = new PNG({ width: w, height: h });
  const overlay = new PNG({ width: w, height: h });
  let differing = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * refPng.width + x) * 4;
      const j = (y * nativePng.width + x) * 4;
      const k = (y * w + x) * 4;
      const dr = Math.abs(refPng.data[i] - nativePng.data[j]);
      const dg = Math.abs(refPng.data[i + 1] - nativePng.data[j + 1]);
      const db = Math.abs(refPng.data[i + 2] - nativePng.data[j + 2]);
      const da = Math.abs(refPng.data[i + 3] - nativePng.data[j + 3]);
      const isDiff = Math.max(dr, dg, db, da) > PIXEL_TOLERANCE;
      if (isDiff) differing++;
      // diff.png: red where the pixels disagree, grey where they agree
      diff.data[k] = isDiff ? 255 : 40;
      diff.data[k + 1] = isDiff ? 0 : 40;
      diff.data[k + 2] = isDiff ? 0 : 40;
      diff.data[k + 3] = 255;
      // overlay.png: reference as luminance, native as magenta — ghosting shows any offset
      const lum = 0.299 * refPng.data[i] + 0.587 * refPng.data[i + 1] + 0.114 * refPng.data[i + 2];
      const nloc = 0.299 * nativePng.data[j] + 0.587 * nativePng.data[j + 1] + 0.114 * nativePng.data[j + 2];
      overlay.data[k] = Math.round(Math.min(255, lum * 0.75 + nloc * 0.25));
      overlay.data[k + 1] = Math.round(Math.min(255, lum * 0.35 + nloc * 0.15));
      overlay.data[k + 2] = Math.round(Math.min(255, lum * 0.35 + nloc * 0.65));
      overlay.data[k + 3] = 255;
    }
  }
  return { differing, total: w * h, diff, overlay, size: [w, h] };
}

function main() {
  if (!fs.existsSync(NATIVE)) {
    console.error(
      `No native captures at ${path.relative(ROOT, NATIVE)}.\n` +
        "Run the instrumented test first:\n" +
        "  cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.thegadget.app.parity.ParityCaptureTest",
    );
    process.exit(2);
  }
  const refDir = path.join(WEB, VIEWPORT);
  const natDir = path.join(NATIVE, VIEWPORT);
  if (!fs.existsSync(refDir)) {
    console.error(`No web reference for viewport ${VIEWPORT} — run: node web/capture.mjs --only <screens>`);
    process.exit(2);
  }
  fs.mkdirSync(DIFF_DIR, { recursive: true });
  const screens = fs
    .readdirSync(refDir)
    .filter((f) => f.endsWith(".png"))
    .map((f) => f.replace(/\.png$/, ""))
    .filter((s) => !ONLY || ONLY.includes(s));
  let worst = 0;
  let failures = 0;
  const rows = [];
  for (const screen of screens) {
    const refFile = path.join(refDir, `${screen}.png`);
    const natFile = path.join(natDir, `${screen}.png`);
    if (!fs.existsSync(natFile)) {
      rows.push({ screen, status: "MISSING", ratio: null });
      failures++;
      continue;
    }
    const { differing, total, diff, overlay, size } = compare(readPng(refFile), readPng(natFile));
    const ratio = differing / total;
    worst = Math.max(worst, ratio);
    fs.writeFileSync(path.join(DIFF_DIR, `${screen}.reference.png`), fs.readFileSync(refFile));
    fs.writeFileSync(path.join(DIFF_DIR, `${screen}.native.png`), fs.readFileSync(natFile));
    fs.writeFileSync(path.join(DIFF_DIR, `${screen}.diff.png`), PNG.sync.write(diff));
    fs.writeFileSync(path.join(DIFF_DIR, `${screen}.overlay.png`), PNG.sync.write(overlay));
    const pass = ratio <= THRESHOLD;
    if (!pass) failures++;
    rows.push({ screen, status: pass ? "PASS" : "FAIL", ratio });
  }
  const pad = Math.max(...rows.map((r) => r.screen.length));
  for (const r of rows) {
    const pct = r.ratio === null ? "   —  " : `${(r.ratio * 100).toFixed(3)}%`;
    console.log(`${r.screen.padEnd(pad)}  ${r.status.padEnd(4)}  ${pct}`);
  }
  console.log(
    `\n${rows.length} screens · ${failures} over ${(THRESHOLD * 100).toFixed(1)}% · worst ${(worst * 100).toFixed(3)}%` +
      `\nartefacts: ${path.relative(ROOT, DIFF_DIR)}/<screen>.{reference,native,diff,overlay}.png`,
  );
  process.exit(failures ? 1 : 0);
}

main();
