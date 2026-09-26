/* The launcher's moon is the title screen's moon: same constants, same fixtures as the mod's
   MoonPhaseTest, and the same terminator the moon shader cuts along. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  SYNODIC,
  daysToFull,
  daysToNextFull,
  isFull,
  litPath,
  moonAt,
  moonName,
} from "../src/core/moonPhase.ts";

const JAVA = readFileSync(
  new URL("../../i3/mod/src/main/java/dev/fullmoon/client/title/MoonPhase.java", import.meta.url),
  "utf8",
);
const EPOCH_NEW = Date.parse("2000-01-06T18:14:00Z");
const daysAfterEpoch = (days: number) => EPOCH_NEW + Math.round(days * 86_400_000);

test("the constants are the mod's own", () => {
  assert.equal(Number(JAVA.match(/SYNODIC = ([\d.]+);/)?.[1]), SYNODIC);
  assert.equal(JAVA.match(/EPOCH = Instant\.parse\("([^"]+)"\)/)?.[1], "2000-01-06T18:14:00Z");
  assert.match(JAVA, /FULL = 0\.98f/);
  assert.match(JAVA, /NEW = 0\.02f/);
});

test("the epoch is a new moon", () => {
  const phase = moonAt(EPOCH_NEW);
  assert.ok(Math.abs(phase.lit) < 1e-6);
  assert.equal(moonName(phase), "new");
});

test("a quarter month later half the disc is lit on the right", () => {
  const phase = moonAt(daysAfterEpoch(SYNODIC / 4));
  assert.ok(Math.abs(phase.lit - 0.5) < 1e-4);
  assert.equal(phase.waxing, true);
  assert.equal(moonName(phase), "first_quarter");
});

test("three quarters later it is waning", () => {
  const phase = moonAt(daysAfterEpoch(SYNODIC * 0.75));
  assert.ok(Math.abs(phase.lit - 0.5) < 1e-4);
  assert.equal(phase.waxing, false);
  assert.equal(moonName(phase), "last_quarter");
});

test("the harvest moon of 2026 is full on the evening of 26 September in Korea", () => {
  const phase = moonAt(new Date("2026-09-26T13:00:00Z"));
  assert.equal(isFull(phase), true);
  assert.equal(moonName(phase), "full");
  assert.equal(daysToFull(phase), 0);
  assert.equal(daysToNextFull(phase), 30);
});

test("a week before full the countdown is a week", () => {
  const phase = moonAt(new Date("2026-09-19T13:00:00Z"));
  assert.equal(phase.waxing, true);
  assert.equal(daysToFull(phase), 7);
});

test("dates before the epoch still land inside the month", () => {
  const phase = moonAt(daysAfterEpoch(-SYNODIC * 3 - 1));
  assert.ok(phase.age >= 0 && phase.age < SYNODIC);
});

test("the lit face follows the shader's terminator", () => {
  assert.equal(litPath(0, 10), null);
  // a crescent closes along an ellipse that bulges toward the lit limb
  assert.equal(litPath(0.25, 10), "M0 -10A10 10 0 0 1 0 10A5 10 0 0 0 0 -10Z");
  // half lit: the terminator is the straight diameter
  assert.equal(litPath(0.5, 10), "M0 -10A10 10 0 0 1 0 10A0 10 0 0 1 0 -10Z");
  // full: the second arc is the other half of the disc
  assert.equal(litPath(1, 10), "M0 -10A10 10 0 0 1 0 10A10 10 0 0 1 0 -10Z");
});
