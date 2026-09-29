/* The launcher's copy of the client's HUD placement contract, checked against the client.
 *
 * The first half asserts the same vectors as the mod's own AnchorTest and HudGridTest, so the two
 * implementations of the arithmetic cannot drift. The second half reads the mod's Java: the element
 * order, the default layout the launcher ships in catalog.json, and the Korean names both surfaces
 * print are the registry's own, and the only way to keep that true is to go and look.
 *
 * Run: node --test scripts/hud-contract.test.ts */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  ANCHORS,
  DEFAULT_GRID_SNAP,
  ELEMENT_ORDER,
  col,
  computeOffsetX,
  computeOffsetY,
  computeX,
  computeY,
  drag,
  fromGrid,
  isAnchor,
  nearest,
  orderIds,
  place,
  reanchor,
  row,
  snap,
  sanitizeSnap,
  u,
  v,
} from "../src/core/hud.ts";
import en from "../src/i18n/en.ts";
import ko from "../src/i18n/ko.ts";

const HUD_JAVA = new URL("../../i3/mod/src/main/java/dev/fullmoon/client/hud/", import.meta.url);
const java = (name: string) => readFileSync(new URL(name, HUD_JAVA), "utf8");
const editor = readFileSync(new URL("../src/widgets/HudEditor.tsx", import.meta.url), "utf8");
const screens = readFileSync(new URL("../src/styles/screens.css", import.meta.url), "utf8");

test("a corner anchor places at its offset and reads the offset back", () => {
  const b = place("TOP_LEFT", 960, 540, 100, 40, 12, 16);
  assert.deepEqual(b, { x: 12, y: 16, w: 100, h: 40 });
  assert.equal(computeOffsetX("TOP_LEFT", 960, 100, b.x), 12);
  assert.equal(computeOffsetY("TOP_LEFT", 540, 40, b.y), 16);

  const r = place("TOP_RIGHT", 960, 540, 100, 40, 12, 16);
  assert.equal(r.x, 960 - 100 - 12);
  assert.equal(r.y, 16);
  assert.equal(computeOffsetX("TOP_RIGHT", 960, 100, r.x), 12);
});

test("a centre anchor splits what is left over", () => {
  const b = place("BOTTOM_CENTER", 960, 540, 200, 50, 0, 20);
  assert.equal(b.x, (960 - 200) / 2);
  assert.equal(b.y, 540 - 50 - 20);
  assert.equal(computeOffsetX("BOTTOM_CENTER", 960, 200, b.x), 0);
  assert.equal(computeOffsetY("BOTTOM_CENTER", 540, 50, b.y), 20);
});

test("an odd screen truncates the way Java's integer division does", () => {
  // 861 / 2 is 430.5; Java gives 430, and Math.floor would too — but a negative would part them,
  // and an element wider than the screen is exactly that case
  const b = place("CENTER", 961, 541, 100, 40, 0, 0);
  assert.equal(b.x, 430);
  assert.equal(b.y, 250);
  assert.equal(computeX("CENTER", 100, 300, 0), -100);
  assert.equal(computeOffsetX("CENTER", 100, 300, -100), 0);
});

test("every anchor inverts its own placement", () => {
  for (const a of ANCHORS) {
    const b = place(a, 960, 540, 100, 40, 13, 17);
    assert.equal(computeOffsetX(a, 960, 100, b.x), 13, a);
    assert.equal(computeOffsetY(a, 540, 40, b.y), 17, a);
  }
});

test("a resolution change keeps the distance from the anchor", () => {
  const small = place("BOTTOM_RIGHT", 960, 540, 120, 30, 16, 16);
  const large = place("BOTTOM_RIGHT", 1920, 1080, 120, 30, 16, 16);
  assert.equal(960 - (small.x + small.w), 16);
  assert.equal(1920 - (large.x + large.w), 16);
  assert.equal(1080 - (large.y + large.h), 16);
});

test("a cell names its anchor and off the grid is the nearest corner", () => {
  assert.equal(fromGrid(2, 1), "CENTER_RIGHT");
  assert.equal(fromGrid(-4, -1), "TOP_LEFT");
  assert.equal(fromGrid(9, 9), "BOTTOM_RIGHT");
});

test("an anchor reports the cell and the fractions it was built from", () => {
  assert.equal(col("BOTTOM_CENTER"), 1);
  assert.equal(row("BOTTOM_CENTER"), 2);
  assert.equal(u("BOTTOM_CENTER"), 0.5);
  assert.equal(v("BOTTOM_CENTER"), 1);
  assert.ok(isAnchor("CENTER"));
  assert.ok(!isAnchor("MIDDLE_LEFT"));
});

