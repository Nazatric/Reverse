import { Page } from "../../components/ui/Page";
import { Art } from "../../components/ui/Art";
import { Empty, Orb, Surface, GlassButton } from "../../components/ui/Controls";
import { Icon, type IconName } from "../../components/ui/Icons";
import { FolderPicker } from "../../components/ui/FolderPicker";
import { useApp } from "../../state/app";
import { useNav, type Route } from "../../state/nav";
import { useTrackMeta } from "../../utils/metaStore";
import { useAlbums } from "../../utils/albums";
import { ui } from "../../utils/audio";

const SECTIONS: { label: string; icon: IconName; route: Route }[] = [
  { label: "albums", icon: "disc", route: { n: "albums" } },
  { label: "songs", icon: "list", route: { n: "songs" } },
  { label: "playlists", icon: "playlists", route: { n: "playlists" } },
  { label: "search", icon: "search", route: { n: "search" } },
];

export function MusicHome() {
  const { player, playlists } = useApp();
  const nav = useNav();
  const meta = useTrackMeta(player.current);
  const albums = useAlbums(player.library);
  const t = player.current;

  if (!player.library.length) {
    return (
      <Page title="music" sub={player.status === "loading" ? "looking for your music…" : "nothing here yet"}>
        <Empty
          icon="folder"
          title="choose your music"
          text="Pick the folder on this device that holds your songs. Everything is read in place — nothing is uploaded."
        >
          <FolderPicker player={player} />
        </Empty>
      </Page>
    );
  }

  return (
    <Page title="music" sub={`${player.library.length} songs · ${albums.length} albums`}>
      <section className="hero">
        <button
          type="button"
          className="hero-orb"
          aria-label="Open now playing"
          onClick={(e) => {
            ui.open();
            nav.push({ n: "now" }, e.currentTarget);
          }}
        >
          <span className="hero-halo" />
          <span className="hero-art">
            <Art track={t} />
          </span>
        </button>
        <div className="hero-meta">
          <h2 className="hero-title">{meta?.title || t?.title || "—"}</h2>
          <p className="hero-sub">{meta?.artist || t?.folder.split("/").pop()}</p>
        </div>
        <div className="hero-ctl">
          <Orb label="Previous" icon="prev" size={52} onClick={player.prev} />
          <Orb label={player.playing ? "Pause" : "Play"} icon={player.playing ? "pause" : "play"} size={78} hot onClick={player.toggle} />
          <Orb label="Next" icon="next" size={52} onClick={player.next} />
        </div>
      </section>

      <nav className="quad" aria-label="Library">
        {SECTIONS.map((s) => (
          <button
            key={s.label}
            type="button"
            className="qnode"
            onPointerEnter={(e) => e.pointerType === "mouse" && ui.hover()}
            onClick={(e) => {
              ui.tap();
              nav.push(s.route, e.currentTarget);
            }}
          >
            <span className="orb qnode-orb" style={{ "--s": "76px" } as React.CSSProperties}>
              <span className="orb-face" />
              <Icon name={s.icon} />
            </span>
            <span className="qlabel">{s.label}</span>
            {s.label === "playlists" && playlists.length > 0 && <span className="qcount">{playlists.length}</span>}
          </button>
        ))}
      </nav>

      <Surface>
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">library</span>
            <span className="info-value">{player.folderName}</span>
          </div>
          <FolderPicker player={player} compact />
        </div>
      </Surface>
      {player.status !== "ready" && <GlassButton variant="ghost" onClick={() => void player.reconnect()}>reconnect</GlassButton>}
    </Page>
  );
}
