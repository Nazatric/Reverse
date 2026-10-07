import { useCallback, useEffect, useMemo, useRef, useState, type MutableRefObject, type ReactNode } from "react";

export interface VItem {
  key: string;
  h: number;
}

export interface VirtualApi {
  scrollToIndex: (i: number) => void;
}

interface Props<T extends VItem> {
  items: T[];
  render: (item: T, index: number) => ReactNode;
  apiRef?: MutableRefObject<VirtualApi | null>;
  className?: string;
  overscan?: number;
  padBottom?: number;
}

/**
 * Fixed-height-per-item windowing: only the rows near the viewport exist in the DOM,
 * so a 10,000-song library scrolls as smoothly as a 10-song one.
 */
export function VirtualList<T extends VItem>({ items, render, apiRef, className, overscan = 8, padBottom = 0 }: Props<T>) {
  const scroller = useRef<HTMLDivElement>(null);
  const raf = useRef(0);
  const [range, setRange] = useState({ s: 0, e: 40 });

  const offsets = useMemo(() => {
    const o = new Array<number>(items.length + 1);
    let y = 0;
    for (let i = 0; i < items.length; i++) {
      o[i] = y;
      y += items[i].h;
    }
    o[items.length] = y;
    return o;
  }, [items]);
  const total = offsets[items.length] ?? 0;

  const calc = useCallback(() => {
    const el = scroller.current;
    if (!el) return;
    const top = el.scrollTop;
    const bottom = top + el.clientHeight;
    let lo = 0;
    let hi = items.length;
    while (lo < hi) {
      const m = (lo + hi) >> 1;
      if (offsets[m + 1] <= top) lo = m + 1;
      else hi = m;
    }
    let e = lo;
    while (e < items.length && offsets[e] < bottom) e++;
    const s = Math.max(0, lo - overscan);
    e = Math.min(items.length, e + overscan);
    setRange((r) => (r.s === s && r.e === e ? r : { s, e }));
  }, [items.length, offsets, overscan]);

  useEffect(() => {
    calc();
    const on = () => calc();
    window.addEventListener("resize", on, { passive: true });
    return () => window.removeEventListener("resize", on);
  }, [calc]);

  useEffect(() => {
    if (!apiRef) return;
    apiRef.current = {
      scrollToIndex: (i) => scroller.current?.scrollTo({ top: offsets[Math.max(0, Math.min(i, items.length - 1))] ?? 0, behavior: "auto" }),
    };
    return () => {
      apiRef.current = null;
    };
  }, [apiRef, offsets, items.length]);

  return (
    <div
      ref={scroller}
      className={className}
      onScroll={() => {
        if (!raf.current) {
          raf.current = requestAnimationFrame(() => {
            raf.current = 0;
            calc();
          });
        }
      }}
    >
      <div style={{ height: total + padBottom, position: "relative" }}>
        {items.slice(range.s, range.e).map((it, k) => (
          <div key={it.key} style={{ position: "absolute", top: offsets[range.s + k], left: 0, right: 0, height: it.h }}>
            {render(it, range.s + k)}
          </div>
        ))}
      </div>
    </div>
  );
}
