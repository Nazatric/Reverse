import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useMusicPlayer, type MusicPlayer } from "../hooks/useMusicPlayer";
import { clearAllStored, useStoredState } from "../utils/storage";
import { ui } from "../utils/audio";
import type { MascotId } from "../components/Mascot";
import { getMeta, useTrackMeta } from "../utils/metaStore";
import { getActiveGame, registerSource } from "../utils/social";
import { topTracks, type TrackStats } from "../utils/stats";

export type Presence = "online" | "away" | "busy" | "offline";

export interface Profile {
  name: string;
  tagline: string;
  avatar: string | null;
  status: Presence;
  mascot: MascotId;
}
export interface Settings {
  sounds: boolean;
  haptics: boolean;
  hour24: boolean;
  ambient: boolean;
  parallax: boolean;
  chainSway: boolean;
  reduceMotion: boolean;
  y2k: boolean;
  autoImmersive: boolean;
  notify: boolean;
  keepAwake: boolean;
  artwork: boolean;
  /** Live design tokens, written straight to CSS custom properties. */
  orbScale: number;
  hubScale: number;
  chainScale: number;
  labelScale: number;
  glow: number;
}
export interface Game {
  id: string;
  name: string;
  category: string;
  url: string;
  cover: string | null;
  favorite: boolean;
  launches: number;
  lastPlayed: number | null;
  added: number;
}
export interface Homie {
  id: string;
  name: string;
  note: string;
  link: string;
  phone: string;
  status: Presence;
  avatar: string | null;
  added: number;
  kind: "local" | "linked";
  code?: string;
}
export interface Playlist {
  id: string;
  name: string;
  ids: string[];
}

const DEFAULT_PROFILE: Profile = { name: "", tagline: "", avatar: null, status: "online", mascot: "grin" };
const DEFAULT_SETTINGS: Settings = {
  sounds: true,
  haptics: true,
  hour24: false,
  ambient: true,
  parallax: true,
  chainSway: true,
  reduceMotion: false,
  y2k: false,
  autoImmersive: true,
  notify: false,
  keepAwake: true,
  artwork: true,
  orbScale: 1,
  hubScale: 1,
  chainScale: 1,
  labelScale: 1,
  glow: 1,
};

/* eslint-disable @typescript-eslint/no-explicit-any */
const normGame = (g: any): Game => ({
  id: String(g.id),
  name: String(g.name ?? ""),
  category: String(g.category ?? g.detail ?? ""),
  url: String(g.url ?? g.link ?? ""),
  cover: g.cover ?? null,
  favorite: !!g.favorite,
  launches: Number(g.launches) || 0,
  lastPlayed: g.lastPlayed ?? null,
  added: Number(g.added) || Number(g.id) || 0,
});
const normHomie = (h: any): Homie => ({
  id: String(h.id),
  name: String(h.name ?? ""),
  note: String(h.note ?? h.detail ?? ""),
  link: String(h.link ?? ""),
  phone: String(h.phone ?? ""),
  status: (h.status as Presence) ?? "online",
  avatar: h.avatar ?? null,
  added: Number(h.added) || Number(h.id) || 0,
  kind: h.kind === "linked" ? "linked" : "local",
  code: h.code ? String(h.code) : undefined,
});

interface AppState {
  profile: Profile;
  setProfile: (p: Partial<Profile>) => void;
  settings: Settings;
  setSettings: (p: Partial<Settings>) => void;
  games: Game[];
  setGames: (fn: (g: Game[]) => Game[]) => void;
  homies: Homie[];
  setHomies: (fn: (h: Homie[]) => Homie[]) => void;
  playlists: Playlist[];
  setPlaylists: (fn: (p: Playlist[]) => Playlist[]) => void;
  player: MusicPlayer;
  listenSec: number;
  trackStats: TrackStats;
  topSongs: { title: string; sec: number }[];
  gamesPlayed: number;
  install: (() => Promise<void>) | null;
  resetAll: () => void;
  clearCache: () => Promise<void>;
}

const Ctx = createContext<AppState>(null as unknown as AppState);
export const useApp = () => useContext(Ctx);

