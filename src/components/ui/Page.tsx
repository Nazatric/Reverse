import type { ReactNode } from "react";
import { cx } from "../../utils/cx";

interface PageProps {
  title?: string;
  sub?: string;
  actions?: ReactNode;
  children: ReactNode;
  /** content manages its own scrolling (virtual lists) */
  fill?: boolean;
  /** hide the heading row (immersive pages like Now Playing) */
  bare?: boolean;
}

/** Shared page frame: sits under the persistent chrome, display-type heading, scroll body. */
export function Page({ title, sub, actions, children, fill, bare }: PageProps) {
  return (
    <div className={cx("page", fill && "page--fill", bare && "page--bare")}>
      {!bare && title && (
        <header className="page-head">
          <div className="page-headtext">
            <h1 className="page-title">{title}</h1>
            {sub && <p className="page-sub">{sub}</p>}
          </div>
          {actions && <div className="page-actions">{actions}</div>}
        </header>
      )}
      {fill ? (
        <div className="page-fill">{children}</div>
      ) : (
        <div className="page-scroll">
          <div className="page-in">{children}</div>
        </div>
      )}
    </div>
  );
}
