import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from "react";
import { Page } from "../components/ui/Page";
import { Empty, Field, GlassButton, Orb, Segmented, StatusDot, Surface } from "../components/ui/Controls";
import { Icon } from "../components/ui/Icons";
import { Sheet } from "../components/ui/Sheet";
import { Mascot } from "../components/Mascot";
import { useApp, type Homie, type Presence } from "../state/app";
import { useNav } from "../state/nav";
import { imageToDataUrl } from "../utils/image";
import { ago, normaliseLink } from "../utils/link";
import { getMyCode, isValidCode, normalizeCode, peekPresence, useConn, usePresence, usePresenceVersion } from "../utils/social";
import { ui } from "../utils/audio";
import { GameBrowser } from "./games/GameBrowser";

const STATUSES: { value: Presence; label: string }[] = [
  { value: "online", label: "online" },
  { value: "away", label: "away" },
  { value: "busy", label: "busy" },
  { value: "offline", label: "offline" },
];

export function Avatar({ h }: { h: { avatar: string | null; name: string } }) {
  return h.avatar ? (
    <img src={h.avatar} alt="" decoding="async" draggable={false} />
  ) : (
    <span className="avatar-initial">{h.name.trim().charAt(0).toUpperCase() || "?"}</span>
  );
}

function NowLine({ h }: { h: Homie }) {
  const pres = usePresence(h.code ?? null);
  if (h.kind !== "linked" || !pres?.now) return null;
  return (
    <span className="hnow">
      {pres.now.kind === "music" ? (
        <>
          <Icon name="note" /> {pres.now.title}
          {pres.now.artist ? ` — ${pres.now.artist}` : ""}
        </>
      ) : (
        <>
          <Icon name="gamepad" /> {pres.now.name}
        </>
      )}
    </span>
  );
}

function Node({ h, style, onOpen }: { h: Homie; style?: CSSProperties; onOpen: (el: Element) => void }) {
  const pres = usePresence(h.code ?? null);
  const status: Presence = h.kind === "linked" ? (pres && pres.up !== false ? ((pres.status as Presence) ?? "online") : "offline") : h.status;
  const name = h.kind === "linked" && pres?.name ? pres.name : h.name;

  return (
    <button type="button" className="hnode" style={style} onClick={(e) => onOpen(e.currentTarget)} aria-label={`${name}, ${status}`}>
      <span className="hnode-orb">
        <span className="hnode-halo" />
        <span className="hnode-face">
          <Avatar h={h.kind === "linked" && pres?.avatar ? { avatar: pres.avatar, name } : { avatar: h.avatar, name }} />
        </span>
        <StatusDot status={status} className="hnode-dot" />
      </span>
      <span className="hnode-name">{name}</span>
      <NowLine h={h} />
    </button>
  );
}

/* ---------------- connect surface ---------------- */

function ConnectPanel() {
  const { setHomies, homies } = useApp();
  const conn = useConn();
  const [code, setCode] = useState("");
  const [err, setErr] = useState("");
  const [shared, setShared] = useState(false);
  const mine = getMyCode();

  const add = (e: FormEvent) => {
    e.preventDefault();
    const c = normalizeCode(code);
    if (c === mine) {
      setErr("That's your own code.");
      return;
    }
    if (!isValidCode(c)) {
      setErr("Codes look like hp7k2m9a.");
      return;
    }
    if (homies.some((h) => h.code === c)) {
      setErr("Already linked.");
      return;
    }
    ui.confirm();
    setHomies((l) => [...l, { id: `lnk-${c}`, name: c, note: "", link: "", phone: "", status: "online", avatar: null, added: Date.now(), kind: "linked", code: c }]);
    setCode("");
    setErr("");
  };

  const share = async () => {
    ui.tap();
    const text = `Add me as a homie on The Gadget — my code is ${mine}`;
    try {
      if (navigator.share) {
        await navigator.share({ text });
        return;
      }
    } catch {
      /* cancelled */
    }
    try {
      await navigator.clipboard.writeText(text);
      setShared(true);
      window.setTimeout(() => setShared(false), 1600);
    } catch {
      /* clipboard blocked — code stays visible on screen */
    }
  };

  return (
    <Surface title="connect">
      <div className="info-row">
        <div className="info-text">
          <span className="info-label">my host code</span>
          <span className="info-value code">{mine}</span>
        </div>
        <div className="connect-actions">
          <Orb label={shared ? "Copied" : "Share code"} icon={shared ? "check" : "link"} size={44} on={shared} onClick={() => void share()} />
          <span className={`conn-state is-${conn}`}>{conn === "online" ? "relay connected" : conn === "connecting" ? "connecting…" : "relay offline"}</span>
        </div>
      </div>
      <form className="inline-form" onSubmit={add}>
        <div className="add-row">
          <input
            value={code}
            onChange={(e) => {
              setCode(e.target.value);
              setErr("");
            }}
            placeholder="paste a homie's code"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            aria-label="Homie host code"
          />
          <Orb label="Add homie" icon="plus" size={48} hot onClick={(e) => (e.currentTarget as HTMLButtonElement).closest("form")?.requestSubmit()} />
        </div>
        {err && <p className="add-err">{err}</p>}
        <p className="picker-note">
          Friends find you by code — no account, no sign-up. They see your name, what you're playing and your stats; your last activity stays even after you go
          offline. Chat runs peer to peer over a free public relay.
        </p>
      </form>
    </Surface>
  );
}

