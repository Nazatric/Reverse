import { useEffect, useRef } from "react";
import type { Track } from "../../utils/musicLibrary";
import { requestMeta, useTrackMeta } from "../../utils/metaStore";
import { cx } from "../../utils/cx";

/* One shared observer: covers are only decoded when they scroll into view. */
const callbacks = new WeakMap<Element, () => void>();
let io: IntersectionObserver | null = null;
function observe(el: Element, cb: () => void) {
  if (!io) {
    io = new IntersectionObserver(
      (entries) => {
        for (const en of entries) {
          if (en.isIntersecting) {
            callbacks.get(en.target)?.();
            callbacks.delete(en.target);
            io!.unobserve(en.target);
          }
        }
      },
      { rootMargin: "300px" },
    );
  }
  callbacks.set(el, cb);
  io.observe(el);
  return () => {
    callbacks.delete(el);
    io?.unobserve(el);
  };
}

const Fallback = ({ name = "" }: { name?: string }) => (
  <svg className="art-fallback" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
    <circle cx="50" cy="50" r="49" fill="#161616" />
    <circle cx="50" cy="50" r="40" fill="none" stroke="#2c2c2b" strokeWidth="1.2" />
    <circle cx="50" cy="50" r="31" fill="none" stroke="#2c2c2b" strokeWidth="1.2" />
    <circle cx="50" cy="50" r="22" fill="none" stroke="#2c2c2b" strokeWidth="1.2" />
    <circle cx="50" cy="50" r="15" fill="#8d8e8b" />
    <circle cx="50" cy="50" r="15" fill="none" stroke="#e4e5e1" strokeWidth="1.5" />
    <circle cx="50" cy="50" r="3" fill="#0b0b0b" />
    <path d="M50 50 62 20" stroke="#fff" strokeOpacity="0.07" strokeWidth="14" />
    {name && <text x="50" y="90" textAnchor="middle" fill="#4b4c4a" fontSize="9" fontFamily="Orbitron, sans-serif">{name}</text>}
  </svg>
);

/**
 * Circular cover art. Prefers the embedded picture, then the folder artwork
 * (cover.jpg / front.png), then the disc fallback.
 */
export function Art({ track, className }: { track: Track | null | undefined; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const meta = useTrackMeta(track);
  const src = meta?.coverUrl ?? track?.cover;

  useEffect(() => {
    const el = ref.current;
    if (!el || !track) return;
    return observe(el, () => requestMeta(track));
  }, [track]);

  return (
    <span ref={ref} className={cx("art", className)}>
      {src ? (
        <img src={src} alt="" decoding="async" draggable={false} />
      ) : (
        <Fallback />
      )}
    </span>
  );
}

/** Same art, but for an explicit cover URL (album cards carry their own). */
export function ArtSrc({ src, fallbackName, className }: { src?: string; fallbackName?: string; className?: string }) {
  return (
    <span className={cx("art", className)}>
      {src ? <img src={src} alt="" decoding="async" draggable={false} /> : <Fallback name={fallbackName} />}
    </span>
  );
}
