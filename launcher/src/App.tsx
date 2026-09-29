import { useEffect, useState } from "react";
import { Seal, TitleBar } from "./components/TitleBar";
import { Sidebar } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { CommandPalette } from "./components/CommandPalette";
import { Dock } from "./components/Dock";
import { ProgressDock, Toasts } from "./components/Docks";
import { LaunchOverlay } from "./widgets/LaunchOverlay";
import { PlayScreen } from "./screens/Play";
import { DashboardScreen } from "./screens/Dashboard";
import { ModsScreen } from "./screens/Mods";
import { CosmeticsScreen } from "./screens/Cosmetics";
import { AccountsScreen } from "./screens/Accounts";
import { SettingsScreen } from "./screens/Settings";
import { useStore } from "./state/store";
import { play as cue } from "./core/uiSounds";
import { useT } from "./i18n";

const SCREENS = {
  play: PlayScreen,
  dashboard: DashboardScreen,
  home: PlayScreen,
  mods: ModsScreen,
  cosmetics: CosmeticsScreen,
  accounts: AccountsScreen,
  settings: SettingsScreen,
} as const;

export default function App() {
  const { ready, screen, settings, game, overlayHiddenFor, setOverlayHidden } = useStore();
  const { setLang } = useT();
  const [paletteOpen, setPaletteOpen] = useState(false);
  /* the overlay shows once per session; hiding it pins that sessionId */
  const overlayOn =
    (game.state === "starting" || game.state === "running") &&
    game.sessionId !== null &&
    overlayHiddenFor !== game.sessionId;

  /* keep the i18n provider in sync with the persisted setting */
  useEffect(() => {
    if (settings) setLang(settings.language);
  }, [settings, setLang]);

  /* global command palette hotkey */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((v) => {
          cue(v ? "close" : "open");
          return !v;
        });
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!ready) {
    return (
      <div className="app-splash">
        <Seal />
      </div>
    );
  }

  const Screen = SCREENS[screen];
  const onPlay = screen === "play" || screen === "home";

  return (
    <div className="app">
      <TitleBar />
      <div className="shell">
        <Sidebar />
        <div className="main">
          {/* the play screen's hero is its own heading; every other screen names itself here */}
          {!onPlay && (
            <TopBar
              onPalette={() => {
                cue("open");
                setPaletteOpen(true);
              }}
            />
          )}
          <main className="content">
            <div className="content-inner screen-enter" key={screen}>
              <Screen />
            </div>
          </main>
          <Dock />
        </div>
      </div>
      <Toasts />
      <ProgressDock />
      <CommandPalette
        open={paletteOpen}
        onClose={() => {
          cue("close");
          setPaletteOpen(false);
        }}
      />
      {overlayOn && <LaunchOverlay onHide={() => setOverlayHidden(game.sessionId)} />}
    </div>
  );
}
