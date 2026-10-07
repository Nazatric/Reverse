import { useRef, useState, type CSSProperties } from "react";
import { Mascot } from "../Mascot";
import { Chains } from "./Chains";
import { Wireframe } from "./Wireframe";
import { NodeGlyph } from "./Glyphs";
import { HUB, NODES } from "./nodes";
import { useApp } from "../../state/app";
import { useNav } from "../../state/nav";
import { cx } from "../../utils/cx";
import { ui } from "../../utils/audio";
import { useActivePlugins } from "../../plugins/registry";

/**
 * The home composition: the chrome mascot, the wireframe, the chains and the five
 * glass orbs, plus any orbs added by installed plugins. Positions come from the
 * original artwork; plugin orbs are laid out on the same stage.
 */
export function Hub({ away }: { away: boolean }) {
  const { player, profile } = useApp();
  const nav = useNav();
  const plugins = useActivePlugins();
  const [pressed, setPressed] = useState(false);
  const [opening, setOpening] = useState<string | null>(null);
  const holdTimer = useRef(0);
  const held = useRef(false);

  const openPlayer = (el: Element | null) => {
    ui.open();
    nav.push(player.current ? { n: "now" } : { n: "music" }, el);
  };

  const onHubDown = () => {
    held.current = false;
    holdTimer.current = window.setTimeout(() => {
      held.current = true;
      if (player.library.length) {
        player.toggle();
        setPressed(true);
        window.setTimeout(() => setPressed(false), 420);
      }
    }, 480);
  };
  const onHubUp = () => window.clearTimeout(holdTimer.current);

  const openNode = (id: string, el: Element) => {
    ui.open();
    setOpening(id);
    window.setTimeout(() => setOpening(null), 700);
    nav.push({ n: id } as never, el);
  };

  return (
    <div className="hub" data-away={away}>
      <div className="hub-zoom" style={{ transformOrigin: `${nav.origin.x}px ${nav.origin.y}px` }}>
        <div className="frame">
          <div className="layer" data-depth="10">
            <Wireframe />
          </div>
          <div className="layer" data-depth="13">
            <Chains />
          </div>
          <div className="layer" data-depth="17">
            <button
              type="button"
              className={cx("hub-mascot", pressed && "is-pressed", player.playing && "is-playing")}
              style={{ left: `${HUB.x}%`, top: `${HUB.y}%`, "--d": HUB.d } as CSSProperties}
              onClick={(e) => {
                if (held.current) {
                  held.current = false;
                  return;
                }
                openPlayer(e.currentTarget);
              }}
              onPointerDown={onHubDown}
              onPointerUp={onHubUp}
              onPointerCancel={onHubUp}
              onPointerLeave={onHubUp}
              aria-label={player.library.length ? "Open player · hold to play or pause" : "Open music"}
            >
              <span className="hub-halo" />
              <Mascot />
            </button>

            {NODES.map((n, i) => (
              <button
                key={n.id}
                type="button"
                className={cx("node", opening === n.id && "is-opening")}
                style={{ left: `${n.x}%`, top: `${n.y}%`, "--d": n.d, "--ly": n.ly, "--i": i } as CSSProperties}
                aria-label={n.label}
                onPointerEnter={(e) => e.pointerType === "mouse" && ui.hover()}
                onClick={(e) => openNode(n.id, e.currentTarget)}
              >
                <span className="node-halo" />
                <span className="node-body">
                  <NodeGlyph id={n.id} avatar={profile.avatar} />
                </span>
                <span className="node-label" aria-hidden="true">
                  {n.label}
                </span>
              </button>
            ))}

            {plugins.nodes.flatMap((plugin, pIndex) =>
              plugin.nodes.map((node, nIndex) => {
                const id = `plugin:${plugin.id}:${node.id}`;
                const page = node.page ?? plugin.pages[0]?.id;
                return (
                  <button
                    key={id}
                    type="button"
                    className={cx("node", "node--plugin", opening === id && "is-opening")}
                    style={
                      {
                        left: `${node.x}%`,
                        top: `${node.y}%`,
                        "--d": node.d,
                        "--ly": node.ly ?? 14,
                        "--i": NODES.length + pIndex * 3 + nIndex,
                      } as CSSProperties
                    }
                    aria-label={node.label}
                    onPointerEnter={(e) => e.pointerType === "mouse" && ui.hover()}
                    onClick={(e) => {
                      if (!page) return;
                      ui.open();
                      setOpening(id);
                      window.setTimeout(() => setOpening(null), 700);
                      nav.push(page.startsWith("plugin:") ? ({ n: "plugin-page", id: page } as never) : ({ n: page } as never), e.currentTarget);
                    }}
                  >
                    <span className="node-halo" />
                    <span className="node-body">
                      <NodeGlyph id={node.icon ?? "star"} />
                    </span>
                    <span className="node-label" aria-hidden="true">
                      {node.label}
                    </span>
                  </button>
                );
              }),
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
