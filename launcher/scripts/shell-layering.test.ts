/* The shell under 달빛 유리: one gliding selection per list, the mock's geometry on the play
   screen, and no palace left anywhere in the launcher. */
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const src = (rel: string) => readFileSync(join(root, "src", rel), "utf8");
const shell = src("styles/shell.css");
const screens = src("styles/screens.css");
const ui = src("styles/ui.css");

const rule = (css: string, selector: string) => {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = css.match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`));
  assert.ok(match, `${selector} rule is missing`);
  return match[1];
};

function files(dir: string, keep: (name: string) => boolean, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) files(p, keep, out);
    else if (keep(name)) out.push(p);
  }
  return out;
}

test("the play screen's hero renders on the play route and nowhere else", () => {
  const app = src("App.tsx");
  const map = app.match(/const SCREENS = \{([\s\S]*?)\} as const;/)?.[1];
  assert.ok(map, "App.tsx maps screens to components in SCREENS");
  const routes = [...map.matchAll(/(\w+):\s*(\w+),/g)];
  assert.deepEqual(
    routes.filter(([, , component]) => component === "PlayScreen").map(([, route]) => route).sort(),
    ["home", "play"],
  );
  assert.match(app, /const Screen = SCREENS\[screen\];/);
  for (const rel of ["screens/Home.tsx", "screens/Dashboard.tsx", "screens/Mods.tsx", "App.tsx"]) {
    assert.doesNotMatch(src(rel), /className="hero"/, rel);
  }
  // the masthead is every other screen's heading; the hero is the play screen's own
  assert.match(app, /\{!onPlay && \(\s*<TopBar/);
});

test("the shell has the mock's frame: titlebar 36, rail 208, dock 40, hero column 72 in", () => {
  const vars = rule(shell, ":root");
  assert.match(vars, /--titlebar-h:\s*36px/);
  assert.match(vars, /--rail-w:\s*208px/);
  assert.match(vars, /--dock-h:\s*40px/);
  assert.match(vars, /--hero-x:\s*72px/);
  assert.match(rule(shell, ".rail-item"), /height:\s*40px/);
  assert.match(rule(shell, ".dock"), /height:\s*var\(--dock-h\)/);
});

test("the play screen lays out as mock/g-launcher.html", () => {
  assert.match(rule(screens, ".hero"), /height:\s*560px/);
  const col = rule(screens, ".hero-col");
  assert.match(col, /left:\s*var\(--hero-x\)/);
  assert.match(col, /top:\s*96px/);
  assert.match(col, /width:\s*420px/);
  assert.match(rule(screens, ".hero-mark"), /700 56px\/60px var\(--font-display\)/);
  assert.match(rule(screens, ".hero-play"), /height:\s*56px/);
  assert.match(rule(screens, ".hero-play-word"), /700 22px\/28px var\(--font-display\)/);
  assert.match(rule(screens, ".hero-sub-btn"), /height:\s*40px/);
  const moon = rule(screens, ".hero-moon");
  assert.match(moon, /right:\s*96px/);
  assert.match(moon, /top:\s*72px/);
  const news = rule(screens, ".hero-news");
  assert.match(news, /width:\s*360px/);
  assert.match(news, /bottom:\s*48px/);
  const grid = rule(screens, ".play-grid");
  assert.match(grid, /repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(grid, /gap:\s*16px/);
  assert.match(grid, /padding:\s*24px var\(--hero-x\)/);
  assert.match(rule(screens, ".srv-card-go"), /height:\s*30px/);
  // the one gold on the screen is the way in; the moon is 72 in radius
  assert.match(src("screens/Play.tsx"), /<Moon className="hero-moon" r=\{72\}/);
});

test("selection glides: one indicator per list, moved on the base duration, snapped on first paint", () => {
  const glide = rule(ui, ".glide");
  assert.match(glide, /position:\s*absolute/);
  assert.match(glide, /top var\(--dur-base\) var\(--ease-out\)/);
  assert.match(glide, /height var\(--dur-base\) var\(--ease-out\)/);
  assert.match(rule(ui, ".glide.is-snapped"), /transition:\s*none/);
  const component = src("components/Glide.tsx");
  assert.match(component, /querySelector<HTMLElement>\("\[data-current='true'\]"\)/);
  assert.match(component, /ResizeObserver/);
  for (const rel of ["components/Sidebar.tsx", "screens/Settings.tsx"]) {
    const s = src(rel);
    assert.match(s, /<GlideList/, `${rel} lists through GlideList`);
    assert.match(s, /data-current=\{[^}]*"true"/, `${rel} marks the current item`);
    assert.doesNotMatch(s, /is-current[^\n]*background/, `${rel} paints no per-item selection`);
  }
  // reduced motion: the launcher's own setting is the same rule as the system's
  const base = src("styles/base.css");
  const media = base.match(/@media \(prefers-reduced-motion: reduce\) \{([\s\S]*?)\n\}/)?.[1] ?? "";
  assert.match(media, /transition-duration: var\(--dur-reduced\) !important/);
  assert.match(base, /\[data-motion="reduced"\] \*[\s\S]*?transition-duration: var\(--dur-reduced\) !important/);
});

test("a screen change fades the old screen out once and never brings it back", () => {
  const base = src("styles/base.css");
  // the window stays live so the rail glides in the open; only the content area transitions
  assert.match(base, /::view-transition-old\(root\), ::view-transition-new\(root\) \{ animation: none; \}/);
  assert.match(base, /\.content \{ view-transition-name: screen; \}/);
  // the fade is filled and the transition lasts exactly as long, so the old screen cannot reappear
  assert.match(base, /::view-transition-old\(screen\) \{ animation: fade-out var\(--dur-fast\) var\(--ease-out\) both; \}/);
  assert.match(base, /::view-transition-group\(screen\) \{ animation-duration: var\(--dur-fast\); \}/);
  // reduced motion skips the view transition altogether
  assert.match(src("state/store.tsx"), /if \(doc\.startViewTransition && !reduced\)/);
});

test("no palace remains: no ornament tokens, no frame classes, no Palace module", () => {
  const tokens = src("design/tokens.css");
  assert.doesNotMatch(tokens, /--color-(ornament|line-gilt|line-gilt-faint|line-lattice)/);
  assert.match(tokens, /--color-brand-seal:/);
  assert.ok(!existsSync(join(root, "src/components/Palace.tsx")));
  assert.ok(!existsSync(join(root, "src/styles/palace.css")));
  const sources = files(join(root, "src"), (n) => /\.(tsx?|css|html)$/.test(n)).concat(join(root, "index.html"));
  const hits = sources
    .filter((f) => !f.endsWith("design/tokens.css"))
    .filter((f) => /\bpf-|Dancheong|MoonDial|--pf-|ornament-|line-gilt|lattice/.test(readFileSync(f, "utf8")))
    .map((f) => f.slice(root.length));
  assert.deepEqual(hits, []);
});

test("figures are tabular: the bold and every .num run come from the full Pretendard", () => {
  // the mod's baked cuts are a subset with no OpenType features, so tabular-nums needs the full face
  const base = src("styles/base.css");
  assert.match(base, /font-family: 'Fullmoon Sans';\s*src: url\('\/fonts\/Pretendard-Bold\.woff2'\) format\('woff2'\);\s*font-weight: 700;/);
  assert.match(base, /\.num \{ font-family: 'Pretendard',[^}]*tabular-nums; \}/);
});

test("the ui sounds are the game's own cues, behind a setting that starts off", () => {
  const sounds = src("core/uiSounds.ts");
  assert.match(sounds, /let enabled = false;/);
  for (const cue of ["focus", "confirm", "back", "open", "close", "error", "tab"]) {
    assert.match(sounds, new RegExp(`i3/mod/src/main/resources/assets/fullmoon/sounds/ui/${cue}\\.ogg`));
  }
  const tokensJson = JSON.parse(readFileSync(join(root, "../i3/design/tokens.json"), "utf8"));
  const sound = tokensJson.sound;
  assert.match(sounds, new RegExp(`volume: ${sound.volume}, focusIntervalMs: ${sound.focusIntervalMs}, focusPitchJitter: ${sound.focusPitchJitter}`));
  const store = src("state/store.tsx");
  assert.match(store, /DEFAULT_UI_PREFS: UiPrefs = \{ reduceMotion: false, sounds: false \}/);
  const settings = src("screens/Settings.tsx");
  assert.match(settings, /settings\.reduceMotion/);
  assert.match(settings, /settings\.sounds/);
});
