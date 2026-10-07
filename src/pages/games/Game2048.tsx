import { useCallback, useEffect, useRef, useState } from "react";
import { Orb } from "../../components/ui/Controls";
import { Page } from "../../components/ui/Page";
import { ui } from "../../utils/audio";
import { readStored, writeStored } from "../../utils/storage";
import { setActiveGame } from "../../utils/social";

/** Built-in Y2K 2048 — the free time-killer shipped inside the app. Pure state + CSS transforms. */

interface Tile {
  id: number;
  v: number;
  r: number;
  c: number;
  pop?: boolean;
}
type Dir = "up" | "down" | "left" | "right";

const BEST_KEY = "best2048";
let uid = 1;

function spawn(tiles: Tile[]): Tile[] {
  const taken = new Set(tiles.map((t) => t.r * 4 + t.c));
  const free: number[] = [];
  for (let i = 0; i < 16; i++) if (!taken.has(i)) free.push(i);
  if (!free.length) return tiles;
  const cell = free[Math.floor(Math.random() * free.length)];
  return [...tiles, { id: uid++, v: Math.random() < 0.9 ? 2 : 4, r: Math.floor(cell / 4), c: cell % 4, pop: true }];
}

function fresh(): Tile[] {
  return spawn(spawn([]));
}

/* Explicit per-direction move over the 4×4 grid. */
function move(tiles: Tile[], dir: Dir): { tiles: Tile[]; gained: number; moved: boolean } {
  const grid: (Tile | null)[][] = Array.from({ length: 4 }, () => Array(4).fill(null));
  tiles.forEach((t) => (grid[t.r][t.c] = t));
  let gained = 0;
  let moved = false;
  const result: (Tile | null)[][] = Array.from({ length: 4 }, () => Array(4).fill(null));

  const lines: Array<{ line: (Tile | null)[]; put: (t: Tile, i: number) => [number, number] }> = [];
  for (let k = 0; k < 4; k++) {
    // up/down walk a column (c = k); left/right walk a row (r = k)
    const row = (i: number) => (dir === "up" || dir === "down" ? [i, k] : [k, i]) as [number, number];
    const order = [0, 1, 2, 3].sort((a, b) => {
      if (dir === "left" || dir === "up") return a - b;
      return b - a;
    });
    lines.push({
      line: order.map((i) => {
        const [r, c] = row(i);
        return grid[r][c];
      }),
      put: (_t, i) => {
        const [r, c] = row(i);
        return [r, c];
      },
    });
  }

  for (const { line, put } of lines) {
    const list = line.filter(Boolean) as Tile[];
    let slot = 0;
    for (let i = 0; i < list.length; i++) {
      if (i + 1 < list.length && list[i].v === list[i + 1].v) {
        const [r, c] = put(list[i], slot);
        result[r][c] = { id: uid++, v: list[i].v * 2, r, c, pop: true };
        gained += list[i].v * 2;
        moved = true;
        i++;
      } else {
        const [r, c] = put(list[i], slot);
        const src = list[i];
        if (src.r !== r || src.c !== c) moved = true;
        result[r][c] = { ...src, r, c };
      }
      slot++;
    }
  }
  const out: Tile[] = [];
  for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) if (result[r][c]) out.push(result[r][c]!);
  return { tiles: out, gained, moved };
}

const TONE: Record<number, [string, string]> = {
  2: ["#4b4c4a", "#e9e9e6"],
  4: ["#5a5b58", "#f2f2ef"],
  8: ["#6f7069", "#f6f6f2"],
  16: ["#7d7e76", "#ffffff"],
  32: ["#9d9ea6", "#ffffff"],
  64: ["#b8b9c2", "#ffffff"],
  128: ["#d4d5dd", "#ffffff"],
  256: ["#ffffff", "#101010"],
  512: ["#ffffff", "#0a0a0a"],
  1024: ["#ffffff", "#050505"],
  2048: ["#ffffff", "#050505"],
};

