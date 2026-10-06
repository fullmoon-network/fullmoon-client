/* When the skin figure may draw. skinview3d runs its own animation-frame loop until told to stop,
   so something has to say when it should: never while the window is hidden, the canvas is off
   screen or a game is live, and never before the skin is in (there is nothing to draw yet).
   `scripts/render-gate.test.ts` drives this against a fake viewer and counts the frames. */

export interface Pausable {
  renderPaused: boolean;
  readonly disposed: boolean;
  render(): void;
}

export interface Gates {
  /** the window is showing */
  tab: boolean;
  /** the canvas is inside the viewport */
  onScreen: boolean;
  /** a game is starting or running */
  inGame: boolean;
  /** the skin texture has arrived */
  loaded: boolean;
}

export function createRenderGate(viewer: Pausable, initial: Partial<Gates> = {}) {
  const gates: Gates = { tab: true, onScreen: true, inGame: false, loaded: false, ...initial };
  const settle = () => {
    if (viewer.disposed || !gates.loaded) return;
    const run = gates.tab && gates.onScreen && !gates.inGame;
    /* the canvas keeps what was last drawn, so draw once more with the newest state before
       stopping — otherwise a figure paused just after its skin loaded stays blank */
    if (!run && !viewer.renderPaused) viewer.render();
    if (viewer.renderPaused !== !run) viewer.renderPaused = !run;
  };
  return {
    set(patch: Partial<Gates>) {
      Object.assign(gates, patch);
      settle();
    },
    settle,
  };
}
