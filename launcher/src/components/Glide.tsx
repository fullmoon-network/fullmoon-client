/* The gliding selection: one indicator per list — the gold bar with its wash — that moves to the
   current item instead of each item painting its own. The list marks the current item with
   `data-current`; the indicator measures it and lets CSS carry it there on the base duration.
   The first placement snaps, so a list does not slide in from its origin, and reduced motion
   (the system's or the launcher's setting) collapses the glide to a crossfade through the same
   token every other transition uses. */

import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

type Box = { top: number; left: number; width: number; height: number };

export function GlideList({
  current,
  axis = "y",
  className = "",
  children,
  role,
  label,
}: {
  /** anything that changes when the current item changes; the indicator re-measures on it */
  current: string | number | null;
  axis?: "x" | "y";
  className?: string;
  children: ReactNode;
  role?: string;
  label?: string;
}) {
  const host = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState<Box | null>(null);
  const settled = useRef(false);

  useLayoutEffect(() => {
    const el = host.current;
    if (!el) return;
    const measure = () => {
      const target = el.querySelector<HTMLElement>("[data-current='true']");
      if (!target) {
        setBox(null);
        return;
      }
      setBox({ top: target.offsetTop, left: target.offsetLeft, width: target.offsetWidth, height: target.offsetHeight });
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [current]);

  // the first frame with a box snaps; from the next change on, the indicator glides
  useLayoutEffect(() => {
    if (box && !settled.current) {
      const id = requestAnimationFrame(() => {
        settled.current = true;
      });
      return () => cancelAnimationFrame(id);
    }
  }, [box]);

  const style: CSSProperties | undefined = box
    ? axis === "y"
      ? { top: box.top, height: box.height }
      : { left: box.left, width: box.width }
    : undefined;

  return (
    <div ref={host} className={`glide-host ${className}`} role={role} aria-label={label}>
      <span
        className={`glide ${axis === "x" ? "glide-x" : ""} ${box ? "" : "is-hidden"} ${settled.current ? "" : "is-snapped"}`}
        style={style}
        aria-hidden
      />
      {children}
    </div>
  );
}