export function AppProvider({ children }: { children: ReactNode }) {
  const [rawProfile, setRawProfile] = useStoredState<any>("profile", DEFAULT_PROFILE);
  const [rawSettings, setRawSettings] = useStoredState<any>("settings", DEFAULT_SETTINGS);
  const [rawGames, setRawGames] = useStoredState<any[]>("games", []);
  const [rawHomies, setRawHomies] = useStoredState<any[]>("homies", []);
  const [playlists, setRawPlaylists] = useStoredState<Playlist[]>("playlists", []);
  const [volume, setStoredVolume] = useStoredState<number>("volume", 0.8);
  const [listenSec, setListenSec] = useStoredState<number>("listenSec", 0);
  const [trackStats, setTrackStats] = useStoredState<TrackStats>("trackStats", {});
  const [installEvent, setInstallEvent] = useState<any>(null);
  const player = useMusicPlayer(volume);

  const profile = useMemo<Profile>(
    () => ({
      ...DEFAULT_PROFILE,
      ...rawProfile,
      status: rawProfile.status ?? (rawProfile.online === false ? "offline" : "online"),
      mascot: (rawProfile.mascot as MascotId) ?? "grin",
    }),
    [rawProfile],
  );
  const settings = useMemo<Settings>(() => ({ ...DEFAULT_SETTINGS, ...rawSettings }), [rawSettings]);
  const games = useMemo(() => rawGames.map(normGame), [rawGames]);
  const homies = useMemo(() => rawHomies.map(normHomie), [rawHomies]);
  const gamesPlayed = useMemo(() => games.reduce((a, g) => a + g.launches, 0), [games]);
  const topSongs = useMemo(() => topTracks(player.library, trackStats), [player.library, trackStats]);

  const setProfile = useCallback((p: Partial<Profile>) => setRawProfile((prev: any) => ({ ...prev, ...p })), [setRawProfile]);
  const setSettings = useCallback((p: Partial<Settings>) => setRawSettings((prev: any) => ({ ...prev, ...p })), [setRawSettings]);
  const setGames = useCallback((fn: (g: Game[]) => Game[]) => setRawGames((prev) => fn(prev.map(normGame))), [setRawGames]);
  const setHomies = useCallback((fn: (h: Homie[]) => Homie[]) => setRawHomies((prev) => fn(prev.map(normHomie))), [setRawHomies]);
  const setPlaylists = useCallback((fn: (p: Playlist[]) => Playlist[]) => setRawPlaylists((prev) => fn(prev)), [setRawPlaylists]);

  /* Persist the volume the user chose. */
  useEffect(() => setStoredVolume(player.volume), [player.volume, setStoredVolume]);

  /* Settings → document flags + sound engine. */
  useEffect(() => {
    const root = document.documentElement;
    const sys = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    root.dataset.motion = settings.reduceMotion || sys ? "off" : "on";
    root.dataset.ambient = settings.ambient ? "on" : "off";
    root.dataset.sway = settings.chainSway ? "on" : "off";
    root.dataset.y2k = settings.y2k ? "on" : "off";
    root.dataset.art = settings.artwork ? "on" : "off";

    // Live design tokens → CSS custom properties (plugins override the same ones).
    const st = root.style;
    st.setProperty("--orb-scale", String(settings.orbScale));
    st.setProperty("--hub-scale", String(settings.hubScale));
    st.setProperty("--chain-scale", String(settings.chainScale));
    st.setProperty("--label-scale", String(settings.labelScale));
    st.setProperty("--glow", String(settings.glow));

    ui.enabled = settings.sounds;
    ui.haptics = settings.haptics;
  }, [settings]);

  /* Real listening time — total + per-track, counted only while audio advances. */
  const listenRef = useRef(0);
  const trackRef = useRef(0);
  const curIdRef = useRef<string | null>(null);
  const statsRef = useRef<TrackStats>(trackStats);
  statsRef.current = trackStats;
  useEffect(() => {
    curIdRef.current = player.current?.id ?? null;
  }, [player.current]);
  useEffect(() => {
    const a = player.audio;
    let last = 0;
    const onTime = () => {
      const t = a.currentTime;
      const d = t - last;
      last = t;
      if (d > 0 && d < 1.5 && !a.paused) {
        listenRef.current += d;
        if (curIdRef.current) trackRef.current += d;
        if (listenRef.current >= 15) {
          const add = listenRef.current;
          const tAdd = trackRef.current;
          const id = curIdRef.current;
          listenRef.current = 0;
          trackRef.current = 0;
          setListenSec((v) => v + add);
          if (id) setTrackStats((prev) => ({ ...prev, [id]: (prev[id] ?? 0) + tAdd }));
        }
      }
    };
    const resync = () => {
      last = a.currentTime;
    };
    a.addEventListener("timeupdate", onTime);
    a.addEventListener("seeked", resync);
    a.addEventListener("loadstart", resync);
    return () => {
      a.removeEventListener("timeupdate", onTime);
      a.removeEventListener("seeked", resync);
      a.removeEventListener("loadstart", resync);
    };
  }, [player.audio, setListenSec, setTrackStats]);

  /* Metadata for whatever is playing (title/artist for presence + notifications). */
  const currentMeta = useTrackMeta(player.current);

  /* Social presence source: who we are, what we're doing, what we've listened to. */
  useEffect(() => {
    registerSource(() => {
      const t = player.current;
      const meta = t ? currentMeta ?? getMeta(t.id) : undefined;
      const game = getActiveGame();
      const now: import("../utils/social").PresenceNow | null = game
        ? { kind: "game" as const, name: game }
        : t
          ? { kind: "music" as const, title: meta?.title || t.title, artist: meta?.artist || t.folder.split("/").pop() || "" }
          : null;
      return {
        name: profile.name || "Gadget user",
        tagline: profile.tagline,
        status: profile.status,
        avatar: profile.avatar,
        now,
        listenedSec: Math.round(listenSec),
        games: gamesPlayed,
        top: topSongs.map((s) => ({ title: s.title, artist: "", sec: Math.round(s.sec) })),
      };
    });
    return () => registerSource(null);
  }, [profile, player.current, currentMeta, listenSec, gamesPlayed, topSongs]);

  /* Keep the screen awake while something is playing (Android Wake Lock). */
  useEffect(() => {
    let lock: { release: () => Promise<void> } | null = null;
    let dead = false;
    const on = async () => {
      try {
        const nav = navigator as Navigator & { wakeLock?: { request: (t: "screen") => Promise<{ release: () => Promise<void> }> } };
        if (!nav.wakeLock || lock || dead) return;
        lock = await nav.wakeLock.request("screen");
      } catch {
        /* denied / not supported */
      }
    };
    const off = () => {
      void lock?.release().catch(() => undefined);
      lock = null;
    };
    if (settings.keepAwake && player.playing) void on();
    else off();
    const vis = () => {
      if (document.visibilityState === "visible" && settings.keepAwake && player.playing) void on();
      else if (document.visibilityState !== "visible") off();
    };
    document.addEventListener("visibilitychange", vis);
    return () => {
      dead = true;
      off();
      document.removeEventListener("visibilitychange", vis);
    };
  }, [settings.keepAwake, player.playing]);

  const clearCache = useCallback(async () => {
    try {
      const keys = await caches.keys();
      await Promise.all(keys.map((k) => caches.delete(k)));
      if ("storage" in navigator) await navigator.storage.estimate();
      await caches.open("gadget-v1"); // the worker re-populates on next load
      if ("serviceWorker" in navigator) {
        const reg = await navigator.serviceWorker.getRegistration();
        await reg?.update().catch(() => undefined);
      }
    } catch {
      /* ignore */
    }
  }, []);

  /* PWA install prompt (Android Chrome). */
  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setInstallEvent(e);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  const install = useMemo(
    () =>
      installEvent
        ? async () => {
            await installEvent.prompt();
            setInstallEvent(null);
          }
        : null,
    [installEvent],
  );

  const resetAll = useCallback(() => {
    void player.clearLibrary();
    clearAllStored();
    setRawProfile(DEFAULT_PROFILE);
    setRawSettings(DEFAULT_SETTINGS);
    setRawGames([]);
    setRawHomies([]);
    setRawPlaylists([]);
    setListenSec(0);
    setTrackStats({});
  }, [player, setRawProfile, setRawSettings, setRawGames, setRawHomies, setRawPlaylists, setListenSec, setTrackStats]);

  const value = useMemo<AppState>(
    () => ({
      profile,
      setProfile,
      settings,
      setSettings,
      games,
      setGames,
      homies,
      setHomies,
      playlists,
      setPlaylists,
      player,
      listenSec,
      trackStats,
      topSongs,
      gamesPlayed,
      install,
      resetAll,
      clearCache,
    }),
    [profile, setProfile, settings, setSettings, games, setGames, homies, setHomies, playlists, setPlaylists, player, listenSec, trackStats, topSongs, gamesPlayed, install, resetAll, clearCache],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}