/* ---------------- constellation ---------------- */

export function HomiesView() {
  const { homies, setHomies } = useApp();
  const nav = useNav();
  const [adding, setAdding] = useState(false);
  const radial = !adding && homies.length > 0 && homies.length <= 8;
  const presV = usePresenceVersion();

  /* Adopt the real name from a linked homie's first presence. */
  useEffect(() => {
    for (const h of homies) {
      if (h.kind !== "linked" || !h.code) continue;
      const p = peekPresence(h.code);
      if (p?.name && h.name === h.code) {
        setHomies((l) => l.map((x) => (x.id === h.id ? { ...x, name: p.name as string } : x)));
        break;
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [homies, presV]);

  return (
    <Page
      title="homies"
      sub={homies.length ? `${homies.length} linked · ${homies.filter((h) => h.status === "online").length} online` : "nobody yet"}
      actions={<Orb label="Add homie manually" icon="plus" size={50} hot on={adding} onClick={() => setAdding((v) => !v)} />}
    >
      <ConnectPanel />

      {adding && <HomieFormInline onDone={() => setAdding(false)} />}

      {homies.length === 0 && !adding ? (
        <Empty icon="people" title="no homies yet" text="Share your code above, or add one here. Linked homies light up live — name, status and what they're playing.">
          <GlassButton variant="primary" icon="plus" onClick={() => setAdding(true)}>
            add a homie
          </GlassButton>
        </Empty>
      ) : radial ? (
        <div className="const">
          <svg className="const-lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            {homies.map((h, i) => {
              const a = (i / homies.length) * Math.PI * 2 - Math.PI / 2;
              return <line key={h.id} x1="50" y1="50" x2={50 + 35 * Math.cos(a)} y2={50 + 35 * Math.sin(a)} />;
            })}
          </svg>
          <div className="const-you">
            <span className="hnode-halo" />
            <span className="hnode-face"><Mascot /></span>
          </div>
          {homies.map((h, i) => {
            const a = (i / homies.length) * Math.PI * 2 - Math.PI / 2;
            return (
              <Node
                key={h.id}
                h={h}
                style={{ left: `${50 + 35 * Math.cos(a)}%`, top: `${50 + 35 * Math.sin(a)}%`, "--i": i } as CSSProperties}
                onOpen={(el) => nav.push({ n: "homie", id: h.id }, el)}
              />
            );
          })}
        </div>
      ) : (
        <div className="hgrid">
          {homies.map((h) => (
            <Node key={h.id} h={h} onOpen={(el) => nav.push({ n: "homie", id: h.id }, el)} />
          ))}
        </div>
      )}
    </Page>
  );
}

