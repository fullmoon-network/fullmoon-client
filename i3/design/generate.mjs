#!/usr/bin/env node
// Emits the design tokens into both halves of the client. Run from i3/design:
//   node generate.mjs
// Anything that needs a colour reads it from the generated file. If you find
// yourself wanting a value that is not here, add it to tokens.json first.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { oklchToRgb, contrast } from './_oklch.mjs';

const tokens = JSON.parse(readFileSync('tokens.json', 'utf8'));
const HERE = import.meta.dirname;

const CONST = (name) =>
  name.replace(/[.-]/g, '_').replace(/([a-z0-9])([A-Z])/g, '$1_$2').toUpperCase();
const CSSVAR = (name) =>
  name.replace(/\./g, '-').replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();

/** Opaque sRGB hex of an entry, from its oklch triple or its hex. */
const hex = (entry) => {
  if (entry.hex) return entry.hex.toUpperCase();
  const [r, g, b] = oklchToRgb(...entry.oklch);
  return '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase();
};
const alpha = (entry) => (entry.alpha === undefined ? 1 : entry.alpha);
/** Packed 0xAARRGGBB as Java writes it. */
const packed = (entry) => {
  const a = Math.round(alpha(entry) * 255).toString(16).padStart(2, '0').toUpperCase();
  return `0x${a}${hex(entry).slice(1)}`;
};
const describe = (entry) => {
  const source = entry.oklch ? `oklch(${entry.oklch.join(' ')})` : hex(entry);
  return alpha(entry) === 1 ? source : `${source} @ ${alpha(entry)}`;
};

const colors = Object.entries(tokens.color).filter(([k]) => !k.startsWith('$'));
const resolved = colors.map(([name, entry]) => ({ name, entry, hex: hex(entry), game: entry.game !== false }));
const gameColors = resolved.filter((c) => c.game);

/* ---------- Tokens.java ---------- */
const javaLines = [];
const j = (s = '') => javaLines.push(s);
j('package dev.fullmoon.client.design;');
j('');
j('/**');
j(' * Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand,');
j(' * and do not write a colour, radius, or duration literal anywhere else in the mod —');
j(' * design/verify-tokens.mjs fails on any that appear outside this file.');
j(' */');
j('public final class Tokens {');
j('    private Tokens() {}');
j('');
j('    /**');
j('     * Packed 0xAARRGGBB. Inks are opaque; grounds carry the alpha the design gives them, because');
j('     * the glass is a translucent pane over the game\'s own blur and a hover or a selection is a');
j('     * tint of that pane. Rgb#alpha reopens the alpha of an opaque token for a scrim or a fade.');
j('     */');
j('    public static final class Color {');
for (const { name, entry } of gameColors) {
  j(`        /** ${entry.use} · ${describe(entry)} */`);
  j(`        public static final int ${CONST(name)} = ${packed(entry)};`);
}
j('');
j('        private Color() {}');
j('    }');
j('');
const intGroup = (className, entries) => {
  j(`    public static final class ${className} {`);
  for (const [k, v] of entries) j(`        public static final int ${CONST(k)} = ${v};`);
  j('');
  j(`        private ${className}() {}`);
  j('    }');
  j('');
};
const plain = (group) => Object.entries(group).filter(([k]) => !k.startsWith('$'));
intGroup('Space', plain(tokens.space));
j('    /** Fixed heights and widths of the glass components, GUI px. */');
intGroup('Size', plain(tokens.size));
intGroup('Radius', plain(tokens.radius));
j('    public static final class Stroke {');
for (const [k, v] of Object.entries(tokens.stroke)) {
  j(Number.isInteger(v)
    ? `        public static final int ${CONST(k)} = ${v};`
    : `        public static final float ${CONST(k)} = ${v}f;`);
}
j('');
j('        private Stroke() {}');
j('    }');
j('');
intGroup('Duration', plain(tokens.motion.duration));
j('    public static final class Easing {');
j('        /** Control points of a cubic Bézier from (0,0) to (1,1), as CSS cubic-bezier() takes them. */');
j('        public record Curve(float x1, float y1, float x2, float y2) {}');
j('');
for (const [k, v] of Object.entries(tokens.motion.easing))
  j(`        public static final Curve ${CONST(k)} = new Curve(${v.map((n) => n.toFixed(2) + 'f').join(', ')});`);
