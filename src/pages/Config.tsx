import { useEffect, useState } from "react";
import { Page } from "../components/ui/Page";
import { GlassButton, Segmented, Surface, Switch } from "../components/ui/Controls";
import { GlassSlider } from "../components/ui/Sliders";
import { FolderPicker } from "../components/ui/FolderPicker";
import { Sheet } from "../components/ui/Sheet";
import { useApp } from "../state/app";
import { getMyCode, useConn } from "../utils/social";
import { ui } from "../utils/audio";
import { NOTIFY_HELP, requestNotify, useNotifyState } from "../utils/notify";
import { useNav } from "../state/nav";
import { usePluginRecords } from "../plugins/registry";
import { cx } from "../utils/cx";

/** Label + glass slider in one row. */
function Slider({
  label,
  value,
  min,
  max,
  step,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="aset">
      <span className="switch-label">{label}</span>
      <div className="aset-slider">
        <GlassSlider label={label} value={value} min={min} max={max} step={step} onChange={onChange} />
        <span className="aset-val">{value.toFixed(2)}</span>
      </div>
    </div>
  );
}

export function ConfigView() {
  const { settings, setSettings, player, install, resetAll, clearCache } = useApp();
  const nav = useNav();
  const records = usePluginRecords();
  const [fs, setFs] = useState(() => !!document.fullscreenElement);
  const [confirm, setConfirm] = useState(false);
  const [cacheInfo, setCacheInfo] = useState("…");
  const [notify, setNotify] = useNotifyState();
  const conn = useConn();
  const code = getMyCode();
  const [copied, setCopied] = useState(false);
  const canFs = typeof document.fullscreenEnabled === "boolean" && document.fullscreenEnabled;

  useEffect(() => {
    const on = () => setFs(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", on);
    return () => document.removeEventListener("fullscreenchange", on);
  }, []);

  useEffect(() => {
    (async () => {
      try {
        let bytes = 0;
        const keys = await caches.keys();
        for (const k of keys) {
          const c = await caches.open(k);
          for (const req of await c.keys()) bytes += (await (await c.match(req))?.blob())?.size ?? 0;
        }
        const mb = bytes / (1024 * 1024);
        setCacheInfo(keys.length ? `${mb.toFixed(1)} mb · ${keys.length} cache${keys.length > 1 ? "s" : ""}` : "nothing cached");
      } catch {
        setCacheInfo("unavailable");
      }
    })();
  }, []);

  const toggleFs = async (want: boolean) => {
    try {
      if (want) await document.documentElement.requestFullscreen({ navigationUI: "hide" });
      else if (document.fullscreenElement) await document.exitFullscreen();
    } catch {
      /* denied */
    }
  };

  const copyCode = async () => {
    ui.tap();
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      /* code is on screen anyway */
    }
  };

  return (
    <Page title="config" sub="tuned for this device">
      <Surface title="appearance">
        <Slider label="orb size" value={settings.orbScale} min={0.7} max={1.5} step={0.02} onChange={(v) => setSettings({ orbScale: v })} />
        <Slider label="hub size" value={settings.hubScale} min={0.7} max={1.5} step={0.02} onChange={(v) => setSettings({ hubScale: v })} />
        <Slider label="chain thickness" value={settings.chainScale} min={0.5} max={2} step={0.05} onChange={(v) => setSettings({ chainScale: v })} />
        <Slider label="label size" value={settings.labelScale} min={0.7} max={1.7} step={0.02} onChange={(v) => setSettings({ labelScale: v })} />
        <Slider label="glow strength" value={settings.glow} min={0.3} max={1.8} step={0.05} onChange={(v) => setSettings({ glow: v })} />
        <div className="form-actions">
          <GlassButton
            variant="ghost"
            onClick={() => {
              ui.tap();
              setSettings({ orbScale: 1, hubScale: 1, chainScale: 1, labelScale: 1, glow: 1 });
            }}
          >
            reset appearance
          </GlassButton>
        </div>
      </Surface>

      <Surface title="music folder">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">active directory</span>
            <span className="info-value">{player.folderName || "no folder chosen"}</span>
            <span className="info-label">
              {player.status === "needs-permission"
                ? "access needs to be allowed again"
                : player.library.length
                  ? `${player.library.length} audio files`
                  : "pick the folder that holds your music"}
            </span>
          </div>
        </div>
        <FolderPicker player={player} compact />
      </Surface>

      <Surface title="connect">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">my host code</span>
            <span className="info-value code">{code}</span>
            <span className="info-label">
              {conn === "online" ? "relay connected" : conn === "connecting" ? "connecting…" : conn === "idle" ? "standby" : "relay offline"}
            </span>
          </div>
          <GlassButton variant="ghost" icon={copied ? "check" : "link"} onClick={() => void copyCode()}>
            {copied ? "copied" : "copy"}
          </GlassButton>
        </div>
        <p className="picker-note">Homies link by code over a free public relay — no account, no server of your own. Their last activity survives offline.</p>
      </Surface>

      <Surface title="plugins">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">mods for this hub</span>
            <span className="info-value">
              {records.length} installed · {records.filter((r) => r.enabled).length} active
            </span>
          </div>
          <GlassButton variant="primary" icon="edit" onClick={() => nav.push({ n: "plugins" })}>
            open
          </GlassButton>
        </div>
        <p className="picker-note">Plugins can change anything — colours, tokens, hub orbs, whole pages, new features. Install only what you trust.</p>
      </Surface>

      <Surface title="feedback">
        <Switch label="interface sounds" hint="soft glass taps" on={settings.sounds} onChange={(v) => setSettings({ sounds: v })} />
        <Switch label="haptic taps" hint="where the device supports it" on={settings.haptics} onChange={(v) => setSettings({ haptics: v })} />
      </Surface>

      <Surface title="display">
        <div className="srow-ctl">
          <span className="switch-label">clock</span>
          <Segmented
            label="Clock format"
            options={[
              { value: "12", label: "12h" },
              { value: "24", label: "24h" },
            ]}
            value={settings.hour24 ? "24" : "12"}
            onChange={(v) => setSettings({ hour24: v === "24" })}
          />
        </div>
        <Switch label="ambient light" hint="drifting haze, streaks and dust" on={settings.ambient} onChange={(v) => setSettings({ ambient: v })} />
        <Switch label="depth parallax" hint="layers shift with pointer or tilt" on={settings.parallax} onChange={(v) => setSettings({ parallax: v })} />
        <Switch label="chain movement" hint="slow sway and light pulses" on={settings.chainSway} onChange={(v) => setSettings({ chainSway: v })} />
        <Switch label="cover artwork" hint="album art in lists and players" on={settings.artwork} onChange={(v) => setSettings({ artwork: v })} />
        <Switch label="Y2K low-res mode" hint="scanlines, dither and RGB fringe" on={settings.y2k} onChange={(v) => setSettings({ y2k: v })} />
        <Switch label="reduce motion" hint="calm everything down" on={settings.reduceMotion} onChange={(v) => setSettings({ reduceMotion: v })} />
      </Surface>

      <Surface title="screen">
        <Switch label="keep screen awake while playing" hint="wake lock · saves battery when off" on={settings.keepAwake} onChange={(v) => setSettings({ keepAwake: v })} />
        <Switch label="auto-immersive on first tap" hint="goes fullscreen without a button" on={settings.autoImmersive} onChange={(v) => setSettings({ autoImmersive: v })} />
        <Switch
          label="notifications"
          hint={NOTIFY_HELP[notify]}
          on={notify === "granted" && settings.notify}
          onChange={async (v) => {
            if (!v) {
              setSettings({ notify: false });
              return;
            }
            const r = notify === "default" ? await requestNotify() : notify;
            setNotify(r);
            setSettings({ notify: r === "granted" });
          }}
        />
        {canFs && <Switch label="immersive fullscreen" hint="hides the browser bars" on={fs} onChange={(v) => void toggleFs(v)} />}
        {install ? (
          <div className="stack">
            <p className="sheet-text">Install to run edge-to-edge as its own app, with no browser interface.</p>
            <div>
              <GlassButton variant="primary" icon="download" onClick={() => void install()}>
                install app
              </GlassButton>
            </div>
          </div>
        ) : (
          <p className="sheet-text">For a true fullscreen app, use your browser’s “Add to Home screen / Install app”.</p>
        )}
      </Surface>

      <Surface title="storage">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">offline cache</span>
            <span className="info-value">{cacheInfo}</span>
          </div>
          <GlassButton
            variant="ghost"
            icon="trash"
            onClick={async () => {
              ui.tap();
              await clearCache();
              location.reload();
            }}
          >
            clear
          </GlassButton>
        </div>
        <p className="picker-note">The app shell and artwork are cached so it opens offline. Your music is never copied or uploaded.</p>
        <p className="sheet-text">Profile, lists, settings and the music-folder link live only on this device.</p>
        <div>
          <GlassButton variant="danger" icon="trash" onClick={() => setConfirm(true)}>
            reset everything
          </GlassButton>
        </div>
      </Surface>

      {confirm && (
        <Sheet title="reset everything?" onClose={() => setConfirm(false)}>
          <p className="sheet-text">This clears your profile, games, homies, playlists, settings and the saved music folder on this device.</p>
          <div className="sheet-actions">
            <GlassButton variant="ghost" onClick={() => setConfirm(false)}>
              cancel
            </GlassButton>
            <GlassButton
              variant="danger"
              icon="trash"
              onClick={() => {
                setConfirm(false);
                resetAll();
              }}
            >
              reset
            </GlassButton>
          </div>
        </Sheet>
      )}

      <div className="config-foot">
        <span>the gadget</span>
        <button type="button" className={cx("btn", "btn--ghost")} onClick={() => nav.push({ n: "account" })}>
          <span>edit profile</span>
        </button>
      </div>
    </Page>
  );
}
