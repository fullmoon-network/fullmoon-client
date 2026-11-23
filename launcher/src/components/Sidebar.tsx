import type { ReactNode } from "react";
import { Icon, type IconName } from "./Icon";
import { SkinFace } from "./ui";
import { useStore, type Screen } from "../state/store";
import { useT } from "../i18n";

const GROUPS: Array<{ labelKey: string; items: Array<{ id: Screen; icon: IconName }> }> = [
  {
    labelKey: "nav.group1",
    items: [
      { id: "play", icon: "play" },
      { id: "dashboard", icon: "home" },
      { id: "mods", icon: "puzzle" },
    ],
  },
  {
    labelKey: "nav.group2",
    items: [
      { id: "cosmetics", icon: "feather" },
      { id: "clips", icon: "clips" },
      { id: "accounts", icon: "users" },
    ],
  },
];

export function Sidebar() {
  const { screen, setScreen, accounts, activeAccount, selectAccount, game } = useStore();
  const { t } = useT();
  const inGame = game.state === "running" || game.state === "starting";

  const rows: ReactNode[] = [];
  for (const g of GROUPS) {
    rows.push(
      <div key={g.labelKey} className="sidebar-group">
        {t(g.labelKey)}
      </div>,
    );
    for (const item of g.items) {
      rows.push(
        <button
          key={item.id}
          className={`sidebar-item ${screen === item.id ? "active" : ""}`}
          onClick={() => setScreen(item.id)}
        >
          {screen === item.id && <span className="sidebar-pill" aria-hidden />}
          <Icon name={item.icon} size={20} />
          <span>{t(`nav.${item.id}`)}</span>
        </button>,
      );
    }
  }

  return (
    <nav className="sidebar">
      <div className="sidebar-items">{rows}</div>

      <div className="sidebar-bottom">
        <button className="sidebar-item" onClick={() => setScreen("settings")}>
          <Icon name="gear" size={20} />
          <span>{t("nav.settings")}</span>
        </button>

        <div className="sidebar-account" onClick={() => setScreen("accounts")} role="button" tabIndex={0}>
          {activeAccount ? (
            <>
              <SkinFace hue={activeAccount.skinHue} size={34} />
              <div className="sidebar-account-meta">
                <strong>{activeAccount.username}</strong>
                <span>{t(`accounts.source.${activeAccount.source}`)}</span>
              </div>
              {accounts.length > 1 && (
                <span className="account-count num">{accounts.length}</span>
              )}
            </>
          ) : (
            <>
              <div className="sidebar-account-empty">
                <Icon name="user" size={16} />
              </div>
              <div className="sidebar-account-meta">
                <strong>{t("dock.needsAccount")}</strong>
              </div>
            </>
          )}
        </div>
        {/* quick-switch strip for multi-account */}
        {accounts.length > 1 && (
          <div className="account-strip">
            {accounts.slice(0, 5).map((a) => (
              <button
                key={a.uuid}
                className={`account-strip-face ${a.uuid === activeAccount?.uuid ? "active" : ""}`}
                title={a.username}
                onClick={() => selectAccount(a.uuid)}
              >
                <SkinFace hue={a.skinHue} size={22} />
              </button>
            ))}
          </div>
        )}
      </div>
    </nav>
  );
}
