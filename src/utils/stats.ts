/** Per-track listening stats (seconds), kept on device. */

export type TrackStats = Record<string, number>;

export function topTracks(lib: { id: string; title: string }[], stats: TrackStats, limit = 3) {
  return Object.entries(stats)
    .filter(([, s]) => s >= 30)
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([id, sec]) => ({ title: lib.find((t) => t.id === id)?.title ?? "song", sec }));
}

export function fmtDuration(sec: number) {
  if (sec < 60) return `${Math.floor(sec)}s`;
  if (sec < 3600) return `${Math.floor(sec / 60)}m`;
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return m ? `${h}h ${m}m` : `${h}h`;
}
