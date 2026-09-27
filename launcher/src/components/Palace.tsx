/* The palace vocabulary, drawn for the launcher the way the mod's ui/Palace.java draws it in game:
   the moon seal, the dancheong band, the row marker, keycaps, and the moon as an instrument.
   Frames, lattice bands and tiles are pure CSS (styles/palace.css); what needs geometry is here.

   Ornament belongs to the frame and never to the content, and the ornament colours never say
   anything about state. The moon is not ornament: its lit fraction is always a real quantity. */

import type { CSSProperties, ReactNode } from "react";
import { litPath } from "../core/moonPhase";

/** 月, cut the way a seal carver cuts it — the same 12×12 cut as Palace.MOON_GLYPH. */
const MOON_GLYPH = [
  "............",
  "...#######..",
  "...#.....#..",
  "...#.....#..",
  "...#######..",
  "...#.....#..",
  "...#.....#..",
  "...#######..",
  "...#.....#..",
  "..#......#..",
  ".#.....#.#..",
  "#.......##..",
];
const GLYPH_PATH = MOON_GLYPH.flatMap((row, y) =>
  [...row].map((c, x) => (c === "#" ? `M${x} ${y}h1v1h-1z` : "")),
).join("");

/** The moon seal: a cinnabar square, a cut ivory line inside it, and 月 in ivory. */
export function Seal({ size = 24, className = "" }: { size?: number; className?: string }) {
  const inset = Math.max(1.5, size / 12);
  const glyph = size * 0.64;
  return (
    <svg
      className={`pf-seal ${className}`}
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden
    >
      <rect className="pf-seal-ground" width={size} height={size} />
      <rect
        className="pf-seal-line"
        x={inset + 0.5}
        y={inset + 0.5}
        width={size - inset * 2 - 1}
        height={size - inset * 2 - 1}
        fill="none"
      />
      <g transform={`translate(${(size - glyph) / 2} ${(size - glyph) / 2}) scale(${glyph / 12})`}>
        <path className="pf-seal-glyph" d={GLYPH_PATH} shapeRendering="crispEdges" />
      </g>
    </svg>
  );
}

/** The seal and the wordmark, as the game sets them side by side. */
export function Wordmark({
  size = "sm",
  name,
}: {
  size?: "sm" | "md" | "lg";
  name: string;
}) {
  const seal = size === "lg" ? 44 : size === "md" ? 30 : 20;
  return (
    <span className={`pf-wordmark pf-wordmark-${size}`}>
      <Seal size={seal} />
      <span className="pf-wordmark-name">{name}</span>
    </span>
  );
}

export function Dancheong({ className = "" }: { className?: string }) {
  return <div className={`pf-dancheong ${className}`} aria-hidden />;
}

/** The row marker: filled when chosen, an outline otherwise. `tone` picks accent for a
 *  persistent selection and cinnabar for the row the pointer or keyboard is on. */
export function Marker({ on = false, tone = "accent" }: { on?: boolean; tone?: "accent" | "cinnabar" }) {
  return <span className={`pf-marker ${on ? `is-on is-${tone}` : ""}`} aria-hidden />;
}

export function Key({ children }: { children: ReactNode }) {
  return <kbd className="pf-key">{children}</kbd>;
}

/** A moon of radius r with `lit` of its disc lit, the unlit face beneath. */
export function MoonDisc({
  r,
  lit,
  waxing,
  cx = 0,
  cy = 0,
}: {
  r: number;
  lit: number;
  waxing: boolean;
  cx?: number;
  cy?: number;
}) {
  const d = litPath(lit, r);
  return (
    <g transform={`translate(${cx} ${cy})`}>
      <circle className="pf-moon-shadow" r={r} />
      {d && <path className="pf-moon-lit" d={d} transform={waxing ? undefined : "scale(-1 1)"} />}
    </g>
  );
}

/**
 * The moon as the dial of an instrument: the disc, a ring, and sixty ticks — the title screen's
 * sky moon. `ticks` marks steps instead when given: one major tick per step, lit up to `reached`.
 * The ring turns accent when the quantity is complete (a full moon, a finished launch).
 */
export function MoonDial({
  r,
  lit,
  waxing = true,
  complete = false,
  steps,
  reached = 0,
  spin = false,
  className = "",
  style,
  label,
}: {
  r: number;
  lit: number;
  waxing?: boolean;
  complete?: boolean;
  steps?: number;
  reached?: number;
  /** the outer tick ring turns slowly while something is under way; reduced motion stills it */
  spin?: boolean;
  className?: string;
  style?: CSSProperties;
  label?: string;
}) {
  const ring = r + Math.max(6, r * 0.22);
  const tickIn = ring + Math.max(4, r * 0.1);
  const size = (tickIn + Math.max(6, r * 0.12)) * 2;
  const half = size / 2;
  const ticks: ReactNode[] = [];
  if (steps && steps > 0) {
    for (let i = 0; i < steps; i++) {
      const a = (i / steps) * Math.PI * 2 - Math.PI / 2;
      const on = i < reached;
      ticks.push(
        <circle
          key={i}
          className={on ? "pf-dial-major is-on" : "pf-dial-major"}
          cx={Math.cos(a) * tickIn}
          cy={Math.sin(a) * tickIn}
          r={Math.max(2, r * 0.06)}
        />,
      );
    }
  } else {
    for (let i = 0; i < 60; i++) {
      const a = (i / 60) * Math.PI * 2;
      const major = i % 5 === 0;
      const d = major ? tickIn + 1.5 : tickIn;
      ticks.push(
        <circle
          key={i}
          className={major ? "pf-dial-major is-on" : "pf-dial-minor"}
          cx={Math.cos(a) * d}
          cy={Math.sin(a) * d}
          r={major ? Math.max(1.2, r * 0.03) : Math.max(0.6, r * 0.015)}
        />,
      );
    }
  }
  return (
    <svg
      className={`pf-dial ${complete ? "is-complete" : ""} ${className}`}
      width={size}
      height={size}
      viewBox={`${-half} ${-half} ${size} ${size}`}
      style={style}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <MoonDisc r={r} lit={lit} waxing={waxing} />
      <circle className="pf-dial-ring" r={ring} fill="none" />
      <g className={spin ? "pf-dial-ticks is-spinning" : "pf-dial-ticks"}>{ticks}</g>
    </svg>
  );
}
