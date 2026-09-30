import { useEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "./Icon";
import { SkinFace } from "./ui";
import { useStore } from "../state/store";
import { usePlayAction, type PlayState } from "../state/playAction";
import { play as cue } from "../core/uiSounds";
import { useT } from "../i18n";

/* upward-opening dock menu with outside-click dismissal */
function DockMenu({
  trigger,
  label,
  children,
  open,
  setOpen,
  align = "left",
}: {
  trigger: ReactNode;
  label: string;
  children: (close: () => void) => ReactNode;
  open: boolean;
  setOpen: (v: boolean) => void;
  align?: "left" | "right";
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open, setOpen]);

  return (
    <div className="dockmenu" ref={ref}>
      <button
        className="dock-chip"
        title={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => {
          cue(open ? "close" : "open");
          setOpen(!open);
        }}
      >
        {trigger}
      </button>
      {open && (
        <div
          className={`dockmenu-panel dockmenu-${align}`}
          role="menu"
          aria-label={label}
          onClick={() => setOpen(false)}
        >
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}

/** What the play button says for each state; the dock and the hero share it. */
export function PlayLabel({ state, idleLabel }: { state: PlayState; idleLabel: string }) {
  const { t } = useT();
  switch (state.kind) {
    case "needsAccount":
      return (
        <>
          <Icon name="user" size={16} />
          <span>{t("dock.needsAccount")}</span>
        </>
      );
    case "preparing":
      return (
        <>
          <span className="spinner spinner-light" />
          <span>{t("dock.preparing")}</span>
        </>
      );
    case "installing":
      return (
        <>
          <span className="playbtn-progress">
            <span>{t(`dock.stage.${state.stage}`)}</span>
            <span className="playbtn-pct num">{Math.floor(state.pct)}%</span>
          </span>
          <span className="playbtn-bar" style={{ width: `${state.pct}%` }} />
        </>
      );
    case "install":
      return (
        <>
          <Icon name="download" size={16} />
          <span>{t("dock.install")}</span>
        </>
      );
    case "starting":
      return (
        <>
          <span className="spinner spinner-light" />
          <span>{t("dock.launching")}</span>
        </>
      );
    case "running":
      return (
        <>
          <span>{t("dock.running")}</span>
          <Icon name="terminal" size={15} />
        </>
      );
    default:
      return <span className="playbtn-word">{idleLabel}</span>;
  }
}

/* The 40 px bar on the main column's floor. On the play screen it states the client — the
   version and loader, the memory, the bundled mods, the install state — as the mock's dock does;
   everywhere else it carries who plays, what is installed, and the way in. */
export function Dock() {
  const { screen, accounts, activeAccount, selectAccount, selectedInstance, setScreen, versions, modCatalog } = useStore();
  const { t } = useT();
  const { state, act, busy } = usePlayAction();
  const [accOpen, setAccOpen] = useState(false);
  const onPlay = screen === "play" || screen === "home";

  if (onPlay) {
    const target = versions.find((v) => v.isTarget)?.id ?? selectedInstance?.versionId ?? "—";
    const loader = selectedInstance?.loader ?? "fabric";
    const memGb = selectedInstance ? (selectedInstance.memoryMb / 1024).toFixed(1).replace(/\.0$/, "") : null;
    const installed = selectedInstance?.installed === true;
    return (
      <footer className="dock">
        <span className="dock-fact num">
          Minecraft <b>{target}</b> · {loader.charAt(0).toUpperCase() + loader.slice(1)}
        </span>
        <span className="dock-fact num">
          {t("home.dockMemory")} <b>{memGb ? `${memGb} GB` : "—"}</b>
        </span>
        {modCatalog && (
          <span className="dock-fact num">
            {t("home.dockMods", { n: modCatalog.mods.length })}
          </span>
        )}
        <span className={`dock-fact dock-end ${installed ? "" : "is-warn"}`}>
          {installed ? t("home.dockInstalled") : t("home.needsInstall")}
        </span>
      </footer>
    );
  }

  return (
    <footer className="dock">
      <div className="dock-left">
        <DockMenu
          open={accOpen}
          setOpen={setAccOpen}
          label={t("dock.selectAccount")}
          trigger={
            activeAccount ? (
              <>
                <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={20} />
                <span className="dock-chip-label">{activeAccount.username}</span>
                <Icon name="chevronDown" size={13} className="dock-chip-caret" />
              </>
            ) : (
              <>
                <span className="dock-chip-none"><Icon name="user" size={12} /></span>
                <span className="dock-chip-label dim">{t("dock.needsAccount")}</span>
                <Icon name="chevronDown" size={13} className="dock-chip-caret" />
              </>
            )
          }
        >
          {() => (
            <>
              {accounts.map((a) => (
                <button
                  key={a.uuid}
                  role="menuitemradio"
                  aria-checked={a.uuid === activeAccount?.uuid}
                  className={`dockmenu-item ${a.uuid === activeAccount?.uuid ? "is-current" : ""}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    void selectAccount(a.uuid);
                    setAccOpen(false);
                  }}
                >
                  <SkinFace hue={a.skinHue} skin={a.skinUrl} size={22} />
                  <span>{a.username}</span>
                </button>
              ))}
              <button role="menuitem" className="dockmenu-item dockmenu-add" onClick={() => setScreen("accounts")}>
                <Icon name="plus" size={14} />
                <span>{t("accounts.add")}</span>
              </button>
            </>
          )}
        </DockMenu>

        <span className="dock-divider" aria-hidden />

        {/* the instance is not a choice — one managed install, shown as state */}
        <div className="dock-chip dock-chip-static" title={t("dock.selectInstance")}>
          {selectedInstance ? (
            <span className="dock-chip-label">
              {selectedInstance.name}
              <em className="num">{selectedInstance.versionId}</em>
            </span>
          ) : (
            <span className="dock-chip-label dim">{t("dock.preparing")}</span>
          )}
        </div>
      </div>

      <button
        className={`playbtn playbtn-${state.kind}`}
        onClick={() => {
          cue("confirm");
          act();
        }}
        aria-busy={busy}
        disabled={state.kind === "preparing"}
      >
        <PlayLabel state={state} idleLabel={t("dock.play")} />
      </button>
    </footer>
  );
}
