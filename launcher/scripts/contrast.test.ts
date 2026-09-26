/* The launcher reads its colours from the tokens i3/design/generate.mjs writes, and its small
   meta text clears the small-text floor in both palaces. */
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import test from "node:test";
import { contrast, oklchToRgb } from "../../i3/design/_oklch.mjs";

const read = (rel: string) => readFileSync(new URL(rel, import.meta.url), "utf8");
const css = read("../src/design/tokens.css");

const block = (selector: string) => {
  const at = css.indexOf(`${selector} {`);
  assert.ok(at >= 0, `${selector} block is generated`);
  return css.slice(at, css.indexOf("\n}", at));
};

const hexOf = (source: string, name: string) => {
  const m = source.match(new RegExp(`--color-${name}: oklch\\(([\\d.]+) ([\\d.]+) ([\\d.]+)\\)`));
  assert.ok(m, `--color-${name} is declared`);
  const [r, g, b] = oklchToRgb(Number(m[1]), Number(m[2]), Number(m[3])) as number[];
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`;
};

for (const [palace, selector] of [["night", '[data-theme="dark"]'], ["day", '[data-theme="light"]']] as const) {
  test(`${palace}: tertiary text clears the small-text floor on every ground it sits on`, () => {
    const src = block(selector);
    const ink = hexOf(src, "ink-tertiary");
    for (const ground of ["surface-base", "surface-sunken", "surface-void"]) {
      const ratio = contrast(ink, hexOf(src, ground)) as number;
      assert.ok(ratio >= 4.5, `ink-tertiary on ${ground} is ${ratio.toFixed(2)}:1`);
    }
  });
}

test("the page loads the generated tokens before it paints", () => {
  assert.match(read("../index.html"), /<link rel="stylesheet" href="\/src\/design\/tokens\.css"/);
});

test("the old sky and moon palettes are gone from the launcher's styles", () => {
  const dir = new URL("../src/styles/", import.meta.url);
  for (const name of readdirSync(dir)) {
    const source = readFileSync(new URL(name, dir), "utf8");
    assert.doesNotMatch(source, /--(sky|moon|teal|lavender|rose|amber)-\d/, name);
  }
});
