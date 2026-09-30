/* ─────────────────────────────────────────────────────────────
   mockCore.ts — standalone implementation of the PinionCore
   contract (PLAN §4). Simulates the real Rust core with honest
   latency, staged installs, streamed game logs and device-code
   polling. Swap this file for tauri-specta bindings and the UI
   never notices.
   ───────────────────────────────────────────────────────────── */

import type {
  Account,
  AuthStatus,
  Cosmetic,
  CosmeticSlot,
  CoreEventName,
  CoreEvents,
  DeviceCodePrompt,
  GameState,
  HudConfig,
  Instance,
  InstancePatch,
  InstanceSpec,
  InstallStage,
  InstalledMod,
  JavaRuntime,
  LaunchOpts,
  Loadout,
  LogLevel,
  ModCatalog,
  NewsItem,
  WalletInfo,
  WalletTx,
  PinionCore,
  ServerEntry,
  ServerStatus,
  Settings,
  VersionSummary,
} from "./bindings";
import { isAnchor } from "./hud";
import { levelOf } from "./logLevel";
import { loadState, saveState } from "./persistence";
import BRAND from "../brand";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** The capture rig's canned ping answer, when it has set one. */
function rigPing(): { players: number; maxPlayers: number; pingMs: number } | null {
  try {
    const raw = localStorage.getItem("fullmoon.rig.ping");
    if (!raw) return null;
    const v = JSON.parse(raw) as { players?: unknown; maxPlayers?: unknown; pingMs?: unknown };
    const n = (x: unknown) => (typeof x === "number" && Number.isFinite(x) ? x : 0);
    return { players: n(v.players), maxPlayers: n(v.maxPlayers), pingMs: n(v.pingMs) };
  } catch {
    return null;
  }
}
const latency = () => sleep(90 + Math.random() * 160);
const uid = () =>
  "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/* ── seed data ─────────────────────────────────────────────── */

const SEED_ACCOUNTS: Account[] = [
  {
    uuid: "8f3c2a10-7b6e-4d9a-9c1f-2e5a7d4b1a01",
    username: "BlackCow",
    skinHue: 212,
    skinUrl: "/skins/blackcow.png",
    source: "microsoft",
    capes: ["aero-cape"],
  },
  {
    uuid: "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    username: "Alex",
    skinHue: 142,
    skinUrl: null,
    source: "imported",
    capes: [],
  },
];

const SEED_INSTANCES: Instance[] = [
  {
    id: "inst-main",
    name: `${BRAND.name} 26.1.2`,
    versionId: "26.1.2",
    loader: "fabric",
    installed: true,
    installing: null,
    memoryMb: 6144,
    iconHue: 216,
    createdAt: new Date(Date.now() - 86400e3 * 21).toISOString(),
    lastPlayedAt: new Date(Date.now() - 86400e3 * 1.2).toISOString(),
    quickPlayServer: null,
  },
  {
    id: "inst-lab",
    name: "Snapshot Lab",
    versionId: "26.2-pre1",
    loader: "fabric",
    installed: false,
    installing: null,
    memoryMb: 4096,
    iconHue: 268,
    createdAt: new Date(Date.now() - 86400e3 * 4).toISOString(),
    lastPlayedAt: null,
    quickPlayServer: null,
  },
];

const VERSIONS: VersionSummary[] = [
  { id: "26.1.2", type: "release", releaseTime: "2026-06-18", isTarget: true },
  { id: "26.1.1", type: "release", releaseTime: "2026-05-29", isTarget: false },
  { id: "26.1.0", type: "release", releaseTime: "2026-05-08", isTarget: false },
  { id: "25.4.2", type: "release", releaseTime: "2026-02-12", isTarget: false },
  { id: "26.2-pre1", type: "snapshot", releaseTime: "2026-07-16", isTarget: false },
  { id: "26.2-snapshot-7", type: "snapshot", releaseTime: "2026-07-09", isTarget: false },
  { id: "26.2-snapshot-6", type: "snapshot", releaseTime: "2026-07-02", isTarget: false },
];

const MOD_CATALOG: ModCatalog = {
  mods: [
    {
      id: "pinion-hud",
      name: `${BRAND.name} HUD`,
      version: "1.0.0",
      description:
        "우리 Fabric 모드. FPS·CPS·키스트로크·좌표·장비·포션·핑 HUD와 인게임 설정 GUI, 줌, 풀브라이트, 코스메틱 렌더.",
      kind: "hud",
      ours: true,
      compatible: true,
      note: null,
    },
    {
      id: "sodium",
      name: "Sodium",
      version: "0.7.2",
      description: "렌더 파이프라인 재작성. 청크 렌더링 최대 ~10배, 프레임 타임 안정화.",
      kind: "perf",
      ours: false,
      compatible: true,
      note: null,
    },
    {
      id: "lithium",
      name: "Lithium",
      version: "0.15.1",
      description: "게임 로직 최적화 — 틱·AI·물리·청크 직렬화. 시각적 변화 없이 TPS 개선.",
      kind: "perf",
      ours: false,
      compatible: true,
      note: null,
    },
    {
      id: "fabric-api",
      name: "Fabric API",
      version: "0.115.0",
      description: "Fabric 모드 공용 API. 번들 모드들의 필수 의존성.",
      kind: "lib",
      ours: false,
      compatible: true,
      note: null,
    },
  ],
};

