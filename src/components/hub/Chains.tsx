import { HUB, NODES } from "./nodes";
import { useMetrics } from "../../utils/metrics";

/**
 * One repeating tile = a flat link + an edge-on link, so a chain is a single div with a
 * repeat-x background. Five divs total — the whole connective web costs almost nothing to
 * paint and animates with transform only.
 */
const TILE = encodeURIComponent(
  `<svg xmlns='http://www.w3.org/2000/svg' width='64' height='40' viewBox='0 0 64 40'>
<defs>
<linearGradient id='a' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='#fff'/><stop offset='.22' stop-color='#c7c8c5'/><stop offset='.5' stop-color='#3b3c3a'/><stop offset='.76' stop-color='#9b9c99'/><stop offset='1' stop-color='#1c1d1c'/></linearGradient>
<linearGradient id='b' x1='0' y1='0' x2='1' y2='0'><stop offset='0' stop-color='#f2f2ef'/><stop offset='.3' stop-color='#7c7d7a'/><stop offset='.6' stop-color='#171817'/><stop offset='1' stop-color='#8d8e8b'/></linearGradient>
<radialGradient id='s'><stop offset='0' stop-color='#000' stop-opacity='.55'/><stop offset='1' stop-color='#000' stop-opacity='0'/></radialGradient>
</defs>
<ellipse cx='34' cy='26' rx='34' ry='10' fill='url(#s)'/>
<ellipse cx='21' cy='20' rx='21.5' ry='13' fill='url(#a)' stroke='#0b0b0b' stroke-width='2.2'/>
<ellipse cx='21' cy='20' rx='13' ry='5.4' fill='#060606'/>
<ellipse cx='13' cy='13.5' rx='8' ry='2' fill='#fff' opacity='.62'/>
<rect x='45.5' y='5' width='15' height='30' rx='7.5' fill='url(#b)' stroke='#0b0b0b' stroke-width='2'/>
<rect x='50.5' y='10' width='5' height='20' rx='2.5' fill='#050505'/>
<rect x='47.6' y='9' width='2.4' height='13' rx='1.2' fill='#fff' opacity='.6'/>
</svg>`,
);
const TILE_URL = `url("data:image/svg+xml,${TILE}")`;

export function Chains() {
  const m = useMetrics();
  const hx = (HUB.x / 100) * m.sw;
  const hy = (HUB.y / 100) * m.sh;
  const H = Math.max(26, 40 * m.u);

  return (
    <div className="chains" aria-hidden="true">
      {NODES.map((n, i) => {
        const dx = (n.x / 100) * m.sw - hx;
        const dy = (n.y / 100) * m.sh - hy;
        return (
          <div
            key={n.id}
            className="chain"
            style={
              {
                left: hx,
                top: hy - H / 2,
                width: Math.hypot(dx, dy),
                height: H,
                "--a": `${(Math.atan2(dy, dx) * 180) / Math.PI}deg`,
                "--d": `${-i * 1.9}s`,
                "--tile": TILE_URL,
              } as React.CSSProperties
            }
          />
        );
      })}
    </div>
  );
}
