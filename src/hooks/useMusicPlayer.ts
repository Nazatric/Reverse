import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  forgetFolder,
  pickFolder,
  reconnectFolder,
  restoreFolder,
  supportsFolderPicker,
  tracksFromFileList,
  type Track,
} from "../utils/musicLibrary";
import { clearMeta, requestMeta, useMeta } from "../utils/metaStore";
import { readStored, writeStored } from "../utils/storage";

export type LibraryStatus = "empty" | "loading" | "needs-permission" | "ready";
export type RepeatMode = "off" | "all" | "one";

const fineHover = () => typeof matchMedia === "function" && matchMedia("(pointer: fine)").matches;

export interface MusicPlayer {
  audio: HTMLAudioElement;
  library: Track[];
  folderName: string;
  status: LibraryStatus;
  error: string | null;
  current: Track | null;
  playing: boolean;
  shuffle: boolean;
  repeat: RepeatMode;
  volume: number;
  canPickFolder: boolean;
  chooseFolder: () => Promise<void>;
  reconnect: () => Promise<void>;
  loadFiles: (files: FileList | File[]) => void;
  clearLibrary: () => Promise<void>;
  /** Replace the play queue and start at `start`. */
  playQueue: (list: Track[], start: number) => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (sec: number) => void;
  setVolume: (v: number) => void;
  setShuffle: (v: boolean) => void;
  cycleRepeat: () => void;
  /** Spectrum analyser — only created on pointer-fine devices so phone playback stays on the native audio path. */
  analyser: () => AnalyserNode | null;
}

