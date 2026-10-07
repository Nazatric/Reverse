import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Page } from "../components/ui/Page";
import { Chip, Empty, Field, GlassButton, Orb } from "../components/ui/Controls";
import { Icon } from "../components/ui/Icons";
import { Sheet } from "../components/ui/Sheet";
import { useApp, type Game } from "../state/app";
import { useNav } from "../state/nav";
import { imageToDataUrl } from "../utils/image";
import { ago, normaliseLink } from "../utils/link";
import { ui } from "../utils/audio";
import { GameBrowser } from "./games/GameBrowser";

const PRESETS = ["PC", "Console", "Mobile", "Web"];

export const BUILTIN_2048: Game = {
  id: "built-in-2048",
  name: "Y2K 2048",
  category: "Built-in",
  url: "",
  cover: null,
  favorite: true,
  launches: 0,
  lastPlayed: null,
  added: 0,
};

const isWebUrl = (url: string) => /^https?:/i.test(url);

export function Cover({ game, className }: { game: Pick<Game, "cover" | "name" | "id">; className?: string }) {
  const builtin = game.id === BUILTIN_2048.id;
  return (
    <span className={`cover${builtin ? " cover--builtin" : ""} ${className ?? ""}`}>
      {game.cover ? (
        <img src={game.cover} alt="" decoding="async" draggable={false} loading="lazy" />
      ) : builtin ? (
        <span className="cover-builtin">
          <b>2048</b>
          <span>built-in</span>
        </span>
      ) : (
        <span className="cover-fallback">
          <span className="cover-initial">{game.name.trim().charAt(0).toUpperCase() || "?"}</span>
        </span>
      )}
      <span className="cover-gloss" />
    </span>
  );
}

function useLaunch() {
  const { setGames } = useApp();
  return (id: string) => setGames((l) => l.map((g) => (g.id === id ? { ...g, launches: g.launches + 1, lastPlayed: Date.now() } : g)));
}

/* ---------------- library ---------------- */

export function GamesView() {
  const { games } = useApp();
  const nav = useNav();
  const bump = useLaunch();
  const [cat, setCat] = useState("all");
  const [web, setWeb] = useState<{ title: string; url: string } | null>(null);

  const cats = useMemo(() => [...new Set(games.map((g) => g.category).filter(Boolean))].sort((a, b) => a.localeCompare(b)), [games]);
  const hasFav = games.some((g) => g.favorite);
  const active = cat === "all" || (cat === "__fav" && hasFav) || cats.includes(cat) ? cat : "all";
  const list = useMemo(
    () =>
      games
        .filter((g) => (active === "all" ? true : active === "__fav" ? g.favorite : g.category === active))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [games, active],
  );
  const recent = useMemo(
    () => [...games].filter((g) => g.lastPlayed).sort((a, b) => (b.lastPlayed ?? 0) - (a.lastPlayed ?? 0))[0],
    [games],
  );

  const open = (g: Game) => {
    if (g.id === BUILTIN_2048.id) {
      bump(g.id);
      nav.push({ n: "g2048" }, document.querySelector(".page"));
      return;
    }
    if (g.url) {
      bump(g.id);
      setWeb({ title: g.name, url: g.url });
    } else {
      nav.push({ n: "game", id: g.id }, document.querySelector(".page"));
    }
  };

  return (
    <Page
      title="games"
      sub={`${games.length + 1} in your library`}
      actions={<Orb label="Add game" icon="plus" size={50} hot onClick={(e) => nav.push({ n: "game-edit" }, e.currentTarget)} />}
    >
      {games.length === 0 && (
        <Empty icon="gamepad" title="your shelf is empty" text="Add web games — paste a link and it plays right here, fullscreen, no browser. Or tap Y2K 2048 to kill time.">
          <GlassButton variant="primary" icon="plus" onClick={(e) => nav.push({ n: "game-edit" }, e.currentTarget)}>
            add a game
          </GlassButton>
        </Empty>
      )}

      {(recent || games.length > 0) && active === "all" && (
        <div className="gfeature">
          <button type="button" className="gfeature-main" onClick={() => open(recent ?? BUILTIN_2048)}>
            <Cover game={recent ?? BUILTIN_2048} className="gfeature-cover" />
            <span className="gfeature-text">
              <span className="gfeature-kicker">continue</span>
              <span className="gfeature-name">{(recent ?? BUILTIN_2048).name}</span>
              <span className="gfeature-sub">{recent ? `played ${ago(recent.lastPlayed)}` : "shipped with the app"}</span>
            </span>
          </button>
          <Orb label={`Play ${(recent ?? BUILTIN_2048).name}`} icon="play" size={56} hot onClick={() => open(recent ?? BUILTIN_2048)} />
        </div>
      )}

      {(cats.length > 0 || hasFav) && (
        <div className="chips" role="group" aria-label="Categories">
          <Chip active={active === "all"} onClick={() => setCat("all")}>
            all
          </Chip>
          {hasFav && (
            <Chip active={active === "__fav"} onClick={() => setCat("__fav")}>
              favorites
            </Chip>
          )}
          {cats.map((c) => (
            <Chip key={c} active={active === c} onClick={() => setCat(c)}>
              {c}
            </Chip>
          ))}
        </div>
      )}

      <div className="ggrid">
        <button type="button" className="tile" onClick={() => open(BUILTIN_2048)}>
          <Cover game={BUILTIN_2048} />
          <span className="tile-name">Y2K 2048</span>
          <span className="tile-sub">built-in · offline</span>
        </button>
        {list.map((g) => (
          <button key={g.id} type="button" className="tile" onClick={() => open(g)}>
            <Cover game={g} />
            {g.favorite && (
              <span className="tile-star">
                <Icon name="star-fill" />
              </span>
            )}
            <span className="tile-name">{g.name}</span>
            <span className="tile-sub">{g.url ? (isWebUrl(g.url) ? "web · plays in app" : g.category || "game") : g.category || "game"}</span>
          </button>
        ))}
      </div>

      {web && <GameBrowser title={web.title} url={web.url} onClose={() => setWeb(null)} />}
    </Page>
  );
}

