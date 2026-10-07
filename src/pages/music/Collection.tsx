import type { ReactNode } from "react";
import { Art } from "../../components/ui/Art";
import type { Track } from "../../utils/musicLibrary";

/** Shared hero for an album or playlist: big glass art orb, display title, action orbs. */
export function CollectionHero({ track, title, sub, children }: { track?: Track; title: string; sub: string; children: ReactNode }) {
  return (
    <section className="chero">
      <div className="chero-orb">
        <span className="chero-halo" />
        <span className="chero-art">
          <Art track={track} />
        </span>
      </div>
      <h2 className="chero-title">{title}</h2>
      <p className="chero-sub">{sub}</p>
      <div className="chero-actions">{children}</div>
    </section>
  );
}
