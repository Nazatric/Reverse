import { useMemo, useState } from "react";
import { Page } from "../../components/ui/Page";
import { Art } from "../../components/ui/Art";
import { Empty, Field } from "../../components/ui/Controls";
import { TrackRow } from "./TrackRow";
import { useApp } from "../../state/app";
import { useNav } from "../../state/nav";
import { useAlbums } from "../../utils/albums";
import { getMeta } from "../../utils/metaStore";

export function SearchView() {
  const { player } = useApp();
  const nav = useNav();
  const [q, setQ] = useState("");
  const albums = useAlbums(player.library);
  const term = q.trim().toLowerCase();

  const res = useMemo(() => {
    if (!term) return { albums: [], songs: [] };
    return {
      albums: albums.filter((a) => a.name.toLowerCase().includes(term)).slice(0, 12),
      songs: player.library
        .filter((t) => {
          const m = getMeta(t.id);
          return (
            t.title.toLowerCase().includes(term) ||
            t.folder.toLowerCase().includes(term) ||
            !!m?.title?.toLowerCase().includes(term) ||
            !!m?.artist?.toLowerCase().includes(term)
          );
        })
        .slice(0, 150),
    };
  }, [term, albums, player.library]);

  const fine = typeof matchMedia === "function" && matchMedia("(pointer: fine)").matches;

  return (
    <Page title="search" sub="songs, albums, artists">
      <Field label="find" type="search" value={q} onChange={setQ} placeholder="start typing…" autoFocus={fine} autoComplete="off" enterKeyHint="search" />

      {!term && <Empty icon="search" title="search your library" text="Matches song names, albums and artists." />}
      {term && !res.albums.length && !res.songs.length && <Empty icon="search" title="nothing found" text={`No matches for “${q.trim()}”.`} />}

      {res.albums.length > 0 && (
        <section className="sresult">
          <h2 className="surface-title">albums</h2>
          <div className="srow">
            {res.albums.map((a) => (
              <button key={a.id} type="button" className="acard acard--sm" onClick={(e) => nav.push({ n: "album", id: a.id }, e.currentTarget)}>
                <span className="acard-art">
                  <span className="acard-halo" />
                  <Art track={a.tracks[0]} />
                </span>
                <span className="acard-name">{a.name}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      {res.songs.length > 0 && (
        <section className="sresult">
          <h2 className="surface-title">songs</h2>
          <div className="tlist">
            {res.songs.map((t) => (
              <TrackRow
                key={t.id}
                track={t}
                active={player.current?.id === t.id}
                playing={player.playing}
                onPlay={() => player.playQueue(res.songs, res.songs.indexOf(t))}
              />
            ))}
          </div>
        </section>
      )}
    </Page>
  );
}
