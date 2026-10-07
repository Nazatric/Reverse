/**
 * UI sound layer built on jsfxr (https://github.com/chr15m/jsfxr) — the JavaScript port of
 * SFXR, the classic 8-bit sound-effect generator. Presets are generated once after the first
 * user gesture (browsers block AudioContext before a gesture) and replayed from cache, so
 * playback is instant and allocation-free.
 */
import jsfxr from "jsfxr";

type PresetName = "pickupCoin" | "powerUp" | "click" | "blipSelect" | "jump" | "hitHurt";

interface Sfx {
  el: HTMLAudioElement;
  vol: number;
}

const MAP: Record<string, { preset: PresetName; vol: number }> = {
  hover: { preset: "blipSelect", vol: 0.1 },
  tap: { preset: "click", vol: 0.4 },
  open: { preset: "powerUp", vol: 0.2 },
  close: { preset: "jump", vol: 0.18 },
  confirm: { preset: "pickupCoin", vol: 0.3 },
  shake: { preset: "hitHurt", vol: 0.22 },
};

const cache = new Map<string, Sfx>();
let warmed = false;
let failed = false;

function warm() {
  if (warmed || failed) return;
  warmed = true;
  try {
    for (const [k, { preset, vol }] of Object.entries(MAP)) {
      const el = jsfxr.generate(preset);
      el.volume = vol;
      cache.set(k, { el, vol });
    }
  } catch {
    failed = true;
    cache.clear();
  }
}

class UiSound {
  enabled = true;
  haptics = true;

  /** Call inside the first pointer gesture so the AudioContext starts unlocked. */
  unlock() {
    warm();
  }

  private play(key: string) {
    if (!this.enabled) return;
    warm();
    const s = cache.get(key);
    if (!s) return;
    try {
      s.el.currentTime = 0;
      void s.el.play().catch(() => undefined);
    } catch {
      /* not ready yet */
    }
  }

  hover() {
    this.play("hover");
  }

  tap() {
    this.play("tap");
    this.buzz(8);
  }

  open() {
    this.play("open");
    this.buzz(10);
  }

  close() {
    this.play("close");
  }

  confirm() {
    this.play("confirm");
    this.buzz(14);
  }

  shake() {
    this.play("shake");
    this.buzz([20, 40, 20]);
  }

  private buzz(ms: number | number[]) {
    if (!this.haptics || typeof navigator === "undefined" || !("vibrate" in navigator)) return;
    try {
      navigator.vibrate(ms);
    } catch {
      /* unsupported */
    }
  }
}

export const ui = new UiSound();
