/* The launcher half of the client's HUD placement contract, mirrored from the mod's own source:
 * `dev.fullmoon.client.hud.Anchor` and `HudGrid`.
 *
 * An element is a nine-way anchor plus an integer pixel offset measured from it — not a percentage
 * of the screen, because a percentage moves every element when the window resizes, and holding a
 * fixed distance from a corner is the whole reason the anchor exists.
 *
 * Both editors write the same `config/fullmoon/hud.json`, so this file has to agree with the Java
 * to the pixel. The two places that would silently disagree are marked: Java truncates an integer
 * division toward zero, and `Math.round` breaks a tie upward in both languages.
 * `scripts/hud-contract.test.ts` asserts the vectors the mod's own `AnchorTest` asserts. */

/** Declaration order of the mod's enum, which is row-major — so the index carries the cell. */
export const ANCHORS = [
  "TOP_LEFT",
  "TOP_CENTER",
  "TOP_RIGHT",
  "CENTER_LEFT",
  "CENTER",
  "CENTER_RIGHT",
  "BOTTOM_LEFT",
  "BOTTOM_CENTER",
  "BOTTOM_RIGHT",
] as const;

export type Anchor = (typeof ANCHORS)[number];

export const isAnchor = (v: string): v is Anchor => (ANCHORS as readonly string[]).includes(v);

export const col = (a: Anchor): number => ANCHORS.indexOf(a) % 3;
export const row = (a: Anchor): number => Math.trunc(ANCHORS.indexOf(a) / 3);
export const u = (a: Anchor): number => col(a) / 2;
export const v = (a: Anchor): number => row(a) / 2;

/** Java's `/` on two ints truncates toward zero. `Math.floor` would not, for a negative. */
const half = (n: number): number => Math.trunc(n / 2);

const clamp3 = (n: number): number => Math.min(2, Math.max(0, Math.trunc(n)));

export function computeX(a: Anchor, screenW: number, elementW: number, offsetX: number): number {
  const uu = u(a);
  if (uu === 0) return offsetX;
  if (uu === 0.5) return half(screenW - elementW) + offsetX;
  return screenW - elementW - offsetX;
}

export function computeY(a: Anchor, screenH: number, elementH: number, offsetY: number): number {
  const vv = v(a);
  if (vv === 0) return offsetY;
  if (vv === 0.5) return half(screenH - elementH) + offsetY;
  return screenH - elementH - offsetY;
}

export function computeOffsetX(a: Anchor, screenW: number, elementW: number, screenX: number): number {
  const uu = u(a);
  if (uu === 0) return screenX;
  if (uu === 0.5) return screenX - half(screenW - elementW);
  return screenW - elementW - screenX;
}

export function computeOffsetY(a: Anchor, screenH: number, elementH: number, screenY: number): number {
  const vv = v(a);
  if (vv === 0) return screenY;
  if (vv === 0.5) return screenY - half(screenH - elementH);
  return screenH - elementH - screenY;
}

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function place(
  a: Anchor,
  screenW: number,
  screenH: number,
  elementW: number,
  elementH: number,
  offsetX: number,
  offsetY: number,
): Box {
  return {
    x: computeX(a, screenW, elementW, offsetX),
    y: computeY(a, screenH, elementH, offsetY),
    w: elementW,
    h: elementH,
  };
}

export const fromGrid = (c: number, r: number): Anchor => ANCHORS[clamp3(r) * 3 + clamp3(c)];

/** Which cell a placed element sits in, by where its centre falls. */
export function nearest(
  screenW: number,
  screenH: number,
  elementW: number,
  elementH: number,
  screenX: number,
  screenY: number,
): Anchor {
  const relX = (screenX + elementW * 0.5) / Math.max(1, screenW);
  const relY = (screenY + elementH * 0.5) / Math.max(1, screenH);
  const c = relX < 0.333 ? 0 : relX < 0.666 ? 1 : 2;
  const r = relY < 0.333 ? 0 : relY < 0.666 ? 1 : 2;
  return fromGrid(c, r);
}

/* ── the grid, mirrored from HudGrid ─────────────────────────────────────────── */

export const DEFAULT_GRID_SNAP = 4;

export const sanitizeSnap = (step: number): number =>
  Number.isFinite(step) && step > 0 ? Math.trunc(step) : DEFAULT_GRID_SNAP;

/** Nearest multiple of the step, halves upward — the tie `Math.round` breaks the same way in Java. */
export function snap(raw: number, step: number): number {
  const s = sanitizeSnap(step);
  return Math.round(raw / s) * s;
}

/* ── the drag, mirrored from HudEditorScreen.mouseDragged ────────────────────── */

export interface Placement {
  anchor: Anchor;
  offsetX: number;
  offsetY: number;
}

/**
 * The pointer moves the element's top-left in screen px; that lands on the grid; where it landed
 * names the anchor; the offset is measured back from that anchor and is never negative. Doing it in
 * this order is what makes a drag across the middle of the screen hand the element to the next cell
 * instead of growing an offset the game would place somewhere else.
 */
export function drag(opts: {
  from: Placement;
  screenW: number;
  screenH: number;
  elementW: number;
  elementH: number;
  dx: number;
  dy: number;
  step: number;
}): Placement {
  const { from, screenW, screenH, elementW, elementH, step } = opts;
  const rawX = computeX(from.anchor, screenW, elementW, from.offsetX) + Math.trunc(opts.dx);
  const rawY = computeY(from.anchor, screenH, elementH, from.offsetY) + Math.trunc(opts.dy);
  const sx = snap(rawX, step);
  const sy = snap(rawY, step);
  const a = nearest(screenW, screenH, elementW, elementH, sx, sy);
  return {
    anchor: a,
    offsetX: Math.max(0, computeOffsetX(a, screenW, elementW, sx)),
    offsetY: Math.max(0, computeOffsetY(a, screenH, elementH, sy)),
  };
}

/**
 * Moving an element to a cell by hand keeps the distance it already had from its old anchor, so
 * picking a corner in the inspector does not also throw away a nudge the player made.
 */
export function reanchor(from: Placement, to: Anchor): Placement {
  return { anchor: to, offsetX: from.offsetX, offsetY: from.offsetY };
}

/* ── what the client registers ───────────────────────────────────────────────── */

/** `HudElementRegistry`'s registration order — the order the mod itself lists the elements in. */
export const ELEMENT_ORDER = [
  "coords",
  "fps",
  "ping",
  "clock",
  "keystrokes",
  "tps",
  "armor",
  "effects",
  "season",
] as const;

export type ElementId = (typeof ELEMENT_ORDER)[number];

/**
 * GUI px. What the game measures in on a 1280×720 window at GUI scale 2, and the geometry the
 * capture rig shoots — so the editor's stage is this plane scaled, and a drag is arithmetic in the
 * same units the client will read back out of the file.
 */
export const STAGE_W = 640;
export const STAGE_H = 360;

/**
 * Registered elements first, in the client's own order; anything else the file carries after them.
 * An id this launcher has never heard of belongs to a newer client, and dropping it from the list
 * would be the one way to lose it — the editor writes back everything it was given.
 */
export function orderIds(ids: Iterable<string>): string[] {
  const rest = [...ids].filter((id) => !(ELEMENT_ORDER as readonly string[]).includes(id)).sort();
  const known = ELEMENT_ORDER.filter((id) => [...ids].includes(id));
  return [...known, ...rest];
}
