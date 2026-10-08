import puppeteer from "puppeteer-core";
import chromium from "@sparticuz/chromium";
import { fakeFsScript } from "/home/user/Reverse/parity/web/seed.mjs";
const b = await puppeteer.launch({ executablePath: "/tmp/chromium", headless: true, args: [...chromium.args, "--no-sandbox"], env: { ...process.env, LD_LIBRARY_PATH: "/tmp/lib" } });
const p = await b.newPage();
await p.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
await p.evaluateOnNewDocument(fakeFsScript());
await p.evaluateOnNewDocument((seed) => { for (const [k,v] of Object.entries(seed)) localStorage.setItem(k, JSON.stringify(v)); }, {
  "gadget:onboarded": true,
  "gadget:profile": { name: "naz", tagline: "", avatar: null, status: "online", mascot: "grin" },
  "gadget:settings": { hour24: false, reduceMotion: true, ambient: false, chainSway: false, y2k: false, parallax: false, sounds: false, haptics: false, keepAwake: false, autoImmersive: false, notify: false, artwork: true, orbScale: 1, hubScale: 1, chainScale: 1, labelScale: 1, glow: 1 },
  "gadget:last": { at: 1700000000000, screen: "hub" },
});
p.on("console", (m) => console.log("[console." + m.type() + "]", m.text()));
p.on("pageerror", (e) => console.log("[pageerror]", e.message));
await p.goto("file:///home/user/Reverse/dist/index.html", { waitUntil: "load" });
await p.waitForSelector(".node[aria-label=music]", { timeout: 8000 });
await p.click(".node[aria-label=music]");
await p.waitForSelector(".picker .btn", { timeout: 8000 });
console.log("picker found; showDirectoryPicker type =", await p.evaluate(() => typeof window.showDirectoryPicker), await p.evaluate(() => window.__gadgetFakeLibrary));
await p.click(".picker .btn");
await new Promise((r) => setTimeout(r, 2500));
console.log("body classes/state:", await p.evaluate(() => document.body.innerHTML.match(/class="[^"]*music[^"]*"/g)?.slice(0, 5)));
console.log("has hero-orb:", await p.evaluate(() => !!document.querySelector(".hero-orb")), "| has agrid:", await p.evaluate(() => !!document.querySelector(".agrid")));
console.log("visible text:", (await p.evaluate(() => document.body.innerText)).slice(0, 300).replace(/\n+/g, " | "));
await b.close();
