import { useEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "./Icon";
import { Key, SkinFace } from "./ui";
import { useStore } from "../state/store";
import { play as cue } from "../core/uiSounds";
import { useT } from "../i18n";

/* downward-opening chrome menu, same dismissal contract as DockMenu */
function TopMenu({
  open,
  setOpen,
  label,
  trigger,
  triggerClass,
  children,
}: {
  open: boolean;
  setOpen: (v: boolean) => void;
  label: string;
  trigger: ReactNode;
  triggerClass: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open, setOpen]);
  return (
    <div className="topmenu" ref={ref}>
      <button
        className={triggerClass}
        aria-label={label}
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
        <div className="topmenu-panel" role="menu" aria-label={label}>
          {children}
        </div>
      )}
    </div>
  );
}

/** The screen's name on a hairline, with search, notifications and the account. The play
 *  screen has none: its hero is the heading. */
export function TopBar({ onPalette }: { onPalette: () => void }) {
  const { screen, setScreen, news, accounts, activeAccount, selectAccount, importOfficial } =
    useStore();
  const { t } = useT();
  const [bellOpen, setBellOpen] = useState(false);
  const [accOpen, setAccOpen] = useState(false);
  const [read, setRead] = useState(false);

  const latest = news.slice(0, 3);

  return (
    <div className="masthead">
      <header className="topbar">
        <div className="topbar-heading">
          <h1>{t(`nav.${screen}`)}</h1>
          <p>{t(`topbar.sub.${screen}`)}</p>
        </div>

        <div className="topbar-actions">
          <button className="searchbtn" onClick={onPalette} aria-keyshortcuts="Control+K">
            <Icon name="search" size={15} />
            <span>{t("topbar.search")}</span>
            <span className="searchbtn-keys" aria-hidden>
              <Key>Ctrl</Key>
              <Key>K</Key>
            </span>
          </button>

          <TopMenu
            open={bellOpen}
            setOpen={setBellOpen}
            label={t("topbar.notifications")}
            triggerClass="bellbtn"
            trigger={
              <>
                <Icon name="bell" size={17} />
                {!read && latest.length > 0 && <span className="bell-dot" />}
              </>
            }
          >
            <div className="topmenu-title">{t("topbar.notifications")}</div>
            {latest.length === 0 ? (
              <div className="topmenu-empty">{t("topbar.noNotifications")}</div>
            ) : (
              latest.map((n) => (
                <button
                  key={n.id}
                  role="menuitem"
                  className="topmenu-item"
                  onClick={() => {
                    setBellOpen(false);
                    setScreen("dashboard");
                  }}
                >
                  <span className="topmenu-item-text">
                    <strong>{n.title}</strong>
                    <em className="num">{t(`news.tag.${n.tag}`)} · {n.date}</em>
                  </span>
                </button>
              ))
            )}
            <button
              role="menuitem"
              className="topmenu-item topmenu-foot"
              onClick={() => {
                setRead(true);
                setBellOpen(false);
              }}
            >
              <Icon name="check" size={13} />
              <span>{t("topbar.markRead")}</span>
            </button>
          </TopMenu>

          <TopMenu
            open={accOpen}
            setOpen={setAccOpen}
            label={t("topbar.account")}
            triggerClass="acctchip"
            trigger={
              <>
                {activeAccount ? (
                  <>
                    <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={24} />
                    <span className="acctchip-meta">
                      <strong>{activeAccount.username}</strong>
                      <em>{t(`accounts.source.${activeAccount.source}`)}</em>
                    </span>
                  </>
                ) : (
                  <>
                    <span className="acctchip-none">
                      <Icon name="user" size={14} />
                    </span>
                    <span className="acctchip-meta">
                      <strong>{t("dock.needsAccount")}</strong>
                    </span>
                  </>
                )}
                <Icon name="chevronDown" size={13} className="acctchip-caret" />
              </>
            }
          >
            {accounts.map((a) => (
              <button
                key={a.uuid}
                role="menuitemradio"
                aria-checked={a.uuid === activeAccount?.uuid}
                className={`topmenu-item ${a.uuid === activeAccount?.uuid ? "is-current" : ""}`}
                onClick={() => {
                  void selectAccount(a.uuid);
                  setAccOpen(false);
                }}
              >
                <SkinFace hue={a.skinHue} skin={a.skinUrl} size={22} />
                <span className="topmenu-item-text">
                  <strong>{a.username}</strong>
                  <em>{t(`accounts.source.${a.source}`)}</em>
                </span>
              </button>
            ))}
            <button
              role="menuitem"
              className="topmenu-item topmenu-foot"
              onClick={() => {
                setAccOpen(false);
                setScreen("accounts");
              }}
            >
              <Icon name="plus" size={13} />
              <span>{t("accounts.add")}</span>
            </button>
            <button
              role="menuitem"
              className="topmenu-item"
              onClick={() => {
                setAccOpen(false);
                void importOfficial();
              }}
            >
              <Icon name="download" size={13} />
              <span>{t("accounts.importOfficial")}</span>
            </button>
          </TopMenu>
        </div>
      </header>
    </div>
  );
}
