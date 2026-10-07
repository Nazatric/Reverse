/**
 * Dependency-free tag + cover-art reader for the formats phones actually hold:
 * MP3 (ID3v2.3/2.4), M4A/MP4 (ilst/covr) and FLAC (Vorbis comment + PICTURE).
 */
export interface Meta {
  title?: string;
  artist?: string;
  album?: string;
  coverUrl?: string;
}

const ascii = (b: Uint8Array, s: number, n: number) => {
  let o = "";
  for (let i = s; i < s + n && i < b.length; i++) o += String.fromCharCode(b[i]);
  return o;
};
const u32 = (b: Uint8Array, p: number) => ((b[p] << 24) | (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3]) >>> 0;
const u32le = (b: Uint8Array, p: number) => ((b[p + 3] << 24) | (b[p + 2] << 16) | (b[p + 1] << 8) | b[p]) >>> 0;
const syncsafe = (b: Uint8Array, p: number) =>
  ((b[p] & 127) << 21) | ((b[p + 1] & 127) << 14) | ((b[p + 2] & 127) << 7) | (b[p + 3] & 127);

const decode = (label: string, b: Uint8Array) => {
  try {
    return new TextDecoder(label).decode(b).replace(/\0+$/g, "").trim();
  } catch {
    return "";
  }
};

const toUrl = (bytes: Uint8Array, mime: string) =>
  URL.createObjectURL(new Blob([bytes.slice().buffer as ArrayBuffer], { type: mime }));

const guessMime = (img: Uint8Array, mime?: string) => {
  if (mime && mime.includes("/") && mime !== "-->") return mime;
  return img[0] === 0x89 ? "image/png" : "image/jpeg";
};

/* ---------------- ID3v2 ---------------- */

function id3Text(b: Uint8Array) {
  const enc = b[0];
  const c = b.subarray(1);
  if (enc === 0) return decode("iso-8859-1", c);
  if (enc === 1) {
    if (c[0] === 0xfe && c[1] === 0xff) return decode("utf-16be", c.subarray(2));
    return decode("utf-16le", c[0] === 0xff && c[1] === 0xfe ? c.subarray(2) : c);
  }
  if (enc === 2) return decode("utf-16be", c);
  return decode("utf-8", c);
}

function id3Picture(b: Uint8Array) {
  const enc = b[0];
  let p = 1;
  let mime = "";
  while (p < b.length && b[p] !== 0) mime += String.fromCharCode(b[p++]);
  p += 2; // terminator + picture type
  if (enc === 1 || enc === 2) {
    while (p + 1 < b.length && !(b[p] === 0 && b[p + 1] === 0)) p += 2;
    p += 2;
  } else {
    while (p < b.length && b[p] !== 0) p++;
    p++;
  }
  const img = b.subarray(p);
  if (img.length < 64) return undefined;
  return toUrl(img, guessMime(img, mime));
}

async function readId3(file: File): Promise<Meta> {
  const head = new Uint8Array(await file.slice(0, 10).arrayBuffer());
  if (head.length < 10 || ascii(head, 0, 3) !== "ID3") return {};
  const ver = head[3];
  if (ver < 3 || ver > 4) return {};
  const size = syncsafe(head, 6);
  const b = new Uint8Array(await file.slice(10, 10 + Math.min(size, 8 * 1024 * 1024)).arrayBuffer());
  let p = 0;
  if (head[5] & 0x40 && b.length >= 4) p = ver === 4 ? syncsafe(b, 0) : u32(b, 0) + 4;
  const meta: Meta = {};
  while (p + 10 <= b.length) {
    const id = ascii(b, p, 4);
    if (!/^[A-Z0-9]{4}$/.test(id)) break;
    const len = ver === 4 ? syncsafe(b, p + 4) : u32(b, p + 4);
    const s = p + 10;
    const e = s + len;
    if (len <= 0 || e > b.length) break;
    const f = b.subarray(s, e);
    if (id === "TIT2") meta.title = id3Text(f);
    else if (id === "TPE1") meta.artist = id3Text(f);
    else if (id === "TALB") meta.album = id3Text(f);
    else if (id === "APIC" && !meta.coverUrl) meta.coverUrl = id3Picture(f);
    p = e;
  }
  return meta;
}

/* ---------------- MP4 / M4A ---------------- */

function eachAtom(b: Uint8Array, s: number, e: number, fn: (type: string, start: number, end: number) => void) {
  let p = s;
  while (p + 8 <= e) {
    const len = u32(b, p);
    if (len < 8 || p + len > e) break;
    fn(ascii(b, p + 4, 4), p + 8, p + len);
    p += len;
  }
}