j('');
j('        private Easing() {}');
j('    }');
j('');
j('    public static final class Spring {');
j('        /**');
j('         * {@code response} is the period, in seconds, of the undamped spring; {@code dampingFraction}');
j('         * 1 is critically damped, which never overshoots.');
j('         */');
j('        public record Shape(float response, float dampingFraction) {}');
j('');
for (const [k, v] of plain(tokens.motion.spring))
  j(`        public static final Shape ${CONST(k)} = new Shape(${v.response}f, ${v.dampingFraction}f);`);
j('');
j('        private Spring() {}');
j('    }');
j('');
j('    public static final class Sound {');
for (const [k, v] of plain(tokens.sound)) {
  j(Number.isInteger(v)
    ? `        public static final int ${CONST(k)} = ${v};`
    : `        public static final float ${CONST(k)} = ${v}f;`);
}
j('');
j('        private Sound() {}');
j('    }');
j('');
intGroup('Layer', plain(tokens.layer));
j('    /**');
j('     * One baked ttf provider per role and GUI scale. The game rasterises per provider, so a role');
j('     * is a font id and not a scale factor — asking for title at 1.4x would resample the body');
j('     * atlas and blur it. {@link Role#font} is the id stem; Typeset appends the scale suffix.');
j('     */');
j('    public static final class Type {');
j('        /**');
j('         * {@code font} is the provider id stem under assets/fullmoon/font; px and leading are GUI px.');
j('         * {@code latinOnly} marks a role too small for Hangul: Typeset sets Hangul out of it.');
j('         */');
j('        public record Role(String font, int px, int leading, boolean latinOnly) {}');
j('');
const typeRoles = plain(tokens.type);
for (const [k, v] of typeRoles) {
  j(`        /** ${v.face} ${v.px}/${v.leading}${v.tabular ? ' · tabular figures' : ''}${v.latinOnly ? ' · Latin and digits only' : ''} */`);
  j(`        public static final Role ${CONST(k)} = new Role("${v.font}", ${v.px}, ${v.leading}, ${v.latinOnly ? 'true' : 'false'});`);
}
j('');
j('        /** Declaration order, for the design specimen screen. */');
j(`        public static final java.util.List<java.util.Map.Entry<String, Role>> ROLL =`);
j('            java.util.List.of(');
typeRoles.forEach(([k], i) => {
  j(`                java.util.Map.entry("${k}", ${CONST(k)})${i === typeRoles.length - 1 ? '' : ','}`);
});
j('            );');
j('');
j('        private Type() {}');
j('    }');
j('');
j('    /** Token name to packed colour, in declaration order, for the design specimen screen. */');
j('    public static final java.util.List<java.util.Map.Entry<String, Integer>> COLOR_ROLL =');
j('        java.util.List.of(');
gameColors.forEach(({ name }, i) => {
  const tail = i === gameColors.length - 1 ? '' : ',';
  j(`            java.util.Map.entry("${name}", Color.${CONST(name)})${tail}`);
});
j('        );');
j('}');

const javaOut = resolve(HERE, '../mod/src/main/java/dev/fullmoon/client/design/Tokens.java');
mkdirSync(dirname(javaOut), { recursive: true });
writeFileSync(javaOut, javaLines.join('\n') + '\n');

