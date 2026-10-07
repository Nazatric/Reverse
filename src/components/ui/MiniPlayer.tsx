import { useEffect, useRef } from "react";
import { Art } from "./Art";
import { Orb } from "./Controls";
import { useApp } from "../../state/app";
import { useNav } from "../../state/nav";
import { useTrackMeta } from "../../utils/metaStore";
import { cx } from "../../utils/cx";
import { ui } from "../../utils/audio";

/**
 * Persistent glass capsule: cover art, title, transport, and a hairline progress bar.
 * The bar is written straight to the DOM on timeupdate — zero React renders per frame.
 */
export function MiniPlayer() {
  const { player } = useApp();
  const nav = useNav();
  const meta = useTrackMeta(player.current);
  const t = player.current;
  const fill = useRef<HTMLDivElement>(null);
  const bar = useRef<HTMLDivElement>(null);
  const audio = player.audio;

  useEffect(() => {
    const paint = () => {
      const d = audio.duration || 0;
      const r = d ? audio.currentTime / d : 0;
      if (fill.current) fill.current.style.transform = `scaleX(${Math.min(1, Math.max(0, r))})`;
    };
    paint();
    const events = ["timeupdate", "loadedmetadata", "durationchange", "seeked", "emptied"] as const;
    events.forEach((e) => audio.addEventListener(e, paint));
    return () => events.forEach((e) => audio.removeEventListener(e, paint));
  }, [audio, t]);

  if (!t || nav.route?.n === "now") return null;

  return (
    <div className={cx("mini", player.playing && "is-playing")}>
      <button
        type="button"
        className="mini-main"
        onClick={(e) => {
          ui.open();
          nav.push({ n: "now" }, e.currentTarget);
        }}
        aria-label="Open now playing"
      >
        <span className="mini-art">
          <Art track={t} />
        </span>
        <span className="mini-text">
          <span className="mini-title">{meta?.title || t.title}</span>
          <span className="mini-sub">{meta?.artist || t.folder.split("/").pop()}</span>
        </span>
      </button>
      <Orb
        label={player.playing ? "Pause" : "Play"}
        icon={player.playing ? "pause" : "play"}
        size={44}
        hot
        onClick={() => {
          player.toggle();
        }}
      />
      <div className="mini-progress" ref={bar} aria-hidden="true">
        <div className="mini-progress-fill" ref={fill} />
      </div>
    </div>
  );
}
