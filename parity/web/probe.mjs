// Sample a captured PNG at normalised points and emit "#RRGGBB" per point, so the native PIXPROBE
// can be compared against the web reference at identical coordinates.
// Usage: node web/probe.mjs <png> <nx,ny> [<nx,ny> ...]
import fs from "node:fs";
import { PNG } from "pngjs";

const file = process.argv[2];
const pts = process.argv.slice(3);
if (!file || !pts.length) {
  console.error("usage: node web/probe.mjs <png> <nx,ny> ...");
  process.exit(2);
}
const png = PNG.sync.read(fs.readFileSync(file));
const hex = (v) => v.toString(16).padStart(2, "0").toUpperCase();
const out = pts
  .map((p) => {
    const [nx, ny] = p.split(",").map(Number);
    const x = Math.max(0, Math.min(png.width - 1, Math.round(nx * png.width)));
    const y = Math.max(0, Math.min(png.height - 1, Math.round(ny * png.height)));
    const i = (png.width * y + x) << 2;
    return `${p}=#${hex(png.data[i])}${hex(png.data[i + 1])}${hex(png.data[i + 2])}`;
  })
  .join(" ");
console.log(`REFPIX|${png.width}x${png.height} ${out}`);