export function useMusicPlayer(initialVolume = 0.8): MusicPlayer {
  const [audio] = useState(() => {
    const a = new Audio();
    a.preload = "metadata";
    a.volume = initialVolume;
    return a;
  });

  const [library, setLibrary] = useState<Track[]>([]);
  const [folderName, setFolderName] = useState("");
  const [status, setStatus] = useState<LibraryStatus>("loading");
  const [error, setError] = useState<string | null>(null);
  const [current, setCurrent] = useState<Track | null>(null);
  const [playing, setPlaying] = useState(false);
  const [shuffle, setShuffleState] = useState(false);
  const [repeat, setRepeat] = useState<RepeatMode>("off");
  const [volume, setVolumeState] = useState(initialVolume);

  const urlRef = useRef<string | null>(null);
  const ctxRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const libraryRef = useRef<Track[]>([]);
  const queueRef = useRef<Track[]>([]);
  const qIdxRef = useRef(-1);
  const shuffleRef = useRef(false);
  const repeatRef = useRef<RepeatMode>("off");
  shuffleRef.current = shuffle;
  repeatRef.current = repeat;

  const meta = useMeta(current);

  const load = useCallback(
    (track: Track, autoplay: boolean) => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      urlRef.current = URL.createObjectURL(track.file);
      audio.src = urlRef.current;
      setCurrent(track);
      setError(null);
      writeStored("last", track.id);
      requestMeta(track, true);
      if (autoplay) audio.play().catch(() => setPlaying(false));
    },
    [audio],
  );

  const ensureAnalyser = useCallback(() => {
    if (analyserRef.current || !fineHover()) return;
    try {
      const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctx) return;
      const ctx = new Ctx();
      const src = ctx.createMediaElementSource(audio);
      const an = ctx.createAnalyser();
      an.fftSize = 256;
      an.smoothingTimeConstant = 0.8;
      src.connect(an);
      an.connect(ctx.destination);
      ctxRef.current = ctx;
      analyserRef.current = an;
      void ctx.resume();
    } catch {
      /* playback continues on the native path */
    }
  }, [audio]);

  const step = useCallback(
    (dir: 1 | -1, fromEnded: boolean) => {
      const q = queueRef.current;
      if (!q.length) return;
      let i = qIdxRef.current;
      if (shuffleRef.current && q.length > 1) {
        let n = i;
        while (n === i) n = Math.floor(Math.random() * q.length);
        i = n;
      } else {
        i += dir;
        if (i >= q.length) {
          if (fromEnded && repeatRef.current !== "all") {
            audio.pause();
            setPlaying(false);
            return;
          }
          i = 0;
        }
        if (i < 0) i = q.length - 1;
      }
      qIdxRef.current = i;
      load(q[i], true);
    },
    [audio, load],
  );

  const next = useCallback(() => step(1, false), [step]);
  const prev = useCallback(() => {
    if (audio.currentTime > 3) {
      audio.currentTime = 0;
      return;
    }
    step(-1, false);
  }, [audio, step]);

  useEffect(() => {
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onEnded = () => {
      if (repeatRef.current === "one") {
        audio.currentTime = 0;
        audio.play().catch(() => undefined);
      } else step(1, true);
    };
    const onError = () => {
      if (!audio.getAttribute("src")) return;
      setError("This file couldn't be played on this device.");
      setPlaying(false);
    };
    audio.addEventListener("play", onPlay);
    audio.addEventListener("pause", onPause);
    audio.addEventListener("ended", onEnded);
    audio.addEventListener("error", onError);
    return () => {
      audio.removeEventListener("play", onPlay);
      audio.removeEventListener("pause", onPause);
      audio.removeEventListener("ended", onEnded);
      audio.removeEventListener("error", onError);
    };
  }, [audio, step]);

  /* Lock-screen / notification controls with real cover art. */
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    if (current) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: meta?.title || current.title,
        artist: meta?.artist || current.folder.split("/").pop() || "",
        album: meta?.album || current.folder.split("/").pop() || "",
        artwork: meta?.coverUrl ? [{ src: meta.coverUrl, sizes: "512x512" }] : [],
      });
    }
    const handlers: Array<[MediaSessionAction, MediaSessionActionHandler]> = [
      ["play", () => void audio.play().catch(() => undefined)],
      ["pause", () => audio.pause()],
      ["nexttrack", () => next()],
      ["previoustrack", () => prev()],
      ["seekto", (d) => {
        if (typeof d.seekTime === "number") audio.currentTime = d.seekTime;
      }],
    ];
    for (const [a, h] of handlers) {
      try {
        navigator.mediaSession.setActionHandler(a, h);
      } catch {
        /* unsupported action */
      }
    }
  }, [audio, current, meta, next, prev]);

  const apply = useCallback(
    (name: string, list: Track[]) => {
      audio.pause();
      clearMeta();
      libraryRef.current = list;
      queueRef.current = list;
      setLibrary(list);
      setFolderName(name);
      setError(null);
      setStatus(list.length ? "ready" : "empty");
      if (list.length) {
        const last = readStored<string | null>("last", null);
        const idx = Math.max(0, list.findIndex((t) => t.id === last));
        qIdxRef.current = idx;
        load(list[idx], false);
      } else {
        setCurrent(null);
        qIdxRef.current = -1;
      }
    },
    [audio, load],
  );

  /* Restore the folder remembered from a previous visit. */
  useEffect(() => {
    let alive = true;
    restoreFolder()
      .then((r) => {
        if (!alive) return;
        if (r.status === "ready") apply(r.name, r.tracks);
        else if (r.status === "needs-permission") {
          setFolderName(r.name);
          setStatus("needs-permission");
        } else setStatus("empty");
      })
      .catch(() => alive && setStatus("empty"));
    return () => {
      alive = false;
    };
  }, [apply]);

  const chooseFolder = useCallback(async () => {
    setError(null);
    const had = libraryRef.current.length > 0;
    try {
      setStatus("loading");
      const r = await pickFolder();
      if (!r) {
        setStatus(had ? "ready" : "empty");
        return;
      }
      apply(r.name, r.tracks);
      if (!r.tracks.length) setError("No audio files were found in that folder.");
    } catch {
      setStatus(had ? "ready" : "empty");
      setError("That folder couldn't be opened.");
    }
  }, [apply]);

  const reconnect = useCallback(async () => {
    setError(null);
    setStatus("loading");
    const r = await reconnectFolder();
    if (r) apply(r.name, r.tracks);
    else setStatus("needs-permission");
  }, [apply]);

  const loadFiles = useCallback(
    (files: FileList | File[]) => {
      const r = tracksFromFileList(files);
      apply(r.name, r.tracks);
      if (!r.tracks.length) setError("No audio files were found in that selection.");
    },
    [apply],
  );

  const clearLibrary = useCallback(async () => {
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    urlRef.current = null;
    clearMeta();
    await forgetFolder();
    libraryRef.current = [];
    queueRef.current = [];
    qIdxRef.current = -1;
    setLibrary([]);
    setCurrent(null);
    setFolderName("");
    setStatus("empty");
    setError(null);
  }, [audio]);

  const playQueue = useCallback(
    (list: Track[], start: number) => {
      if (!list[start]) return;
      ensureAnalyser();
      queueRef.current = list;
      qIdxRef.current = start;
      load(list[start], true);
    },
    [ensureAnalyser, load],
  );

  const toggle = useCallback(() => {
    if (!audio.getAttribute("src")) {
      const lib = libraryRef.current;
      if (lib.length) playQueue(lib, 0);
      return;
    }
    if (audio.paused) {
      ensureAnalyser();
      void ctxRef.current?.resume();
      audio.play().catch(() => undefined);
    } else audio.pause();
  }, [audio, ensureAnalyser, playQueue]);

  const seek = useCallback(
    (sec: number) => {
      if (Number.isFinite(sec)) audio.currentTime = sec;
    },
    [audio],
  );

  const setVolume = useCallback(
    (v: number) => {
      const c = Math.min(1, Math.max(0, v));
      audio.volume = c;
      setVolumeState(c);
    },
    [audio],
  );

  const setShuffle = useCallback((v: boolean) => setShuffleState(v), []);
  const cycleRepeat = useCallback(() => setRepeat((r) => (r === "off" ? "all" : r === "all" ? "one" : "off")), []);
  const analyser = useCallback(() => analyserRef.current, []);

  return useMemo<MusicPlayer>(
    () => ({
      audio,
      library,
      folderName,
      status,
      error,
      current,
      playing,
      shuffle,
      repeat,
      volume,
      canPickFolder: supportsFolderPicker(),
      chooseFolder,
      reconnect,
      loadFiles,
      clearLibrary,
      playQueue,
      toggle,
      next,
      prev,
      seek,
      setVolume,
      setShuffle,
      cycleRepeat,
      analyser,
    }),
    [audio, library, folderName, status, error, current, playing, shuffle, repeat, volume, chooseFolder, reconnect, loadFiles, clearLibrary, playQueue, toggle, next, prev, seek, setVolume, setShuffle, cycleRepeat, analyser],
  );
}
