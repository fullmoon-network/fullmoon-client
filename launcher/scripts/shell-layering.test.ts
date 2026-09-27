import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const css = readFileSync(fileURLToPath(new URL("../src/styles/shell.css", import.meta.url)), "utf8");
const backdrop = readFileSync(
  fileURLToPath(new URL("../src/widgets/AtmosphericBackdrop.tsx", import.meta.url)),
  "utf8",
);

const rule = (selector: string) => {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`));
  assert.ok(match, `${selector} rule is missing`);
  return match[1];
};

test("the atmospheric backdrop is painted behind every launcher surface", () => {
  assert.match(rule(".app"), /\bisolation:\s*isolate\s*;/);
  assert.match(rule(".game-backdrop"), /\bz-index:\s*-1\s*;/);
});

test("the night carries no coloured halo and does not move", () => {
  // a glow on a dark ground is a shadow in disguise, and the tokens forbid it
  assert.doesNotMatch(rule(".game-backdrop"), /radial-gradient|blur\(/);
  assert.doesNotMatch(css, /\.nebula-|\.twinkle-/);
  assert.doesNotMatch(backdrop, /nebula|twinkle|animation/);
});

test("the play screen's hero renders on the play route and nowhere else", () => {
  const src = (rel: string) => readFileSync(fileURLToPath(new URL(`../src/${rel}`, import.meta.url)), "utf8");
  const app = src("App.tsx");
  const map = app.match(/const SCREENS = \{([\s\S]*?)\} as const;/)?.[1];
  assert.ok(map, "App.tsx maps screens to components in SCREENS");
  const routes = [...map.matchAll(/(\w+):\s*(\w+),/g)];
  assert.deepEqual(
    routes.filter(([, , component]) => component === "PlayScreen").map(([, route]) => route).sort(),
    ["home", "play"],
  );
  // one screen is mounted at a time, so the hero exists only where PlayScreen is the screen
  assert.match(app, /const Screen = SCREENS\[screen\];/);
  assert.doesNotMatch(src("screens/Dashboard.tsx"), /from "\.\/Play"/);
  for (const rel of ["screens/Home.tsx", "screens/Dashboard.tsx", "screens/Mods.tsx", "App.tsx"]) {
    assert.doesNotMatch(src(rel), /hero-plaque|className="hero"/, rel);
  }
});
