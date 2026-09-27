/* The launch surface counts only what the game has printed. These pin the matchers to a real
   client boot (i3/docs/evidence/p10-live-client-960x540.log, a Fabric client joining a Paper
   server) so a line that changes shape upstream fails here rather than stalling the surface. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { launchProgress, stepsFor } from "../src/core/launchSteps.ts";

const realBoot = readFileSync(
  new URL("../../i3/docs/evidence/p10-live-client-960x540.log", import.meta.url),
  "utf8",
).split("\n");

/** The real log, cut at the first line matching `re` (exclusive). */
const until = (re: RegExp) => realBoot.slice(0, realBoot.findIndex((l) => re.test(l)));

test("a real client joining a server passes every step", () => {
  const p = launchProgress(realBoot, true);
  assert.deepEqual(p.steps, ["start", "mods", "render", "resources", "menu", "connect", "joined"]);
  assert.equal(p.done, 7);
});

test("each step waits for its own line", () => {
  assert.equal(launchProgress(until(/Loading \d+ mods/), true).done, 1);
  assert.equal(launchProgress(until(/Backend library/), true).done, 2);
  assert.equal(launchProgress(until(/Reloading ResourceManager/), true).done, 3);
  assert.equal(launchProgress(until(/Sound engine started/), true).done, 4);
  assert.equal(launchProgress(until(/Connecting to/), true).done, 5);
  assert.equal(launchProgress(until(/Loaded \d+ advancements/), true).done, 6);
});

test("a launch without a server ends at the menu", () => {
  assert.equal(stepsFor(false).length, 5);
  assert.equal(launchProgress(realBoot, false).done, 5);
});

test("nothing printed is nothing proved", () => {
  assert.equal(launchProgress([], true).done, 0);
  assert.equal(launchProgress(["WARNING: A restricted method in java.lang.System has been called"], false).done, 0);
});

test("a backlog that opens mid-boot still counts what came before", () => {
  const tail = realBoot.slice(realBoot.findIndex((l) => /Sound engine started/.test(l)));
  assert.equal(launchProgress(tail, true).done, 7);
});
