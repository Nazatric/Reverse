import { useId, type ButtonHTMLAttributes, type CSSProperties, type InputHTMLAttributes, type MouseEvent, type ReactNode } from "react";
import { Icon, type IconName } from "./Icons";
import { cx } from "../../utils/cx";
import { ui } from "../../utils/audio";
import type { Presence } from "../../state/app";

/* ---------- Orb: the glowing circular control ---------- */

interface OrbProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "onClick" | "children"> {
  label: string;
  icon?: IconName;
  size?: number;
  /** bright, glossy primary orb */
  hot?: boolean;
  /** toggled-on glow */
  on?: boolean;
  quiet?: boolean;
  onClick?: (e: MouseEvent<HTMLButtonElement>) => void;
  children?: ReactNode;
}

export function Orb({ label, icon, size = 52, hot, on, quiet, onClick, children, className, ...rest }: OrbProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={on === undefined ? undefined : on}
      className={cx("orb", hot && "orb--hot", on && "is-on", className)}
      style={{ "--s": `${size}px` } as CSSProperties}
      onClick={(e) => {
        if (!quiet) ui.tap();
        onClick?.(e);
      }}
      {...rest}
    >
      <span className="orb-face" />
      {icon && <Icon name={icon} />}
      {children}
    </button>
  );
}

/* ---------- Glass pill button ---------- */

interface BtnProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "onClick"> {
  icon?: IconName;
  variant?: "primary" | "ghost" | "danger";
  block?: boolean;
  onClick?: (e: MouseEvent<HTMLButtonElement>) => void;
}

export function GlassButton({ icon, variant, block, onClick, children, className, ...rest }: BtnProps) {
  return (
    <button
      type="button"
      className={cx("btn", variant && `btn--${variant}`, block && "btn--block", className)}
      onClick={(e) => {
        ui.tap();
        onClick?.(e);
      }}
      {...rest}
    >
      {icon && <Icon name={icon} />}
      <span>{children}</span>
    </button>
  );
}

/* ---------- Switch ---------- */

export function Switch({ label, hint, on, onChange }: { label: string; hint?: string; on: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      className="switch"
      onClick={() => {
        ui.tap();
        onChange(!on);
      }}
    >
      <span className="switch-text">
        <span className="switch-label">{label}</span>
        {hint && <span className="switch-hint">{hint}</span>}
      </span>
      <span className="switch-track" aria-hidden="true">
        <span className="switch-thumb" />
      </span>
    </button>
  );
}

/* ---------- Segmented ---------- */

export function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  const i = Math.max(0, options.findIndex((o) => o.value === value));
  return (
    <div className="seg" role="radiogroup" aria-label={label} style={{ "--n": options.length, "--i": i } as CSSProperties}>
      <span className="seg-thumb" aria-hidden="true" />
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          onClick={() => {
            if (o.value !== value) ui.tap();
            onChange(o.value);
          }}
        >
          <span>{o.label}</span>
        </button>
      ))}
    </div>
  );
}

/* ---------- Field ---------- */

export function Field({
  label,
  value,
  onChange,
  ...rest
}: { label: string; value: string; onChange: (v: string) => void } & Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value">) {
  const id = useId();
  return (
    <label className="field" htmlFor={id}>
      <span className="field-label">{label}</span>
      <input id={id} className="field-input" value={value} onChange={(e) => onChange(e.target.value)} {...rest} />
    </label>
  );
}

/* ---------- Chip ---------- */

export function Chip({ active, onClick, children }: { active?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      className={cx("chip", active && "is-active")}
      aria-pressed={!!active}
      onClick={() => {
        ui.tap();
        onClick();
      }}
    >
      <span>{children}</span>
    </button>
  );
}

/* ---------- Surfaces & small pieces ---------- */

export function Surface({ title, children, className }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={cx("surface", className)}>
      {title && <h2 className="surface-title">{title}</h2>}
      {children}
    </section>
  );
}

export function Empty({ icon, title, text, children }: { icon: IconName; title: string; text: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <span className="orb orb--hot empty-orb" style={{ "--s": "88px" } as CSSProperties}>
        <span className="orb-face" />
        <Icon name={icon} />
      </span>
      <h2 className="empty-title">{title}</h2>
      <p className="empty-text">{text}</p>
      {children}
    </div>
  );
}

export function StatusDot({ status, className }: { status: Presence; className?: string }) {
  return <span className={cx("sdot", `sdot--${status}`, className)} aria-label={status} role="img" />;
}
