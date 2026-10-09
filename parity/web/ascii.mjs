// Downsample a captured PNG to an ASCII luminance grid so the hub layout can be compared as text
// from networks that cannot reach the artifact blob store. Mirrors the native harness' grid so the
// two maps are directly comparable.
//
// Usage: node web/ascii.mjs <png> [cols]
import fs from "node:fs";
import { PNG } from "pngjs";

const file = process.argv[2];
const cols = Number(process.argv[3] || 46);
if (!file) {
  console.error("usage: node web/ascii.mjs <png> [cols]");
  process.exit(2);
}

const png = PNG.sync.read(fs.readFileSync(file));
const cols2 = Math.max(8, Math.min(120, cols));
const rows = Math.max(8, Math.round((cols2 * png.height) / png.width));
const RAMP = " .:-=+*#%@"; // 10 luminance levels, dark -> bright

function lumAt(c, r) {
  // box-average the source pixels mapping to this cell
  const x0 = Math.floor((c * png.width) / cols2);
  const x1 = Math.max(x0 + 1, Math.floor(((c + 1) * png.width) / cols2));
  const y0 = Math.floor((r * png.height) / rows);
  const y1 = Math.max(y0 + 1, Math.floor(((r + 1) * png.height) / rows));
  let sum = 0;
  let n = 0;
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const i = (png.width * y + x) << 2;
      const r8 = png.data[i];
      const g8 = png.data[i + 1];
      const b8 = png.data[i + 2];
      sum += (r8 * 299 + g8 * 587 + b8 * 114) / 1000;
      n++;
    }
  }
  return n ? sum / n : 0;
}

for (let r = 0; r < rows; r++) {
  let line = "";
  for (let c = 0; c < cols2; c++) {
    const v = lumAt(c, r);
    line += RAMP[Math.min(RAMP.length - 1, Math.floor((v * RAMP.length) / 256))];
  }
  console.log(`G|${line}`);
}