function HomieFormInline({ onDone }: { onDone: () => void }) {
  const { setHomies } = useApp();
  const [name, setName] = useState("");
  const [note, setNote] = useState("");
  const [link, setLink] = useState("");
  const [phone, setPhone] = useState("");
  const [status, setStatus] = useState<Presence>("online");
  const [avatar, setAvatar] = useState<string | null>(null);
  const file = useRef<HTMLInputElement>(null);

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    ui.confirm();
    setHomies((l) => [
      ...l,
      { id: String(Date.now()), name: name.trim(), note: note.trim(), link: normaliseLink(link), phone: phone.trim(), status, avatar, added: Date.now(), kind: "local" },
    ]);
    onDone();
  };

  return (
    <form className="inline-form" onSubmit={save}>
      <div className="avatar-row">
        <button type="button" className="avatar-pick" onClick={() => file.current?.click()} aria-label="Choose photo">
          <span className="hnode-halo" />
          <span className="hnode-face">
            <Avatar h={{ avatar, name }} />
          </span>
        </button>
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
              setAvatar(await imageToDataUrl(f, 240, 240));
            } catch {
              /* unreadable */
            }
          }}
        />
      </div>
      <Field label="name" value={name} onChange={setName} maxLength={32} required placeholder="their name" autoComplete="off" />
      <Field label="note" value={note} onChange={setNote} maxLength={80} placeholder="optional" />
      <Field label="link" value={link} onChange={setLink} inputMode="url" autoCapitalize="none" autoCorrect="off" placeholder="profile or chat link" />
      <Field label="phone" value={phone} onChange={setPhone} inputMode="tel" placeholder="optional — opens dialer / SMS" />
      <div className="field">
        <span className="field-label">status</span>
        <Segmented label="Status" options={STATUSES} value={status} onChange={setStatus} />
      </div>
      <div className="form-actions">
        <GlassButton variant="ghost" onClick={onDone}>
          cancel
        </GlassButton>
        <GlassButton type="submit" variant="primary" icon="check">
          save
        </GlassButton>
      </div>
    </form>
  );
}

/* ---------------- profile ---------------- */