/* ---------- tokens.css ---------- */
// The launcher reads the same colours by the same names. It also carries a daylight palette
// (colorDay) and the accent metals, which the game has no use for and so never sees.
const density = tokens.cssDensity;
const px = (gui, factor) => `${Math.round(gui * factor)}px`;
const cssColor = (e) => {
  if (alpha(e) !== 1) {
    const h = hex(e);
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
    return `rgb(${r} ${g} ${b} / ${alpha(e)})`;
  }
  return e.oklch ? `oklch(${e.oklch[0]} ${e.oklch[1]} ${e.oklch[2]})` : hex(e);
};
const dayColors = plain(tokens.colorDay);
const missingDay = colors.map(([k]) => k).filter((k) => !tokens.colorDay[k]);
if (missingDay.length) throw new Error(`colorDay lacks ${missingDay.join(', ')}`);
const metals = plain(tokens.accentMetal);

const css = [];
const c = (s = '') => css.push(s);
const colorBlock = (selector, scheme, entries, hour) => {
  c(`${selector} {`);
  c(`  color-scheme: ${scheme};`);
  for (const [name, entry] of entries) {
    c(`  --color-${CSSVAR(name)}: ${cssColor(entry)}; /* ${describe(entry)} · ${entry.use} */`);
  }
  const gilt = entries.find(([name]) => name === 'accent')[1];
  c(`  --metal-gilt: ${cssColor(gilt)};`);
  for (const [metal, set] of metals) c(`  --metal-${metal}: ${cssColor(set[hour].accent)};`);
  c('}');
  c('');
};
c('/* Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand. */');
c(':root {');
for (const [k, v] of plain(tokens.space)) c(`  --space-${CSSVAR(k)}: ${px(v, density.space)};`);
c('');
for (const [k, v] of plain(tokens.size)) c(`  --size-${CSSVAR(k)}: ${px(v, density.space)};`);
c('');
for (const [k, v] of plain(tokens.radius))
  c(`  --radius-${CSSVAR(k)}: ${k === 'round' ? '999px' : px(v, density.space)};`);
c('');
for (const [k, v] of Object.entries(tokens.stroke)) c(`  --stroke-${CSSVAR(k)}: ${v}px;`);
c('');
for (const [k, v] of plain(tokens.motion.duration)) c(`  --dur-${CSSVAR(k)}: ${v}ms;`);
for (const [k, v] of Object.entries(tokens.motion.easing))
  c(`  --ease-${CSSVAR(k)}: cubic-bezier(${v.join(', ')});`);
c('');
for (const [k, v] of plain(tokens.layer)) c(`  --layer-${CSSVAR(k)}: ${v};`);
c('');
c("  --font-display: 'Fullmoon Serif', 'Hahmlet', serif;");
c("  --font-body: 'Pretendard', system-ui, sans-serif;");
const typeVars = (name, v) => {
  const size = px(v.px, density.type);
  const leading = px(v.leading, density.type);
  c(`  --type-${CSSVAR(name)}-size: ${size};`);
  c(`  --type-${CSSVAR(name)}-leading: ${leading};`);
  c(`  --type-${CSSVAR(name)}: ${v.weight} ${size}/${leading} var(${v.family === 'serif' ? '--font-display' : '--font-body'});`);
};
for (const [k, v] of typeRoles) typeVars(k, v);
for (const [alias, target] of plain(tokens.typeAliases)) typeVars(alias, tokens.type[target]);
c('}');
c('');
colorBlock(':root,\n[data-theme="dark"]', 'dark', resolved.map(({ name, entry }) => [name, entry]), 'night');
colorBlock('[data-theme="light"]', 'light', dayColors, 'day');
for (const [metal, { night, day }] of metals) {
  const withUse = (set) => Object.entries(set).map(([k, e]) => [k, { ...e, use: `${metal} ${k}` }]);
  c(`[data-accent="${metal}"],`);
  c(`[data-accent="${metal}"] [data-theme="dark"] {`);
  for (const [k, e] of withUse(night)) c(`  --color-${CSSVAR(k)}: ${cssColor(e)}; /* ${describe(e)} */`);
  c('}');
  c('');
  c(`[data-theme="light"][data-accent="${metal}"],`);
  c(`[data-accent="${metal}"] [data-theme="light"] {`);
  for (const [k, e] of withUse(day)) c(`  --color-${CSSVAR(k)}: ${cssColor(e)}; /* ${describe(e)} */`);
  c('}');
  c('');
}
c('@media (prefers-reduced-motion: reduce) {');
c('  :root {');
for (const k of Object.keys(plain(Object.fromEntries(plain(tokens.motion.duration)))))
  c(`    --dur-${CSSVAR(k)}: ${k === 'instant' ? 0 : tokens.motion.duration.reduced}ms;`);
