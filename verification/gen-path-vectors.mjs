/**
 * Reference vectors for the SVG path parser.
 *
 * The native app re-draws every icon, the wireframe, the logo and the mascot from the exact `d`
 * strings the web sources carry (see extract-svg.mjs). That only pays off if the *parser* lands on
 * the same geometry as the renderer the app actually ships against — the browser. So this script
 * puts each path into a live DOM, asks Chromium for `getTotalLength()` and samples
 * `getPointAtLength()` at even arc-length fractions, and writes the result to
 * `verification/vectors/paths.json`.
 *
 * `RunVectors.kt` then runs the Kotlin parser over the same strings and compares.
 *
 * Usage (from Reverse/):  node verification/gen-path-vectors.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";
import { extractAll } from "./extract-svg.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");

/**
 * The browser toolchain lives with the parity harness (`parity/node_modules`), which is the only
 * place that declares puppeteer. Resolve it from there instead of asking this folder to duplicate
 * the dependency — ESM ignores NODE_PATH, so the require-based lookup is what makes the script
 * runnable from any cwd.
 */
const require_ = createRequire(path.join(ROOT, "parity", "package.json"));
async function fromParity(name) {
  return (await import(pathToFileURL(require_.resolve(name)).href)).default;
}
const puppeteer = await fromParity("puppeteer-core");
const chromium = await fromParity("@sparticuz/chromium");
const OUT = path.join(HERE, "vectors/paths.json");
const SAMPLES = 32;

const groups = extractAll();
const paths = [];
for (const [group, , els] of groups) {
  els.forEach((el, i) => {
    if (el.kind === "path" && el.d) paths.push({ group, index: i, d: el.d });
  });
}
console.log(`sampling ${paths.length} paths from ${groups.length} groups`);

process.env.LD_LIBRARY_PATH = "/tmp/lib";
const browser = await puppeteer.launch({
  args: [...chromium.args, "--no-sandbox"],
  executablePath: await chromium.executablePath(),
  headless: true,
  env: { ...process.env, LD_LIBRARY_PATH: "/tmp/lib" },
});

try {
  const page = await browser.newPage();
  await page.setContent("<!doctype html><body style='margin:0'><svg id='s' width='800' height='800'></svg></body>");
  const vectors = await page.evaluate(
    (items, samples) => {
      const svg = document.getElementById("s");
      const out = [];
      for (const item of items) {
        const el = document.createElementNS("http://www.w3.org/2000/svg", "path");
        el.setAttribute("d", item.d);
        svg.appendChild(el);
        const len = el.getTotalLength();
        const pts = [];
        for (let i = 0; i <= samples; i++) {
          const p = el.getPointAtLength((len * i) / samples);
          pts.push([Number(p.x.toFixed(5)), Number(p.y.toFixed(5))]);
        }
        out.push({ group: item.group, index: item.index, d: item.d, length: Number(len.toFixed(5)), points: pts });
        svg.removeChild(el);
      }
      return out;
    },
    paths,
    SAMPLES,
  );

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(
    OUT,
    JSON.stringify(
      {
        note: "Sampled from Chromium SVGGeometryElement.getPointAtLength — the ground truth for the Kotlin SvgPath parser.",
        samples: SAMPLES,
        paths: vectors,
      },
      null,
      1,
    ),
  );
  const bad = vectors.filter((v) => !Number.isFinite(v.length) || v.points.some(([x, y]) => !Number.isFinite(x) || !Number.isFinite(y)));
  console.log(`wrote ${path.relative(ROOT, OUT)} — ${vectors.length} paths`);
  if (bad.length) {
    console.error(`WARNING: ${bad.length} paths did not sample cleanly:`);
    for (const b of bad.slice(0, 10)) console.error(`  ${b.group}#${b.index} len=${b.length}`);
    process.exitCode = 1;
  }
} finally {
  await browser.close().catch(() => {});
}
