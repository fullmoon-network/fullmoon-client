/* Tonight's moon, from the date alone — the same arithmetic as the mod's title/MoonPhase.java,
   so the launcher and the title screen never disagree about the sky.

   A mean synodic month counted from one observed new moon. The real moon runs up to about half a
   day either side of the mean, which is below what a phase name or a whole-day countdown can show,
   so no ephemeris is worth carrying for it. */

export const SYNODIC = 29.530588853;
/** The new moon of 2000-01-06 18:14 UTC. */
const EPOCH = Date.parse("2000-01-06T18:14:00Z");
/** Lit this much or more, the disc reads as full to the eye. */
const FULL = 0.98;
const NEW = 0.02;

export type MoonName =
  | "new"
  | "waxing_crescent"
  | "first_quarter"
  | "waxing_gibbous"
  | "full"
  | "waning_gibbous"
  | "last_quarter"
  | "waning_crescent";

export interface MoonPhase {
  /** days since the last new moon, [0, SYNODIC) */
  age: number;
  /** fraction of the disc that is lit, 0 new to 1 full */
  lit: number;
  /** true from new moon to full, when the lit limb is on the right */
  waxing: boolean;
}

export function moonAt(when: Date | number): MoonPhase {
  const days = ((typeof when === "number" ? when : when.getTime()) - EPOCH) / 86_400_000;
  const age = ((days % SYNODIC) + SYNODIC) % SYNODIC;
  const lit = (1 - Math.cos((2 * Math.PI * age) / SYNODIC)) / 2;
  return { age, lit, waxing: age < SYNODIC / 2 };
}

export function isFull(m: MoonPhase): boolean {
  return m.lit >= FULL;
}

/** Eight names, each centred on its point of the month. */
export function moonName(m: MoonPhase): MoonName {
  if (m.lit >= FULL) return "full";
  if (m.lit <= NEW) return "new";
  const octant = Math.floor((m.age / SYNODIC) * 8 + 0.5) % 8;
  switch (octant) {
    case 1:
      return "waxing_crescent";
    case 2:
      return "first_quarter";
    case 3:
      return "waxing_gibbous";
    case 5:
      return "waning_gibbous";
    case 6:
      return "last_quarter";
    case 7:
      return "waning_crescent";
    default:
      return m.waxing ? "waxing_gibbous" : "waning_crescent";
  }
}

/** Days until the next full moon, to the nearest day; 0 only on the night itself. */
export function daysToFull(m: MoonPhase): number {
  if (isFull(m)) return 0;
  const toFull = (SYNODIC / 2 - m.age + SYNODIC) % SYNODIC;
  return Math.max(1, Math.round(toFull));
}

/** On a full night, days to the full moon after this one, to the nearest day. */
export function daysToNextFull(m: MoonPhase): number {
  return Math.round(SYNODIC / 2 - m.age + SYNODIC);
}

/**
 * The lit face of a waxing moon of radius r centred on 0,0, as an SVG path: the right half-disc
 * closed by the terminator, the ellipse x = k·sqrt(r² − y²) with k = 1 − 2·lit that the mod's
 * moon shader cuts along. A waning moon is the same path mirrored. Null when nothing is lit.
 */
export function litPath(lit: number, r: number): string | null {
  const p = Math.min(1, Math.max(0, lit));
  if (p <= 0) return null;
  const rx = Math.abs(1 - 2 * p) * r;
  const sweep = p < 0.5 ? 0 : 1;
  return `M0 ${-r}A${r} ${r} 0 0 1 0 ${r}A${rx} ${r} 0 0 ${sweep} 0 ${-r}Z`;
}
