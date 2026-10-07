import { useId } from "react";

interface MascotFaceProps {
  /** "yellow" = hub logo · "mono" = grey face inside the account orb */
  variant?: "yellow" | "mono";
  className?: string;
  /** drop the glass bezel (status-pill avatar) */
  bare?: boolean;
}

/**
 * The grinning logo, drawn as layered glossy volumes rather than a flat emoji:
 * glass bezel → domed face with rim shading → heavy wedge brows → lens flare → chrome tooth grille.
 */
export function MascotFace({ variant = "yellow", className = "", bare = false }: MascotFaceProps) {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const yellow = variant === "yellow";
  const stops = yellow
    ? [["0", "#fffdb4"], ["0.24", "#eef43d"], ["0.58", "#c2cf29"], ["0.86", "#7f8c16"], ["1", "#434d0b"]]
    : [["0", "#fcfcfb"], ["0.24", "#dcddda"], ["0.58", "#a1a29f"], ["0.86", "#5f605e"], ["1", "#2a2b2a"]];
  const ink = yellow ? "#14150e" : "#161716";

  return (
    <svg className={className} viewBox="0 0 200 200" aria-hidden="true" focusable="false">
      <defs>
        <radialGradient id={`${id}f`} cx="44%" cy="26%" r="84%">
          {stops.map(([o, c]) => (
            <stop key={o} offset={o} stopColor={c} />
          ))}
        </radialGradient>
        <linearGradient id={`${id}b`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#9a9b98" />
          <stop offset="0.4" stopColor="#2b2c2b" />
          <stop offset="1" stopColor="#575856" />
        </linearGradient>
        <linearGradient id={`${id}t`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="0.35" stopColor="#e7e8e5" />
          <stop offset="0.75" stopColor="#a3a4a0" />
          <stop offset="1" stopColor="#5d5e5a" />
        </linearGradient>
        <radialGradient id={`${id}s`}>
          <stop offset="0" stopColor="#fff" stopOpacity="1" />
          <stop offset="0.4" stopColor="#fff" stopOpacity="0.4" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
        <clipPath id={`${id}c`}>
          <circle cx="100" cy="100" r="85" />
        </clipPath>
        <clipPath id={`${id}m`}>
          <path d="M38 124Q100 146 162 124L159 138Q150 172 100 176Q50 172 41 138Z" />
        </clipPath>
      </defs>

      {!bare && (
        <>
          <circle cx="100" cy="100" r="99" fill="#ecede9" />
          <circle cx="100" cy="100" r="96" fill="#0b0b0b" />
          <circle cx="100" cy="100" r="93" fill={`url(#${id}b)`} />
          <circle cx="100" cy="100" r="88.5" fill="#050505" />
        </>
      )}

      <g clipPath={`url(#${id}c)`}>
        <circle cx="100" cy="100" r="85" fill={`url(#${id}f)`} />
        <ellipse cx="100" cy="190" rx="74" ry="28" fill="#fff" opacity="0.1" />
        <ellipse cx="92" cy="46" rx="62" ry="34" fill="#fff" opacity="0.26" />
        <ellipse cx="68" cy="34" rx="27" ry="9" fill="#fff" opacity="0.42" transform="rotate(-18 68 34)" />

        {/* brows */}
        <path d="M34 82C46 56 76 56 100 94L96 100C76 74 54 74 40 90Z" fill={ink} />
        <path d="M166 82C154 56 124 56 100 94L104 100C124 74 146 74 160 90Z" fill={ink} />
        <path d="M44 70C58 60 74 64 88 80" fill="none" stroke="#fff" strokeOpacity="0.18" strokeWidth="1.6" strokeLinecap="round" />
        <path d="M156 70C142 60 126 64 112 80" fill="none" stroke="#fff" strokeOpacity="0.18" strokeWidth="1.6" strokeLinecap="round" />

        {/* eyes */}
        <g className="mascot-eyes" fill={ink}>
          <ellipse cx="66" cy="102" rx="5.5" ry="10" transform="rotate(12 66 102)" />
          <ellipse cx="134" cy="102" rx="5.5" ry="10" transform="rotate(-12 134 102)" />
        </g>

        {/* lens flare */}
        <g transform="translate(100 90)">
          <g className="mascot-flare">
            <circle r="24" fill={`url(#${id}s)`} />
            <path d="M-30 0Q0 0 0-30Q0 0 30 0Q0 0 0 30Q0 0-30 0Z" fill="#fff" opacity="0.95" />
            <circle r="4.2" fill="#fff" />
          </g>
        </g>

        {/* mouth */}
        <path d="M28 116Q100 140 172 116L168 136Q158 176 100 181Q42 176 32 136Z" fill="#0a0a07" />
        <path d="M38 124Q100 146 162 124L159 138Q150 172 100 176Q50 172 41 138Z" fill={`url(#${id}t)`} />
        <g clipPath={`url(#${id}m)`}>
          <path d="M53 112V182M69 112V182M85 112V182M100 112V182M115 112V182M131 112V182M147 112V182" stroke="#15160f" strokeWidth="3.2" />
          <ellipse cx="100" cy="176" rx="64" ry="12" fill="#000" opacity="0.28" />
        </g>
        <path d="M40 126Q100 148 160 126" fill="none" stroke="#fff" strokeOpacity="0.6" strokeWidth="1.5" />
        <path d="M28 116Q100 140 172 116" fill="none" stroke="#0a0a07" strokeWidth="5" />

        {/* rim shading */}
        <circle cx="100" cy="100" r="84" fill="none" stroke="#000" strokeOpacity="0.5" strokeWidth="3.5" />
        <circle cx="100" cy="100" r="80" fill="none" stroke="#fff" strokeOpacity="0.14" strokeWidth="1.2" />
      </g>
    </svg>
  );
}