const COSMETICS: Cosmetic[] = [
  { id: "aero-cape", slot: "cape", name: "아에로 케이프", rarity: "rare", hue: 214, desc: `비행 깃털 문양을 수놓은 ${BRAND.name} 기본 케이프.`, capeUrl: "/capes/aero-cape.png" },
  { id: "ember-cape", slot: "cape", name: "엠버 케이프", rarity: "epic", hue: 22, desc: "불씨가 낙엽처럼 흩날리는 자수.", capeUrl: "/capes/ember-cape.png" },
  { id: "void-cape", slot: "cape", name: "보이드 케이프", rarity: "legendary", hue: 272, desc: "끝없이 가라앉는 별먼지 그라데이션.", capeUrl: "/capes/void-cape.png" },
  { id: "mint-cape", slot: "cape", name: "민트 케이프", rarity: "common", hue: 152, desc: "산뜻한 단색. 조용한 취향.", capeUrl: "/capes/mint-cape.png" },
  { id: "regal-cape", slot: "cape", name: "리갈 케이프", rarity: "epic", hue: 46, desc: "금실 테두리의 의전용 케이프.", capeUrl: "/capes/regal-cape.png" },
  { id: "gale-wings", slot: "wings", name: "질풍 날개", rarity: "epic", hue: 200, desc: "바람을 가르는 6엽 깃털 날개.", capeUrl: null },
  { id: "dusk-wings", slot: "wings", name: "황혼 날개", rarity: "rare", hue: 318, desc: "노을빛으로 물든 겹깃.", capeUrl: null },
  { id: "sprout-wings", slot: "wings", name: "새싹 날개", rarity: "common", hue: 110, desc: "작지만 싱그럽게 파닥여요.", capeUrl: null },
  { id: "eclipse-wings", slot: "wings", name: "이클립스 날개", rarity: "legendary", hue: 258, desc: "일식의 코로나를 형상화한 광륜 날개.", capeUrl: null },
  { id: "comet-trail", slot: "trail", name: "혜성 꼬리", rarity: "rare", hue: 190, desc: "지나간 자리에 푸른 입자를 남겨요.", capeUrl: null },
  { id: "petal-trail", slot: "trail", name: "꽃잎 궤적", rarity: "common", hue: 340, desc: "발걸음마다 꽃잎이 흩어져요.", capeUrl: null },
  { id: "static-trail", slot: "trail", name: "스태틱 궤적", rarity: "epic", hue: 56, desc: "전기 스파크가 튀는 잔광.", capeUrl: null },
];

const NEWS: NewsItem[] = [
  {
    id: "n1",
    tag: "update",
    title: `${BRAND.name} 1.0 — 만월의 정원으로`,
    summary: `첫 정식 릴리스예요. 26.1.2 대상, Fabric 번들, 풀문 HUD와 브리지를 담았어요. 만월 아래, 모두가 모이는 정원.`,
    date: "2026-07-20",
    hue: 45,
    featured: true,
  },
  {
    id: "n2",
    tag: "dev",
    title: "로비 리뉴얼 — 만월궁이 다시 떠오르는 중",
    summary: "백악 벽과 곡선 지붕의 히메지형 궁궐. 워프 메뉴(K)로 정문에서 바로 이동하세요.",
    date: "2026-07-17",
    hue: 258,
    featured: false,
  },
  {
    id: "n3",
    tag: "cosmetic",
    title: "패치노트 — 야생 첫 시즌 밸런스",
    summary: "몹 난이도 곡선과 드롭 테이블을 조정했어요. 재화 반영 내역은 런처 대시보드의 재화 탭에서 볼 수 있어요.",
    date: "2026-07-14",
    hue: 170,
    featured: false,
  },
  {
    id: "n4",
    tag: "event",
    title: "보름달 채집축제 — 이번 달 보름날 밤 8시",
    summary: "광장에 뜨는 달 조각을 모아 한정 코스메틱으로 교환하세요. 일정은 디스코드 공지 채널에서 확인해 주세요.",
    date: "2026-07-10",
    hue: 45,
    featured: false,
  },
  {
    id: "n5",
    tag: "update",
    title: `${BRAND.name} HUD 프리뷰: 키스트로크 & 포션 타이머`,
    summary: "인게임 모듈 두 개가 런처 설정과 실시간으로 맞춰져요. hud.json 계약도 공개했어요.",
    date: "2026-07-06",
    hue: 22,
    featured: false,
  },
];

