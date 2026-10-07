import { useCallback, useMemo, useSyncExternalStore } from "react";
import { readTags, type Meta } from "./tags";
import type { Track } from "./musicLibrary";

/**
 * Lazy tag/cover cache. Tags are read only for tracks that are actually on screen (or playing),
 * two at a time, so opening a 5,000-song library never stalls the UI.
 */
const cache = new Map<string, Meta>();
const pending = new Set<string>();
const subs = new Map<string, Set<() => void>>();
const queue: Track[] = [];
let active = 0;

/* version counter — lets album grouping re-run as tags resolve */
let version = 0;
const versionSubs = new Set<() => void>();
export function bumpMetaVersion() {
  version++;
  versionSubs.forEach((f) => f());
}
export function useMetaVersion() {
  return useSyncExternalStore(
    (f) => {
      versionSubs.add(f);
      return () => versionSubs.delete(f);
    },
    () => version,
    () => version,
  );
}

function notify(id: string) {
  subs.get(id)?.forEach((cb) => cb());
}

function resolve(track: Track): Meta {
  // Folder artwork first (cover.jpg / front.png …), embedded tags layered over it —
  // so a file with no embedded picture still shows its album art.
  const seeded: Meta = {};
  if (track.cover) seeded.coverUrl = track.cover;
  return seeded;
}

function pump() {
  while (active < 2 && queue.length) {
    const t = queue.shift()!;
    active++;
    const seeded = resolve(t);
    const hadCover = !!cache.get(t.id)?.coverUrl;
    readTags(t.file)
      .then((m) => {
        const merged: Meta = { ...seeded, ...m };
        if (!merged.coverUrl && hadCover) merged.coverUrl = cache.get(t.id)?.coverUrl;
        cache.set(t.id, merged);
        if (merged.album || merged.artist) bumpMetaVersion();
      })
      .catch(() => cache.set(t.id, seeded))
      .finally(() => {
        pending.delete(t.id);
        active--;
        notify(t.id);
        bumpMetaVersion();
        pump();
      });
  }
}

/** Read tags when a track scrolls into view (or plays); re-seeds folder art on retry. */
export function requestMeta(track: Track, priority = false) {
  if (pending.has(track.id)) return;
  const cached = cache.get(track.id);
  if (cached && !track.cover && (cached.title || !cached.coverUrl)) return;
  pending.add(track.id);
  if (priority) queue.unshift(track);
  else queue.push(track);
  pump();
}

export const getMeta = (id: string) => cache.get(id);

export function clearMeta() {
  queue.length = 0;
  cache.forEach((m) => m.coverUrl && m.coverUrl.startsWith("blob:") && URL.revokeObjectURL(m.coverUrl));
  cache.clear();
  pending.clear();
  subs.forEach((set) => set.forEach((cb) => cb()));
  bumpMetaVersion();
}

/** Metadata-aware view of a track: embedded tags + folder artwork fallbacks. */
export function useTrackMeta(track: Track | null | undefined): Meta | undefined {
  const meta = useMeta(track);
  return useMemo(() => {
    if (!track) return undefined;
    if (meta) {
      return meta.coverUrl ? meta : { ...meta, coverUrl: track.cover };
    }
    return track.cover ? { coverUrl: track.cover } : undefined;
  }, [meta, track]);
}

function subscribe(id: string, cb: () => void) {
  let set = subs.get(id);
  if (!set) subs.set(id, (set = new Set()));
  set.add(cb);
  return () => {
    set!.delete(cb);
    if (!set!.size) subs.delete(id);
  };
}

export function useMeta(track: Track | null | undefined): Meta | undefined {
  const id = track?.id ?? "";
  const sub = useCallback((cb: () => void) => subscribe(id, cb), [id]);
  return useSyncExternalStore(sub, () => cache.get(id), () => undefined);
}