export function HomieView({ id }: { id: string }) {
  const { homies, setHomies } = useApp();
  const nav = useNav();
  const h = homies.find((x) => x.id === id);
  const [confirm, setConfirm] = useState(false);
  const [web, setWeb] = useState<{ title: string; url: string } | null>(null);

  const pres = usePresence(h?.code ?? null);
  const linked = h?.kind === "linked";
  const status: Presence = linked ? (pres && pres.up !== false ? ((pres.status as Presence) ?? "online") : "offline") : (h?.status ?? "offline");
  const name = linked && pres?.name ? pres.name : (h?.name ?? "");
  const avatar = linked && pres?.avatar ? pres.avatar : (h?.avatar ?? null);

  useEffect(() => {
    if (!h) nav.back();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [h]);
  if (!h) return null;

  const openLink = (kind: "call" | "sms" | "web") => {
    ui.tap();
    if (kind === "web") {
      setWeb({ title: name, url: h.link });
      return;
    }
    const url = kind === "call" ? `tel:${h.phone}` : `sms:${h.phone}`;
    // let the native dialer / SMS app take over; the user can always come back
    window.setTimeout(() => {
      window.location.href = url;
    }, 60);
  };

  const now = pres?.now;
  const stats = pres && linked ? pres : null;

  return (
    <Page>
      <section className="gdetail">
        <div className="hdetail-orb">
          <span className="hnode-halo" />
          <span className="hnode-face">
            <Avatar h={{ avatar, name }} />
          </span>
          <StatusDot status={status} className="hnode-dot" />
        </div>
        <h2 className="gdetail-name">{name}</h2>
        <div className="gdetail-tags">
          <span className="tag">
            <StatusDot status={status} /> {linked ? (status === "offline" ? `last seen ${ago(pres?.t ?? null)}` : status) : status}
          </span>
          {linked && (
            <span className="tag">
              <Icon name="link" /> {h.code}
            </span>
          )}
        </div>

        {now && (
          <div className="hdetail-activity">
            <span className="chat-activity-kind">{now.kind === "music" ? "now playing" : "playing"}</span>
            <span className="chat-activity-text">
              {now.kind === "music" ? `${now.title}${now.artist ? ` — ${now.artist}` : ""}` : now.name}
            </span>
          </div>
        )}

        {linked && stats?.up && stats.listenedSec ? (
          <dl className="stats">
            <div>
              <dt>listened</dt>
              <dd>{(stats.listenedSec / 3600).toFixed(1)}h</dd>
            </div>
            <div>
              <dt>games played</dt>
              <dd>{stats.games ?? 0}×</dd>
            </div>
          </dl>
        ) : null}

        {!linked && h.note && <p className="hdetail-note">{h.note}</p>}

        <div className="contact">
          {linked && (
            <button type="button" className="btn btn--primary" onClick={() => nav.push({ n: "chat", id: h.code! }, document.querySelector(".page"))}>
              <Icon name="chat" />
              <span>chat</span>
            </button>
          )}
          {!linked && h.link && (
            <button type="button" className="btn btn--primary" onClick={() => openLink("web")}>
              <Icon name="external" />
              <span>open link</span>
            </button>
          )}
          {!linked && h.phone && (
            <>
              <button type="button" className="btn" onClick={() => openLink("call")}>
                <Icon name="phone" />
                <span>call</span>
              </button>
              <button type="button" className="btn" onClick={() => openLink("sms")}>
                <Icon name="chat" />
                <span>message</span>
              </button>
            </>
          )}
        </div>

        <div className="chero-actions">
          {!linked && (
            <Orb label="Edit homie" icon="edit" size={52} onClick={(e) => nav.push({ n: "homie-edit", id: h.id }, e.currentTarget)} />
          )}
          <Orb
            label="Remove homie"
            icon="trash"
            size={52}
            onClick={() => setConfirm(true)}
          />
        </div>
      </section>

      {web && <GameBrowser title={web.title} url={web.url} onClose={() => setWeb(null)} />}

      {confirm && (
        <Sheet title={`remove ${name}?`} onClose={() => setConfirm(false)}>
          <p className="sheet-text">{linked ? "They'll stop appearing here. Nothing happens on their side." : "They're removed from your homies on this device only."}</p>
          <div className="sheet-actions">
            <GlassButton variant="ghost" onClick={() => setConfirm(false)}>
              cancel
            </GlassButton>
            <GlassButton
              variant="danger"
              icon="trash"
              onClick={() => {
                setConfirm(false);
                setHomies((l) => l.filter((x) => x.id !== h.id));
              }}
            >
              remove
            </GlassButton>
          </div>
        </Sheet>
      )}
    </Page>
  );
}

/* ---------------- add / edit (local homies only) ---------------- */

export function HomieForm({ id }: { id?: string }) {
  const { homies, setHomies } = useApp();
  const nav = useNav();
  const existing = id ? homies.find((h) => h.id === id) : undefined;
  const [name, setName] = useState(existing?.name ?? "");
  const [note, setNote] = useState(existing?.note ?? "");
  const [link, setLink] = useState(existing?.link ?? "");
  const [phone, setPhone] = useState(existing?.phone ?? "");
  const [status, setStatus] = useState<Presence>(existing?.status ?? "online");
  const [avatar, setAvatar] = useState<string | null>(existing?.avatar ?? null);
  const file = useRef<HTMLInputElement>(null);

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    ui.confirm();
    const data = { name: name.trim(), note: note.trim(), link: normaliseLink(link), phone: phone.trim(), status, avatar };
    if (existing) setHomies((l) => l.map((h) => (h.id === existing.id ? { ...h, ...data } : h)));
    else setHomies((l) => [...l, { id: String(Date.now()), ...data, added: Date.now(), kind: "local" as const }]);
    nav.back();
  };

  return (
    <Page title={existing ? "edit homie" : "add homie"}>
      <form className="form" onSubmit={save}>
        <div className="avatar-row">
          <button type="button" className="avatar-pick" onClick={() => file.current?.click()} aria-label="Choose photo">
            <span className="hnode-halo" />
            <span className="hnode-face">
              <Avatar h={{ avatar, name }} />
            </span>
          </button>
          <div className="avatar-actions">
            <GlassButton icon="image" onClick={() => file.current?.click()}>
              {avatar ? "change photo" : "add photo"}
            </GlassButton>
            {avatar && (
              <GlassButton variant="ghost" onClick={() => setAvatar(null)}>
                remove
              </GlassButton>
            )}
          </div>
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
                setAvatar(await imageToDataUrl(f, 240, 240));
              } catch {
                /* unreadable */
              }
            }}
          />
        </div>

        <Field label="name" value={name} onChange={setName} maxLength={32} required placeholder="their name" autoComplete="off" />
        <Field label="note" value={note} onChange={setNote} maxLength={80} placeholder="optional" />
        <Field label="link" value={link} onChange={setLink} inputMode="url" autoCapitalize="none" autoCorrect="off" placeholder="profile or chat link" />
        <Field label="phone" value={phone} onChange={setPhone} inputMode="tel" placeholder="optional — opens dialer / SMS" />
        <div className="field">
          <span className="field-label">status</span>
          <Segmented label="Status" options={STATUSES} value={status} onChange={setStatus} />
        </div>

        <div className="form-actions">
          <GlassButton variant="ghost" onClick={() => nav.back()}>
            cancel
          </GlassButton>
          <GlassButton type="submit" variant="primary" icon="check">
            save
          </GlassButton>
        </div>
      </form>
    </Page>
  );
}