const SERVERS: ServerEntry[] = [
  { id: "s1", name: "로비", address: "play.fullmoon.ink", motd: "만월의 정원 — 26.1.2", players: 14, maxPlayers: 30, pingMs: 9, hue: 45 },
  { id: "s2", name: "야생", address: "play.fullmoon.ink", motd: "시즌 1 — 첫 야생", players: 37, maxPlayers: 30, pingMs: 11, hue: 170 },
  { id: "s3", name: "훈련장", address: "play.fullmoon.ink", motd: "전투·점프맵 훈련장", players: 8, maxPlayers: 30, pingMs: 10, hue: 270 },
];

const JAVA_RUNTIMES: JavaRuntime[] = [
  { path: "C:\\Program Files\\Java\\jdk-25", version: "25.0.3", vendor: "Oracle JDK", arch: "x64", recommended: true },
  { path: "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.5", version: "21.0.5", vendor: "Temurin", arch: "x64", recommended: false },
  { path: "C:\\Program Files\\Zulu\\zulu-17", version: "17.0.11", vendor: "Azul Zulu", arch: "x64", recommended: false },
];

const DEFAULT_SETTINGS: Settings = {
  javaPath: JAVA_RUNTIMES[0].path,
  javaArgs: "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200",
  memoryMb: 6144,
  concurrency: 8,
  theme: "dark",
  accent: "gilt",
  language: "ko",
  telemetry: false,
};

/* Wallet fixtures use the economy backend's own TX_LABELS vocabulary
   (coin-bridge-bot/src/economy/cardTheme.js) — a launcher panel that
   renames 출석 보상 or 알바 is a panel that drifts from the bot. */
const WALLET: WalletInfo = { currency: "원", balance: 128450, updatedAt: "2026-08-22T21:40:00Z" };

const TXS: WalletTx[] = [
  { delta: -3500, reason: "discord.shop.buy", label: "상점 구매", balanceAfter: 128450, at: "2026-08-22T20:12:00Z" },
  { delta: 1200, reason: "discord.daily", label: "출석 보상", balanceAfter: 131950, at: "2026-08-22T09:02:00Z" },
  { delta: 800, reason: "discord.work", label: "알바", balanceAfter: 130750, at: "2026-08-21T23:30:00Z" },
  { delta: 640, reason: "discord.drop.pick", label: "돈뿌리기 줍기", balanceAfter: 129950, at: "2026-08-21T22:58:00Z" },
  { delta: 320, reason: "discord.chat", label: "채팅 활동", balanceAfter: 129310, at: "2026-08-21T21:47:00Z" },
];

/* The layout the real core hands out, which is `catalog.json`'s `defaultHud` — itself pinned to the
   client's own element constructors by `scripts/hud-contract.test.ts`. Restated rather than imported
   so this file stays a standalone stand-in, and wrong the moment it drifts. */
const DEFAULT_HUD: HudConfig = {
  elements: {
    coords: { enabled: true, anchor: "TOP_LEFT", offsetX: 16, offsetY: 56, scale: 1 },
    fps: { enabled: true, anchor: "TOP_LEFT", offsetX: 16, offsetY: 82, scale: 1 },
    ping: { enabled: true, anchor: "TOP_RIGHT", offsetX: 16, offsetY: 56, scale: 1 },
    clock: { enabled: true, anchor: "TOP_RIGHT", offsetX: 16, offsetY: 82, scale: 1 },
    keystrokes: { enabled: true, anchor: "BOTTOM_RIGHT", offsetX: 16, offsetY: 56, scale: 1 },
    tps: { enabled: false, anchor: "TOP_RIGHT", offsetX: 16, offsetY: 108, scale: 1 },
    armor: { enabled: false, anchor: "BOTTOM_LEFT", offsetX: 16, offsetY: 56, scale: 1 },
    effects: { enabled: false, anchor: "TOP_RIGHT", offsetX: 16, offsetY: 134, scale: 1 },
  },
  gridSnap: 4,
};

const DEFAULT_LOADOUT: Loadout = { cape: "aero-cape", wings: null, trail: null };

/** The stored half of a HUD layout over the default, keyed by element id, ignoring a gridSnap the
 *  editor could not divide by. Anything stored under an id this build cannot name is carried
 *  through untouched: it belongs to a client newer than this launcher. */
function mergeHud(stored: unknown): HudConfig {
  const base: HudConfig = {
    elements: Object.fromEntries(
      Object.entries(DEFAULT_HUD.elements).map(([id, e]) => [id, { ...e }]),
    ),
    gridSnap: DEFAULT_HUD.gridSnap,
  };
  if (!stored || typeof stored !== "object") return base;
  const disk = stored as Partial<HudConfig>;
  for (const [id, e] of Object.entries(disk.elements ?? {})) {
    if (e && isAnchor(String(e.anchor)) && Number.isFinite(e.scale) && e.scale > 0) {
      base.elements[id] = { ...e };
    }
  }
  if (Number.isFinite(disk.gridSnap) && (disk.gridSnap as number) > 0) {
    base.gridSnap = Math.trunc(disk.gridSnap as number);
  }
  return base;
}

