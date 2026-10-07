import { useEffect, useMemo, useState } from "react";
import { Page } from "../../components/ui/Page";
import { Art } from "../../components/ui/Art";
import { Empty, Field, GlassButton, Orb } from "../../components/ui/Controls";
import { Icon } from "../../components/ui/Icons";
import { Sheet } from "../../components/ui/Sheet";
import { CollectionHero } from "./Collection";
import { TrackRow } from "./TrackRow";
import { useApp, type Playlist } from "../../state/app";
import { useNav } from "../../state/nav";
import type { Track } from "../../utils/musicLibrary";
import { ui } from "../../utils/audio";

/** Pick (or create) a playlist for one song. */
export function AddToPlaylistSheet({ track, onClose }: { track: Track; onClose: () => void }) {
  const { playlists, setPlaylists } = useApp();
  const [name, setName] = useState("");

  const toggle = (pl: Playlist) =>
    setPlaylists((list) =>
      list.map((p) => (p.id === pl.id ? { ...p, ids: p.ids.includes(track.id) ? p.ids.filter((i) => i !== track.id) : [...p.ids, track.id] } : p)),
    );

  const create = () => {
    const n = name.trim();
    if (!n) return;
    ui.confirm();
    setPlaylists((list) => [...list, { id: String(Date.now()), name: n, ids: [track.id] }]);
    onClose();
  };

  return (
    <Sheet title="add to playlist" onClose={onClose}>
      {playlists.length > 0 && (
        <ul className="sheet-list">
          {playlists.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                className="sheet-row"
                onClick={() => {
                  ui.tap();
                  toggle(p);
                }}
              >
                <span>{p.name}</span>
                {p.ids.includes(track.id) && <Icon name="check" />}
              </button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="sheet-new"
        onSubmit={(e) => {
          e.preventDefault();
          create();
        }}
      >
        <Field label="new playlist" value={name} onChange={setName} maxLength={40} placeholder="name it" />
        <GlassButton type="submit" variant="primary" icon="plus">
          create
        </GlassButton>
      </form>
    </Sheet>
  );
}

export function PlaylistsView() {
  const { playlists, setPlaylists, player } = useApp();
  const nav = useNav();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const byId = useMemo(() => new Map(player.library.map((t) => [t.id, t])), [player.library]);

  const create = () => {
    const n = name.trim();
    if (!n) return;
    ui.confirm();
    setPlaylists((list) => [...list, { id: String(Date.now()), name: n, ids: [] }]);
    setName("");
    setAdding(false);
  };

  return (
    <Page
      title="playlists"
      sub={`${playlists.length} ${playlists.length === 1 ? "playlist" : "playlists"}`}
      actions={<Orb label="New playlist" icon="plus" size={50} hot on={adding} onClick={() => setAdding((v) => !v)} />}
    >
      {adding && (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            create();
          }}
        >
          <Field label="name" value={name} onChange={setName} maxLength={40} placeholder="my playlist" autoFocus />
          <GlassButton type="submit" variant="primary">
            create
          </GlassButton>
        </form>
      )}

      {playlists.length === 0 && !adding ? (
        <Empty icon="playlists" title="no playlists yet" text="Make one here, then add songs from the songs list or Now Playing." />
      ) : (
        <ul className="plist">
          {playlists.map((p) => {
            const first = p.ids.map((i) => byId.get(i)).find(Boolean);
            return (
              <li key={p.id}>
                <button type="button" className="prow" onClick={(e) => nav.push({ n: "playlist", id: p.id }, e.currentTarget)}>
                  <span className="prow-art">
                    <Art track={first} />
                  </span>
                  <span className="prow-text">
                    <span className="prow-name">{p.name}</span>
                    <span className="prow-sub">{p.ids.filter((i) => byId.has(i)).length} songs</span>
                  </span>
                  <Icon name="chevron" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Page>
  );
}

export function PlaylistView({ id }: { id: string }) {
  const { playlists, setPlaylists, player } = useApp();
  const nav = useNav();
  const pl = playlists.find((p) => p.id === id);
  const byId = useMemo(() => new Map(player.library.map((t) => [t.id, t])), [player.library]);
  const tracks = useMemo(() => (pl ? (pl.ids.map((i) => byId.get(i)).filter(Boolean) as Track[]) : []), [pl, byId]);
  const [renaming, setRenaming] = useState(false);
  const [name, setName] = useState(pl?.name ?? "");
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    if (!pl) nav.back();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pl]);
  if (!pl) return null;

  const play = (shuffle: boolean) => {
    if (!tracks.length) return;
    player.setShuffle(shuffle);
    player.playQueue(tracks, shuffle ? Math.floor(Math.random() * tracks.length) : 0);
  };

  return (
    <Page>
      <CollectionHero track={tracks[0]} title={pl.name} sub={`${tracks.length} songs`}>
        <Orb label="Play playlist" icon="play" size={64} hot disabled={!tracks.length} onClick={() => play(false)} />
        <Orb label="Shuffle playlist" icon="shuffle" size={52} disabled={!tracks.length} onClick={() => play(true)} />
        <Orb label="Rename playlist" icon="edit" size={52} on={renaming} onClick={() => setRenaming((v) => !v)} />
        <Orb label="Delete playlist" icon="trash" size={52} onClick={() => setConfirm(true)} />
      </CollectionHero>

      {renaming && (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim()) {
              ui.confirm();
              setPlaylists((list) => list.map((p) => (p.id === pl.id ? { ...p, name: name.trim() } : p)));
              setRenaming(false);
            }
          }}
        >
          <Field label="name" value={name} onChange={setName} maxLength={40} autoFocus />
          <GlassButton type="submit" variant="primary">
            save
          </GlassButton>
        </form>
      )}

      {tracks.length === 0 ? (
        <Empty icon="note" title="empty playlist" text="Add songs from the songs list or from Now Playing." />
      ) : (
        <div className="tlist">
          {tracks.map((t, i) => (
            <TrackRow
              key={t.id}
              track={t}
              active={player.current?.id === t.id}
              playing={player.playing}
              onPlay={() => player.playQueue(tracks, i)}
              trailing={
                <Orb
                  label="Remove from playlist"
                  icon="close"
                  size={40}
                  className="orb--ghost"
                  onClick={() => setPlaylists((list) => list.map((p) => (p.id === pl.id ? { ...p, ids: p.ids.filter((x) => x !== t.id) } : p)))}
                />
              }
            />
          ))}
        </div>
      )}

      {confirm && (
        <Sheet title={`delete “${pl.name}”?`} onClose={() => setConfirm(false)}>
          <p className="sheet-text">The playlist is removed. Your songs stay exactly where they are.</p>
          <div className="sheet-actions">
            <GlassButton variant="ghost" onClick={() => setConfirm(false)}>
              cancel
            </GlassButton>
            <GlassButton
              variant="danger"
              icon="trash"
              onClick={() => {
                setConfirm(false);
                setPlaylists((list) => list.filter((p) => p.id !== pl.id));
              }}
            >
              delete
            </GlassButton>
          </div>
        </Sheet>
      )}
    </Page>
  );
}
