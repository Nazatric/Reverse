import { useMemo, useRef, useState } from "react";
import { Page } from "../../components/ui/Page";
import { Empty, Orb } from "../../components/ui/Controls";
import { VirtualList, type VItem, type VirtualApi } from "../../components/ui/VirtualList";
import { TrackRow } from "./TrackRow";
import { AddToPlaylistSheet } from "./Playlists";
import { useApp } from "../../state/app";
import { byTitle, letterOf, LETTERS } from "../../utils/albums";
import type { Track } from "../../utils/musicLibrary";
import { cx } from "../../utils/cx";

type SItem = VItem & { kind: "head" | "row"; letter?: string; track?: Track; idx?: number };

function AlphaRail({ present, onJump }: { present: Record<string, number>; onJump: (letter: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [hot, setHot] = useState<string | null>(null);

  const pick = (clientY: number) => {
    const r = ref.current!.getBoundingClientRect();
    const i = Math.min(LETTERS.length - 1, Math.max(0, Math.floor(((clientY - r.top) / r.height) * LETTERS.length)));
    let L = LETTERS[i];
    if (!(L in present)) {
      const next = LETTERS.slice(i).find((x) => x in present) ?? [...LETTERS.slice(0, i)].reverse().find((x) => x in present);
      if (!next) return;
      L = next;
    }
    setHot(L);
    onJump(L);
  };

  return (
    <div
      ref={ref}
      className="rail"
      role="group"
      aria-label="Jump to letter"
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        pick(e.clientY);
      }}
      onPointerMove={(e) => e.buttons && pick(e.clientY)}
      onPointerUp={() => setHot(null)}
      onPointerCancel={() => setHot(null)}
    >
      {LETTERS.map((L) => (
        <span key={L} className={cx("rail-l", L in present && "has", hot === L && "is-hot")}>
          {L}
        </span>
      ))}
      {hot && <span className="rail-bubble">{hot}</span>}
    </div>
  );
}

export function SongsView() {
  const { player } = useApp();
  const [sheetTrack, setSheetTrack] = useState<Track | null>(null);
  const api = useRef<VirtualApi | null>(null);

  const sorted = useMemo(() => [...player.library].sort(byTitle), [player.library]);
  const { items, heads } = useMemo(() => {
    const items: SItem[] = [];
    const heads: Record<string, number> = {};
    let last = "";
    sorted.forEach((t, i) => {
      const L = letterOf(t.title);
      if (L !== last) {
        heads[L] = items.length;
        items.push({ key: `h-${L}`, h: 38, kind: "head", letter: L });
        last = L;
      }
      items.push({ key: t.id, h: 66, kind: "row", track: t, idx: i });
    });
    return { items, heads };
  }, [sorted]);

  return (
    <Page title="songs" sub={`${sorted.length} songs · a–z`} fill>
      {sorted.length === 0 ? (
        <Empty icon="list" title="no songs" text="Choose a music folder first." />
      ) : (
        <div className="songs">
          <VirtualList
            className="songs-list"
            items={items}
            apiRef={api}
            padBottom={130}
            render={(it) =>
              it.kind === "head" ? (
                <div className="lhead">
                  <span>{it.letter}</span>
                </div>
              ) : (
                <TrackRow
                  track={it.track!}
                  active={player.current?.id === it.track!.id}
                  playing={player.playing}
                  onPlay={() => player.playQueue(sorted, it.idx!)}
                  trailing={<Orb label="Add to playlist" icon="playlist-add" size={40} className="orb--ghost" onClick={() => setSheetTrack(it.track!)} />}
                />
              )
            }
          />
          <AlphaRail present={heads} onJump={(L) => api.current?.scrollToIndex(heads[L])} />
        </div>
      )}
      {sheetTrack && <AddToPlaylistSheet track={sheetTrack} onClose={() => setSheetTrack(null)} />}
    </Page>
  );
}
