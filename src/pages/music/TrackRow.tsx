import type { ReactNode } from "react";
import { Art } from "../../components/ui/Art";
import { useTrackMeta } from "../../utils/metaStore";
import type { Track } from "../../utils/musicLibrary";
import { cx } from "../../utils/cx";

interface TrackRowProps {
  track: Track;
  active: boolean;
  playing: boolean;
  onPlay: () => void;
  trailing?: ReactNode;
}

/** One song: circular cover, title, artist — with a live equaliser over the art when it's the current track. */
export function TrackRow({ track, active, playing, onPlay, trailing }: TrackRowProps) {
  const meta = useTrackMeta(track);
  return (
    <div className={cx("trow", active && "is-active")}>
      <button type="button" className="trow-main" onClick={onPlay}>
        <span className="trow-art">
          <Art track={track} />
          {active && playing && (
            <span className="eq" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
          )}
        </span>
        <span className="trow-text">
          <span className="trow-title">{meta?.title || track.title}</span>
          <span className="trow-sub">{meta?.artist || track.folder.split("/").pop()}</span>
        </span>
      </button>
      {trailing}
    </div>
  );
}
