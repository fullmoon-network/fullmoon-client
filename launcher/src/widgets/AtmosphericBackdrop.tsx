import { useMemo } from "react";

/* The night behind every surface: the void ground and a sparse, still field of stars — dots,
   never sparkles, and no coloured halo, which on a dark ground is only a shadow in disguise.
   A fixed seed, so the sky does not rearrange itself between screens. */
export function AtmosphericBackdrop() {
  const stars = useMemo(() => {
    let seed = 11;
    const rnd = () => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    };
    return Array.from({ length: 70 }, (_, i) => ({
      id: i,
      x: rnd() * 100,
      // most of the sky is overhead; the lower half of the window holds a few
      y: Math.pow(rnd(), 1.6) * 100,
      r: rnd() < 0.12 ? 1.1 : 0.7,
      bright: rnd() < 0.3,
    }));
  }, []);

  return (
    <div className="game-backdrop" aria-hidden="true">
      <svg className="starfield" width="100%" height="100%">
        {stars.map((s) => (
          <circle
            key={s.id}
            className={s.bright ? "star star-bright" : "star"}
            cx={`${s.x.toFixed(2)}%`}
            cy={`${s.y.toFixed(2)}%`}
            r={s.r}
          />
        ))}
      </svg>
    </div>
  );
}
export default AtmosphericBackdrop;