/* ── install simulation tables ─────────────────────────────── */

const STAGE_WEIGHTS: Record<Exclude<InstallStage, "done">, number> = {
  manifest: 4,
  libraries: 26,
  assets: 30,
  jre: 18,
  fabric: 12,
  mods: 10,
};

const STAGE_FILES: Record<Exclude<InstallStage, "done">, string[]> = {
  manifest: ["version_manifest_v2.json", "26.1.2.json"],
  libraries: [
    "client.jar",
    "net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar",
    "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
    "com/google/guava/guava/33.2.1/guava-33.2.1.jar",
    "net/minecraft/minecraft-merged-26.1.2.jar",
    "org/ow2/asm/asm-9.7.1.jar",
  ],
  assets: [
    "indexes/26.json",
    "objects/4f/4f9a…c1.lang",
    "objects/9b/9b2e…a7.ogg",
    "objects/d1/d1cc…3f.png",
    "objects/77/77aa…e9.ogg",
    "objects/30/30fe…b2.png",
  ],
  jre: ["jre-25+7-windows-x64.tar.gz", "bin/java.exe", "lib/modules"],
  fabric: [
    "intermediary/26.1.2/mappings.tiny",
    "fabric-loader-0.17.2.jar (install)",
    "fabric-api-0.115.0.jar",
  ],
  mods: ["pinion-hud-1.0.0.jar", "sodium-0.7.2.jar", "lithium-0.15.1.jar"],
};

/* ── boot log script ───────────────────────────────────────── */

/* Whole lines in the shape the game prints them, carrying the level the game itself printed. What
   the console makes of one is then derived by `levelOf`, the way the real core derives it, so a line
   accented out here is a line the running game would really have got accented. Our client's own
   lines carry `(Fullmoon/…)`; their text is its real output (i3/docs/evidence/p9r-live-client.log). */
interface LogLine { delay: number; level: "INFO" | "WARN" | "DEBUG" | "ERROR"; thread: string; text: string }

function bootScript(version: string, server: string | null): LogLine[] {
  const L = (delay: number, thread: string, text: string, level: LogLine["level"] = "INFO"): LogLine =>
    ({ delay, level, thread, text });
  const lines: LogLine[] = [
    L(120, "main", `Loading Minecraft ${version} with Fabric Loader 0.17.2`),
    L(180, "main", "Loading 4 mods:"),
    L(60, "main", "\t- fabric-api 0.115.0"),
    L(40, "main", "\t- fullmoon 3.0.0"),
    L(40, "main", "\t- lithium 0.15.1"),
    L(40, "main", "\t- sodium 0.7.2"),
    L(210, "main", "SpongePowered MIXIN Subsystem Version=0.8.7"),
    L(140, "main", "Compatibility level: JAVA_25"),
    L(330, "main", "Applying 214 mixins... done (812 ms)"),
    L(260, "Render thread", "Backend library: LWJGL version 3.3.3"),
    L(190, "Render thread", "OpenGL Vendor: NVIDIA Corporation"),
    L(90, "Render thread", "OpenGL Renderer: NVIDIA GeForce RTX 4070/PCIe/SSE2"),
    L(90, "Render thread", "OpenGL Version: 4.6.0 NVIDIA 561.09"),
    L(520, "Render thread", "Reloading ResourceManager: Default, Fabric Mods"),
    L(420, "Render thread", "(Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1"),
    L(610, "Worker-Main-3", "Baking models: 21468 models in 748 ms"),
    L(280, "Render thread", "Ambiguity between arguments [teleport, location] and [teleport, targets]", "WARN"),
    L(340, "Render thread", "Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"),
    L(150, "Render thread", "Created: 256x128x4 minecraft:textures/atlas/signs.png-atlas"),
    L(290, "Render thread", "Sound engine started (OpenAL Soft 1.23.1)"),
    L(220, "Render thread", "Narrator library successfully loaded"),
    L(510, "Server thread", `Starting integrated minecraft server version ${version}`),
    L(380, "Server thread", 'Preparing level "world"'),
    L(300, "Server thread", "Preparing spawn area: 34%"),
    L(300, "Server thread", "Preparing spawn area: 78%"),
    L(300, "Server thread", "Preparing spawn area: 100%"),
    L(240, "Server thread", "Time elapsed: 2401 ms"),
  ];
  if (server) {
    lines.push(
      L(420, "Render thread", `Connecting to ${server}, 25565`),
      L(380, "Netty Client IO #1", "Handshake complete — protocol 773"),
      L(320, "Render thread", "Joined server hub. 40 chunks loaded."),
      L(360, "Render thread", "(Fullmoon/Channel) Sent fullmoon:v1 hello (proto 1)"),
      L(240, "Render thread", "(Fullmoon/Channel) Received fullmoon:v1 welcome (server proto 1, mode ACTIVE)"),
    );
  } else {
    lines.push(
      L(360, "Render thread", 'Loaded 0 advancements, world "world" ready'),
      L(300, "Render thread", "Singleplayer session started."),
    );
  }
  return lines;
}

