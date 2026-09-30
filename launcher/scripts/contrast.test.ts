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

/* A token is a hex, an oklch triple, or an rgb with an alpha (a translucent ground); the last is
   composited over the block's own void, the darkest thing it can sit on. */
const rgbOf = (source: string, name: string): [number, number, number] => {
  const line = source.match(new RegExp(`--color-${name}: ([^;]+);`));
  assert.ok(line, `--color-${name} is declared`);
  const value = line[1].trim();
  let m = value.match(/^#([0-9a-f]{6})$/i);
  if (m) return [0, 2, 4].map((i) => parseInt(m![1].slice(i, i + 2), 16)) as [number, number, number];
  m = value.match(/^oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)$/);
  if (m) return oklchToRgb(Number(m[1]), Number(m[2]), Number(m[3])) as [number, number, number];
  m = value.match(/^rgb\((\d+) (\d+) (\d+) \/ ([\d.]+)\)$/);
  if (m) {
    const a = Number(m[4]);
    const top = [Number(m[1]), Number(m[2]), Number(m[3])];
    const under = name === "surface-void" ? [0, 0, 0] : rgbOf(source, "surface-void");
    return top.map((v, i) => Math.round(v * a + under[i] * (1 - a))) as [number, number, number];
  }
  throw new Error(`--color-${name} has an unexpected form: ${value}`);
};
const hexOf = (source: string, name: string) =>
  `#${rgbOf(source, name).map((v) => v.toString(16).padStart(2, "0")).join("")}`;

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
