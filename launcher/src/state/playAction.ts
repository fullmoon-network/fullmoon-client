/* The one decision behind every play button: what pressing it does right now. The dock and the
   home plaque both draw it, so a launcher that has no account, is still provisioning, is mid
   install, or already has a game up says the same thing in both places. */

import { useStore } from "./store";

export type PlayState =
  | { kind: "needsAccount" }
  | { kind: "preparing" }
  | { kind: "installing"; stage: string; pct: number }
  | { kind: "install" }
  | { kind: "starting" }
  | { kind: "running" }
  | { kind: "ready" };

export function usePlayAction(server?: string | null) {
  const { activeAccount, selectedInstance, game, installInstance, launch, setScreen, setOverlayHidden } =
    useStore();

  const sessionIsMine = game.sessionId !== null && game.instanceId === selectedInstance?.id;
  let state: PlayState;
  if (!activeAccount) state = { kind: "needsAccount" };
  else if (!selectedInstance) state = { kind: "preparing" };
  else if (selectedInstance.installing)
    state = { kind: "installing", stage: selectedInstance.installing.stage, pct: selectedInstance.installing.pct };
  else if (!selectedInstance.installed) state = { kind: "install" };
  else if (game.state === "starting" && sessionIsMine) state = { kind: "starting" };
  else if (game.state === "running" && sessionIsMine) state = { kind: "running" };
  else state = { kind: "ready" };

  const act = () => {
    switch (state.kind) {
      case "needsAccount":
        setScreen("accounts");
        break;
      case "install":
        if (selectedInstance) void installInstance(selectedInstance.id);
        break;
      case "starting":
      case "running":
        // the launch surface is the live view of a run; pressing play again brings it back
        setOverlayHidden(null);
        break;
      case "ready":
        if (selectedInstance) void launch(selectedInstance.id, server ?? undefined);
        break;
      default:
        break;
    }
  };

  return { state, act, busy: state.kind === "preparing" || state.kind === "installing" };
}