/* Fabric prints a named logger as `[thread/LEVEL] (Name) msg` and the game's own as
   `[thread/LEVEL]: msg`; the tag in the first form is what `levelOf` reads as ours. */
const composed = (line: LogLine): string =>
  `[${line.thread}/${line.level}]${line.text.startsWith("(Fullmoon/") ? " " : ": "}${line.text}`;

const AMBIENT_LOGS: LogLine[] = [
  { delay: 0, level: "DEBUG", thread: "Worker-Main-5", text: "Chunk system: updated 12 chunk tickets" },
  { delay: 0, level: "INFO", thread: "Render thread", text: "[CHAT] <Steve> o/" },
  {
    delay: 0,
    level: "INFO",
    thread: "Render thread",
    text: "(Fullmoon/Map) Map open: 226x140 cells at 2 blocks per cell, 6 published route(s) in minecraft:overworld",
  },
  { delay: 0, level: "DEBUG", thread: "Server thread", text: "Autosave started" },
  { delay: 0, level: "INFO", thread: "Render thread", text: "Loaded 214 advancements" },
  {
    delay: 0,
    level: "WARN",
    thread: "Render thread",
    text: "Can't keep up! Is the server overloaded? Running 2037ms behind",
  },
  { delay: 0, level: "DEBUG", thread: "Netty Epoll IO #2", text: "Keep-alive RTT 22 ms" },
  {
    delay: 0,
    level: "INFO",
    thread: "Render thread",
    text: "(Fullmoon/Map) Map route chosen: palace_gate at 500 -100 in world",
  },
];

/* ── the mock core ─────────────────────────────────────────── */

type Handler<E extends CoreEventName> = (payload: CoreEvents[E]) => void;

export class MockCore implements PinionCore {
  private accounts: Account[];
  private activeUuid: string | null;
  private instances: Instance[];
  private modEnabled: Record<string, Record<string, boolean>>;
  private modFavorite: Record<string, Record<string, boolean>>;
  private settings: Settings;
  private loadouts: Record<string, Loadout>;
  private hudConfigs: Record<string, HudConfig>;
  private servers: ServerEntry[];
  private game: GameState = { state: "idle", sessionId: null, instanceId: null, server: null, startedAt: null, exitCode: null };

  private handlers = new Map<CoreEventName, Set<Handler<CoreEventName>>>();
  private timers = new Set<ReturnType<typeof setTimeout>>();
  private authPolls = new Map<string, number>();

  constructor() {
    const saved = loadState();
    this.accounts = (saved.accounts as Account[] | undefined) ?? SEED_ACCOUNTS;
    this.activeUuid = saved.activeUuid !== undefined ? saved.activeUuid : this.accounts[0]?.uuid ?? null;
    this.instances = ((saved.instances as Instance[] | undefined) ?? SEED_INSTANCES).map((i) => ({
      ...i,
      installing: null, // transient — never persisted mid-flight
    }));
    this.modEnabled = saved.modEnabled ?? {};
    this.modFavorite = saved.modFavorite ?? {};
    this.settings = { ...DEFAULT_SETTINGS, ...(saved.settings as Partial<Settings> | null) };
    this.loadouts = (saved.loadouts as Record<string, Loadout> | undefined) ?? {};
    this.hudConfigs = (saved.hudConfigs as Record<string, HudConfig> | undefined) ?? {};
    this.servers = (saved.servers as ServerEntry[] | undefined) ?? SERVERS;
  }

  private persist() {
    saveState({
      accounts: this.accounts,
      activeUuid: this.activeUuid,
      instances: this.instances,
      modEnabled: this.modEnabled,
      modFavorite: this.modFavorite,
      settings: this.settings,
      loadouts: this.loadouts,
      hudConfigs: this.hudConfigs,
      servers: this.servers,
    });
  }

  private emit<E extends CoreEventName>(event: E, payload: CoreEvents[E]) {
    const set = this.handlers.get(event);
    if (!set) return;
    set.forEach((cb) => (cb as Handler<E>)(payload));
  }

  on<E extends CoreEventName>(event: E, cb: Handler<E>): () => void {
    let set = this.handlers.get(event);
    if (!set) {
      set = new Set();
      this.handlers.set(event, set);
    }
    set.add(cb as Handler<CoreEventName>);
    return () => set.delete(cb as Handler<CoreEventName>);
  }

  private after(ms: number, fn: () => void) {
    const t = setTimeout(() => {
      this.timers.delete(t);
      fn();
    }, ms);
    this.timers.add(t);
  }

  /* ── auth ── */

