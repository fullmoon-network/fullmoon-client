/* ─────────────────────────────────────────────────────────────
   store.tsx — app state over the core contract.

   The store owns NOTHING the core owns. It hydrates from core
   commands, mirrors core events, and exposes intent-level actions
   to the views. Views stay pure (PLAN §5).
   ───────────────────────────────────────────────────────────── */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { flushSync } from "react-dom";
import { core, errText, getActiveAccountUuid } from "../core/client";
import type {
  Account,
  Cosmetic,
  GameState,
  Instance,
  InstanceSpec,
  JavaRuntime,
  Loadout,
  ModCatalog,
  NewsItem,
  ServerEntry,
  ServerStatus,
  Settings,
  VersionSummary,
  WalletInfo,
  WalletTx,
} from "../core/bindings";
import { useT } from "../i18n";
import { setUiSoundsEnabled } from "../core/uiSounds";
import { LogsProvider } from "./logs";

export type Screen = "play" | "dashboard" | "home" | "mods" | "cosmetics" | "accounts" | "settings";
export type SettingsTab = "java" | "perf" | "look" | "hud" | "privacy" | "about";

export interface Toast {
  id: number;
  kind: "info" | "success" | "error";
  text: string;
}

export interface DownloadInfo {
  taskId: string;
  file: string;
  pct: number;
  bytesPerSec: number;
  at: number;
}

/** The launcher's own two preferences. They are not the core's Settings: the IPC contract stays
 *  as it is, and these live in the webview alone. */
export interface UiPrefs {
  /** collapses every glide and reveal to a short crossfade, as the system preference does */
  reduceMotion: boolean;
  /** the game's UI cues on rail moves, confirms and menus; off until the player asks */
  sounds: boolean;
}
const UI_PREFS_KEY = "pinion.v1.ui";
const DEFAULT_UI_PREFS: UiPrefs = { reduceMotion: false, sounds: false };
function loadUiPrefs(): UiPrefs {
  try {
    const raw = localStorage.getItem(UI_PREFS_KEY);
    return raw ? { ...DEFAULT_UI_PREFS, ...(JSON.parse(raw) as Partial<UiPrefs>) } : DEFAULT_UI_PREFS;
  } catch {
    return DEFAULT_UI_PREFS;
  }
}

interface Store {
  ready: boolean;
  uiPrefs: UiPrefs;
  setUiPref: (patch: Partial<UiPrefs>) => void;
  screen: Screen;
  /** `tab` deep-links into a settings section; ignored for every other screen */
  setScreen: (s: Screen, tab?: SettingsTab) => void;
  settingsTab: SettingsTab | null;
  /** sessionId whose launch overlay the user dismissed; null re-shows it */
  overlayHiddenFor: string | null;
  setOverlayHidden: (sessionId: string | null) => void;

  accounts: Account[];
  activeAccount: Account | null;
  selectAccount: (uuid: string) => Promise<void>;
  removeAccount: (uuid: string) => Promise<void>;
  refreshAccount: (uuid: string) => Promise<void>;
  importOfficial: () => Promise<void>;
  syncAccounts: () => Promise<void>;

  versions: VersionSummary[];
  instances: Instance[];
  selectedInstanceId: string | null;
  selectInstance: (id: string) => void;
  selectedInstance: Instance | null;
  createInstance: (spec: InstanceSpec) => Promise<void>;
  deleteInstance: (id: string) => Promise<void>;
  installInstance: (id: string) => Promise<void>;

  modCatalog: ModCatalog | null;
  cosmetics: Cosmetic[];

  settings: Settings | null;
  patchSettings: (patch: Partial<Settings>) => Promise<void>;

  /** detected once at boot, shared by the settings picker and the summary strip */
  javaRuntimes: JavaRuntime[];
  /** physical RAM in MB, 0 when unknown — the memory slider's ceiling */
  systemMemoryMb: number;
  scanningJava: boolean;
  rescanJava: () => Promise<void>;

  news: NewsItem[];
  wallet: WalletInfo | null;
  walletTxs: WalletTx[];
  servers: ServerEntry[];
  /** live status by address; absent while the first probe is still out */
  serverStatus: Record<string, ServerStatus>;
  pingingServers: boolean;
  refreshServers: () => Promise<void>;
  addServer: (name: string, address: string) => Promise<void>;
  removeServer: (id: string) => Promise<void>;

