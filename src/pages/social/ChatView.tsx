import { useEffect, useRef, useState } from "react";
import { Orb } from "../../components/ui/Controls";
import { Page } from "../../components/ui/Page";
import { useApp } from "../../state/app";
import { getMyCode, sendChat, useChatBuffer, useConn, usePresence } from "../../utils/social";
import { ago } from "../../utils/link";
import { ui } from "../../utils/audio";

export function ChatView({ code }: { code: string }) {
  const { homies } = useApp();
  const conn = useConn();
  const pres = usePresence(code);
  const msgs = useChatBuffer(code);
  const [text, setText] = useState("");
  const list = useRef<HTMLDivElement>(null);
  const h = homies.find((x) => x.code === code);
  const online = pres?.up !== false && (pres?.status ?? "offline") !== "offline";
  const name = pres?.name || h?.name || code;

  useEffect(() => {
    const el = list.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [msgs.length]);

  const send = () => {
    if (!text.trim()) return;
    sendChat(code, text);
    ui.tap();
    setText("");
  };

  return (
    <Page fill title={name} sub={`${code} · ${pres?.up === false ? "last seen " + ago(pres?.t ?? null) : online ? "online" : "offline"}`}>
      <div className="chat">
        {pres?.now && (
          <div className="chat-activity">
            <span className="chat-activity-kind">{pres.now.kind === "music" ? "now playing" : "playing"}</span>
            <span className="chat-activity-text">
              {pres.now.kind === "music" ? `${pres.now.title}${pres.now.artist ? ` — ${pres.now.artist}` : ""}` : pres.now.name}
            </span>
          </div>
        )}

        <div className="chat-list" ref={list}>
          {msgs.length === 0 && (
            <p className="chat-empty">Say hi. Messages go straight between your two devices over the public relay.</p>
          )}
          {msgs.map((m) => (
            <div key={m.id} className={`bubble ${m.from === getMyCode() ? "is-me" : ""}`}>
              <span className="bubble-name">{m.name || (m.from === getMyCode() ? "you" : m.from)}</span>
              <p>{m.text}</p>
            </div>
          ))}
        </div>

        <form
          className="chat-input"
          onSubmit={(e) => {
            e.preventDefault();
            send();
          }}
        >
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={conn === "online" ? `message ${name}…` : "reconnecting…"}
            maxLength={500}
            enterKeyHint="send"
            autoComplete="off"
            disabled={conn !== "online"}
          />
          <Orb label="Send" icon="play" size={48} hot onClick={send} disabled={conn !== "online"} />
        </form>
      </div>
    </Page>
  );
}
