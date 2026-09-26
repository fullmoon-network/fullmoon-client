import { useMemo } from "react";
import { Icon, type IconName } from "./Icon";
import { Marker, MoonDisc } from "./Palace";
import { SkinFace } from "./ui";
import { useStore, type Screen } from "../state/store";
import { daysToFull, daysToNextFull, isFull, moonAt, moonName } from "../core/moonPhase";
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
      { id: "accounts", icon: "users" },
    ],
  },
];

function RailItem({ id, icon, current, onPick, label }: {
  id: Screen;
  icon: IconName;
  current: boolean;
  onPick: (s: Screen) => void;
  label: string;
}) {
  return (
    <button
      className={`rail-item ${current ? "is-current" : ""}`}
      aria-current={current ? "page" : undefined}
      onClick={() => onPick(id)}
    >
      <Marker on={current} />
      <Icon name={icon} size={18} />
      <span>{label}</span>
    </button>
  );
}

/** Tonight's moon, one line, at the foot of every screen's rail. */
function Tonight() {
  const { t } = useT();
  const moon = useMemo(() => moonAt(Date.now()), []);
  const full = isFull(moon);
  return (
    <div className="rail-tonight" title={t(`moon.${moonName(moon)}`)}>
      <svg width="18" height="18" viewBox="-9 -9 18 18" aria-hidden className={`rail-tonight-moon ${full ? "is-full" : ""}`}>
        <MoonDisc r={6} lit={moon.lit} waxing={moon.waxing} />
        <circle className="rail-tonight-ring" r={8.5} fill="none" />
      </svg>
      <span>
        <b>{t(`moon.${moonName(moon)}`)}</b>
        <em className="num">
          {full ? t("moon.next", { n: daysToNextFull(moon) }) : t("moon.until", { n: daysToFull(moon) })}
        </em>
      </span>
    </div>
  );
}

export function Sidebar() {
  const { screen, setScreen, accounts, activeAccount, selectAccount } = useStore();
  const { t } = useT();
  const here = screen === "home" ? "play" : screen;

  return (
    <nav className="rail" aria-label={t("nav.label")}>
      {GROUPS.map((g) => (
        <div key={g.labelKey} className="rail-group">
          <div className="rail-group-label">{t(g.labelKey)}</div>
          {g.items.map((item) => (
            <RailItem
              key={item.id}
              {...item}
              current={here === item.id}
              onPick={setScreen}
              label={t(`nav.${item.id}`)}
            />
          ))}
        </div>
      ))}

      <div className="rail-foot">
        <RailItem id="settings" icon="gear" current={here === "settings"} onPick={setScreen} label={t("nav.settings")} />
        {here !== "play" && <Tonight />}
        <button className="rail-account pf-tile" onClick={() => setScreen("accounts")}>
          {activeAccount ? (
            <>
              <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={32} />
              <span className="rail-account-meta">
                <strong>{activeAccount.username}</strong>
                <span>{t(`accounts.source.${activeAccount.source}`)}</span>
              </span>
              {accounts.length > 1 && <span className="rail-account-count num">{accounts.length}</span>}
            </>
          ) : (
            <>
              <span className="rail-account-empty">
                <Icon name="user" size={16} />
              </span>
              <span className="rail-account-meta">
                <strong>{t("dock.needsAccount")}</strong>
              </span>
            </>
          )}
        </button>
        {accounts.length > 1 && (
          <div className="rail-faces">
            {accounts.slice(0, 5).map((a) => (
              <button
                key={a.uuid}
                className={`rail-face ${a.uuid === activeAccount?.uuid ? "is-current" : ""}`}
                title={a.username}
                aria-label={a.username}
                aria-pressed={a.uuid === activeAccount?.uuid}
                onClick={() => void selectAccount(a.uuid)}
              >
                <SkinFace hue={a.skinHue} skin={a.skinUrl} size={22} />
              </button>
            ))}
          </div>
        )}
      </div>
    </nav>
  );
}