c('  }');
c('}');

const cssOut = resolve(HERE, '../../launcher/src/design/tokens.css');
mkdirSync(dirname(cssOut), { recursive: true });
writeFileSync(cssOut, css.join('\n') + '\n');

/* ---------- contrast evidence ---------- */
// A translucent ground is composited over the glass, and the glass over the void, before it is
// measured: that is the darkest thing the game's blur can be under it.
const entryOf = (n) => tokens.color[n];
const dayEntryOf = (n) => tokens.colorDay[n];
const rgbOf = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const hexOf = (rgb) => '#' + rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
const over = (top, bottomHex) => {
  const a = alpha(top);
  const t = rgbOf(hex(top));
  const b = rgbOf(bottomHex);
  return hexOf(t.map((v, i) => v * a + b[i] * (1 - a)));
};
const ground = (name, palette, base) => over(palette(name), base);
const nightGlass = over(entryOf('surface.glass'), hex(entryOf('surface.void')));
const dayGlass = over(dayEntryOf('surface.glass'), hex(dayEntryOf('surface.void')));
const nightGround = (n) => (alpha(entryOf(n)) === 1 ? hex(entryOf(n)) : ground(n, entryOf, nightGlass));
const dayGround = (n) => (alpha(dayEntryOf(n)) === 1 ? hex(dayEntryOf(n)) : ground(n, dayEntryOf, dayGlass));
const ink = (n) => hex(entryOf(n));
const dayInk = (n) => hex(dayEntryOf(n));

const checks = [
  ['ink.primary on glass', ink('ink.primary'), nightGlass, 4.5],
  ['ink.secondary on glass', ink('ink.secondary'), nightGlass, 4.5],
  ['ink.tertiary on glass', ink('ink.tertiary'), nightGlass, 4.5],
  ['ink.primary on raised', ink('ink.primary'), nightGround('surface.raised'), 4.5],
  ['ink.primary on accent.wash', ink('ink.primary'), nightGround('accent.wash'), 4.5],
  ['ink.secondary on accent.wash', ink('ink.secondary'), nightGround('accent.wash'), 4.5],
  ['ink.onAccent on accent', ink('ink.onAccent'), ink('accent'), 4.5],
  ['ink.onAccent on accent.pressed', ink('ink.onAccent'), ink('accent.pressed'), 4.5],
  ['accent on glass', ink('accent'), nightGlass, 4.5],
  ['status.live on glass', ink('status.live'), nightGlass, 4.5],
  ['status.warn on glass', ink('status.warn'), nightGlass, 4.5],
  ['status.danger on glass', ink('status.danger'), nightGlass, 4.5],
  ['status.win on glass', ink('status.win'), nightGlass, 4.5],
  ['status.ash on glass', ink('status.ash'), nightGlass, 4.5],
  ['ink.disabled on glass (42%, decorative floor)', ink('ink.disabled'), nightGlass, 2.0],
  ['line.hairline on glass', ink('line.hairline'), nightGlass, 1.0],
  ['moon.lit on moon.shadow', ink('moon.lit'), ink('moon.shadow'), 7.0],
  ['ink.primary on glassHud over void', ink('ink.primary'), nightGround('surface.glassHud'), 4.5],
];

