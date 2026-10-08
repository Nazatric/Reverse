/** Evidence run: what does the *production build* actually do on the chat + homie-edit routes? */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import puppeteer from "puppeteer-core";
import chromium from "@sparticuz/chromium";
import { fakeFsScript } from "./seed.mjs";
import { setRoute, settle } from "./drive.mjs";

const DIST = "/home/user/Reverse/dist";
const MIME = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png" };
const ORIGIN = await new Promise((resolve) => {
  const s = http.createServer((req, res) => {
    const url = decodeURIComponent((req.url || "/").split("?")[0]);
    let file = path.join(DIST, url === "/" ? "index.html" : url);
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(DIST, "index.html");
    res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream", "cache-control": "no-store" });
    fs.createReadStream(file).pipe(res);
  });
  s.listen(0, "127.0.0.1", () => resolve(`http://127.0.0.1:${s.address().port}`));
});

const b = await puppeteer.launch({ executablePath: "/tmp/chromium", headless: true, args: [...chromium.args, "--no-sandbox"], env: { ...process.env, LD_LIBRARY_PATH: "/tmp/lib" } });
const p = await b.newPage();
await p.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
await p.evaluateOnNewDocument(fakeFsScript());
await p.evaluateOnNewDocument(() => {
  const seed = {
    "gadget:onboarded": true,
    "gadget:profile": { name: "naz", tagline: "player one", avatar: null, status: "online", mascot: "grin" },
    "gadget:homies": [
      { id: "h1", name: "kaya", note: "neighbour", link: "", phone: "", status: "online", avatar: null, added: 1, kind: "local" },
      { id: "h4", name: "sable", note: "linked", link: "", phone: "", status: "online", avatar: null, added: 2, kind: "linked", code: "hpabc234" },
    ],
    "gadget:settings": { sounds: false, haptics: false, reduceMotion: true, ambient: false, parallax: false, chainSway: false, y2k: false, keepAwake: false, autoImmersive: false, notify: false, artwork: true, orbScale: 1, hubScale: 1, chainScale: 1, labelScale: 1, glow: 1 },
  };
  for (const [k, v] of Object.entries(seed)) localStorage.setItem(k, JSON.stringify(v));
});
p.on("pageerror", (e) => console.log("[pageerror]", e.message.slice(0, 160)));
await p.goto(ORIGIN + "/", { waitUntil: "networkidle2" });

for (const [label, routes] of [
  ["chat", [{ n: "homies" }, { n: "chat", id: "hpabc234" }]],
  ["homie-edit", [{ n: "homies" }, { n: "homie-edit", id: "h1" }]],
]) {
  await setRoute(p, routes);
  await settle(p, 500);
  const info = await p.evaluate(() => ({
    pagehost: !!document.querySelector(".pagehost"),
    page: !!document.querySelector(".page"),
    surfaceHTML: (document.querySelector(".page-surface")?.innerHTML ?? "").slice(0, 160),
    text: (document.querySelector(".page-surface")?.innerText ?? "").trim().slice(0, 120),
  }));
  console.log(label, JSON.stringify(info));
  await p.screenshot({ path: `/home/user/Reverse/parity/out/evidence-${label}.png` });
}
// and the real user path: click "Edit homie" on a homie detail page
await setRoute(p, [{ n: "homies" }, { n: "homie", id: "h1" }]);
await settle(p, 400);
const orb = await p.$('.page-head .orb, .orb[aria-label="Edit homie"]');
console.log("edit orb present:", !!orb);
if (orb) {
  await orb.click();
  await settle(p, 600);
  console.log("after click:", JSON.stringify(await p.evaluate(() => ({
    stack: history.state?.stack, text: (document.querySelector(".page-surface")?.innerText ?? "").trim().slice(0, 80),
  }))));
}
await b.close();
