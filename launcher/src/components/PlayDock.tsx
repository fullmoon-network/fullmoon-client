import { useEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "./Icon";
import { Marker } from "./Palace";
import { SkinFace } from "./ui";
import { useStore } from "../state/store";
import { usePlayAction, type PlayState } from "../state/playAction";
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
        onClick={() => setOpen(!open)}
      >
        {trigger}
      </button>
      {open && (
        <div
          className={`dockmenu-panel dockmenu-${align} pf-frame pf-frame-sm`}
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

/** What the play plaque says for each state; the dock and the home plaque share it. */
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
      return (
        <>
          <Icon name="play" size={13} strokeWidth={2.6} />
          <span className="playbtn-word">{idleLabel}</span>
        </>
      );
  }
}

export function PlayDock() {
  const { screen, accounts, activeAccount, selectAccount, selectedInstance, setScreen } = useStore();
  const { t } = useT();
  const { state, act, busy } = usePlayAction();
  const [accOpen, setAccOpen] = useState(false);

  // The play screen carries its own play plaque, so the dock waits until the plaque has
  // scrolled out of view; everywhere else it stands by, out of the way while reading down.
  const [visible, setVisible] = useState(() => screen !== "play" && screen !== "home");
  const lastScrollTopRef = useRef(0);

  useEffect(() => {
    const onPlay = screen === "play" || screen === "home";
    setVisible(!onPlay);

    const contentEl = document.querySelector(".content");
    if (!contentEl) return;
    lastScrollTopRef.current = contentEl.scrollTop;

    const onScroll = () => {
      const st = contentEl.scrollTop;
      const diff = st - lastScrollTopRef.current;
      if (onPlay) {
        const hero = contentEl.querySelector(".hero");
        const heroGone = hero ? st > (hero as HTMLElement).offsetHeight - 80 : st > 100;
        setVisible(heroGone);
      } else if (st <= 10) setVisible(true);
      else if (diff > 6) setVisible(true);
      else if (diff < -6) setVisible(false);
      lastScrollTopRef.current = st;
    };

    contentEl.addEventListener("scroll", onScroll, { passive: true });
    return () => contentEl.removeEventListener("scroll", onScroll);
  }, [screen]);

  const plaqueClass = `playbtn playbtn-${state.kind}`;

  return (
    <footer className={`dock pf-frame pf-frame-sm ${visible ? "" : "hidden"}`}>
      <div className="dock-left">
        <DockMenu
          open={accOpen}
          setOpen={setAccOpen}
          label={t("dock.selectAccount")}
          trigger={
            activeAccount ? (
              <>
                <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={24} />
                <span className="dock-chip-label">{activeAccount.username}</span>
                <Icon name="chevronDown" size={13} className="dock-chip-caret" />
              </>
            ) : (
              <>
                <span className="dock-chip-none"><Icon name="user" size={14} /></span>
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
                  <Marker on={a.uuid === activeAccount?.uuid} />
                </button>
              ))}
              <button role="menuitem" className="dockmenu-item dockmenu-add" onClick={() => setScreen("accounts")}>
                <Icon name="plus" size={14} />
                <span>{t("accounts.add")}</span>
              </button>
            </>
          )}
        </DockMenu>

        {/* the instance is not a choice — one managed install, shown as state.
            Repair lives in Settings; there is no picker and no "+ new". */}
        <div className="dock-chip dock-chip-static" title={t("dock.selectInstance")}>
          {selectedInstance ? (
            <>
              <span className="dock-chip-cube"><Icon name="layers" size={13} /></span>
              <span className="dock-chip-label">
                {selectedInstance.name}
                <em className="num">{selectedInstance.versionId}</em>
              </span>
            </>
          ) : (
            <>
              <span className="dock-chip-none"><Icon name="layers" size={14} /></span>
              <span className="dock-chip-label dim">{t("dock.preparing")}</span>
            </>
          )}
        </div>
      </div>

      <span className="dock-divider" aria-hidden />

      <button className={plaqueClass} onClick={act} aria-busy={busy} disabled={state.kind === "preparing"}>
        <PlayLabel state={state} idleLabel={t("dock.play")} />
      </button>
    </footer>
  );
}