/* ---------------- detail ---------------- */

export function GameView({ id }: { id: string }) {
  const { games, setGames } = useApp();
  const nav = useNav();
  const bump = useLaunch();
  const g = games.find((x) => x.id === id);
  const [confirm, setConfirm] = useState(false);
  const [web, setWeb] = useState<{ title: string; url: string } | null>(null);

  useEffect(() => {
    if (!g) nav.back();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g]);
  if (!g) return null;

  let host = g.url;
  try {
    host = new URL(g.url).host || g.url;
  } catch {
    /* custom scheme — show raw */
  }

  const play = () => {
    bump(g.id);
    if (isWebUrl(g.url)) setWeb({ title: g.name, url: g.url });
    else if (g.url) window.open(g.url, "_blank", "noopener");
  };

  return (
    <Page>
      <section className="gdetail">
        <div className="gdetail-cover">
          <span className="gdetail-halo" />
          <Cover game={g} />
        </div>
        <h2 className="gdetail-name">{g.name}</h2>
        <div className="gdetail-tags">
          {g.category && <span className="tag">{g.category}</span>}
          {g.url && (
            <span className="tag">
              <Icon name="link" /> {host}
            </span>
          )}
          {g.favorite && (
            <span className="tag">
              <Icon name="star-fill" /> favorite
            </span>
          )}
        </div>

        <dl className="stats">
          <div>
            <dt>launched</dt>
            <dd>{g.launches}×</dd>
          </div>
          <div>
            <dt>last played</dt>
            <dd>{ago(g.lastPlayed)}</dd>
          </div>
        </dl>

        {g.url ? (
          <>
            <div className={isWebUrl(g.url) ? "launch is-ready" : "launch"}>
              <span className="launch-ring" />
              <Orb label={`Play ${g.name}`} icon="play" size={100} hot onClick={play} />
            </div>
            <p className="launch-hint">{isWebUrl(g.url) ? "plays fullscreen inside the app" : "opens the link"}</p>
          </>
        ) : (
          <p className="launch-hint">add a link to make it launchable</p>
        )}

        <div className="chero-actions">
          <Orb
            label={g.favorite ? "Remove favorite" : "Favorite"}
            icon={g.favorite ? "star-fill" : "star"}
            size={52}
            on={g.favorite}
            onClick={() => setGames((l) => l.map((x) => (x.id === g.id ? { ...x, favorite: !x.favorite } : x)))}
          />
          <Orb label="Edit game" icon="edit" size={52} onClick={(e) => nav.push({ n: "game-edit", id: g.id }, e.currentTarget)} />
          <Orb label="Delete game" icon="trash" size={52} onClick={() => setConfirm(true)} />
        </div>
      </section>

      {web && <GameBrowser title={web.title} url={web.url} onClose={() => setWeb(null)} />}

      {confirm && (
        <Sheet title={`remove “${g.name}”?`} onClose={() => setConfirm(false)}>
          <p className="sheet-text">It’s removed from your library on this device only.</p>
          <div className="sheet-actions">
            <GlassButton variant="ghost" onClick={() => setConfirm(false)}>
              cancel
            </GlassButton>
            <GlassButton
              variant="danger"
              icon="trash"
              onClick={() => {
                setConfirm(false);
                setGames((l) => l.filter((x) => x.id !== g.id));
              }}
            >
              remove
            </GlassButton>
          </div>
        </Sheet>
      )}
    </Page>
  );
}

