/**
 * Deterministic library seed for the parity harness.
 *
 * The web app reads music through the File System Access API (`showDirectoryPicker`). Headless
 * Chromium has no real folders, so this module installs a *fake* directory handle that hands the
 * app genuine `File` objects: a couple of albums, a cover.jpg per folder, lowercase/uppercase
 * names, unicode, numbers (for the numeric sort), and one file carrying a real ID3v2.4 tag so the
 * tag reader is exercised end to end. The app cannot tell the difference — it walks the handle
 * exactly as it would a real folder.
 */

export const FAKE_FS_SCRIPT = `
(() => {
  const files = __FILES__;
  const granted = async () => "granted";

  // Real FileSystem*Handle look-alikes: methods live on the prototype so the objects survive
  // IndexedDB's structured clone (the app persists the folder handle between sessions).
  class FakeFileHandle {
    constructor(name, file) { this.kind = "file"; this.name = name; this.blob = file; }
    async getFile() { return this.blob; }
    async isSameEntry(other) { return other === this; }
  }
  class FakeDirHandle {
    constructor(name, entries) { this.kind = "directory"; this.name = name; this.entries = entries; }
    async *values() { for (const key of Object.keys(this.entries)) yield this.entries[key]; }
    async *keys() { for (const key of Object.keys(this.entries)) yield key; }
    async *entries_() { for (const key of Object.keys(this.entries)) yield [key, this.entries[key]]; }
    async getFileHandle(name) { const e = this.entries[name]; if (!e || e.kind !== "file") throw new DOMException("not found", "NotFoundError"); return e; }
    async getDirectoryHandle(name) { const e = this.entries[name]; if (!e || e.kind !== "directory") throw new DOMException("not found", "NotFoundError"); return e; }
    queryPermission() { return granted(); }
    requestPermission() { return granted(); }
    async isSameEntry(other) { return other === this; }
  }

  function silentWav() {
    // A valid 0.4s silent 8-bit mono WAV so <audio> playback really succeeds.
    const rate = 8000, n = Math.floor(rate * 0.4);
    const buf = new ArrayBuffer(44 + n);
    const dv = new DataView(buf);
    const ascii = (off, s) => { for (let i = 0; i < s.length; i++) dv.setUint8(off + i, s.charCodeAt(i)); };
    ascii(0, "RIFF"); dv.setUint32(4, 36 + n, true); ascii(8, "WAVE");
    ascii(12, "fmt "); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
    dv.setUint32(24, rate, true); dv.setUint32(28, rate, true); dv.setUint16(32, 1, true); dv.setUint16(34, 8, true);
    ascii(36, "data"); dv.setUint32(40, n, true);
    for (let i = 0; i < n; i++) dv.setUint8(44 + i, 128);
    return new Uint8Array(buf);
  }

  // A genuine ID3v2.4 header (TIT2/TPE1/TALB + 1x1 PNG APIC, synchsafe sizes) so the app's tag
  // reader runs for real.
  function id3Bytes() {
    const png = Uint8Array.from(atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="), (c) => c.charCodeAt(0));
    const syncsafe = (out, off, size) => { out[off] = (size >> 21) & 0x7f; out[off+1] = (size >> 14) & 0x7f; out[off+2] = (size >> 7) & 0x7f; out[off+3] = size & 0x7f; };
    const frame = (id, text) => {
      const body = new Uint8Array(1 + text.length);
      body[0] = 3;
      for (let i = 0; i < text.length; i++) body[i + 1] = text.charCodeAt(i) & 0xff;
      const out = new Uint8Array(10 + body.length);
      for (let i = 0; i < 4; i++) out[i] = id.charCodeAt(i);
      syncsafe(out, 4, body.length);
      out.set(body, 10);
      return out;
    };
    const mime = "image/png";
    const apicBody = new Uint8Array(1 + mime.length + 1 + 1 + 1 + png.length);
    for (let i = 0; i < mime.length; i++) apicBody[1 + i] = mime.charCodeAt(i);
    let o = 1 + mime.length + 1;
    apicBody[o++] = 3;
    apicBody[o++] = 0;
    apicBody.set(png, o);
    const apic = new Uint8Array(10 + apicBody.length);
    "APIC".split("").forEach((c, i) => (apic[i] = c.charCodeAt(0)));
    syncsafe(apic, 4, apicBody.length);
    apic.set(apicBody, 10);
    const frames = [frame("TIT2", "Chrome Sigh"), frame("TPE1", "Naz & the Gloss"), frame("TALB", "Chrome Dreams"), apic];
    const total = frames.reduce((a, f) => a + f.length, 0);
    const head = new Uint8Array(10 + total);
    head[0] = 0x49; head[1] = 0x44; head[2] = 0x33; head[3] = 4;
    syncsafe(head, 6, total);
    let off = 10;
    for (const f of frames) { head.set(f, off); off += f.length; }
    return head;
  }

  const wav = silentWav();
  const id3 = id3Bytes();
  const jpeg = Uint8Array.from(atob("/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="), (c) => c.charCodeAt(0));

  const root = new FakeDirHandle("Music", {});
  for (const folder of files) {
    const entries = {};
    for (const f of folder.tracks) {
      const head = f.kind === "tagged" ? id3 : null;
      const parts = head ? [head, wav] : [wav];
      entries[f.name] = new FakeFileHandle(f.name, new File(parts, f.name, { type: "audio/wav" }));
    }
    for (const art of folder.art ?? []) {
      const isPng = /\.png$/i.test(art);
      entries[art] = new FakeFileHandle(art, new File([isPng ? atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==").split("").map((c) => c.charCodeAt(0)) : jpeg], art, { type: isPng ? "image/png" : "image/jpeg" }));
    }
    root.entries[folder.name] = new FakeDirHandle(folder.name, entries);
  }

  window.showDirectoryPicker = async () => root;
  window.__gadgetFakeLibrary = true;
})();
`;

export const FAKE_LIBRARY = [
  {
    name: "Chrome Dreams",
    path: "Music/Chrome Dreams",
    art: ["cover.jpg"],
    tracks: [
      { name: "01 Chrome Sigh.mp3", kind: "tagged" },
      { name: "02 Glass Teeth.mp3", kind: "wav" },
      { name: "10 Late Room.mp3", kind: "wav" },
      { name: "11 Fade_Out.mp3", kind: "wav" },
    ],
  },
  {
    name: "Late Night",
    path: "Music/Late Night",
    art: ["front.png"],
    tracks: [
      { name: "01 Ámbar.flac", kind: "wav" },
      { name: "02 Neon Yard.flac", kind: "wav" },
    ],
  },
];

/**
 * The fake-FS bootstrap script. Pass `files` to override the library — the `music-empty` reference
 * screen boots with `[]` so the music page renders its real empty state instead of the seeded one.
 */
export function fakeFsScript(files = FAKE_LIBRARY) {
  return FAKE_FS_SCRIPT.replace("__FILES__", JSON.stringify(files));
}
