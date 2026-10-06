/* The launcher's WOFF2 files are baked, not built: i3/design/make-web-fonts.py writes them from
 * launcher/fonts-src and the mod's own font files, and records the source bytes it read. A source
 * that changed since then (the mod's face re-baked, a Pretendard update) means the launcher still
 * ships the old outlines until the script runs again. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import test from "node:test";

const file = (path: string) => new URL(`../../${path}`, import.meta.url);
const manifest = readFileSync(file("launcher/fonts-src/web-fonts.sha256"), "utf8")
  .trim()
  .split("\n")
  .map((line) => {
    const [sha256, source, output] = line.split(/\s{2}/);
    return { sha256, source, output };
  });

test("every shipped web font was baked from the sources as they are now", () => {
  for (const { sha256, source, output } of manifest) {
    assert.ok(existsSync(file(output)), `${output} is missing`);
    const now = createHash("sha256").update(readFileSync(file(source))).digest("hex");
    assert.equal(now, sha256, `${source} changed since ${output} was baked: run i3/design/make-web-fonts.py`);
  }
});

test("nothing under public/fonts ships without a recorded source", () => {
  const listed = new Set(manifest.map((m) => m.output));
  for (const name of readdirSync(file("launcher/public/fonts"))) {
    assert.ok(listed.has(`launcher/public/fonts/${name}`), `${name} has no entry in web-fonts.sha256`);
  }
});