function parseMoov(b: Uint8Array): Meta {
  const meta: Meta = {};
  eachAtom(b, 0, b.length, (t, s, e) => {
    if (t !== "udta") return;
    eachAtom(b, s, e, (t2, s2, e2) => {
      if (t2 !== "meta") return;
      eachAtom(b, s2 + 4, e2, (t3, s3, e3) => {
        if (t3 !== "ilst") return;
        eachAtom(b, s3, e3, (key, s4, e4) => {
          eachAtom(b, s4, e4, (t5, s5, e5) => {
            if (t5 !== "data") return;
            const payload = b.subarray(s5 + 8, e5);
            if (key === "covr") {
              if (!meta.coverUrl && payload.length > 64) {
                const flag = u32(b, s5) & 0xffffff;
                meta.coverUrl = toUrl(payload, flag === 14 ? "image/png" : "image/jpeg");
              }
            } else if (key === "\u00a9nam") meta.title = decode("utf-8", payload);
            else if (key === "\u00a9ART") meta.artist = decode("utf-8", payload);
            else if (key === "\u00a9alb") meta.album = decode("utf-8", payload);
          });
        });
      });
    });
  });
  return meta;
}

async function readMp4(file: File): Promise<Meta> {
  let pos = 0;
  while (pos + 8 <= file.size) {
    const h = new DataView(await file.slice(pos, pos + 16).arrayBuffer());
    if (h.byteLength < 8) break;
    let len = h.getUint32(0);
    const type = String.fromCharCode(h.getUint8(4), h.getUint8(5), h.getUint8(6), h.getUint8(7));
    let hdr = 8;
    if (len === 1 && h.byteLength >= 16) {
      len = Number(h.getBigUint64(8));
      hdr = 16;
    } else if (len === 0) len = file.size - pos;
    if (len < hdr) break;
    if (type === "moov") {
      if (len > 16 * 1024 * 1024) return {};
      return parseMoov(new Uint8Array(await file.slice(pos + hdr, pos + len).arrayBuffer()));
    }
    pos += len;
  }
  return {};
}

/* ---------------- FLAC ---------------- */

async function readFlac(file: File): Promise<Meta> {
  const b = new Uint8Array(await file.slice(0, Math.min(file.size, 10 * 1024 * 1024)).arrayBuffer());
  if (ascii(b, 0, 4) !== "fLaC") return {};
  const meta: Meta = {};
  let p = 4;
  while (p + 4 <= b.length) {
    const last = b[p] & 0x80;
    const type = b[p] & 0x7f;
    const len = (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3];
    const s = p + 4;
    const e = s + len;
    if (e > b.length) break;
    if (type === 4) {
      let q = s + 4 + u32le(b, s);
      const count = u32le(b, q);
      q += 4;
      for (let i = 0; i < count && q + 4 <= e; i++) {
        const l = u32le(b, q);
        const line = decode("utf-8", b.subarray(q + 4, q + 4 + l));
        const eq = line.indexOf("=");
        if (eq > 0) {
          const k = line.slice(0, eq).toUpperCase();
          const v = line.slice(eq + 1);
          if (k === "TITLE") meta.title = v;
          else if (k === "ARTIST") meta.artist = v;
          else if (k === "ALBUM") meta.album = v;
        }
        q += 4 + l;
      }
    } else if (type === 6 && !meta.coverUrl) {
      let q = s + 4;
      const ml = u32(b, q);
      const mime = ascii(b, q + 4, ml);
      q += 4 + ml;
      q += 4 + u32(b, q); // description
      q += 16; // width, height, depth, colours
      const dl = u32(b, q);
      q += 4;
      if (dl > 64 && q + dl <= e) meta.coverUrl = toUrl(b.subarray(q, q + dl), guessMime(b.subarray(q, q + 4), mime));
    }
    if (last) break;
    p = e;
  }
  return meta;
}

/** Legacy ID3v1.1 — the last 128 bytes of a plain MP3. */
async function readId3v1(file: File): Promise<Meta> {
  if (file.size < 128) return {};
  const b = new Uint8Array(await file.slice(file.size - 128).arrayBuffer());
  if (ascii(b, 0, 3) !== "TAG") return {};
  const dec = (s: number, n: number) => decode("iso-8859-1", b.subarray(s, s + n)).replace(/\0/g, "").trim();
  const meta: Meta = {};
  const title = dec(3, 30);
  const artist = dec(33, 30);
  const album = dec(63, 30);
  if (title) meta.title = title;
  if (artist) meta.artist = artist;
  if (album) meta.album = album;
  return meta;
}

export async function readTags(file: File): Promise<Meta> {
  try {
    const h = new Uint8Array(await file.slice(0, 12).arrayBuffer());
    if (ascii(h, 0, 3) === "ID3") {
      const v2 = await readId3(file);
      if (v2.title || v2.artist || v2.album || v2.coverUrl) return v2;
      // some files carry an ID3v2 header but no frames — fall through to v1
      const v1 = await readId3v1(file);
      return v1.title || v1.artist ? v1 : v2;
    }
    if (ascii(h, 0, 4) === "fLaC") return await readFlac(file);
    if (ascii(h, 4, 4) === "ftyp") return await readMp4(file);
    const v1 = await readId3v1(file);
    if (v1.title || v1.artist) return v1;
  } catch {
    /* unreadable tags are fine — filename is the fallback */
  }
  return {};
}
