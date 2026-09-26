import { useEffect, useState } from "react";
import { TitleBar } from "./components/TitleBar";
import { Sidebar } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { CommandPalette } from "./components/CommandPalette";
import { PlayDock } from "./components/PlayDock";
import { ProgressDock, Toasts } from "./components/Docks";
import { LaunchOverlay } from "./widgets/LaunchOverlay";
import { AtmosphericBackdrop } from "./widgets/AtmosphericBackdrop";
import { Seal } from "./components/Palace";
import { PlayScreen } from "./screens/Play";
import { DashboardScreen } from "./screens/Dashboard";
import { HomeScreen } from "./screens/Home";
import { ModsScreen } from "./screens/Mods";
import { CosmeticsScreen } from "./screens/Cosmetics";
import { AccountsScreen } from "./screens/Accounts";
import { SettingsScreen } from "./screens/Settings";
import { useStore } from "./state/store";
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
        setPaletteOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!ready) {
    return (
      <div className="app-splash">
        <Seal size={44} />
      </div>
    );
  }

  const Screen = SCREENS[screen];

  return (
    <div className="app">
      <TitleBar />
      <AtmosphericBackdrop />
      <div className="shell">
        <Sidebar />
        <div className="main">
          <TopBar onPalette={() => setPaletteOpen(true)} />
          <main className="content">
            <div className="content-inner screen-enter" key={screen}>
              <Screen />
            </div>
          </main>
          <PlayDock />
        </div>
      </div>
      <Toasts />
      <ProgressDock />
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
      {overlayOn && <LaunchOverlay onHide={() => setOverlayHidden(game.sessionId)} />}
    </div>
  );
}
