import { useEffect, useState } from "react";
import { Seal, TitleBar } from "./components/TitleBar";
import { Sidebar } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { CommandPalette } from "./components/CommandPalette";
import { Dock } from "./components/Dock";
import { ProgressDock, Toasts } from "./components/Docks";
import { LaunchOverlay } from "./widgets/LaunchOverlay";
import { PlayScreen } from "./screens/Play";
import { lazyComponent } from "./components/lazyComponent";
import Skin3D from "./widgets/Skin3D";
import { useStore } from "./state/store";
import { play as cue } from "./core/uiSounds";
import { useT } from "./i18n";

/* the play screen is the first view; the others load on first use, and are fetched in the
   background once the first view is up so a click never waits for a chunk */
const DashboardScreen = lazyComponent(() => import("./screens/Dashboard").then((m) => ({ default: m.DashboardScreen })));
const ModsScreen = lazyComponent(() => import("./screens/Mods").then((m) => ({ default: m.ModsScreen })));
const CosmeticsScreen = lazyComponent(() => import("./screens/Cosmetics").then((m) => ({ default: m.CosmeticsScreen })));
const AccountsScreen = lazyComponent(() => import("./screens/Accounts").then((m) => ({ default: m.AccountsScreen })));
const SettingsScreen = lazyComponent(() => import("./screens/Settings").then((m) => ({ default: m.SettingsScreen })));

const SCREENS = {
  play: PlayScreen,
  dashboard: DashboardScreen,
  home: PlayScreen,
  mods: ModsScreen,
  cosmetics: CosmeticsScreen,
  accounts: AccountsScreen,
  settings: SettingsScreen,
} as const;

function preloadChunks() {
  const chunks = [DashboardScreen, ModsScreen, CosmeticsScreen, AccountsScreen, SettingsScreen, Skin3D];
  let i = 0;
  const next = () => {
    if (i < chunks.length) chunks[i++].preload().then(() => idle(next), () => idle(next));
  };
  idle(next);
}
const idle = (fn: () => void) =>
  typeof requestIdleCallback === "function" ? requestIdleCallback(fn, { timeout: 2000 }) : setTimeout(fn, 300);

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

  useEffect(() => {
    if (ready) preloadChunks();
  }, [ready]);

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
