/* What a starting game has actually done, read off the lines it prints.

   The launch surface shows progress as the steps the log has proved and nothing else: no timer,
   no estimate. Each step is a line vanilla or Fabric prints on every boot, so the same matchers
   hold for the real core and the mock, and a later step implies the ones before it — a console
   opened late, over a backlog, still counts what already happened. */

export type LaunchStep = "start" | "mods" | "render" | "resources" | "menu" | "connect" | "joined";

const MATCH: Record<LaunchStep, RegExp> = {
  start: /Loading Minecraft \S+/,
  mods: /Loading \d+ mods/,
  render: /Backend library: LWJGL/,
  resources: /Reloading ResourceManager/,
  menu: /Sound engine started/,
  connect: /Connecting to \S+/,
  // vanilla loads the server's advancements on every join; the mock says it in words
  joined: /Loaded \d+ advancements|Joined server/,
};

const BOOT: LaunchStep[] = ["start", "mods", "render", "resources", "menu"];
const JOIN: LaunchStep[] = ["connect", "joined"];

export function stepsFor(toServer: boolean): LaunchStep[] {
  return toServer ? [...BOOT, ...JOIN] : BOOT;
}

export interface LaunchProgress {
  steps: LaunchStep[];
  /** how many steps the log has proved, 0..steps.length */
  done: number;
}

export function launchProgress(lines: Iterable<string>, toServer: boolean): LaunchProgress {
  const steps = stepsFor(toServer);
  let done = 0;
  for (const line of lines) {
    for (let i = steps.length - 1; i >= done; i--) {
      if (MATCH[steps[i]].test(line)) {
        done = i + 1;
        break;
      }
    }
    if (done === steps.length) break;
  }
  return { steps, done };
}
