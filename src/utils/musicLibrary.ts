import { deleteHandle, loadHandle, saveHandle } from "./storage";

export interface Track {
  id: string;
  title: string;
  /** parent folder path — doubles as the album when a file has no album tag */
  folder: string;
  /** folder artwork (cover.jpg / front.png …) used when the file itself has no embedded art */
  cover?: string;
  file: File;
}

/* Minimal typings for the File System Access API */
interface DirHandle {
  kind: "directory";
  name: string;
  values(): AsyncIterable<DirHandle | FileHandle>;
  queryPermission?(opts: { mode: "read" }): Promise<"granted" | "denied" | "prompt">;
  requestPermission?(opts: { mode: "read" }): Promise<"granted" | "denied" | "prompt">;
}
interface FileHandle {
  kind: "file";
  name: string;
  getFile(): Promise<File>;
}
type PickerWindow = Window & { showDirectoryPicker?: (opts?: { mode?: "read" }) => Promise<DirHandle> };

const HANDLE_KEY = "music-folder";
const AUDIO_EXT = /\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|weba|webm|aif|aiff)$/i;
const IMAGE_EXT = /\.(jpe?g|png|webp|gif)$/i;
/** recognised by every media player as album artwork */
const ART_NAME = /^(cover|front|folder|album|art|artwork|thumbnail|albumart)[^/]*\.(jpe?g|png|webp)$/i;

export const supportsFolderPicker = () => typeof (window as PickerWindow).showDirectoryPicker === "function";

export function titleFromFile(name: string) {
  return name.replace(/\.[^.]+$/, "").replace(/_+/g, " ").trim();
}

function sortTracks(tracks: Track[]) {
  return tracks.sort((a, b) => a.folder.localeCompare(b.folder) || a.title.localeCompare(b.title, undefined, { numeric: true }));
}

/** Pick the most likely album art file inside a folder (cover > front > album > any art-ish image). */
function artRank(name: string) {
  const n = name.toLowerCase();
  if (/^(cover|front)/.test(n)) return 0;
  if (/^(folder|album)/.test(n)) return 1;
  return 2;
}

interface WalkCtx {
  tracks: Track[];
  covers: Map<string, { score: number; file: File }>;
}

async function walk(dir: DirHandle, path: string, ctx: WalkCtx, depth = 0) {
  if (depth > 6) return;
  for await (const entry of dir.values()) {
    if (entry.kind === "file") {
      if (AUDIO_EXT.test(entry.name)) {
        const file = await entry.getFile();
        ctx.tracks.push({ id: `${path}/${entry.name}`, title: titleFromFile(entry.name), folder: path, file });
      } else if (IMAGE_EXT.test(entry.name) && ART_NAME.test(entry.name)) {
        const score = artRank(entry.name);
        const cur = ctx.covers.get(path);
        if (!cur || score < cur.score) {
          ctx.covers.set(path, { score, file: await entry.getFile() });
        }
      }
    } else if (!entry.name.startsWith(".")) {
      await walk(entry, `${path}/${entry.name}`, ctx, depth + 1);
    }
  }
}

/** Attach folder artwork to every track in that folder (one object URL per folder). */
function attachCovers(tracks: Track[], ctx: WalkCtx) {
  if (!ctx.covers.size) return;
  const urls = new Map<string, string>();
  for (const [folder, hit] of ctx.covers) {
    try {
      urls.set(folder, URL.createObjectURL(hit.file));
    } catch {
      /* skip unusable image */
    }
  }
  for (const t of tracks) {
    const u = urls.get(t.folder);
    if (u) t.cover = u;
  }
}

function finalise(ctx: WalkCtx): Track[] {
  attachCovers(ctx.tracks, ctx);
  return sortTracks(ctx.tracks);
}

export async function pickFolder(): Promise<{ name: string; tracks: Track[] } | null> {
  const picker = (window as PickerWindow).showDirectoryPicker;
  if (!picker) return null;
  try {
    const handle = await picker.call(window, { mode: "read" });
    const ctx: WalkCtx = { tracks: [], covers: new Map() };
    await walk(handle, handle.name, ctx);
    await saveHandle(HANDLE_KEY, handle);
    return { name: handle.name, tracks: finalise(ctx) };
  } catch (err) {
    if ((err as DOMException)?.name === "AbortError") return null;
    throw err;
  }
}

export async function restoreFolder(): Promise<
  { status: "ready"; name: string; tracks: Track[] } | { status: "needs-permission"; name: string } | { status: "none" }
> {
  const handle = await loadHandle<DirHandle>(HANDLE_KEY);
  if (!handle) return { status: "none" };
  const perm = (await handle.queryPermission?.({ mode: "read" })) ?? "granted";
  if (perm !== "granted") return { status: "needs-permission", name: handle.name };
  const ctx: WalkCtx = { tracks: [], covers: new Map() };
  try {
    await walk(handle, handle.name, ctx);
  } catch {
    return { status: "none" };
  }
  return { status: "ready", name: handle.name, tracks: finalise(ctx) };
}

/** Must run inside a user gesture. */
export async function reconnectFolder(): Promise<{ name: string; tracks: Track[] } | null> {
  const handle = await loadHandle<DirHandle>(HANDLE_KEY);
  if (!handle) return null;
  const perm = (await handle.requestPermission?.({ mode: "read" })) ?? "granted";
  if (perm !== "granted") return null;
  const ctx: WalkCtx = { tracks: [], covers: new Map() };
  await walk(handle, handle.name, ctx);
  return { name: handle.name, tracks: finalise(ctx) };
}

export async function forgetFolder() {
  await deleteHandle(HANDLE_KEY);
}

/** Phones: <input webkitdirectory> or multi-file selection. */
export function tracksFromFileList(list: FileList | File[]): { name: string; tracks: Track[] } {
  const files = Array.from(list);
  const ctx: WalkCtx = { tracks: [], covers: new Map() };
  for (const file of files) {
    const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
    const parts = rel.split("/");
    parts.pop();
    const folder = parts.join("/") || "Selected files";
    if (AUDIO_EXT.test(file.name) || file.type.startsWith("audio/")) {
      ctx.tracks.push({ id: rel, title: titleFromFile(file.name), folder, file });
    } else if (IMAGE_EXT.test(file.name) && ART_NAME.test(file.name)) {
      const score = artRank(file.name);
      const cur = ctx.covers.get(folder);
      if (!cur || score < cur.score) ctx.covers.set(folder, { score, file });
    }
  }
  const root = ctx.tracks[0]?.folder.split("/")[0] || "Selected files";
  return { name: root, tracks: finalise(ctx) };
}

export function formatTime(sec: number) {
  if (!Number.isFinite(sec) || sec < 0) return "0:00";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}
