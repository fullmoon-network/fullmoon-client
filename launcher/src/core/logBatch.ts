/* The game prints hundreds of lines in the seconds after it starts. Each line used to be its own
   state update; lines now gather here and reach React together, so a boot is a handful of renders
   rather than one per line. Pure, so `scripts/log-batch.test.ts` can count the flushes. */

/** the console keeps the last 901 lines: each append used to trim to 900 and then add itself */
export const LOG_KEEP = 901;
export const LOG_FLUSH_MS = 100;

/** what appending `batch` one by one, trimming as it went, would have left */
export function appendCapped<T>(list: readonly T[], batch: readonly T[]): T[] {
  return [...list, ...batch].slice(-LOG_KEEP);
}

export interface Timers {
  set: (fn: () => void, ms: number) => unknown;
  clear: (handle: unknown) => void;
}

const realTimers: Timers = {
  set: (fn, ms) => setTimeout(fn, ms),
  clear: (h) => clearTimeout(h as ReturnType<typeof setTimeout>),
};

export interface Batcher<T> {
  push: (item: T) => void;
  /** delivers what is waiting now, without waiting for the timer */
  flush: () => void;
  /** drops what is waiting and stops the timer */
  cancel: () => void;
}

/** Items pushed while a flush is pending join it; the first push after a flush arms the next one. */
export function createBatcher<T>(
  deliver: (items: T[]) => void,
  wait = LOG_FLUSH_MS,
  timers: Timers = realTimers,
): Batcher<T> {
  let waiting: T[] = [];
  let handle: unknown = null;
  const flush = () => {
    if (handle !== null) timers.clear(handle);
    handle = null;
    if (waiting.length === 0) return;
    const items = waiting;
    waiting = [];
    deliver(items);
  };
  return {
    push: (item) => {
      waiting.push(item);
      if (handle === null) handle = timers.set(flush, wait);
    },
    flush,
    cancel: () => {
      if (handle !== null) timers.clear(handle);
      handle = null;
      waiting = [];
    },
  };
}