  game: GameState;
  launch: (instanceId: string, server?: string) => Promise<void>;
  killGame: () => Promise<void>;

  loadout: Loadout | null;
  equip: (slot: keyof Loadout, itemId: string | null) => Promise<void>;

  downloads: DownloadInfo[];
  toasts: Toast[];
  toast: (kind: Toast["kind"], text: string) => void;
  dismissToast: (id: number) => void;
}

const Ctx = createContext<Store | null>(null);
let toastSeq = 0;

/* boot fallbacks — only reached when a core call fails outright */
const BOOT_SETTINGS: Settings = {
  javaPath: null,
  javaArgs: "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200",
  memoryMb: 4096,
  concurrency: 8,
  theme: "dark",
  accent: "gilt",
  language: "ko",
  telemetry: false,
};

const IDLE_GAME: GameState = {
  state: "idle", sessionId: null, instanceId: null, server: null, startedAt: null, exitCode: null,
};

/* a core answering over IPC fails per command — an offline manifest must not
   sink the whole boot and leave the splash up forever */
function soft<T>(p: Promise<T>, fallback: T, failed: string[]): Promise<T> {
  return p.catch((e) => {
    failed.push(errText(e));
    return fallback;
  });
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const { t } = useT();
  const [ready, setReady] = useState(false);
  const [screen, setScreenState] = useState<Screen>("play");
  const [settingsTab, setSettingsTab] = useState<SettingsTab | null>(null);
  /** sessionId whose launch overlay the user dismissed — null shows it again */
  const [overlayHiddenFor, setOverlayHiddenFor] = useState<string | null>(null);

  /* screen switches ride the View Transitions API when available and motion is not reduced */
  const setScreen = useCallback((s: Screen, tab?: SettingsTab) => {
    setSettingsTab(tab ?? null);
    const doc = document as Document & { startViewTransition?: (cb: () => void) => void };
    const reduced =
      document.documentElement.dataset.motion === "reduced" ||
      (typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches);
    if (doc.startViewTransition && !reduced) {
      doc.startViewTransition(() => flushSync(() => setScreenState(s)));
    } else {
      setScreenState(s);
    }
  }, []);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [activeUuid, setActiveUuid] = useState<string | null>(null);
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(
    () => localStorage.getItem("pinion.v1.sel"),
  );
  const [modCatalog, setModCatalog] = useState<ModCatalog | null>(null);
  const [cosmetics, setCosmetics] = useState<Cosmetic[]>([]);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [uiPrefs, setUiPrefs] = useState<UiPrefs>(loadUiPrefs);
  const setUiPref = useCallback((patch: Partial<UiPrefs>) => {
    setUiPrefs((prev) => {
      const next = { ...prev, ...patch };
      try {
        localStorage.setItem(UI_PREFS_KEY, JSON.stringify(next));
      } catch {
        /* private mode: the choice lasts the session */
      }
      return next;
    });
  }, []);
  /* motion is an attribute on the root, read by the same rule as prefers-reduced-motion */
  useEffect(() => {
    const root = document.documentElement;
    if (uiPrefs.reduceMotion) root.dataset.motion = "reduced";
    else delete root.dataset.motion;
    setUiSoundsEnabled(uiPrefs.sounds);
  }, [uiPrefs]);
  const [news, setNews] = useState<NewsItem[]>([]);
  const [wallet, setWallet] = useState<WalletInfo | null>(null);
  const [walletTxs, setWalletTxs] = useState<WalletTx[]>([]);
  const [servers, setServers] = useState<ServerEntry[]>([]);
  const [serverStatus, setServerStatus] = useState<Record<string, ServerStatus>>({});
  const [pingingServers, setPingingServers] = useState(false);
  const [game, setGame] = useState<GameState>({
    state: "idle", sessionId: null, instanceId: null, server: null, startedAt: null, exitCode: null,
  });
  const [loadout, setLoadout] = useState<Loadout | null>(null);
  const [javaRuntimes, setJavaRuntimes] = useState<JavaRuntime[]>([]);
  const [systemMemoryMb, setSystemMemoryMb] = useState(0);
  const [scanningJava, setScanningJava] = useState(false);
  const [downloads, setDownloads] = useState<DownloadInfo[]>([]);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const settingsRef = useRef<Settings | null>(null);

  const dismissToast = useCallback((id: number) => {
    setToasts((ts) => ts.filter((x) => x.id !== id));
  }, []);

  const toast = useCallback(
    (kind: Toast["kind"], text: string) => {
      const id = ++toastSeq;
      setToasts((ts) => [...ts.slice(-3), { id, kind, text }]);
      window.setTimeout(() => dismissToast(id), 4200);
    },
    [dismissToast],
  );

  /* ── hydrate + subscribe ── */

  useEffect(() => {
    let alive = true;
    (async () => {
      const failed: string[] = [];
      const [accs, vers, insts, mods, cos, st, nw, sv, gs, wal, wtx] = await Promise.all([
        soft(core.auth_list(), [], failed),
        /* disk only: the live manifest refreshes after the first paint, below */
        soft(core.versions_cached(), [], failed),
        soft(core.instances_list(), [], failed),
        soft(core.mods_available(), { mods: [] }, failed),
        soft(core.cosmetics_catalog(), [], failed),
        soft(core.settings_get(), BOOT_SETTINGS, failed),
        soft(core.news_feed(), [], failed),
        soft(core.servers_list(), [], failed),
        soft(core.game_status(), IDLE_GAME, failed),
        /* economy panels degrade quietly — a core without the bridge yet is
           normal, not an error worth a boot toast */
        core.economy_wallet().catch(() => null),
        core.economy_transactions().catch(() => [] as WalletTx[]),
      ]);
      if (!alive) return;
      setAccounts(accs);
      setActiveUuid(getActiveAccountUuid());
      setVersions(vers);
      setInstances(insts);
      setModCatalog(mods);
      setCosmetics(cos);
      setSettings(st);
      settingsRef.current = st;
      setNews(nw);
      setWallet(wal);
      setWalletTxs(wtx);
      setServers(sv);
      setGame(gs);
      setSelectedInstanceId((sel) =>
        sel && insts.some((i) => i.id === sel) ? sel : insts[0]?.id ?? null,
      );
      setReady(true);
      if (failed.length > 0) toast("error", failed[0]);
      /* launchermeta can take its whole connect timeout when the box is offline */
      core.versions_manifest().then(
        (fresh) => alive && setVersions(fresh),
        (e) => alive && vers.length === 0 && toast("error", errText(e)),
      );
      /* probing every JDK on the box takes a second — never hold up the boot */
      core.java_detect().then(
        (rs) => alive && setJavaRuntimes(rs),
        () => {},
      );
      core.system_memory_mb().then(
        (mb) => alive && setSystemMemoryMb(mb),
        () => {},
      );
      /* same for the servers: a dead host costs a five second timeout */
      if (sv.length > 0) {
        core.servers_ping(sv.map((s) => s.address)).then(
          (st) => alive && setServerStatus(st),
          () => {},
        );
      }
    })();

    const offInstall = core.on("install://stage", ({ instanceId, stage, pct }) => {
      setInstances((list) =>
        list.map((i) =>
          i.id === instanceId
            ? stage === "done"
              ? { ...i, installed: true, installing: null }
              : { ...i, installing: { stage, pct } }
            : i,
        ),
      );
      if (stage === "done") {
        setDownloads((d) => d.filter((x) => x.taskId !== `task-${instanceId}`));
        toast("success", t("instances.installDone"));
      }
    });

    const offDl = core.on("download://progress", ({ taskId, file, done, bytesPerSec }) => {
      setDownloads((d) => {
        const rest = d.filter((x) => x.taskId !== taskId);
        return [...rest, { taskId, file, pct: done * 100, bytesPerSec, at: Date.now() }];
      });
    });

    const offState = core.on("game://state", ({ sessionId, state, exitCode }) => {
      setGame((g) => (g.sessionId === sessionId ? { ...g, state, exitCode: exitCode ?? null } : g));
      if (state === "crashed") toast("error", t("console.crashedToast", { code: exitCode ?? -1 }));
      if (state === "closed") toast("info", t("console.closedToast"));
    });

    return () => {
      alive = false;
      offInstall();
      offDl();
      offState();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* loadout follows the active account */
  useEffect(() => {
    if (!activeUuid) {
      setLoadout(null);
      return;
    }
    core.cosmetics_equipped(activeUuid).then(setLoadout);
  }, [activeUuid]);

  /* persist selection */
  useEffect(() => {
    if (selectedInstanceId) localStorage.setItem("pinion.v1.sel", selectedInstanceId);
  }, [selectedInstanceId]);

  /* Theme and accent are attributes on the document; the palettes themselves are the generated
     tokens. An accent stored by an older launcher as a hex has no metal and reads as gilt. */
  useEffect(() => {
    if (!settings) return;
    const root = document.documentElement;
    root.dataset.theme = settings.theme === "light" ? "light" : "dark";
    root.dataset.accent = accentMetal(settings.accent);
  }, [settings]);

  /* ── actions ── */

  const selectAccount = useCallback(async (uuid: string) => {
    await core.auth_select(uuid);
    setActiveUuid(uuid);
  }, []);

  const removeAccount = useCallback(
    async (uuid: string) => {
      await core.auth_remove(uuid);
      const list = await core.auth_list();
      setAccounts(list);
      setActiveUuid(getActiveAccountUuid());
      toast("info", t("accounts.removed"));
    },
    [toast, t],
  );

  const refreshAccount = useCallback(
    async (uuid: string) => {
      try {
        await core.auth_refresh(uuid);
        setAccounts(await core.auth_list());
        toast("success", t("accounts.refreshed"));
      } catch (e) {
        toast("error", errText(e));
      }
    },
    [toast, t],
  );

  const importOfficial = useCallback(async () => {
    try {
      const added = await core.auth_import_official();
      if (added.length === 0) toast("info", t("accounts.importNone"));
      else {
        toast("success", t("accounts.importSome", { n: added.length }));
        setAccounts(await core.auth_list());
        setActiveUuid(getActiveAccountUuid());
      }
    } catch (e) {
      toast("error", errText(e));
    }
  }, [toast, t]);

  const syncAccounts = useCallback(async () => {
    setAccounts(await core.auth_list());
    setActiveUuid(getActiveAccountUuid());
  }, []);

  const selectInstance = useCallback((id: string) => setSelectedInstanceId(id), []);

  /* Install runs to completion inside the command, so it is never awaited by a
     caller that owns UI state — the dialog closes, the card shows the bar. */
  const runInstall = useCallback(
    async (id: string) => {
      try {
        await core.instance_install(id);
        setInstances(await core.instances_list());
      } catch (e) {
        toast("error", errText(e));
        setInstances(await core.instances_list().catch(() => []));
      }
    },
    [toast],
  );

  const createInstance = useCallback(
    async (spec: InstanceSpec) => {
      const inst = await core.instance_create(spec);
      setInstances((l) => [...l, inst]);
      setSelectedInstanceId(inst.id);
      toast("success", t("instances.created"));
      void runInstall(inst.id);
    },
    [toast, t, runInstall],
  );

  const deleteInstance = useCallback(
    async (id: string) => {
      await core.instance_delete(id);
      setInstances((l) => l.filter((i) => i.id !== id));
      setSelectedInstanceId((sel) => (sel === id ? null : sel));
      toast("info", t("instances.deleted"));
    },
    [toast, t],
  );

  const installInstance = useCallback(
    async (id: string) => {
      toast("info", t("instances.installStarted"));
      void runInstall(id);
    },
    [toast, t, runInstall],
  );

  const rescanJava = useCallback(async () => {
    setScanningJava(true);
    try {
      setJavaRuntimes(await core.java_detect());
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setScanningJava(false);
    }
  }, [toast]);

  const patchSettings = useCallback(async (patch: Partial<Settings>) => {
    const next = await core.settings_set(patch);
    settingsRef.current = next;
    setSettings(next);
  }, []);

  /* The card shows what the server said, so the list is only as true as its
     last probe. Kept out of the boot bundle: a dead host must not hold up the
     window coming up. */
  const serversRef = useRef<ServerEntry[]>([]);
  serversRef.current = servers;

  const refreshServers = useCallback(async () => {
    const list = serversRef.current;
    if (list.length === 0) {
      setServerStatus({});
      return;
    }
    setPingingServers(true);
    try {
      setServerStatus(await core.servers_ping(list.map((s) => s.address)));
    } catch {
      /* leave the last known status up rather than blanking the cards */
    } finally {
      setPingingServers(false);
    }
  }, []);

  const addServer = useCallback(
    async (name: string, address: string) => {
      const entry: ServerEntry = {
        id: `srv-${Date.now().toString(36)}`,
        name: name.trim() || address.trim(),
        address: address.trim(),
        motd: "",
        players: 0,
        maxPlayers: 0,
        pingMs: 0,
        hue: (Math.abs(hashCode(address)) % 36) * 10,
      };
      const next = [...serversRef.current, entry];
      setServers(next);
      await core.servers_save(next);
      void refreshServers();
    },
    [refreshServers],
  );

  const removeServer = useCallback(async (id: string) => {
    setServers((s) => {
      const next = s.filter((x) => x.id !== id);
      void core.servers_save(next);
      return next;
    });
  }, []);

  const launch = useCallback(
    async (instanceId: string, server?: string) => {
      try {
        const sessionId = server
          ? await core.launch_quickplay(instanceId, server)
          : await core.launch(instanceId);
        if (server) toast("info", t("toast.quickPlay", { server }));
        setGame((g) => ({
          ...g,
          state: "starting",
          sessionId,
          instanceId,
          server: server ?? null,
          startedAt: Date.now(),
        }));
      } catch (e) {
        toast("error", t("toast.launchFail", { reason: errText(e) }));
      }
    },
    [toast, t],
  );

  const killGame = useCallback(async () => {
    if (game.sessionId) {
      await core.game_kill(game.sessionId);
      toast("info", t("console.killed"));
    }
  }, [game.sessionId, toast, t]);

  const equip = useCallback(
    async (slot: keyof Loadout, itemId: string | null) => {
      if (!activeUuid) return;
      await core.cosmetics_equip(activeUuid, slot, itemId);
      setLoadout(await core.cosmetics_equipped(activeUuid));
      const item = cosmetics.find((c) => c.id === itemId);
      if (item) toast("success", t("cosmetics.equippedToast", { name: item.name }));
    },
    [activeUuid, cosmetics, toast, t],
  );

  const activeAccount = useMemo(
    () => accounts.find((a) => a.uuid === activeUuid) ?? null,
    [accounts, activeUuid],
  );
  const selectedInstance = useMemo(
    () => instances.find((i) => i.id === selectedInstanceId) ?? null,
    [instances, selectedInstanceId],
  );

  const value = useMemo<Store>(
    () => ({
      ready, uiPrefs, setUiPref, screen, setScreen, settingsTab,
      overlayHiddenFor, setOverlayHidden: setOverlayHiddenFor,
      accounts, activeAccount, selectAccount, removeAccount, refreshAccount, importOfficial, syncAccounts,
      versions, instances, selectedInstanceId, selectInstance, selectedInstance,
      createInstance, deleteInstance, installInstance,
      modCatalog, cosmetics,
      settings, patchSettings,
      javaRuntimes, systemMemoryMb, scanningJava, rescanJava,
      news, wallet, walletTxs, servers, serverStatus, pingingServers, refreshServers, addServer, removeServer,
      game, launch, killGame,
      loadout, equip,
      downloads, toasts, toast, dismissToast,
    }),
    [
      ready, uiPrefs, setUiPref, screen, setScreen, settingsTab, overlayHiddenFor, setOverlayHiddenFor,
      accounts, activeAccount, selectAccount, removeAccount, refreshAccount, importOfficial,
      syncAccounts, versions, instances, selectedInstanceId, selectInstance, selectedInstance,
      createInstance, deleteInstance, installInstance, modCatalog, cosmetics, settings, patchSettings,
      javaRuntimes, systemMemoryMb, scanningJava, rescanJava, news, wallet, walletTxs, servers,
      serverStatus, pingingServers, refreshServers, addServer, removeServer, game, launch, killGame,
      loadout, equip, downloads, toasts, toast, dismissToast,
    ],
  );

  return (
    <Ctx.Provider value={value}>
      <LogsProvider>{children}</LogsProvider>
    </Ctx.Provider>
  );
}

export function useStore(): Store {
  const s = useContext(Ctx);
  if (!s) throw new Error("useStore outside provider");
  return s;
}

/* color helpers for runtime accent injection */
/* a server keeps the same swatch across restarts because the colour comes from
   its address, not from the order it was added */
function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

export const ACCENT_METALS = ["gilt", "silver", "bronze"] as const;
export type AccentMetal = (typeof ACCENT_METALS)[number];

export function accentMetal(stored: string): AccentMetal {
  return (ACCENT_METALS as readonly string[]).includes(stored) ? (stored as AccentMetal) : "gilt";
}
