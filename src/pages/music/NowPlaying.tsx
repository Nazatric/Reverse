import { useEffect, useRef, useState } from "react";
import { Page } from "../../components/ui/Page";
import { Art } from "../../components/ui/Art";
import { Empty, Orb } from "../../components/ui/Controls";
import { GlassSlider, Scrubber } from "../../components/ui/Sliders";
import { AddToPlaylistSheet } from "./Playlists";
import { useApp } from "../../state/app";
import { useTrackMeta } from "../../utils/metaStore";
import { useMetrics } from "../../utils/metrics";
import type { MusicPlayer } from "../../hooks/useMusicPlayer";
import { cx } from "../../utils/cx";

const N = 72;

/**
 * Radial ticks around the cover. The playhead sweeps the ring (real progress on every device);
 * on desktop the ticks also react to the live spectrum. Pure canvas, one stroke call per frame.
 */
function Ring({ player }: { player: MusicPlayer }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const { w } = useMetrics();
  const { audio, playing } = player;

  useEffect(() => {
    const c = ref.current;
    const ctx = c?.getContext("2d");
    if (!c || !ctx) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const size = c.clientWidth;
    c.width = c.height = Math.round(size * dpr);
    const r0 = size * 0.345;
    const maxLen = size * 0.12;
    const levels = new Float32Array(N).fill(0.08);
    const motionOff = document.documentElement.dataset.motion === "off";

    const draw = () => {
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, size, size);
      ctx.translate(size / 2, size / 2);
      const d = audio.duration || 0;
      const prog = d ? audio.currentTime / d : 0;
      const head = Math.floor(prog * N);
      ctx.lineCap = "round";
      ctx.lineWidth = Math.max(2, size * 0.0075);
      // upcoming ticks (dim) and elapsed ticks (bright) — two strokes total
      for (const lit of [false, true]) {
        ctx.beginPath();
        for (let i = 0; i < N; i++) {
          if ((i <= head) !== lit) continue;
          const a = (i / N) * Math.PI * 2 - Math.PI / 2;
          const len = size * 0.012 + levels[i] * maxLen + (i === head ? size * 0.014 : 0);
          const cs = Math.cos(a);
          const sn = Math.sin(a);
          ctx.moveTo(cs * r0, sn * r0);
          ctx.lineTo(cs * (r0 + len), sn * (r0 + len));
        }
        ctx.strokeStyle = lit ? "rgba(255,255,255,.95)" : "rgba(255,255,255,.22)";
        ctx.stroke();
      }
    };

    const an = player.analyser();
    let raf = 0;
    if (playing && an && !motionOff) {
      const data = new Uint8Array(an.frequencyBinCount);
      const per = Math.max(1, Math.floor((data.length * 0.7) / (N / 2)));
      const loop = () => {
        an.getByteFrequencyData(data);
        for (let i = 0; i < N; i++) {
          const j = i < N / 2 ? i : N - 1 - i; // mirror left/right
          let s = 0;
          for (let k = 0; k < per; k++) s += data[j * per + k] || 0;
          const target = Math.pow(s / per / 255, 1.35);
          levels[i] += (target - levels[i]) * 0.32;
        }
        draw();
        raf = requestAnimationFrame(loop);
      };
      raf = requestAnimationFrame(loop);
    } else {
      draw();
      if (playing && !motionOff) {
        const t = () => draw();
        audio.addEventListener("timeupdate", t);
        return () => audio.removeEventListener("timeupdate", t);
      }
      const t = () => draw();
      audio.addEventListener("seeked", t);
      return () => audio.removeEventListener("seeked", t);
    }
    return () => cancelAnimationFrame(raf);
  }, [audio, playing, player, w]);

  return <canvas ref={ref} className="np-ring" aria-hidden="true" />;
}

export function NowPlaying() {
  const { player } = useApp();
  const t = player.current;
  const meta = useTrackMeta(t);
  const [sheet, setSheet] = useState(false);

  if (!t) {
    return (
      <Page title="now playing">
        <Empty icon="note" title="nothing playing" text="Pick a song from music and it will appear here." />
      </Page>
    );
  }

  return (
    <Page bare>
      <div className="np">
        <p className="np-kicker">now playing</p>
        <div className="np-stage">
          <Ring player={player} />
          <span className="np-halo" />
          <span className={cx("np-art", player.playing && "is-playing")}>
            <Art track={t} />
          </span>
        </div>

        <div className="np-info">
          <h1 className="np-title">{meta?.title || t.title}</h1>
          <p className="np-artist">{[meta?.artist, meta?.album || t.folder.split("/").pop()].filter(Boolean).join(" · ")}</p>
        </div>

        <Scrubber audio={player.audio} onSeek={player.seek} />

        <div className="np-ctl">
          <Orb label="Shuffle" icon="shuffle" size={46} className="orb--ghost" on={player.shuffle} onClick={() => player.setShuffle(!player.shuffle)} />
          <Orb label="Previous" icon="prev" size={58} onClick={player.prev} />
          <Orb label={player.playing ? "Pause" : "Play"} icon={player.playing ? "pause" : "play"} size={86} hot onClick={player.toggle} />
          <Orb label="Next" icon="next" size={58} onClick={player.next} />
          <Orb
            label={`Repeat ${player.repeat}`}
            icon={player.repeat === "one" ? "repeat-one" : "repeat"}
            size={46}
            className="orb--ghost"
            on={player.repeat !== "off"}
            onClick={player.cycleRepeat}
          />
        </div>

        <div className="np-foot">
          <span className="np-vol">
            <GlassSlider label="Volume" value={player.volume} onChange={player.setVolume} />
          </span>
          <Orb label="Add to playlist" icon="playlist-add" size={46} onClick={() => setSheet(true)} />
        </div>
      </div>
      {sheet && <AddToPlaylistSheet track={t} onClose={() => setSheet(false)} />}
    </Page>
  );
}