  async auth_begin_device_code(): Promise<DeviceCodePrompt> {
    await latency();
    const code = Array.from({ length: 8 }, () => "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[(Math.random() * 32) | 0]).join("");
    const prompt: DeviceCodePrompt = {
      session: uid(),
      userCode: `${code.slice(0, 4)}-${code.slice(4)}`,
      verificationUri: "https://microsoft.com/link",
      expiresIn: 900,
    };
    this.authPolls.set(prompt.session, 0);
    this.emit("auth://device", {
      userCode: prompt.userCode,
      verificationUri: prompt.verificationUri,
      expiresIn: prompt.expiresIn,
    });
    return prompt;
  }

  async auth_poll(session: string): Promise<AuthStatus> {
    await sleep(620);
    const polls = (this.authPolls.get(session) ?? 0) + 1;
    this.authPolls.set(session, polls);
    if (polls < 6) return { state: "pending" };
    this.authPolls.delete(session);
    const account: Account = {
      uuid: uid(),
      username: "EnderKnight",
      skinHue: 262,
      skinUrl: null,
      source: "microsoft",
      capes: [],
    };
    this.accounts.push(account);
    this.activeUuid = account.uuid;
    this.persist();
    return { state: "done", account };
  }

  async auth_login_authcode(): Promise<Account> {
    await sleep(2400); // embedded webview round-trip
    const account: Account = {
      uuid: uid(),
      username: "Vex_07",
      skinHue: 8,
      skinUrl: null,
      source: "microsoft",
      capes: ["ember-cape"],
    };
    this.accounts.push(account);
    this.activeUuid = account.uuid;
    this.persist();
    return account;
  }

  async auth_import_official(): Promise<Account[]> {
    await latency();
    const candidates: Account[] = [
      { uuid: uid(), username: "CraftMaster", skinHue: 96, skinUrl: null, source: "imported", capes: [] },
      { uuid: uid(), username: "MinnieKim", skinHue: 330, skinUrl: null, source: "imported", capes: [] },
    ];
    const added = candidates.filter((c) => !this.accounts.some((a) => a.username === c.username));
    this.accounts.push(...added);
    if (added.length > 0) this.persist();
    return added;
  }

  async auth_list(): Promise<Account[]> {
    await latency();
    return this.accounts.map((a) => ({ ...a }));
  }

  async auth_select(uuidSel: string): Promise<void> {
    await sleep(60);
    if (this.accounts.some((a) => a.uuid === uuidSel)) {
      this.activeUuid = uuidSel;
      this.persist();
    }
  }

  async auth_remove(uuidDel: string): Promise<void> {
    await sleep(80);
    this.accounts = this.accounts.filter((a) => a.uuid !== uuidDel);
    delete this.loadouts[uuidDel];
    if (this.activeUuid === uuidDel) this.activeUuid = this.accounts[0]?.uuid ?? null;
    this.persist();
  }

  async auth_refresh(uuidRef: string): Promise<Account> {
    await sleep(420);
    const found = this.accounts.find((a) => a.uuid === uuidRef);
    if (!found) throw new Error("account not found");
    return { ...found };
  }

  async auth_add_offline(username: string): Promise<Account> {
    await sleep(80);
    const name = username.trim();
    if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) {
      throw new Error("a Minecraft name is 1-16 characters of letters, digits or underscore");
    }
    if (this.accounts.some((account) => account.username === name && account.source === "offline")) {
      throw new Error(`${name} is already added`);
    }

    const account: Account = {
      uuid: uid(),
      username: name,
      skinHue: 45,
      skinUrl: null,
      source: "offline",
      capes: [],
    };
    this.accounts = [...this.accounts, account];
    this.activeUuid = this.activeUuid ?? account.uuid;
    this.persist();
    return { ...account };
  }

  getActiveUuid(): string | null {
    return this.activeUuid;
  }

  /* ── versions / instances ── */

  async versions_manifest(): Promise<VersionSummary[]> {
    await latency();
    return VERSIONS.map((v) => ({ ...v }));
  }

  async instances_list(): Promise<Instance[]> {
    await sleep(60);
    return this.instances.map((i) => ({ ...i }));
  }

  async instance_create(spec: InstanceSpec): Promise<Instance> {
    await latency();
    const inst: Instance = {
      id: uid().slice(0, 8),
      name: spec.name,
      versionId: spec.versionId,
      loader: spec.loader,
      installed: false,
      installing: null,
      memoryMb: spec.memoryMb ?? 4096,
      iconHue: spec.iconHue ?? Math.floor(Math.random() * 360),
      createdAt: new Date().toISOString(),
      lastPlayedAt: null,
      quickPlayServer: null,
    };
    this.instances.push(inst);
    this.persist();
    return { ...inst };
  }

  async instance_update(id: string, patch: InstancePatch): Promise<Instance> {
    await sleep(70);
    const inst = this.instances.find((i) => i.id === id);
    if (!inst) throw new Error("instance not found");
    Object.assign(inst, patch);
    this.persist();
    return { ...inst };
  }

