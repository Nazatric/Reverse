import { useSyncExternalStore } from "react";

/**
 * Stage metrics.
 *
 * The reference is a 736×736 composition. Everything on the hub is authored in those
 * original pixel units and multiplied by `--u` (one reference pixel in real pixels), so the
 * proportions stay exact at any size. On tall (portrait) screens the stage grows vertically
 * instead of shrinking, and orbs get a size boost so the centre logo stays dominant.
 */
export interface Metrics {
  w: number;
  h: number;
  /** stage width / height in px */
  sw: number;
  sh: number;
  /** stage offset in px */
  fx: number;
  fy: number;
  /** one reference pixel, in px */
  u: number;
  portrait: boolean;
}

const MAX_STAGE = 1000;

export function computeMetrics(w: number, h: number): Metrics {
  const portrait = h > w * 1.02;
  let sw: number;
  let sh: number;
  if (portrait) {
    sw = Math.min(w, MAX_STAGE);
    const maxRatio = w >= 700 ? 1.3 : 2.05;
    sh = Math.min(h, sw * maxRatio);
  } else {
    sh = Math.min(h, w, MAX_STAGE);
    sw = sh;
  }
  const minDim = Math.min(w, h);
  const boost = minDim < 520 ? 1.2 : minDim < 800 ? 1.08 : 1;
  return { w, h, sw, sh, fx: (w - sw) / 2, fy: (h - sh) / 2, u: (sw / 736) * boost, portrait };
}

let current: Metrics = computeMetrics(window.innerWidth, window.innerHeight);
const listeners = new Set<() => void>();

function write(m: Metrics) {
  const s = document.documentElement.style;
  s.setProperty("--sw", `${m.sw}px`);
  s.setProperty("--sh", `${m.sh}px`);
  s.setProperty("--fx", `${m.fx}px`);
  s.setProperty("--fy", `${m.fy}px`);
  s.setProperty("--u", `${m.u.toFixed(4)}px`);
}

/** Call once before first render so there is no layout flash. */
export function initMetrics() {
  write(current);
  let raf = 0;
  const update = () => {
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(() => {
      const m = computeMetrics(window.innerWidth, window.innerHeight);
      if (m.w === current.w && m.h === current.h) return;
      current = m;
      write(m);
      listeners.forEach((l) => l());
    });
  };
  window.addEventListener("resize", update, { passive: true });
  window.addEventListener("orientationchange", update);
  window.visualViewport?.addEventListener("resize", update);
}

const subscribe = (cb: () => void) => {
  listeners.add(cb);
  return () => listeners.delete(cb);
};

export const getMetrics = () => current;
export const useMetrics = () => useSyncExternalStore(subscribe, getMetrics, getMetrics);
