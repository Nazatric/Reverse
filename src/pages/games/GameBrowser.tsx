import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "../../components/ui/Icons";
import { ui } from "../../utils/audio";
import { setActiveGame } from "../../utils/social";

/**
 * In-app browser for web games and links: the URL loads inside the app (no tab, no browser
 * chrome) and the whole screen goes fullscreen while it's open.
 */
export function GameBrowser({ title, url, onClose }: { title: string; url: string; onClose: () => void }) {
  const root = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);
  let host = url;
  try {
    host = new URL(url).host;
  } catch {
    /* keep raw */
  }

  useEffect(() => {
    setActiveGame(title);
    // User activation is still warm — go fullscreen before the frame settles.
    const raf = requestAnimationFrame(() => {
      root.current?.requestFullscreen?.({ navigationUI: "hide" } as FullscreenOptions).catch(() => undefined);
    });
    const onFs = () => setLoaded(true);
    document.addEventListener("fullscreenchange", onFs);

    // Cross-origin frames can't be inspected, so treat "still blank after 6s" as blocked.
    const timer = window.setTimeout(() => setFailed(true), 6000);

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !document.fullscreenElement) closeRef.current();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      cancelAnimationFrame(raf);
      window.clearTimeout(timer);
      window.removeEventListener("keydown", onKey);
      document.removeEventListener("fullscreenchange", onFs);
      setActiveGame(null);
      if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined);
    };
  }, [title, url]);

  return createPortal(
    <div className="webview" ref={root}>
      <div className="webview-bar">
        <button type="button" className="webview-title" onClick={() => { ui.close(); onClose(); }} aria-label="Close">
          <Icon name="close" />
        </button>
        <span className="webview-name">
          {title}
          <span className="webview-host">{host}</span>
        </span>
        <a className="webview-ext" href={url} target="_blank" rel="noopener noreferrer" aria-label="Open in browser">
          <Icon name="external" />
        </a>
      </div>
      <div className="webview-body">
        {!loaded && (
          <div className="webview-loading">
            <span className="webview-spinner" />
            <span>connecting to {host}…</span>
          </div>
        )}
        {failed && (
          <div className="webview-note">
            <span>If it stays blank, this site blocks embedding.</span>
            <a href={url} target="_blank" rel="noopener noreferrer">
              open in a new tab
            </a>
          </div>
        )}
        <iframe
          src={url}
          title={title}
          allow="autoplay; fullscreen; gamepad; accelerometer; gyroscope; clipboard-write"
          referrerPolicy="no-referrer"
          onLoad={() => setLoaded(true)}
        />
      </div>
    </div>,
    document.body,
  );
}