test("nearest bins a placed element, and a screen with no size still names one", () => {
  assert.equal(nearest(960, 540, 100, 30, 20, 20), "TOP_LEFT");
  assert.equal(nearest(960, 540, 100, 30, 800, 20), "TOP_RIGHT");
  assert.equal(nearest(960, 540, 100, 30, 430, 500), "BOTTOM_CENTER");
  assert.equal(nearest(960, 540, 100, 30, 430, 250), "CENTER");
  assert.equal(nearest(0, 0, 100, 30, 0, 0), "BOTTOM_RIGHT");
});

test("a coordinate goes to the nearest grid line, and a tie goes up", () => {
  assert.equal(snap(1, 4), 0);
  assert.equal(snap(3, 4), 4);
  assert.equal(snap(57, 4), 56);
  assert.equal(snap(2, 4), 4);
  assert.equal(snap(-6, 4), -4);
  assert.equal(snap(-7, 4), -8);
  assert.equal(snap(57, 1), 57);
});

test("a step the file cannot mean falls back to the default", () => {
  assert.equal(sanitizeSnap(0), 4);
  assert.equal(sanitizeSnap(-16), 4);
  assert.equal(sanitizeSnap(Number.NaN), 4);
  assert.equal(sanitizeSnap(16), 16);
  assert.equal(snap(57, 0), 56);
});

test("a drag across the screen hands the element to the far cell", () => {
  const from = { anchor: "TOP_LEFT", offsetX: 16, offsetY: 56 } as const;
  const moved = drag({ from, screenW: 640, screenH: 360, elementW: 166, elementH: 20, dx: 400, dy: 0, step: 4 });
  // top-left went to 416, whose centre is at 499/640 — past the two-thirds line, so the right column
  assert.deepEqual(moved, { anchor: "TOP_RIGHT", offsetX: 640 - 166 - 416, offsetY: 56 });
});

test("a drag off the left edge clamps instead of storing a negative offset", () => {
  const from = { anchor: "TOP_LEFT", offsetX: 16, offsetY: 56 } as const;
  const moved = drag({ from, screenW: 640, screenH: 360, elementW: 166, elementH: 20, dx: -100, dy: -100, step: 4 });
  assert.deepEqual(moved, { anchor: "TOP_LEFT", offsetX: 0, offsetY: 0 });
});

test("a drag that does not move does not move the element", () => {
  const from = { anchor: "BOTTOM_RIGHT", offsetX: 16, offsetY: 56 } as const;
  const still = drag({ from, screenW: 640, screenH: 360, elementW: 76, elementH: 92, dx: 0, dy: 0, step: 4 });
  assert.deepEqual(still, { anchor: "BOTTOM_RIGHT", offsetX: 16, offsetY: 56 });
});

test("picking a cell by hand keeps the offset the element already had", () => {
  assert.deepEqual(reanchor({ anchor: "TOP_LEFT", offsetX: 16, offsetY: 56 }, "CENTER"), {
    anchor: "CENTER",
    offsetX: 16,
    offsetY: 56,
  });
});

test("unknown ids sort after the client's own, and none are lost", () => {
  const ids = ["lantern", "fps", "aurora", "coords"];
  assert.deepEqual(orderIds(ids), ["coords", "fps", "aurora", "lantern"]);
  assert.deepEqual(orderIds(ELEMENT_ORDER), [...ELEMENT_ORDER]);
});

/* ── against the client's own source ─────────────────────────────────────────── */