  async instance_delete(id: string): Promise<void> {
    await sleep(120);
    this.instances = this.instances.filter((i) => i.id !== id);
    delete this.modEnabled[id];
    delete this.hudConfigs[id];
    this.persist();
  }

  async instance_install(id: string): Promise<string> {
    const inst = this.instances.find((i) => i.id === id);
    if (!inst) throw new Error("instance not found");
    if (inst.installing) return id; // already running
    const taskId = `task-${id}`;
    inst.installing = { stage: "manifest", pct: 0 };

    const stages = Object.keys(STAGE_WEIGHTS) as Array<Exclude<InstallStage, "done">>;
    const stageStart: Record<string, number> = {};
    let acc = 0;
    for (const s of stages) {
      stageStart[s] = acc;
      acc += STAGE_WEIGHTS[s];
    }

    const tickMs = 120;
    const totalMs = 11_000 + Math.random() * 2_500;
    let elapsed = 0;
    let fileIdx = 0;

    const timer = setInterval(() => {
      const current = this.instances.find((i) => i.id === id);
      if (!current) {
        clearInterval(timer);
        return;
      }
      elapsed += tickMs * (0.75 + Math.random() * 0.6);
      const overall = clamp((elapsed / totalMs) * 100, 0, 100);

      let stage: Exclude<InstallStage, "done"> = "mods";
      for (const s of stages) {
        if (overall >= stageStart[s]) stage = s;
      }
      current.installing = { stage, pct: overall };
      this.emit("install://stage", { instanceId: id, stage, pct: overall });

      // file-level download events during the heavy stages
      const files = STAGE_FILES[stage];
      if (Math.random() < 0.8) {
        const file = files[fileIdx % files.length];
        fileIdx += 1;
        const done = clamp(elapsed / totalMs, 0.02, 1);
        this.emit("download://progress", {
          taskId,
          file,
          done,
          total: 1,
          bytesPerSec: (6 + Math.random() * 20) * 1_048_576,
        });
      }

      if (overall >= 100) {
        clearInterval(timer);
        current.installed = true;
        current.installing = null;
        // freshly installed instances get the full bundle enabled
        this.modEnabled[id] = Object.fromEntries(MOD_CATALOG.mods.map((m) => [m.id, true]));
        this.emit("install://stage", { instanceId: id, stage: "done", pct: 100 });
        this.persist();
      }
    }, tickMs);
    this.timers.add(timer as unknown as ReturnType<typeof setTimeout>);
    return taskId;
  }

  /* ── mods ── */

  async mods_available(): Promise<ModCatalog> {
    await latency();
    return MOD_CATALOG;
  }

  async mods_list(instanceId: string): Promise<InstalledMod[]> {
    await sleep(70);
    const enabledMap = this.modEnabled[instanceId] ?? {};
    const favMap = this.modFavorite[instanceId] ?? {};
    const modded = this.instances.find((i) => i.id === instanceId)?.loader === "fabric";
    return MOD_CATALOG.mods.map((m) => ({
      ...m,
      compatible: m.compatible && modded,
      enabled: modded && (enabledMap[m.id] ?? true),
      favorite: favMap[m.id] ?? false,
      installed: modded && (enabledMap[m.id] ?? true),
      file: modded ? `${m.id}-${m.version}.jar` : undefined,
    }));
  }

  async mod_toggle(instanceId: string, modId: string, enabled: boolean): Promise<void> {
    await sleep(60);
    if (!this.modEnabled[instanceId]) this.modEnabled[instanceId] = {};
    this.modEnabled[instanceId][modId] = enabled;
    this.persist();
  }

  async mod_favorite(instanceId: string, modId: string, favorite: boolean): Promise<void> {
    await sleep(40);
    if (!this.modFavorite[instanceId]) this.modFavorite[instanceId] = {};
    this.modFavorite[instanceId][modId] = favorite;
    this.persist();
  }

  /* ── launch ── */

  async launch(instanceId: string, opts?: LaunchOpts): Promise<string> {
    const inst = this.instances.find((i) => i.id === instanceId);
    if (!inst || !inst.installed) throw new Error("instance not installed");
    if (this.game.state === "running" || this.game.state === "starting") return this.game.sessionId!;
    await sleep(300);

    const sessionId = uid();
    this.game = {
      state: "starting",
      sessionId,
      instanceId,
      server: opts?.server ?? null,
      startedAt: Date.now(),
      exitCode: null,
    };
    this.emit("game://state", { sessionId, state: "starting" });

    // stream the boot log, then flip to running
    const script = bootScript(inst.versionId, opts?.server ?? null);
    let t = 0;
    script.forEach((line) => {
      t += line.delay;
      this.after(t, () => {
        const text = composed(line);
        this.emit("game://log", { sessionId, level: levelOf(text), line: text });
      });
    });
    this.after(t + 350, () => {
      if (this.game.sessionId !== sessionId) return;
      this.game = { ...this.game, state: "running" };
      this.emit("game://state", { sessionId, state: "running" });
      inst.lastPlayedAt = new Date().toISOString();
      this.persist();
      // ambient chatter while "in game"
      let n = 0;
      const ambient = setInterval(() => {
        if (this.game.sessionId !== sessionId || this.game.state !== "running") {
          clearInterval(ambient);
          return;
        }
        const text = composed(AMBIENT_LOGS[n % AMBIENT_LOGS.length]);
        n += 1;
        this.emit("game://log", { sessionId, level: levelOf(text), line: text });
      }, 3400);
      this.timers.add(ambient as unknown as ReturnType<typeof setTimeout>);
    });
    return sessionId;
  }

