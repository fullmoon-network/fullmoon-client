/* The skin figure's draw gate, counted in frames. A fake viewer stands in for skinview3d's
 * animation-frame loop: `frame()` is one requestAnimationFrame tick and only draws while the
 * viewer is not paused, exactly as the real loop does.
 *
 * Run with `node --test scripts/`. */
import assert from "node:assert/strict";
import { test } from "node:test";

import { createRenderGate } from "../src/core/renderGate.ts";

function fakeViewer() {
  const v = {
    renderPaused: false,
    disposed: false,
    frames: 0,
    render() {
      v.frames++;
    },
    /** one animation-frame tick of the loop */
    frame() {
      if (!v.renderPaused && !v.disposed) v.render();
    },
    run(n: number) {
      for (let i = 0; i < n; i++) v.frame();
    },
  };
  return v;
}

test("a figure on screen with no game draws every frame", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v);
  gate.set({ loaded: true });
  v.run(600);
  assert.equal(v.frames, 600);
});

test("a live game stops the loop: 0 frames over 600 ticks, one last draw to keep the canvas", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v);
  gate.set({ loaded: true });
  v.run(10);
  const before = v.frames;
  gate.set({ inGame: true });
  v.run(600);
  assert.equal(v.frames - before, 1);
  assert.equal(v.renderPaused, true);
});

test("a hidden window or an off-screen canvas stops it, and each is needed to resume", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v);
  gate.set({ loaded: true, tab: false });
  const paused = v.frames;
  v.run(100);
  assert.equal(v.frames, paused);

  gate.set({ onScreen: false });
  gate.set({ tab: true });
  v.run(100);
  assert.equal(v.frames, paused, "tab is back but the canvas is still off screen");

  gate.set({ onScreen: true });
  v.run(100);
  assert.equal(v.frames, paused + 100);
});

test("the game ending resumes drawing", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v);
  gate.set({ loaded: true, inGame: true });
  v.run(50);
  gate.set({ inGame: false });
  const resumed = v.frames;
  v.run(30);
  assert.equal(v.frames - resumed, 30);
});

test("nothing pauses before the skin is in, and a game that was running at mount holds it still after", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v, { inGame: true });
  v.run(5);
  assert.equal(v.frames, 5, "still loading: the loop runs until there is something to keep");
  gate.set({ loaded: true });
  assert.equal(v.renderPaused, true);
  assert.equal(v.frames, 6, "the loaded skin is drawn once before it stops");
  v.run(100);
  assert.equal(v.frames, 6);
});

test("a disposed viewer is left alone", () => {
  const v = fakeViewer();
  const gate = createRenderGate(v);
  v.disposed = true;
  gate.set({ loaded: true, inGame: true });
  assert.equal(v.renderPaused, false);
  assert.equal(v.frames, 0);
});
