import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

export type Route =
  | { n: "music" }
  | { n: "albums" }
  | { n: "album"; id: string }
  | { n: "songs" }
  | { n: "playlists" }
  | { n: "playlist"; id: string }
  | { n: "search" }
  | { n: "now" }
  | { n: "games" }
  | { n: "game"; id: string }
  | { n: "game-edit"; id?: string }
  | { n: "g2048" }
  | { n: "homies" }
  | { n: "homie"; id: string }
  | { n: "homie-edit"; id?: string }
  | { n: "chat"; id: string }
  | { n: "account" }
  | { n: "config" }
  | { n: "plugins" }
  | { n: "plugin-page"; id: string };

export const routeKey = (r: Route) => r.n + ("id" in r ? `:${r.id ?? ""}` : "");

export interface Origin {
  x: number;
  y: number;
  r: number;
}

interface Nav {
  stack: Route[];
  route: Route | null;
  dir: "fwd" | "back";
  origin: Origin;
  push: (r: Route, from?: Element | null) => void;
  back: () => void;
  home: () => void;
}

const NavCtx = createContext<Nav>(null as unknown as Nav);
export const useNav = () => useContext(NavCtx);

/**
 * Navigation is a real stack mirrored into the History API, so the device back gesture
 * (and browser back) always steps exactly one level up: Home → Music → Album → Now Playing.
 */
export function NavProvider({ children }: { children: ReactNode }) {
  const [stack, setStack] = useState<Route[]>([]);
  const [dir, setDir] = useState<"fwd" | "back">("fwd");
  const [origin, setOrigin] = useState<Origin>(() => ({ x: window.innerWidth / 2, y: window.innerHeight / 2, r: 40 }));
  const stackRef = useRef(stack);
  stackRef.current = stack;

  useEffect(() => {
    history.replaceState({ stack: [] }, "");
    const onPop = (e: PopStateEvent) => {
      const next: Route[] = Array.isArray(e.state?.stack) ? e.state.stack : [];
      setDir(next.length < stackRef.current.length ? "back" : "fwd");
      stackRef.current = next;
      setStack(next);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && stackRef.current.length && !(e.target instanceof HTMLInputElement)) history.back();
    };
    window.addEventListener("popstate", onPop);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("popstate", onPop);
      window.removeEventListener("keydown", onKey);
    };
  }, []);

  const push = useCallback((route: Route, from?: Element | null) => {
    // The radial open/close always anchors to the hub node the user started from.
    if (from && stackRef.current.length === 0) {
      const r = from.getBoundingClientRect();
      setOrigin({ x: r.left + r.width / 2, y: r.top + r.height / 2, r: r.width / 2 });
    }
    const next = [...stackRef.current, route];
    stackRef.current = next;
    history.pushState({ stack: next }, "");
    setDir("fwd");
    setStack(next);
  }, []);

  const back = useCallback(() => {
    if (stackRef.current.length) history.back();
  }, []);

  const home = useCallback(() => {
    const n = stackRef.current.length;
    if (n) history.go(-n);
  }, []);

  const value = useMemo<Nav>(
    () => ({ stack, route: stack[stack.length - 1] ?? null, dir, origin, push, back, home }),
    [stack, dir, origin, push, back, home],
  );

  return <NavCtx.Provider value={value}>{children}</NavCtx.Provider>;
}
