import { useEffect, useState, type MouseEvent } from "react";
import { Mascot } from "../Mascot";
import { useApp } from "../../state/app";
import { useNav } from "../../state/nav";
import { cx } from "../../utils/cx";
import { ui } from "../../utils/audio";

function useClock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    let t = 0;
    const tick = () => {
      const d = new Date();
      setNow(d);
      // wake exactly on the next minute — one render a minute, never per second
      t = window.setTimeout(tick, 60000 - (d.getSeconds() * 1000 + d.getMilliseconds()) + 30);
    };
    tick();
    const vis = () => document.visibilityState === "visible" && setNow(new Date());
    document.addEventListener("visibilitychange", vis);
    return () => {
      window.clearTimeout(t);
      document.removeEventListener("visibilitychange", vis);
    };
  }, []);
  return now;
}

export function formatClock(d: Date, hour24: boolean) {
  const m = d.getMinutes().toString().padStart(2, "0");
  if (hour24) return `${d.getHours().toString().padStart(2, "0")}:${m}`;
  return `${d.getHours() % 12 || 12}:${m}${d.getHours() >= 12 ? "PM" : "AM"}`;
}

/** Persistent chrome: the glowing up-arrow (back one level) and the status pill. */
export function TopBar() {
  const { profile, settings } = useApp();
  const nav = useNav();
  const now = useClock();
  const [bump, setBump] = useState(false);
  const name = profile.name.trim();

  const onUp = () => {
    ui.close();
    if (nav.stack.length) nav.back();
    else {
      setBump(true);
      window.setTimeout(() => setBump(false), 460);
    }
  };

  const onPill = (e: MouseEvent<HTMLButtonElement>) => {
    ui.tap();
    if (nav.route?.n !== "account") nav.push({ n: "account" }, e.currentTarget);
  };

  return (
    <header className="chrome">
      <button type="button" className={cx("up", bump && "is-bump")} onClick={onUp} aria-label={nav.stack.length ? "Back" : "The Gadget"}>
        <svg viewBox="0 0 72 58" aria-hidden="true" focusable="false">
          <defs>
            <linearGradient id="up-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#9a9a98" />
              <stop offset="0.45" stopColor="#4b4b4a" />
              <stop offset="1" stopColor="#161616" />
            </linearGradient>
          </defs>
          <path d="M36 4 69 54H3Z" fill="url(#up-fill)" stroke="#f1f1ee" strokeWidth="3.4" strokeLinejoin="round" />
          <path d="M36 14 59 49H13Z" fill="none" stroke="#fff" strokeOpacity="0.25" strokeWidth="1.4" strokeLinejoin="round" />
          <path d="M36 9 22 31" stroke="#fff" strokeOpacity="0.55" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>

      <button
        type="button"
        className="pill"
        onClick={onPill}
        onPointerEnter={(e) => e.pointerType === "mouse" && ui.hover()}
        aria-label={`${name || "Set your name"}, ${formatClock(now, settings.hour24)}. Open account.`}
      >
        <span className="pill-avatar" aria-hidden="true">
          {profile.avatar ? (
            <span className="pill-img glossy">
              <img src={profile.avatar} alt="" draggable={false} />
            </span>
          ) : (
            <Mascot bare className="pill-face" />
          )}
        </span>
        <span className={cx("sdot", `sdot--${profile.status}`)} aria-hidden="true" />
        <span className={cx("pill-name", !name && "is-empty")}>{name || "set your name"}</span>
        <time className="pill-time" dateTime={now.toISOString()}>
          {formatClock(now, settings.hour24)}
        </time>
      </button>
    </header>
  );
}
