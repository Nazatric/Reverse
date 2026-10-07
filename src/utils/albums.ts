import { useMemo } from "react";
import type { Track } from "./musicLibrary";
import { getMeta, useMetaVersion } from "./metaStore";

export interface Album {
  id: string;
  name: string;
  artist: string;
  cover?: string;
  tracks: Track[];
}

const albumKeyOf = (t: Track) => {
  const a = getMeta(t.id)?.album;
  const album = typeof a === "string" ? a.trim() : "";
  return album || t.folder;
};

export function groupAlbums(tracks: Track[]): Album[] {
  const map = new Map<string, Album>();
  for (const t of tracks) {
    const key = albumKeyOf(t) || t.folder;
    const meta = getMeta(t.id);
    let album = map.get(key);
    if (!album) {
      album = { id: key, name: key.split("/").pop() || key, artist: meta?.artist || "", cover: undefined, tracks: [] };
      map.set(key, album);
    }
    if (!album.artist && meta?.artist) album.artist = meta.artist;
    if (!album.cover) album.cover = meta?.coverUrl || t.cover;
    album.tracks.push(t);
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
}

/**
 * Albums grouped by real metadata (ID3/MP4/FLAC tags) when the tags have been read,
 * falling back to the folder name. Re-groups as tags stream in.
 */
export function useAlbums(tracks: Track[]): Album[] {
  const v = useMetaVersion();
  return useMemo(() => groupAlbums(tracks), [tracks, v]);
}

export const byTitle = (a: Track, b: Track) => a.title.localeCompare(b.title, undefined, { numeric: true, sensitivity: "base" });

export function letterOf(title: string) {
  const c = title.trim().normalize("NFD").replace(/[\u0300-\u036f]/g, "").charAt(0).toUpperCase();
  return /[A-Z]/.test(c) ? c : "#";
}

export const LETTERS = ["#", ..."ABCDEFGHIJKLMNOPQRSTUVWXYZ"];