test("the anchor vocabulary is the mod's enum, in the mod's order", () => {
  const declared = [...java("Anchor.java").matchAll(/^ {4}([A-Z_]+)\(\d, \d,/gm)].map((m) => m[1]);
  assert.deepEqual(declared, [...ANCHORS]);
});

test("the default grid step is the client's own", () => {
  const step = java("HudGrid.java").match(/DEFAULT_STEP = (\d+);/);
  assert.equal(Number(step?.[1]), DEFAULT_GRID_SNAP);
});

/* A default offset is a literal or one of the design's spacing tokens (Tokens.Space.COZY is the
   eight GUI px the glass HUD stands in from the edge); the token's value comes from tokens.json. */
const SPACE: Record<string, number> = JSON.parse(
  readFileSync(new URL("../../i3/design/tokens.json", import.meta.url), "utf8"),
).space;
const offset = (expr: string): number => {
  const token = expr.match(/^Tokens\.Space\.([A-Z_]+)$/);
  if (!token) return Number(expr);
  const value = SPACE[token[1].toLowerCase()];
  assert.ok(typeof value === "number", `${expr} is not a spacing token`);
  return value;
};

/** id, enabled, anchor and offsets as each element's constructor states them. */
function registryDefaults() {
  const registry = java("HudElementRegistry.java");
  const classes = [...registry.matchAll(/register\(new (\w+)\(\)\);/g)].map((m) => m[1]);
  return classes.map((cls) => {
    const ctor = java(`${cls}.java`).match(
      /super\("(\w+)", "[^"]*", "[^"]*", (true|false), Anchor\.([A-Z_]+), (-?\d+|Tokens\.Space\.[A-Z_]+), (-?\d+|Tokens\.Space\.[A-Z_]+)\)/,
    );
    assert.ok(ctor, `${cls} does not state its defaults where this test can read them`);
    return {
      id: ctor[1],
      enabled: ctor[2] === "true",
      anchor: ctor[3],
      offsetX: offset(ctor[4]),
      offsetY: offset(ctor[5]),
      scale: 1,
    };
  });
}

test("the launcher lists the elements in the order the client registers them", () => {
  assert.deepEqual(registryDefaults().map((e) => e.id), [...ELEMENT_ORDER]);
});

test("the layout the catalogue ships is the one the client would have built itself", () => {
  const catalog = JSON.parse(
    readFileSync(new URL("../src-tauri/resources/catalog.json", import.meta.url), "utf8"),
  );
  const shipped = catalog.defaultHud;
  assert.equal(shipped.gridSnap, DEFAULT_GRID_SNAP);
  assert.deepEqual(Object.keys(shipped.elements), [...ELEMENT_ORDER]);
  for (const { id, ...want } of registryDefaults()) {
    assert.deepEqual(shipped.elements[id], want, id);
  }
});

/** anchor → the pair of names the enum carries. */
function anchorNames() {
  const rows = [
    ...java("Anchor.java").matchAll(
      /^ {4}([A-Z_]+)\(\d, \d, [\d.]+f, [\d.]+f, "([^"]+)", "([^"]+)"\)/gm,
    ),
  ];
  assert.equal(rows.length, ANCHORS.length);
  return rows.map(([, id, koName, enName]) => ({ id, ko: koName, en: enName }));
}

test("the anchor names the launcher prints are the ones the client prints", () => {
  // the client puts its own name in the badge over a selected element; a launcher that
  // translated the same nine cells differently would be describing another screen
  for (const { id, ko: koName, en: enName } of anchorNames()) {
    assert.equal(ko.settings.anchors[id as keyof typeof ko.settings.anchors], koName, id);
    assert.equal(en.settings.anchors[id as keyof typeof en.settings.anchors], enName, id);
  }
});

test("the Korean element names are the client's own, and every id has an English one", () => {
  const registry = java("HudElementRegistry.java");
  const classes = [...registry.matchAll(/register\(new (\w+)\(\)\);/g)].map((m) => m[1]);
  for (const cls of classes) {
    const ctor = java(`${cls}.java`).match(/super\("(\w+)", "([^"]*)"/);
    assert.ok(ctor, `${cls} does not state its name where this test can read them`);
    const [, id, name] = ctor;
    assert.equal(ko.settings.elements[id as keyof typeof ko.settings.elements], name, id);
    // English is the launcher's own: the mod carries no English element names, so this pins
    // presence, not wording
    assert.match(en.settings.elements[id as keyof typeof en.settings.elements] ?? "", /\S/, id);
  }
});

test("the settings heading names the HUD surface available below it", () => {
  assert.ok(ko.topbar.sub.settings.includes(ko.settings.hudSection));
  assert.ok(en.topbar.sub.settings.includes(en.settings.hudSection));
});

test("pointer selection leaves the selected HUD element ready for arrow keys", () => {
  assert.match(editor, /e\.currentTarget\.focus\(\)/);
  assert.match(editor, /onKeyDown=\{\(e\) => onKey\(e, id\)\}/);
});

test("reset does not toast when the restored layout is already visible", () => {
  assert.doesNotMatch(editor, /toast\("success"/);
});

test("the HUD editor stacks before its stage becomes too small to read", () => {
  assert.match(
    screens,
    /@media\s*\(max-width:\s*1180px\)[\s\S]*?\.hud-editor\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/,
  );
});
