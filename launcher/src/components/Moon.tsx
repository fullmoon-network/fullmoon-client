/* The moon, wherever the launcher draws one. It is never ornament: its lit fraction is always a
   real quantity — tonight's phase, or the share of a launch that the log has proved. */

import { litPath } from "../core/moonPhase";

/** A moon of radius r with `lit` of its disc lit, the unlit face beneath, as an SVG group. */
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
      <circle className="moon-shadow" r={r} />
      {d && <path className="moon-lit" d={d} transform={waxing ? undefined : "scale(-1 1)"} />}
    </g>
  );
}

/** The moon as its own picture: an SVG exactly 2r on a side. */
export function Moon({
  r,
  lit,
  waxing,
  className,
  label,
}: {
  r: number;
  lit: number;
  waxing: boolean;
  className?: string;
  label?: string;
}) {
  return (
    <svg
      className={className}
      width={r * 2}
      height={r * 2}
      viewBox={`${-r} ${-r} ${r * 2} ${r * 2}`}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={{ display: "block", flex: "none" }}
    >
      <MoonDisc r={r} lit={lit} waxing={waxing} />
    </svg>
  );
}
