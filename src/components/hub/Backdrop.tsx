const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};

/** A handful of dust motes — transform/opacity only, deterministic so they never reshuffle. */
const MOTES = Array.from({ length: 16 }, (_, i) => ({
  left: rnd(i + 1) * 100,
  top: 18 + rnd(i + 31) * 78,
  size: 1.4 + rnd(i + 61) * 2.2,
  dur: 18 + rnd(i + 91) * 20,
  delay: -rnd(i + 121) * 36,
  dx: (rnd(i + 151) - 0.5) * 70,
}));

/**
 * Layered atmosphere: photo texture → soft light pools → drifting streaks → vignette → motes.
 * The photo and motes sit in `data-depth` wrappers so they take part in the parallax.
 */
export function Backdrop() {
  return (
    <div className="bg" aria-hidden="true">
      <div className="bg-layer" data-depth="6">
        <div className="bg-photo" />
      </div>
      <div className="bg-glow" />
      <div className="bg-streaks ambient">
        <span className="bg-streak" style={{ "--y": "36%", "--r": "-7deg" } as React.CSSProperties} />
        <span className="bg-streak bg-streak--b" style={{ "--y": "58%", "--r": "5deg" } as React.CSSProperties} />
      </div>
      <div className="bg-vignette" />
      <div className="bg-layer ambient" data-depth="9">
        {MOTES.map((m, i) => (
          <span
            key={i}
            className="mote"
            style={
              {
                left: `${m.left}%`,
                top: `${m.top}%`,
                width: m.size,
                height: m.size,
                animationDuration: `${m.dur}s`,
                animationDelay: `${m.delay}s`,
                "--dx": `${m.dx}px`,
              } as React.CSSProperties
            }
          />
        ))}
      </div>
    </div>
  );
}