/* ---------------- add / edit ---------------- */

export function GameForm({ id }: { id?: string }) {
  const { games, setGames } = useApp();
  const nav = useNav();
  const existing = id ? games.find((g) => g.id === id) : undefined;
  const [name, setName] = useState(existing?.name ?? "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [url, setUrl] = useState(existing?.url ?? "");
  const [cover, setCover] = useState<string | null>(existing?.cover ?? null);
  const file = useRef<HTMLInputElement>(null);

  const cats = useMemo(() => [...new Set([...PRESETS, ...games.map((g) => g.category).filter(Boolean)])], [games]);

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    ui.confirm();
    const data = { name: name.trim(), category: category.trim(), url: normaliseLink(url), cover };
    if (existing) setGames((l) => l.map((g) => (g.id === existing.id ? { ...g, ...data } : g)));
    else setGames((l) => [...l, { id: String(Date.now()), ...data, favorite: false, launches: 0, lastPlayed: null, added: Date.now() }]);
    nav.back();
  };

  return (
    <Page title={existing ? "edit game" : "add game"}>
      <form className="form" onSubmit={save}>
        <div className="cover-pick-row">
          <button type="button" className="cover-pick" onClick={() => file.current?.click()} aria-label="Choose cover image">
            <Cover game={{ cover, name, id: "pick" }} />
            <span className="cover-pick-hint">
              <Icon name="image" /> {cover ? "change" : "add cover"}
            </span>
          </button>
          {cover && (
            <GlassButton variant="ghost" onClick={() => setCover(null)}>
              remove cover
            </GlassButton>
          )}
          <input
            ref={file}
            type="file"
            hidden
            accept="image/*"
            onChange={async (e) => {
              const f = e.target.files?.[0];
              e.target.value = "";
              if (!f) return;
              try {
                setCover(await imageToDataUrl(f, 360, 450));
              } catch {
                /* unreadable */
              }
            }}
          />
        </div>

        <Field label="name" value={name} onChange={setName} maxLength={48} required placeholder="game title" />
        <Field label="category" value={category} onChange={setCategory} maxLength={24} placeholder="optional" />
        <div className="chips" role="group" aria-label="Suggested categories">
          {cats.map((c) => (
            <Chip key={c} active={category === c} onClick={() => setCategory(category === c ? "" : c)}>
              {c}
            </Chip>
          ))}
        </div>
        <Field
          label="game link"
          value={url}
          onChange={setUrl}
          inputMode="url"
          autoCapitalize="none"
          autoCorrect="off"
          placeholder="https:// — web games open fullscreen in the app"
        />
        <p className="picker-note">Web games play inside the app, no browser tab. Some sites block embedding — the app will tell you and offer a tab.</p>

        <div className="form-actions">
          <GlassButton variant="ghost" onClick={() => nav.back()}>
            cancel
          </GlassButton>
          <GlassButton type="submit" variant="primary" icon="check">
            save
          </GlassButton>
        </div>
      </form>
    </Page>
  );
}
