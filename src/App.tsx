import { useEffect, useMemo, useRef, useState } from "react";
import { AppProvider, useApp } from "./state/app";
import { NavProvider, useNav } from "./state/nav";
import { Backdrop } from "./components/hub/Backdrop";
import { Hub } from "./components/hub/Hub";
import { TopBar } from "./components/hub/TopBar";
import { Onboarding } from "./components/Onboarding";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { MiniPlayer } from "./components/ui/MiniPlayer";
import { PageHost } from "./pages/PageHost";
import { useParallax } from "./hooks/useParallax";
import { ensureConnection } from "./utils/social";
import { ui } from "./utils/audio";
import {
  alreadyAsked,
  markAsked,
  notifyState,
  requestNotify,
  showMediaNotification,
  NOTIFY_HELP,
  useNotifyState,
  type NotifyState,
} from "./utils/notify";
import { readStored, writeStored } from "./utils/storage";
import { getMeta } from "./utils/metaStore";
import { bootPlugins, useActivePlugins } from "./plugins/registry";

const goImmersive = () => {
  if (document.fullscreenElement) return;
  document.documentElement.requestFullscreen?.({ navigationUI: "hide" } as FullscreenOptions).catch(() => undefined);
};

function Shell() {
  const { route } = useNav();
  const { settings, setSettings, setProfile, homies, player } = useApp();
  const scene = useRef<HTMLDivElement>(null);
  const away = route !== null;
  const [notifyAsk, setNotifyAsk] = useState(false);
  const [notifyResult, setNotifyResult] = useState<NotifyState | null>(null);
  const [notify, setNotify] = useNotifyState();
  const [onboarded, setOnboarded] = useState(() => readStored("onboarded", false));
  const [boot, setBoot] = useState(() => readStored("onboarded", false) && !settings.reduceMotion);

  useParallax(scene, settings.parallax && !settings.reduceMotion && !away);

  /* Boot: rings expand out of the hub, then everything settles. Runs once per session. */
  useEffect(() => {
    if (!boot) return;
    document.documentElement.dataset.boot = "on";
    const t = window.setTimeout(() => {
      document.documentElement.dataset.boot = "off";
      setBoot(false);
    }, 1500);
    return () => window.clearTimeout(t);
  }, [boot]);

  /* Restore installed plugins, then apply their theme tokens to the document. */
  const plugins = useActivePlugins();

  useEffect(() => {
    bootPlugins();
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    const theme = plugins.theme;
    if (theme.accent) root.style.setProperty("--accent", theme.accent);
    if (theme.orb) {
      root.style.setProperty("--orb-1", theme.orb[0]);
      root.style.setProperty("--orb-2", theme.orb[1]);
      root.style.setProperty("--orb-3", theme.orb[2]);
      root.style.setProperty("--orb-4", theme.orb[3]);
    } else {
      root.style.removeProperty("--orb-1");
      root.style.removeProperty("--orb-2");
      root.style.removeProperty("--orb-3");
      root.style.removeProperty("--orb-4");
    }
    if (theme.orbScale) root.style.setProperty("--orb-scale", String(theme.orbScale));
    else root.style.setProperty("--orb-scale", String(settings.orbScale));
    if (theme.hubScale) root.style.setProperty("--hub-scale", String(theme.hubScale));
    else root.style.setProperty("--hub-scale", String(settings.hubScale));
    if (theme.chainScale) root.style.setProperty("--chain-scale", String(theme.chainScale));
    else root.style.setProperty("--chain-scale", String(settings.chainScale));
    if (theme.labelScale) root.style.setProperty("--label-scale", String(theme.labelScale));
    else root.style.setProperty("--label-scale", String(settings.labelScale));
    if (theme.glow) root.style.setProperty("--glow", String(theme.glow));
    else root.style.setProperty("--glow", "1");
  }, [plugins, settings.orbScale, settings.hubScale, settings.chainScale, settings.labelScale]);


  /* --- first tap: unlock audio, go immersive, offer notifications once --- */
  const tapped = useRef(false);
  useEffect(() => {
    if (!onboarded) return;
    const firstTap = () => {
      if (tapped.current) return;
      tapped.current = true;
      ui.unlock();
      const standalone =
        window.matchMedia("(display-mode: standalone)").matches ||
        (navigator as unknown as { standalone?: boolean }).standalone === true;
      if (settings.autoImmersive && !standalone) goImmersive();
      if (notifyState() === "default" && !alreadyAsked()) window.setTimeout(() => setNotifyAsk(true), 1400);
    };
    window.addEventListener("pointerdown", firstTap, { passive: true, capture: true });
    return () => window.removeEventListener("pointerdown", firstTap);
  }, [onboarded, settings.autoImmersive]);

  /* --- lock-screen media notification, through the service worker --- */
  useEffect(() => {
    if (!settings.notify || notify !== "granted") return;
    const t = player.current;
    if (!t) return;
    const meta = getMeta(t.id);
    void showMediaNotification({
      title: meta?.title || t.title,
      sub: `${meta?.artist || t.folder.split("/").pop() || ""} · ${player.playing ? "playing" : "paused"}`,
      playing: player.playing,
    });
  }, [settings.notify, notify, player.current, player.playing]);

  useEffect(() => {
    const onMessage = (e: MessageEvent) => {
      const d = e.data as { type?: string; action?: string } | null;
      if (d?.type !== "media") return;
      if (d.action === "toggle") player.toggle();
      if (d.action === "next") player.next();
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [player]);

  /* --- social relay for linked homies --- */
  const linkedCodes = useMemo(() => homies.filter((h) => h.kind === "linked" && h.code).map((h) => h.code as string), [homies]);
  useEffect(() => {
    if (onboarded) void ensureConnection(linkedCodes);
  }, [linkedCodes, onboarded]);

  const allowNotify = async () => {
    ui.tap();
    setNotifyAsk(false);
    const r = await requestNotify();
    setNotify(r);
    setSettings({ notify: r === "granted" });
    setNotifyResult(r);
    window.setTimeout(() => setNotifyResult(null), 3400);
    if (settings.autoImmersive) goImmersive();
  };

  const laterNotify = () => {
    ui.tap();
    setNotifyAsk(false);
    markAsked();
  };

  const startHub = (name: string) => {
    setProfile({ name });
    writeStored("onboarded", true);
    setOnboarded(true);
    ui.unlock();
    if (settings.autoImmersive) goImmersive(); // synchronous with the tap
    if (notifyState() === "default") window.setTimeout(() => setNotifyAsk(true), 1600);
  };

  return (
    <div className="scene" ref={scene}>
      <Backdrop />

      {boot && (
        <div className="boot" aria-hidden="true">
          <span className="boot-ring" />
          <span className="boot-ring boot-ring--b" />
          <span className="boot-flash" />
        </div>
      )}

      <Hub away={away} />
      <PageHost />
      <MiniPlayer />
      <TopBar />
      <div className="y2k-fx" aria-hidden="true" />

      {onboarded && notifyAsk && !away && (
        <div className="toast" role="dialog" aria-label="Notifications">
          <span className="toast-text">Allow notifications so your music shows on the lock screen with controls.</span>
          <div className="toast-actions">
            <button type="button" className="btn btn--ghost" onClick={laterNotify}>
              <span>not now</span>
            </button>
            <button type="button" className="btn btn--primary" onClick={() => void allowNotify()}>
              <span>allow</span>
            </button>
          </div>
        </div>
      )}

      {notifyResult && (
        <button
          type="button"
          className="toast toast--mini"
          role="status"
          onClick={() => {
            if (settings.autoImmersive) goImmersive();
            setNotifyResult(null);
          }}
        >
          <span className="toast-text">
            {notifyResult === "granted"
              ? NOTIFY_HELP.granted
              : notifyResult === "denied"
                ? NOTIFY_HELP.denied
                : notifyResult === "needs-install"
                  ? NOTIFY_HELP["needs-install"]
                  : NOTIFY_HELP.unsupported}
          </span>
        </button>
      )}

      {!onboarded && <Onboarding onStart={startHub} />}
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <AppProvider>
        <NavProvider>
          <Shell />
        </NavProvider>
      </AppProvider>
    </ErrorBoundary>
  );
}
