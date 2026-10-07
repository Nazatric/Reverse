import { useCallback, useEffect, useRef, type KeyboardEvent, type PointerEvent } from "react";
import { formatTime } from "../../utils/musicLibrary";

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));
const setText = (el: HTMLElement | null, s: string) => {
  if (el && el.textContent !== s) el.textContent = s;
};

/* ---------- Generic glass slider (controlled) ---------- */

interface SliderProps {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onChange: (v: number) => void;
}

export function GlassSlider({ label, value, min = 0, max = 1, step = 0.01, onChange }: SliderProps) {
  const rail = useRef<HTMLDivElement>(null);
  const ratio = (value - min) / (max - min);

  const set = (clientX: number) => {
    const r = rail.current!.getBoundingClientRect();
    const t = clamp((clientX - r.left) / r.width, 0, 1);
    onChange(clamp(Math.round((min + t * (max - min)) / step) * step, min, max));
  };

  const onKey = (e: KeyboardEvent) => {
    const d = e.key === "ArrowRight" || e.key === "ArrowUp" ? step * 5 : e.key === "ArrowLeft" || e.key === "ArrowDown" ? -step * 5 : 0;
    if (d) {
      e.preventDefault();
      onChange(clamp(value + d, min, max));
    }
  };

  return (
    <div
      className="gslider"
      role="slider"
      tabIndex={0}
      aria-label={label}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={Number(value.toFixed(2))}
      onKeyDown={onKey}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        set(e.clientX);
      }}
      onPointerMove={(e) => e.buttons && set(e.clientX)}
    >
      <div className="gslider-rail" ref={rail}>
        <div className="gslider-fill" style={{ transform: `scaleX(${ratio})` }} />
        <div className="gslider-thumb" style={{ left: `${ratio * 100}%` }} />
      </div>
    </div>
  );
}

/* ---------- Playback scrubber: DOM-driven, zero React renders while playing ---------- */

export function Scrubber({ audio, onSeek }: { audio: HTMLAudioElement; onSeek: (sec: number) => void }) {
  const rail = useRef<HTMLDivElement>(null);
  const fill = useRef<HTMLDivElement>(null);
  const thumb = useRef<HTMLDivElement>(null);
  const cur = useRef<HTMLSpanElement>(null);
  const dur = useRef<HTMLSpanElement>(null);
  const drag = useRef(false);
  const width = useRef(0);

  const paint = useCallback((r: number) => {
    const c = clamp(r, 0, 1);
    if (fill.current) fill.current.style.transform = `scaleX(${c})`;
    if (thumb.current) thumb.current.style.transform = `translate3d(${c * width.current}px,-50%,0)`;
  }, []);

  useEffect(() => {
    const el = rail.current!;
    const sync = () => {
      const d = audio.duration || 0;
      const t = audio.currentTime || 0;
      if (!drag.current) {
        paint(d ? t / d : 0);
        setText(cur.current, formatTime(t));
      }
      setText(dur.current, formatTime(d));
    };
    const measure = () => {
      width.current = el.clientWidth;
      sync();
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    const events = ["timeupdate", "loadedmetadata", "durationchange", "seeked", "emptied"] as const;
    events.forEach((ev) => audio.addEventListener(ev, sync));
    return () => {
      ro.disconnect();
      events.forEach((ev) => audio.removeEventListener(ev, sync));
    };
  }, [audio, paint]);

  const at = (e: PointerEvent) => {
    const r = rail.current!.getBoundingClientRect();
    return clamp((e.clientX - r.left) / r.width, 0, 1);
  };

  return (
    <div className="scrub">
      <span ref={cur} className="scrub-time">0:00</span>
      <div
        className="scrub-hit"
        role="slider"
        tabIndex={0}
        aria-label="Seek"
        aria-valuemin={0}
        aria-valuemax={100}
        onKeyDown={(e) => {
          if (e.key === "ArrowRight") onSeek(audio.currentTime + 5);
          if (e.key === "ArrowLeft") onSeek(audio.currentTime - 5);
        }}
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId);
          drag.current = true;
          width.current = rail.current!.clientWidth;
          const r = at(e);
          paint(r);
          setText(cur.current, formatTime(r * (audio.duration || 0)));
        }}
        onPointerMove={(e) => {
          if (!drag.current) return;
          const r = at(e);
          paint(r);
          setText(cur.current, formatTime(r * (audio.duration || 0)));
        }}
        onPointerUp={(e) => {
          if (!drag.current) return;
          drag.current = false;
          onSeek(at(e) * (audio.duration || 0));
        }}
        onPointerCancel={() => {
          drag.current = false;
        }}
      >
        <div className="scrub-rail" ref={rail}>
          <div className="scrub-fill" ref={fill} />
          <div className="scrub-thumb" ref={thumb} />
        </div>
      </div>
      <span ref={dur} className="scrub-time scrub-time--end">0:00</span>
    </div>
  );
}
