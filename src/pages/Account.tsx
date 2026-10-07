import { useRef, useState } from "react";
import { Page } from "../components/ui/Page";
import { Field, GlassButton, Segmented, Surface } from "../components/ui/Controls";
import { Icon } from "../components/ui/Icons";
import { Mascot } from "../components/Mascot";
import { useApp } from "../state/app";
import { imageToDataUrl } from "../utils/image";
import { getMyCode } from "../utils/social";
import { fmtDuration } from "../utils/stats";
import { ui } from "../utils/audio";

/** Profile edits apply instantly — the status pill updates as you type, and homies see the same. */
export function AccountView() {
  const { profile, setProfile, player, playlists, games, homies, listenSec, topSongs, gamesPlayed } = useApp();
  const code = getMyCode();
  const file = useRef<HTMLInputElement>(null);
  const [copied, setCopied] = useState(false);

  const stats: [string, string][] = [
    [String(player.library.length), "songs"],
    [String(playlists.length), "playlists"],
    [String(games.length + 1), "games"],
    [String(homies.length), "homies"],
    [fmtDuration(listenSec), "listened"],
    [`${gamesPlayed}×`, "launches"],
  ];

  const copy = async () => {
    ui.tap();
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      /* visible on screen anyway */
    }
  };

  return (
    <Page title="account" sub="stored on this device · visible to your homies">
      <section className="acct">
        <button
          type="button"
          className="acct-orb"
          aria-label="Change photo"
          onClick={() => {
            ui.tap();
            file.current?.click();
          }}
        >
          <span className="acct-halo" />
          <span className="acct-face">
            {profile.avatar ? (
              <span className="acct-img glossy">
                <img src={profile.avatar} alt="" draggable={false} />
              </span>
            ) : (
              <Mascot variant="mono" className="acct-mascot" />
            )}
          </span>
          <span className="acct-edit">
            <Icon name="image" />
          </span>
          <span className={`acct-dot sdot sdot--${profile.status}`} />
        </button>
        <h2 className="acct-name">{profile.name.trim() || "set your name"}</h2>
        {profile.tagline && <p className="acct-tag">{profile.tagline}</p>}
        <input
          ref={file}
          type="file"
          hidden
          accept="image/*"
          onChange={async (e) => {
            const f = e.target.files?.[0];
            e.target.value = "";
            if (!f) return;
            try {
              setProfile({ avatar: await imageToDataUrl(f, 240, 240) });
            } catch {
              /* unreadable */
            }
          }}
        />
      </section>

      <Surface title="profile">
        <div className="stack">
          <Field label="name" value={profile.name} onChange={(v) => setProfile({ name: v })} maxLength={24} autoComplete="name" placeholder="shown to homies and in the status pill" />
          <Field label="tagline" value={profile.tagline} onChange={(v) => setProfile({ tagline: v })} maxLength={48} placeholder="optional" />
          {profile.avatar && (
            <div>
              <GlassButton variant="ghost" onClick={() => setProfile({ avatar: null })}>
                use my mascot instead
              </GlassButton>
            </div>
          )}
        </div>
      </Surface>

      <Surface title="picture">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">shown in the status pill and to homies</span>
            <span className="info-value">{profile.avatar ? "your photo" : "the mascot"}</span>
          </div>
          <div className="acct-acts">
            <GlassButton
              variant={!profile.avatar ? "primary" : "ghost"}
              onClick={() => {
                ui.tap();
                setProfile({ avatar: null });
              }}
              icon="user"
            >
              mascot
            </GlassButton>
            <GlassButton
              variant={profile.avatar ? "primary" : "ghost"}
              onClick={() => {
                ui.tap();
                file.current?.click();
              }}
              icon="image"
            >
              photo
            </GlassButton>
          </div>
        </div>
        <p className="picker-note">Either way it gets the same glass gloss as the rest of the interface.</p>
      </Surface>

      <Surface title="visible to homies">
        <div className="info-row">
          <div className="info-text">
            <span className="info-label">host code</span>
            <span className="info-value code">{code}</span>
          </div>
          <button type="button" className="btn btn--ghost" onClick={() => void copy()}>
            <Icon name={copied ? "check" : "link"} />
            <span>{copied ? "copied" : "copy"}</span>
          </button>
        </div>
        <p className="picker-note">Your name, presence, what you're playing and your stats broadcast under this code. Last activity survives offline.</p>
      </Surface>

      <Surface title="presence">
        <Segmented
          label="Presence"
          options={[
            { value: "online", label: "online" },
            { value: "away", label: "away" },
            { value: "busy", label: "busy" },
            { value: "offline", label: "offline" },
          ]}
          value={profile.status}
          onChange={(v) => setProfile({ status: v })}
        />
      </Surface>

      <Surface title="activity">
        <ul className="orbstats">
          {stats.map(([n, label]) => (
            <li key={label}>
              <span className="orb orbstat" style={{ "--s": "68px" } as React.CSSProperties}>
                <span className="orb-face" />
                <span className="orbstat-n">{n}</span>
              </span>
              <span className="orbstat-l">{label}</span>
            </li>
          ))}
        </ul>
        {topSongs.length > 0 && (
          <div className="topsongs">
            <span className="info-label">most listened</span>
            {topSongs.map((s, i) => (
              <div key={i} className="topsong-row">
                <span className="topsong-title">{s.title}</span>
                <span className="topsong-sec">{fmtDuration(s.sec)}</span>
              </div>
            ))}
          </div>
        )}
      </Surface>
    </Page>
  );
}
