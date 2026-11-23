/* The UI half of the clip feature, checked against the frozen core contract.
 *
 * The Rust core lives in another worktree, so this test reads the hand-maintained
 * bindings instead: every command the screen calls must be declared there and
 * implemented by MockCore, and both dictionaries must carry the same copy. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import en from "../src/i18n/en.ts";
import ko from "../src/i18n/ko.ts";

const source = (path: string) =>
  readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const bindings = source("src/core/bindings.ts");
const mock = source("src/core/mockCore.ts");
const screen = source("src/screens/Clips.tsx");
const sidebar = source("src/components/Sidebar.tsx");
const palette = source("src/components/CommandPalette.tsx");
const app = source("src/App.tsx");
const store = source("src/state/store.tsx");

const CLIP_METHODS = [
  "clips_list",
  "clips_status",
  "clips_save",
  "clips_replay_start",
  "clips_replay_stop",
  "clips_delete",
  "clips_reveal",
  "clips_open",
  "clips_settings_get",
  "clips_settings_set",
  "clips_thumbnail",
] as const;

function flatten(value: unknown, prefix = "", out: Record<string, string> = {}): Record<string, string> {
  if (typeof value === "string") {
    out[prefix] = value;
    return out;
  }
  if (value !== null && typeof value === "object") {
    for (const [key, child] of Object.entries(value)) {
      flatten(child, prefix ? `${prefix}.${key}` : key, out);
    }
  }
  return out;
}

test("all ten clip commands are non-optional on the frozen contract", () => {
  const section = bindings.slice(bindings.indexOf("// clips —"), bindings.indexOf("// event subscription"));
  assert.ok(section.length > 0, "the clips section of bindings.ts is missing");
  for (const method of CLIP_METHODS) {
    const declared = section.match(new RegExp(`^\\s*${method}\\([^)]*\\): Promise<[^;]+;`, "m"));
    assert.ok(declared, `${method} is not declared as a method on PinionCore`);
    assert.doesNotMatch(declared![0], /\?/, `${method} must not be optional`);
  }
});

test("MockCore implements all ten clip commands", () => {
  assert.match(mock, /class MockCore implements PinionCore/);
  const section = mock.slice(mock.indexOf("/* ── clips:"));
  assert.ok(section.length > 0, "the clips section of MockCore is missing");
  for (const method of CLIP_METHODS) {
    assert.match(section, new RegExp(`async ${method}\\(`), `${method} is missing from MockCore`);
  }
});

test("the clips screen is wired into navigation and the store", () => {
  assert.match(screen, /export function ClipsScreen/);
  assert.match(screen, /core\.clips_save\(\)/);
  assert.match(screen, /clips:\/\/saved/);
  assert.match(screen, /clips:\/\/error/);
  assert.match(sidebar, /\{ id: "clips", icon: "clips" \}/);
  assert.match(palette, /\{ id: "clips", icon: "clips" \}/);
  assert.match(app, /clips: ClipsScreen/);
  assert.match(store, /"mods" \| "clips"/);
  assert.match(store, /core\.on\("clips:\/\/saved"/);
  assert.match(store, /core\.on\("clips:\/\/error"/);
});

test("the Korean clip copy is polite 해요체, never 합쇼체 or 반말", () => {
  const leaves = flatten(ko.clips, "clips");
  assert.ok(Object.keys(leaves).length >= 35, "the clips dictionary is unexpectedly small");
  for (const [path, text] of Object.entries(leaves)) {
    assert.doesNotMatch(text, /(습니다|합니다|입니다|해라|한다|했다)\.?$/, `clips.${path}`);
  }
  for (const key of ["subtitle", "enabledHint", "setupHelp", "passwordNote", "settingsSaved", "savedToast", "deletedToast", "emptyHint"]) {
    assert.match(leaves[`clips.${key}`] ?? "", /(요\.|세요\.)$/, `clips.${key} needs a 해요체 ending`);
  }
  assert.equal(leaves["clips.saveNow"], "지금 클립 저장");
  assert.equal(leaves["clips.emptyTitle"], "아직 클립이 없어요");
  assert.equal(leaves["clips.savedToast"], "클립을 저장했어요.");
  assert.match(leaves["clips.emptyHint"], /^OBS를 켜고 다시보기 버퍼를 시작한 뒤 저장해 보세요\./);
});

test("neither dictionary misses a leaf key", () => {
  const koKeys = Object.keys(flatten(ko)).sort();
  const enKeys = Object.keys(flatten(en)).sort();
  assert.deepEqual(koKeys, enKeys);
  assert.ok(koKeys.includes("nav.clips"));
  assert.ok(koKeys.includes("topbar.sub.clips"));
  assert.ok(koKeys.includes("clips.setupTitle"));
});
