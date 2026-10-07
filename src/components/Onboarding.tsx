import { useEffect, useRef, useState } from "react";
import { Mascot } from "./Mascot";
import { ui } from "../utils/audio";
import { cx } from "../utils/cx";

interface Props {
  onStart: (name: string) => void;
}

/**
 * First run only: a compact card over the live hub — not a splash screen.
 * Asks for a username, then hands straight over to the app.
 */
export function Onboarding({ onStart }: Props) {
  const [name, setName] = useState("");
  const [touched, setTouched] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const t = window.setTimeout(() => input.current?.focus(), 450);
    return () => window.clearTimeout(t);
  }, []);

  const start = () => {
    const trimmed = name.trim().slice(0, 24);
    if (!trimmed) {
      setTouched(true);
      ui.close();
      input.current?.focus();
      return;
    }
    ui.confirm();
    onStart(trimmed);
  };

  return (
    <div className="onboard" role="dialog" aria-modal="true" aria-label="Choose your username">
      <div className="onboard-card">
        <div className="onboard-mascot">
          <span className="onboard-halo" />
          <Mascot className="onboard-face" />
        </div>

        <p className="onboard-kicker">the gadget</p>
        <h1 className="onboard-title">who's using the hub?</h1>

        <label className="field onboard-field" htmlFor="onboard-name">
          <span className="field-label">your username</span>
          <input
            id="onboard-name"
            ref={input}
            className={cx("field-input", touched && !name.trim() && "is-error")}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setTouched(false);
            }}
            onKeyDown={(e) => e.key === "Enter" && start()}
            maxLength={24}
            placeholder="type it here"
            autoComplete="nickname"
            autoCapitalize="off"
            spellCheck={false}
            enterKeyHint="done"
          />
          {touched && !name.trim() && <span className="onboard-error">pick a name to continue</span>}
        </label>

        <button type="button" className="btn btn--primary onboard-start" onClick={start}>
          <span>start the hub</span>
        </button>

        <p className="onboard-note">stays on this device</p>
      </div>
    </div>
  );
}