export function Game2048() {
  const [tiles, setTiles] = useState<Tile[]>(fresh);
  const [score, setScore] = useState(0);
  const [best, setBest] = useState(() => readStored(BEST_KEY, 0));
  const [phase, setPhase] = useState<"play" | "over" | "won">("play");
  const touch = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    setActiveGame("Y2K 2048");
    return () => setActiveGame(null);
  }, []);

  const step = useCallback(
    (dir: Dir) => {
      if (phase !== "play") return;
      const { tiles: movedTiles, gained, moved } = move(tiles, dir);
      if (!moved) return;
      ui.tap();
      setTiles(movedTiles);
      setScore((s) => {
        const n = s + gained;
        if (n > best) {
          setBest(n);
          writeStored(BEST_KEY, n);
        }
        return n;
      });
      // phase 2 — after the slide animation: spawn a new tile, check win/lose
      window.setTimeout(() => {
        setTiles((prev) => {
          const next = spawn([...prev]);
          if (next.some((t) => t.v >= 2048)) setPhase("won");
          else {
            let stuck = true;
            for (const d of ["up", "down", "left", "right"] as Dir[]) {
              if (move(next, d).moved) {
                stuck = false;
                break;
              }
            }
            if (stuck) setPhase("over");
          }
          return next;
        });
      }, 120);
    },
    [tiles, phase, best],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const map: Record<string, Dir> = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right", w: "up", s: "down", a: "left", d: "right" };
      const d = map[e.key];
      if (d) {
        e.preventDefault();
        step(d);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [step]);

  const restart = () => {
    ui.confirm();
    setTiles(fresh());
    setScore(0);
    setPhase("play");
  };

  const onTouchStart = (e: React.TouchEvent) => {
    const t = e.touches[0];
    touch.current = { x: t.clientX, y: t.clientY };
  };
  const onTouchEnd = (e: React.TouchEvent) => {
    if (!touch.current) return;
    const t = e.changedTouches[0];
    const dx = t.clientX - touch.current.x;
    const dy = t.clientY - touch.current.y;
    touch.current = null;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 28) return;
    if (Math.abs(dx) > Math.abs(dy)) step(dx > 0 ? "right" : "left");
    else step(dy > 0 ? "down" : "up");
  };

  return (
    <Page title="2048" sub="built-in · swipe or arrow keys">
      <div className="g2048">
        <div className="g2048-scores">
          <div className="g2048-score">
            <span>score</span>
            <b>{score}</b>
          </div>
          <div className="g2048-score">
            <span>best</span>
            <b>{best}</b>
          </div>
        </div>

        <div
          className="board"
          onTouchStart={onTouchStart}
          onTouchEnd={onTouchEnd}
          role="application"
          aria-label="2048 board"
        >
          <div className="board-cells" aria-hidden="true">
            {Array.from({ length: 16 }, (_, i) => (
              <span key={i} style={{ transform: `translate(${(i % 4) * 100}%, ${Math.floor(i / 4) * 100}%)` }} />
            ))}
          </div>
          {tiles.map((t) => {
            const [tone, ink] = TONE[t.v] ?? TONE[2048];
            return (
              <div
                key={t.id}
                className={`t48${t.pop ? " is-pop" : ""}${t.v >= 256 ? " is-hot" : ""}`}
                style={{ transform: `translate(${t.c * 100}%, ${t.r * 100}%)` }}
              >
                <span
                  className="t48-face"
                  style={{ background: `radial-gradient(circle at 50% 22%, ${tone}, ${tone} 55%, #0d0d0d 160%)`, color: ink }}
                >
                  {t.v}
                </span>
              </div>
            );
          })}

          {phase !== "play" && (
            <div className="board-overlay">
              <b>{phase === "won" ? "2048 · you win" : "no moves left"}</b>
              <span className="board-actions">
                <Orb label="Play again" icon="repeat" size={54} hot onClick={restart} />
              </span>
            </div>
          )}
        </div>

        <div className="g2048-foot">
          <Orb label="New game" icon="repeat" size={46} onClick={restart} />
          <span className="g2048-hint">merge the tiles to 2048</span>
        </div>
      </div>
    </Page>
  );
}