  async launch_quickplay(instanceId: string, server: string): Promise<string> {
    return this.launch(instanceId, { server });
  }

  async game_kill(sessionId: string): Promise<void> {
    if (this.game.sessionId !== sessionId) return;
    // the real core kills the child and reports it on game://state; it prints no line of its own
    this.after(500, () => {
      this.game = { ...this.game, state: "closed", exitCode: 0 };
      this.emit("game://state", { sessionId, state: "closed", exitCode: 0 });
    });
  }

  async game_status(): Promise<GameState> {
    return { ...this.game };
  }

  async game_log(): Promise<{ sessionId: string; level: LogLevel; line: string }[]> {
    return [];
  }

  /* ── cosmetics / hud ── */

  async cosmetics_catalog(): Promise<Cosmetic[]> {
    await latency();
    return COSMETICS.map((c) => ({ ...c }));
  }

  async cosmetics_equipped(uuid: string): Promise<Loadout> {
    await sleep(60);
    return { ...(this.loadouts[uuid] ?? DEFAULT_LOADOUT) };
  }

  async cosmetics_equip(uuid: string, slot: CosmeticSlot, itemId: string | null): Promise<void> {
    await sleep(70);
    this.loadouts[uuid] = { ...(this.loadouts[uuid] ?? DEFAULT_LOADOUT), [slot]: itemId };
    this.persist();
  }

  /* Merged onto the default per element, as `hud.rs::read` does — the file on the other side is
     written by the mod too, and a layout saved by an older launcher is exactly the case the real
     core survives by merging rather than replacing. */
  async hud_get(instanceId: string): Promise<HudConfig> {
    await sleep(60);
    return mergeHud(this.hudConfigs[instanceId]);
  }

  async hud_set(instanceId: string, cfg: HudConfig): Promise<void> {
    await sleep(70);
    this.hudConfigs[instanceId] = mergeHud(cfg);
    this.persist();
  }

  async hud_reset(instanceId: string): Promise<HudConfig> {
    await sleep(70);
    delete this.hudConfigs[instanceId];
    this.persist();
    return mergeHud(undefined);
  }

  /* ── settings ── */

  async settings_get(): Promise<Settings> {
    await sleep(50);
    return { ...this.settings };
  }

  async settings_set(patch: Partial<Settings>): Promise<Settings> {
    await sleep(70);
    this.settings = { ...this.settings, ...patch };
    this.persist();
    return { ...this.settings };
  }

  async java_detect(): Promise<JavaRuntime[]> {
    await sleep(700); // scanning PATH / registry
    return JAVA_RUNTIMES.map((j) => ({ ...j }));
  }

  async system_memory_mb(): Promise<number> {
    return 16384; // a plausible laptop, so the slider's ceiling copy has something to say
  }

  /* ── home ── */

  async news_feed(): Promise<NewsItem[]> {
    await latency();
    return NEWS.map((n) => ({ ...n }));
  }

  async economy_wallet(): Promise<WalletInfo> {
    await latency();
    return { ...WALLET };
  }

  async economy_transactions(): Promise<WalletTx[]> {
    await latency();
    return TXS.map((t) => ({ ...t }));
  }

  async servers_list(): Promise<ServerEntry[]> {
    await sleep(120);
    return this.servers.map((s) => ({ ...s }));
  }

  /* The browser build cannot open a TCP socket, so this is the one place the
     mock has to invent — it says so by reporting every server as unreachable
     rather than by making up a plausible player count. */
  async servers_ping(addresses: string[]): Promise<Record<string, ServerStatus>> {
    await sleep(200);
    // The capture rig (scripts/systemshot.mjs) photographs the live state too; it sets this key
    // with the answer to report. Nothing else sets it, so a player's browser build never sees it.
    const rig = rigPing();
    if (rig) {
      return Object.fromEntries(addresses.map((a) => [a, { online: true, motd: "", version: "", ...rig }]));
    }
    return Object.fromEntries(
      addresses.map((a) => [
        a,
        {
          online: false,
          motd: "",
          players: 0,
          maxPlayers: 0,
          pingMs: 0,
          version: "",
          error: "no socket in the browser build",
        },
      ]),
    );
  }

  async servers_save(list: ServerEntry[]): Promise<void> {
    await sleep(70);
    this.servers = list.map((s) => ({ ...s }));
    this.persist();
  }
}