const launcherPairs = [
  ['ink.tertiary', 'surface.base', 4.5],
  ['ink.tertiary', 'surface.sunken', 4.5],
  ['ink.tertiary', 'surface.void', 4.5],
  ['ink.secondary', 'surface.raised', 4.5],
  ['ink.primary', 'surface.overlay', 4.5],
  ['status.live', 'surface.base', 3.0],
  ['status.danger', 'surface.base', 3.0],
];
for (const [a, b, floor] of launcherPairs) {
  checks.push([`launcher night · ${a} on ${b}`, ink(a), nightGround(b), floor]);
  checks.push([`launcher day · ${a} on ${b}`, dayInk(a), dayGround(b), floor]);
}
const accentSets = [
  ['gilt', 'night', (n) => entryOf(n), nightGlass, ink],
  ['gilt', 'day', (n) => dayEntryOf(n), dayGlass, dayInk],
  ...metals.flatMap(([metal, set]) => [
    [metal, 'night', (n) => set.night[n], nightGlass, ink],
    [metal, 'day', (n) => set.day[n], dayGlass, dayInk],
  ]),
];
for (const [metal, hour, own, glass, base] of accentSets) {
  checks.push([`${metal} ${hour} · ink.onAccent on accent`, base('ink.onAccent'), hex(own('accent')), 4.5]);
  checks.push([`${metal} ${hour} · ink.onAccent on accent.pressed`, base('ink.onAccent'), hex(own('accent.pressed')), 4.5]);
  checks.push([`${metal} ${hour} · accent on glass`, hex(own('accent')), glass, 4.5]);
  checks.push([`${metal} ${hour} · ink.primary on accent.wash`, base('ink.primary'), over(own('accent.wash'), glass), 4.5]);
}

let failed = 0;
console.log(`wrote ${javaOut.replace(/.*\/i3\//, 'i3/')}`);
console.log(`wrote ${cssOut.replace(/.*\/(launcher\/)/, '$1')}`);
console.log('\ncontrast (WCAG 2.x ratio · floor · verdict)');
for (const [label, a, b, floor] of checks) {
  const ratio = contrast(a, b);
  const ok = ratio >= floor;
  if (!ok) failed++;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${ratio.toFixed(2)} : 1  (>= ${floor})  ${label}`);
}
if (failed) {
  console.error(`\n${failed} contrast floor(s) missed — fix tokens.json, not the call site.`);
  process.exit(1);
}
console.log('\nall contrast floors met');

/* ---------- font providers ---------- */
// One json per role and GUI scale. The game rasterises a ttf provider once at size × oversample
// and samples the atlas with nearest filtering, so a glyph is only crisp where oversample equals
// the GUI scale; Typeset picks the id whose suffix is the window's scale.
const FONT_DIR = resolve(HERE, '../mod/src/main/resources/assets/fullmoon/font');
const FILES = {
  sans: { 400: 'sans-regular.ttf', 600: 'sans-semibold.ttf', 700: 'sans-bold.ttf' },
  serif: { 600: 'serif-semibold.ttf', 700: 'serif-bold.ttf' },
};
const FONT_SCALES = [2, 3, 4];
let providers = 0;
for (const [name, v] of typeRoles) {
  for (const scale of FONT_SCALES) {
    const list = [];
    const primary = FILES[v.family][v.weight];
    if (!primary) throw new Error(`no ${v.family} face at weight ${v.weight} for ${name}`);
    list.push({ type: 'ttf', file: `fullmoon:${primary}`, size: v.px, oversample: scale });
    if (v.family === 'serif') {
      // Hahmlet carries KS X 1001; a syllable outside it falls through to the sans at the same size.
      list.push({ type: 'ttf', file: `fullmoon:${FILES.sans[v.weight]}`, size: v.px, oversample: scale });
    }
    list.push({ type: 'reference', id: 'minecraft:include/space' });
    const id = v.font.replace(/^fullmoon:/, '');
    writeFileSync(resolve(FONT_DIR, `${id}_x${scale}.json`), JSON.stringify({ providers: list }, null, 4) + '\n');
    providers++;
  }
}
console.log(`wrote ${providers} font providers under assets/fullmoon/font`);
