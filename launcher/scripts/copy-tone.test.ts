/* Everything a player reads in Korean is friendly 해요체, and the second server is 야생.
   The dictionary, the mock core's fixtures and the catalogue the real core ships are the three
   places player-facing Korean lives; the views carry none of their own. */
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import ko from "../src/i18n/ko.ts";

const root = fileURLToPath(new URL("..", import.meta.url));
const read = (rel: string) => readFileSync(join(root, rel), "utf8");
const HANGUL = /[가-힣]/;

function strings(node: unknown, out: string[] = []): string[] {
  if (typeof node === "string") out.push(node);
  else if (Array.isArray(node)) node.forEach((n) => strings(n, out));
  else if (node && typeof node === "object") Object.values(node).forEach((n) => strings(n, out));
  return out;
}

/** String literals in a source file that carry Hangul. */
function literals(source: string): string[] {
  return [...source.matchAll(/"([^"\n]*)"|`([^`]*)`/g)]
    .map((m) => m[1] ?? m[2])
    .filter((s) => HANGUL.test(s));
}

/* 합쇼체 anywhere; the plain declarative at the end of a sentence; the plain propositive and
   questions where punctuation marks them as sentences. A one-word string is a label, not a
   sentence, and nouns end in 다 and 자 as often as verbs do. */
const STIFF = /(습니다|습니까|ㅂ니다)/;
const PLAIN_DECLARATIVE = /[가-힣]다(?=[.!?]|\s*$)/;
const PLAIN_OTHER = /[가-힣](자|냐|니)(?=[.!?])/;

function offenders(list: string[]): string[] {
  return list.filter((s) => {
    if (!HANGUL.test(s)) return false;
    if (STIFF.test(s)) return true;
    if (!/\s/.test(s.trim())) return false;
    return PLAIN_DECLARATIVE.test(s) || PLAIN_OTHER.test(s);
  });
}

const catalog = JSON.parse(read("src-tauri/resources/catalog.json"));
const mock = read("src/core/mockCore.ts");

test("the dictionary speaks 해요체", () => {
  assert.deepEqual(offenders(strings(ko)), []);
});

test("the mock core's fixtures speak 해요체", () => {
  assert.deepEqual(offenders(literals(mock)), []);
});

test("the catalogue the real core ships speaks 해요체", () => {
  assert.deepEqual(offenders(strings(catalog)), []);
});

test("the second server is 야생, never 생야생", () => {
  const files: string[] = [];
  (function walk(dir: string) {
    for (const name of readdirSync(dir)) {
      const p = join(dir, name);
      if (statSync(p).isDirectory()) walk(p);
      else if (/\.(ts|tsx|json|css|html)$/.test(name)) files.push(p);
    }
  })(join(root, "src"));
  files.push(join(root, "src-tauri/resources/catalog.json"), join(root, "index.html"));
  const hits = files.filter((f) => readFileSync(f, "utf8").includes("생야생"));
  assert.deepEqual(hits, []);
});

test("views carry no Korean of their own; it all goes through the dictionary", () => {
  const views: string[] = [];
  (function walk(dir: string) {
    for (const name of readdirSync(dir)) {
      const p = join(dir, name);
      if (statSync(p).isDirectory()) walk(p);
      else if (name.endsWith(".tsx")) views.push(p);
    }
  })(join(root, "src"));
  // a language's own name is how a picker lets a reader find it; the effects chip's demo reading is
  // the mod's own editor text (StatusEffectsHud), which the HUD stage shows verbatim
  const allowed = new Set(["한국어", "신속 II · 02:45"]);
  const found = views.flatMap((f) =>
    literals(readFileSync(f, "utf8"))
      .concat([...readFileSync(f, "utf8").matchAll(/>([^<>{}]*[가-힣][^<>{}]*)</g)].map((m) => m[1].trim()))
      .filter((s) => !allowed.has(s))
      .map((s) => `${f.slice(root.length)}: ${s}`),
  );
  assert.deepEqual(found, []);
});
