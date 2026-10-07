import { Mascot } from "../Mascot";
import type { NodeId } from "./nodes";

/**
 * Glyphs drawn for the hub orbs and plugin nodes.
 * `account` is deliberately the grey mascot — it reads as "you", not as the logo.
 */
export type GlyphName = NodeId | "play" | "note" | "star";

export function isGlyphName(value: string): value is GlyphName {
  return ["music", "games", "homies", "config", "account", "play", "note", "star"].includes(value);
}

function PlayGlyph() {
  return (
    <svg className="glyph glyph--play" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <path d="M48 32 L112 70 L48 108 Z" fill="currentColor" strokeLinejoin="round" />
    </svg>
  );
}

function NoteGlyph() {
  return (
    <svg className="glyph glyph--note" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <path d="M54 106 V30 L104 20 V94" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
      <ellipse cx="44" cy="107" rx="13" ry="10" fill="currentColor" transform="rotate(-16 44 107)" />
      <ellipse cx="94" cy="95" rx="13" ry="10" fill="currentColor" transform="rotate(-16 94 95)" />
    </svg>
  );
}

function StarGlyph() {
  return (
    <svg className="glyph glyph--star" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <path
        d="M70 20l17 34 38 6-27 27 6 38-34-18-34 18 6-38-27-27 38-6z"
        fill="currentColor"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function GamesGlyph() {
  return (
    <svg className="glyph glyph--games" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <path
        d="M38 44c-14 0-22 10-25 24l-7 33c-3 15 9 23 20 14l25-20c6-5 12-7 19-7s13 2 19 7l25 20c11 9 23 1 20-14l-7-33c-3-14-11-24-25-24-14 0-20 8-32 8s-18-8-32-8z"
        fill="currentColor"
      />
      <path d="M38 58v26M25 71h26" stroke="var(--glyph-hole)" strokeWidth="7.5" strokeLinecap="round" />
      <circle cx="98" cy="66" r="5.5" fill="var(--glyph-hole)" />
      <circle cx="112" cy="79" r="5.5" fill="var(--glyph-hole)" />
    </svg>
  );
}

function HomiesGlyph() {
  return (
    <svg className="glyph glyph--homies" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <g fill="currentColor">
        <circle cx="70" cy="42" r="17" />
        <path d="M36 100c0-18 14-31 34-31s34 13 34 31v6H36z" />
        <circle cx="34" cy="54" r="13.5" opacity="0.85" />
        <path d="M6 103c0-15 10-26 26-26 5 0 11 3 16 8-6 7-10 15-10 24H7z" opacity="0.85" />
        <circle cx="106" cy="54" r="13.5" opacity="0.85" />
        <path d="M134 103c0-15-10-26-26-26-5 0-11 3-16 8 6 7 10 15 10 24h31z" opacity="0.85" />
        <path d="M14 114c5-10 16-15 32-15h48c16 0 27 5 32 15v4H14z" />
      </g>
    </svg>
  );
}

function ConfigGlyph() {
  const gear = (cx: number, cy: number, ro: number, ri: number, teeth: number, hole: number) => {
    const step = (Math.PI * 2) / teeth;
    let d = "";
    for (let i = 0; i < teeth; i++) {
      const a = i * step;
      const pts: Array<[number, number]> = [
        [ri, a - 0.31 * step],
        [ro, a - 0.17 * step],
        [ro, a + 0.17 * step],
        [ri, a + 0.31 * step],
      ];
      pts.forEach(([r, t], k) => {
        d += `${i === 0 && k === 0 ? "M" : "L"}${(cx + Math.cos(t) * r).toFixed(1)} ${(cy + Math.sin(t) * r).toFixed(1)}`;
      });
    }
    d += "Z";
    d += `M${cx - hole} ${cy}a${hole} ${hole} 0 1 0 ${hole * 2} 0a${hole} ${hole} 0 1 0 ${-hole * 2} 0Z`;
    return d;
  };
  return (
    <svg className="glyph glyph--config" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <path d={gear(56, 82, 40, 32, 9, 15)} fill="currentColor" fillRule="evenodd" />
      <path d={gear(106, 38, 24, 19, 8, 8)} fill="currentColor" fillRule="evenodd" />
      <circle cx="56" cy="82" r="8" fill="var(--glyph-dot)" />
    </svg>
  );
}

function MusicGlyph() {
  return (
    <svg className="glyph glyph--music" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
      <g fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="6.5">
        <path d="M20 42C50 28 86 28 117 38" />
        <path d="M18 54C50 40 87 40 119 50" />
        <path d="M18 66C48 53 84 53 113 62" />
        <path d="M19 78C45 66 76 66 99 71" />
      </g>
      <path d="M108 18L100 88" fill="none" stroke="currentColor" strokeWidth="7.5" strokeLinecap="round" />
      <ellipse cx="94" cy="97" rx="15" ry="12.5" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" transform="rotate(-12 94 97)" />
      <g fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="5.5">
        <path d="M40 86L38 113" />
        <path d="M38 100C30 98 24 103 25 110C26 116 34 118 40 114" />
        <path d="M64 86L62 113" />
        <path d="M62 100C54 98 48 103 49 110C50 116 58 118 64 114" />
      </g>
    </svg>
  );
}

/** Renders a glyph by name. Unknown names fall back to a plain ring rather than crashing. */
export function NodeGlyph({ id, avatar }: { id: string; avatar?: string | null }) {
  if (id === "account") {
    return avatar ? (
      <span className="glyph glyph--avatar">
        <img src={avatar} alt="" decoding="async" draggable={false} />
      </span>
    ) : (
      <Mascot variant="mono" className="glyph glyph--face" />
    );
  }
  switch (id) {
    case "music": return <MusicGlyph />;
    case "games": return <GamesGlyph />;
    case "homies": return <HomiesGlyph />;
    case "config": return <ConfigGlyph />;
    case "play": return <PlayGlyph />;
    case "note": return <NoteGlyph />;
    case "star": return <StarGlyph />;
    default:
      return (
        <svg className="glyph" viewBox="0 0 140 140" aria-hidden="true" focusable="false">
          <circle cx="70" cy="70" r="34" fill="none" stroke="currentColor" strokeWidth="9" />
        </svg>
      );
  }
}
