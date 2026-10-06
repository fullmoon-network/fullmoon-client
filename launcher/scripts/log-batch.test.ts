/* The console's line batching, counted in React commits. Each `deliver` is one `setLogs`, so one
 * render of whatever reads the logs; before batching there was one per line.
 *
 * Run with `node --test scripts/`. */
import assert from "node:assert/strict";
import { test } from "node:test";

import { LOG_FLUSH_MS, LOG_KEEP, appendCapped, createBatcher, type Timers } from "../src/core/logBatch.ts";

function fakeClock() {
  let now = 0;
  let next = 1;
  const due = new Map<number, { at: number; fn: () => void }>();
  const timers: Timers = {
    set: (fn, ms) => {
      due.set(next, { at: now + ms, fn });
      return next++;
    },
    clear: (h) => void due.delete(h as number),
  };
  return {
    timers,
    pending: () => due.size,
    advance(ms: number) {
      now += ms;
      for (const [id, t] of [...due]) {
        if (t.at <= now) {
          due.delete(id);
          t.fn();
        }
      }
    },
  };
}

test("a burst of 800 lines inside one window is one commit, in order", () => {
  const clock = fakeClock();
  const commits: number[][] = [];
  const b = createBatcher<number>((items) => commits.push(items), LOG_FLUSH_MS, clock.timers);
  for (let i = 0; i < 800; i++) b.push(i);
  assert.equal(commits.length, 0, "nothing is delivered before the window closes");
  clock.advance(LOG_FLUSH_MS);
  assert.equal(commits.length, 1);
  assert.deepEqual(commits[0], Array.from({ length: 800 }, (_, i) => i));
});

test("a 10 second boot at 80 lines a second commits at most 10 times a second, not 80", () => {
  const clock = fakeClock();
  let commits = 0;
  let lines = 0;
  const b = createBatcher<number>((items) => {
    commits++;
    lines += items.length;
  }, LOG_FLUSH_MS, clock.timers);
  let pushed = 0;
  for (let ms = 0; ms < 10_000; ms++) {
    if (ms % 12 === 0) {
      b.push(ms); // ~80 lines/s
      pushed++;
    }
    clock.advance(1);
  }
  clock.advance(LOG_FLUSH_MS);
  assert.ok(pushed >= 800, `pushed: ${pushed}`);
  assert.equal(lines, pushed, "every line arrives");
  assert.ok(commits <= 100, `commits: ${commits}`);
});

test("an idle stream arms no timer", () => {
  const clock = fakeClock();
  const b = createBatcher<number>(() => {}, LOG_FLUSH_MS, clock.timers);
  b.push(1);
  clock.advance(LOG_FLUSH_MS);
  assert.equal(clock.pending(), 0);
  b.flush();
  assert.equal(clock.pending(), 0);
});

test("flush delivers now and disarms; cancel drops what is waiting", () => {
  const clock = fakeClock();
  const commits: number[][] = [];
  const b = createBatcher<number>((items) => commits.push(items), LOG_FLUSH_MS, clock.timers);
  b.push(1);
  b.push(2);
  b.flush();
  assert.deepEqual(commits, [[1, 2]]);
  assert.equal(clock.pending(), 0);
  b.push(3);
  b.cancel();
  clock.advance(1000);
  assert.deepEqual(commits, [[1, 2]]);
});

test("appending a batch leaves what appending line by line with the old trim did", () => {
  const old = (list: number[], line: number) => [...list.slice(-900), line];
  for (const [have, add] of [[0, 5], [895, 10], [901, 1], [901, 300], [10, 2000]]) {
    const list = Array.from({ length: have }, (_, i) => i);
    const batch = Array.from({ length: add }, (_, i) => 10_000 + i);
    assert.deepEqual(appendCapped(list, batch), batch.reduce(old, list), `have ${have}, add ${add}`);
  }
  assert.equal(appendCapped([], Array.from({ length: 5000 }, (_, i) => i)).length, LOG_KEEP);
});
