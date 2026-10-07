import { useEffect, useState, type CSSProperties } from "react";
import { routeKey, useNav, type Route } from "../state/nav";
import { MusicHome } from "./music/MusicHome";
import { AlbumsView, AlbumView } from "./music/Albums";
import { SongsView } from "./music/Songs";
import { PlaylistsView, PlaylistView } from "./music/Playlists";
import { SearchView } from "./music/Search";
import { NowPlaying } from "./music/NowPlaying";
import { GamesView, GameView, GameForm } from "./Games";
import { Game2048 } from "./games/Game2048";
import { HomiesView, HomieView } from "./Homies";
import { AccountView } from "./Account";
import { ConfigView } from "./Config";
import { PluginsPage } from "../plugins/PluginsPage";
import { usePluginPages } from "../plugins/registry";
import { NodeGlyph, isGlyphName } from "../components/hub/Glyphs";
import { cx } from "../utils/cx";

function renderRoute(r: Route) {
  switch (r.n) {
    case "music": return <MusicHome />;
    case "albums": return <AlbumsView />;
    case "album": return <AlbumView id={r.id} />;
    case "songs": return <SongsView />;
    case "playlists": return <PlaylistsView />;
    case "playlist": return <PlaylistView id={r.id} />;
    case "search": return <SearchView />;
    case "now": return <NowPlaying />;
    case "games": return <GamesView />;
    case "game": return <GameView id={r.id} />;
    case "game-edit": return <GameForm id={r.id} />;
    case "g2048": return <Game2048 />;
    case "homies": return <HomiesView />;
    case "homie": return <HomieView id={r.id} />;
    case "account": return <AccountView />;
    case "config": return <ConfigView />;
    case "plugins": return <PluginsPage />;
  }
}

/** Opens an internal page or an external link, exactly as a plugin block describes. */
function usePluginNavigate() {
  const nav = useNav();
  return (target: { page?: string; url?: string }) => {
    if (target.url) {
      window.open(target.url, "_blank", "noopener,noreferrer");
      return;
    }
    if (target.page) nav.push({ n: target.page } as never, null);
  };
}

/** A page declared by a plugin, rendered from typed blocks. */
function PluginPageView({ id }: { id: string }) {
  const pages = usePluginPages();
  const entry = pages.find((p) => `plugin:${p.pluginId}:${p.page.id}` === id || p.page.id === id);
  const navigate = usePluginNavigate();

  if (!entry) {
    return (
      <div className="page-in">
        <div className="empty">
          <h2 className="empty-title">plugin page unavailable</h2>
          <p className="empty-text">The plugin that declared this page is switched off or was removed.</p>
        </div>
      </div>
    );
  }

  const { page } = entry;
  return (
    <div className="page page--plugin">
      <header className="page-head">
        <div className="page-headtext">
          <h1 className="page-title">{page.title || page.label}</h1>
          {page.subtitle && <p className="page-sub">{page.subtitle}</p>}
        </div>
      </header>
      <div className="page-scroll">
        <div className="page-in">
          {page.blocks.map((block, i) => {
            const key = `${page.id}-${i}`;
            switch (block.type) {
              case "header":
                return <h2 key={key} className="group-title">{block.value}</h2>;
              case "text":
                return <p key={key} className="plugin-text">{block.value}</p>;
              case "note":
                return <p key={key} className="picker-note">{block.value}</p>;
              case "link":
                return (
                  <a
                    key={key}
                    className="plugin-link"
                    href={block.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {block.label}
                  </a>
                );
              case "button":
                return (
                  <button
                    key={key}
                    type="button"
                    className="btn btn--primary btn--block"
                    onClick={() => navigate({ page: block.page, url: block.url })}
                  >
                    <span>{block.label}</span>
                  </button>
                );
              case "tiles":
                return (
                  <div key={key} className="plugin-tiles">
                    {block.items.map((item, ti) => (
                      <button
                        key={`${key}-${ti}`}
                        type="button"
                        className="plugin-tile"
                        onClick={() => navigate(item)}
                      >
                        <span className="node-body plugin-tile-orb">
                          <NodeGlyph id={item.icon && isGlyphName(item.icon) ? item.icon : "star"} />
                        </span>
                        <span className="plugin-tile-label">{item.label}</span>
                      </button>
                    ))}
                  </div>
                );
              default:
                return null;
            }
          })}
        </div>
      </div>
    </div>
  );
}

const IRIS_BASE = 600;

export function PageHost() {
  const { route, dir, origin } = useNav();
  const [shown, setShown] = useState<Route | null>(route);

  useEffect(() => {
    if (route) {
      setShown(route);
      return;
    }
    const t = window.setTimeout(() => setShown(null), 480);
    return () => window.clearTimeout(t);
  }, [route]);

  const view = route ?? shown;
  if (!view) return null;
  const closing = !route;

  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const far = Math.hypot(Math.max(origin.x, vw - origin.x), Math.max(origin.y, vh - origin.y));
  const style = {
    "--ox": `${origin.x}px`,
    "--oy": `${origin.y}px`,
    "--iris-start": Math.max(0.06, origin.r / (IRIS_BASE / 2)),
    "--iris-end": (far / (IRIS_BASE / 2)) * 1.06,
  } as CSSProperties;

  return (
    <div className={cx("pagehost", closing ? "is-closing" : "is-open")} style={style}>
      <div className="iris" />
      <div className="page-surface">
        <div key={routeKey(view)} className={cx("view", `view--${dir}`)}>
          {view.n === "plugin-page"
            ? <PluginPageView id={view.id} />
            : renderRoute(view as Exclude<Route, { n: "plugin-page" }>)}
        </div>
      </div>
    </div>
  );
}
