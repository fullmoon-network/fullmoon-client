import type { ReactNode } from "react";
import { GlideList } from "./Glide";
import { SkinFace } from "./ui";
import { useStore, type Screen } from "../state/store";
import { play as cue } from "../core/uiSounds";
import { useT } from "../i18n";

/* The rail's six glyphs, 16 on a side at a 1.4 stroke, as mock/g-launcher.html draws them. */
const GLYPH: Record<string, ReactNode> = {
  play: <path d="M4 2l10 6-10 6z" />,
  dashboard: <path d="M2 2h5v5H2zM9 2h5v5H9zM2 9h5v5H2zM9 9h5v5H9z" />,
  mods: <path d="M6 2h4v2h4v4h-2v4h2v2H2v-4h2V8H2V4h4z" />,
  cosmetics: <path d="M13 3c-4 0-8 3-9 8l-2 2 2-1 1-2c5-1 8-4 8-7z" />,
  accounts: (
    <>
      <circle cx="8" cy="5" r="3" />
      <path d="M2 14c0-3 3-5 6-5s6 2 6 5" />
    </>
  ),
  settings: (
    <>
      <circle cx="8" cy="8" r="2.5" />
      <path d="M8 1v2M8 13v2M1 8h2M13 8h2M3 3l1.5 1.5M11.5 11.5L13 13M3 13l1.5-1.5M11.5 4.5L13 3" />
    </>
  ),
};

function RailGlyph({ id }: { id: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" aria-hidden>
      {GLYPH[id]}
    </svg>
  );
}

/* The first group has no label; the mock names only 꾸미기 and 기타. */
const GROUPS: Array<{ labelKey: string | null; items: Screen[] }> = [
  { labelKey: null, items: ["play", "dashboard", "mods"] },
  { labelKey: "nav.group2", items: ["cosmetics", "accounts"] },
  { labelKey: "nav.group3", items: ["settings"] },
];

export function Sidebar() {
  const { screen, setScreen, activeAccount } = useStore();
  const { t } = useT();
  const here = screen === "home" ? "play" : screen;

  const go = (s: Screen) => {
    if (s !== here) cue("confirm");
    setScreen(s);
  };

  return (
    <nav className="rail" aria-label={t("nav.label")}>
      <GlideList current={here} className="rail-list">
        {GROUPS.map((g, gi) => (
          <div key={gi} className="rail-group">
            {g.labelKey && <div className="rail-group-label">{t(g.labelKey)}</div>}
            {g.items.map((id) => {
              const current = here === id;
              return (
                <button
                  key={id}
                  className={`rail-item ${current ? "is-current" : ""}`}
                  aria-current={current ? "page" : undefined}
                  data-current={current ? "true" : undefined}
                  onClick={() => go(id)}
                  onFocus={(e) => {
                    if (e.currentTarget.matches(":focus-visible")) cue("focus");
                  }}
                >
                  <RailGlyph id={id} />
                  <span>{t(`nav.${id}`)}</span>
                </button>
              );
            })}
          </div>
        ))}
      </GlideList>

      <button className="rail-account" onClick={() => go("accounts")}>
        {activeAccount ? (
          <>
            <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={32} />
            <span className="rail-account-meta">
              <strong>{activeAccount.username}</strong>
              <span>{t(`accounts.sourceLong.${activeAccount.source}`)}</span>
            </span>
          </>
        ) : (
          <>
            <span className="rail-account-empty" aria-hidden />
            <span className="rail-account-meta">
              <strong>{t("dock.needsAccount")}</strong>
            </span>
          </>
        )}
      </button>
    </nav>
  );
}
