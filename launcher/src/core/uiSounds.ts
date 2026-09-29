/* The launcher's UI cues are the game's: the seven original OGGs the mod ships under
   assets/fullmoon/sounds/ui, synthesised by i3/design/make-ui-sounds.py. They play only when the
   player turns 메뉴 소리 on (off by default), at the token volume, with the same 45 ms gate and
   ±4 % pitch spread on the focus tick that UiSounds applies in game. */

import focusUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/focus.ogg";
import confirmUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/confirm.ogg";
import backUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/back.ogg";
import openUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/open.ogg";
import closeUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/close.ogg";
import errorUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/error.ogg";
import tabUrl from "../../../i3/mod/src/main/resources/assets/fullmoon/sounds/ui/tab.ogg";

export type Cue = "focus" | "confirm" | "back" | "open" | "close" | "error" | "tab";

const URLS: Record<Cue, string> = {
  focus: focusUrl,
  confirm: confirmUrl,
  back: backUrl,
  open: openUrl,
  close: closeUrl,
  error: errorUrl,
  tab: tabUrl,
};

/** tokens.json sound: volume 0.5, focusIntervalMs 45, focusPitchJitter 0.04 */
export const SOUND = { volume: 0.5, focusIntervalMs: 45, focusPitchJitter: 0.04 } as const;

let enabled = false;
let lastFocusAt = 0;
const pool = new Map<Cue, HTMLAudioElement>();

export function setUiSoundsEnabled(on: boolean): void {
  enabled = on;
}

export function uiSoundsEnabled(): boolean {
  return enabled;
}

function element(cue: Cue): HTMLAudioElement | null {
  if (typeof Audio === "undefined") return null;
  let el = pool.get(cue);
  if (!el) {
    el = new Audio(URLS[cue]);
    el.preload = "auto";
    pool.set(cue, el);
  }
  return el;
}

/** Plays a cue if the setting is on. The focus tick is rate-limited and pitch-jittered. */
export function play(cue: Cue): void {
  if (!enabled) return;
  const now = performance.now();
  if (cue === "focus") {
    if (now - lastFocusAt < SOUND.focusIntervalMs) return;
    lastFocusAt = now;
  }
  const el = element(cue);
  if (!el) return;
  try {
    el.pause();
    el.currentTime = 0;
    el.volume = SOUND.volume;
    el.playbackRate = cue === "focus" ? 1 + (Math.random() * 2 - 1) * SOUND.focusPitchJitter : 1;
    void el.play().catch(() => {
      /* autoplay policy before the first gesture: silence is the right answer */
    });
  } catch {
    /* a detached element in a test runtime */
  }
}
