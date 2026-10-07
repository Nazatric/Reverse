import { useEffect } from "react";
import type { RefObject } from "react";

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

/**
 * Depth parallax. Moves every `[data-depth]` descendant by (depth px × pointer/tilt).
 * One rAF loop, writes transforms only, and goes fully idle once it settles — so it costs
 * nothing at rest instead of burning frames forever.
 */
export function useParallax(root: RefObject<HTMLElement | null>, enabled: boolean) {
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const layers = Array.from(el.querySelectorAll<HTMLElement>("[data-depth]"));
    const reset = () => layers.forEach((l) => (l.style.transform = ""));
    if (!enabled) {
      reset();
      return;
    }

    let tx = 0;
    let ty = 0;
    let cx = 0;
    let cy = 0;
    let raf = 0;
    let base: { b: number; g: number } | null = null;

    const tick = () => {
      cx += (tx - cx) * 0.09;
      cy += (ty - cy) * 0.09;
      for (const l of layers) {
        const d = Number(l.dataset.depth) || 0;
        l.style.transform = `translate3d(${(cx * d).toFixed(2)}px,${(cy * d).toFixed(2)}px,0)`;
      }
      raf = Math.abs(tx - cx) > 0.0015 || Math.abs(ty - cy) > 0.0015 ? requestAnimationFrame(tick) : 0;
    };
    const kick = () => {
      if (!raf) raf = requestAnimationFrame(tick);
    };

    const onMove = (e: PointerEvent) => {
      if (e.pointerType === "touch") return; // touch panning must never fight the UI
      tx = (e.clientX / window.innerWidth - 0.5) * 2;
      ty = (e.clientY / window.innerHeight - 0.5) * 2;
      kick();
    };

    // Gyro: relative to wherever the device was when the page gained focus, so holding the
    // phone at a natural angle is always "neutral" and the range stays small and smooth.
    const onTilt = (e: DeviceOrientationEvent) => {
      if (e.gamma == null || e.beta == null) return;
      if (!base) base = { b: e.beta, g: e.gamma };
      tx = clamp((e.gamma - base.g) / 22, -1, 1);
      ty = clamp((e.beta - base.b) / 22, -1, 1);
      kick();
    };
    const rebase = () => {
      base = null;
      tx = 0;
      ty = 0;
      kick();
    };

    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("deviceorientation", onTilt, { passive: true });
    document.addEventListener("visibilitychange", rebase);
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("deviceorientation", onTilt);
      document.removeEventListener("visibilitychange", rebase);
      cancelAnimationFrame(raf);
      reset();
    };
  }, [root, enabled]);
}
