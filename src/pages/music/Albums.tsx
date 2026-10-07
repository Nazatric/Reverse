import { useEffect, useMemo } from "react";
import { Page } from "../../components/ui/Page";
import { ArtSrc } from "../../components/ui/Art";
import { Empty, Orb } from "../../components/ui/Controls";
import { CollectionHero } from "./Collection";
import { TrackRow } from "./TrackRow";
import { useApp } from "../../state/app";
import { useNav } from "../../state/nav";
import { useAlbums } from "../../utils/albums";

export function AlbumsView() {
  const { player } = useApp();
  const nav = useNav();
  const albums = useAlbums(player.library);

  return (
    <Page title="albums" sub={`${albums.length} on this device`}>
      {albums.length === 0 ? (
        <Empty icon="disc" title="no albums" text="Choose a music folder first." />
      ) : (
        <div className="agrid">
          {albums.map((a) => (
            <button key={a.id} type="button" className="acard" onClick={(e) => nav.push({ n: "album", id: a.id }, e.currentTarget)}>
              <span className="acard-art">
                <span className="acard-halo" />
                <ArtSrc src={a.cover} fallbackName={a.name.slice(0, 3)} />
                </span>
                <span className="acard-name">{a.name}</span>
                <span className="acard-sub">{a.artist ? `${a.artist} · ` : ""}{a.tracks.length} songs</span>
            </button>
          ))}
        </div>
      )}
    </Page>
  );
}

export function AlbumView({ id }: { id: string }) {
  const { player } = useApp();
  const nav = useNav();
  const albums = useAlbums(player.library);
  const album = useMemo(() => albums.find((a) => a.id === id), [albums, id]);

  useEffect(() => {
    if (!album) nav.back();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [album]);
  if (!album) return null;

  const playAll = (shuffle: boolean) => {
    player.setShuffle(shuffle);
    player.playQueue(album.tracks, shuffle ? Math.floor(Math.random() * album.tracks.length) : 0);
  };

  return (
    <Page>
      <CollectionHero track={album.tracks[0]} title={album.name} sub={`${album.tracks.length} songs`}>
        <Orb label="Play album" icon="play" size={64} hot onClick={() => playAll(false)} />
        <Orb label="Shuffle album" icon="shuffle" size={52} onClick={() => playAll(true)} />
      </CollectionHero>
      <div className="tlist">
        {album.tracks.map((t, i) => (
          <TrackRow
            key={t.id}
            track={t}
            active={player.current?.id === t.id}
            playing={player.playing}
            onPlay={() => player.playQueue(album.tracks, i)}
          />
        ))}
      </div>
    </Page>
  );
}
