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

const hex = (entry) => {
  const [r, g, b] = oklchToRgb(...entry.oklch);
  return '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('');
};

const colors = Object.entries(tokens.color).filter(([k]) => !k.startsWith('$'));
const resolved = colors.map(([name, entry]) => ({ name, entry, hex: hex(entry) }));

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
j('    /** Packed 0xAARRGGBB, opaque. Only a scrim reopens the alpha, via Rgb#alpha. */');
j('    public static final class Color {');
for (const { name, entry, hex: h } of resolved) {
  j(`        /** ${entry.use} · oklch(${entry.oklch.join(' ')}) */`);
  j(`        public static final int ${CONST(name)} = 0xFF${h.slice(1).toUpperCase()};`);
}
j('');
j('        private Color() {}');
j('    }');
j('');
j('    public static final class Space {');
for (const [k, v] of Object.entries(tokens.space).filter(([k]) => !k.startsWith('$')))
  j(`        public static final int ${CONST(k)} = ${v};`);
j('');
j('        private Space() {}');
j('    }');
j('');
j('    public static final class Radius {');
for (const [k, v] of Object.entries(tokens.radius).filter(([k]) => !k.startsWith('$')))
  j(`        public static final int ${CONST(k)} = ${v};`);
j('');
j('        private Radius() {}');
j('    }');
j('');
j('    public static final class Stroke {');
for (const [k, v] of Object.entries(tokens.stroke)) j(`        public static final int ${CONST(k)} = ${v};`);
j('');
j('        private Stroke() {}');
j('    }');
j('');
j('    public static final class Duration {');
for (const [k, v] of Object.entries(tokens.motion.duration))
  j(`        public static final int ${CONST(k)} = ${v};`);
j('');
j('        private Duration() {}');
j('    }');
j('');
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
j('    public static final class Layer {');
for (const [k, v] of Object.entries(tokens.layer).filter(([k]) => !k.startsWith('$')))
  j(`        public static final int ${CONST(k)} = ${v};`);
j('');
j('        private Layer() {}');
j('    }');
j('');
j('    /**');
j('     * One baked ttf provider per role. The game rasterises per provider, so a role is');
j('     * a font id and not a scale factor — asking for title at 1.4x would resample the');
j('     * body atlas and blur it.');
j('     */');
j('    public static final class Type {');
j('        /** {@code font} is the provider id under assets/fullmoon/font; px and leading are GUI px. */');
j('        public record Role(String font, int px, int leading) {}');
j('');
const typeRoles = Object.entries(tokens.type).filter(([k]) => !k.startsWith('$'));
for (const [k, v] of typeRoles) {
  j(`        /** ${v.face} ${v.px}/${v.leading} */`);
  j(`        public static final Role ${CONST(k)} = new Role("${v.font}", ${v.px}, ${v.leading});`);
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
resolved.forEach(({ name }, i) => {
  const tail = i === resolved.length - 1 ? '' : ',';
  j(`            java.util.Map.entry("${name}", Color.${CONST(name)})${tail}`);
});
j('        );');
j('}');

const javaOut = resolve(HERE, '../mod/src/main/java/dev/fullmoon/client/design/Tokens.java');
mkdirSync(dirname(javaOut), { recursive: true });
writeFileSync(javaOut, javaLines.join('\n') + '\n');

/* ---------- tokens.css ---------- */
// The launcher reads the same colours by the same names. It also carries a daylight palace
// (colorDay) and the accent metals, which the game has no use for and so never sees.
const density = tokens.cssDensity;
const px = (gui, factor) => `${Math.round(gui * factor)}px`;
const oklchCss = (e) => `oklch(${e.oklch[0]} ${e.oklch[1]} ${e.oklch[2]})`;
const dayColors = Object.entries(tokens.colorDay).filter(([k]) => !k.startsWith('$'));
const missingDay = colors.map(([k]) => k).filter((k) => !tokens.colorDay[k]);
if (missingDay.length) throw new Error(`colorDay lacks ${missingDay.join(', ')}`);
const metals = Object.entries(tokens.accentMetal).filter(([k]) => !k.startsWith('$'));

const css = [];
const c = (s = '') => css.push(s);
// Each palace also names every metal's accent, so a picker can show all three at once
// without scoping itself into a theme it is not in.
const colorBlock = (selector, scheme, entries, hour) => {
  c(`${selector} {`);
  c(`  color-scheme: ${scheme};`);
  for (const [name, entry] of entries) {
    c(`  --color-${CSSVAR(name)}: ${oklchCss(entry)}; /* ${hex(entry)} · ${entry.use} */`);
  }
  const gilt = entries.find(([name]) => name === 'accent')[1];
  c(`  --metal-gilt: ${oklchCss(gilt)};`);
  for (const [metal, set] of metals) c(`  --metal-${metal}: ${oklchCss(set[hour].accent)};`);
  c('}');
  c('');
};
c('/* Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand. */');
c(':root {');
for (const [k, v] of Object.entries(tokens.space).filter(([k]) => !k.startsWith('$')))
  c(`  --space-${CSSVAR(k)}: ${px(v, density.space)};`);
c('');
for (const [k, v] of Object.entries(tokens.radius).filter(([k]) => !k.startsWith('$')))
  c(`  --radius-${CSSVAR(k)}: ${k === 'round' ? '999px' : px(v, density.space)};`);
c('');
for (const [k, v] of Object.entries(tokens.stroke)) c(`  --stroke-${CSSVAR(k)}: ${v}px;`);
c('');
for (const [k, v] of Object.entries(tokens.motion.duration)) c(`  --dur-${CSSVAR(k)}: ${v}ms;`);
for (const [k, v] of Object.entries(tokens.motion.easing))
  c(`  --ease-${CSSVAR(k)}: cubic-bezier(${v.join(', ')});`);
c('');
for (const [k, v] of Object.entries(tokens.layer).filter(([k]) => !k.startsWith('$')))
  c(`  --layer-${CSSVAR(k)}: ${v};`);
c('');
c("  --font-display: 'Fullmoon Serif', 'Noto Serif KR', serif;");
c("  --font-body: 'Pretendard', system-ui, sans-serif;");
for (const [k, v] of Object.entries(tokens.type).filter(([k]) => !k.startsWith('$'))) {
  const serif = v.face.startsWith('Fullmoon Serif');
  const weight = serif || v.face.endsWith('SemiBold') ? 600 : 400;
  const size = px(v.px, density.type);
  const leading = px(v.leading, density.type);
  c(`  --type-${CSSVAR(k)}-size: ${size};`);
  c(`  --type-${CSSVAR(k)}-leading: ${leading};`);
  c(`  --type-${CSSVAR(k)}: ${weight} ${size}/${leading} var(${serif ? '--font-display' : '--font-body'});`);
}
c('}');
c('');
colorBlock(':root,\n[data-theme="dark"]', 'dark', resolved.map(({ name, entry }) => [name, entry]), 'night');
colorBlock('[data-theme="light"]', 'light', dayColors, 'day');
for (const [metal, { night, day }] of metals) {
  const withUse = (set) => Object.entries(set).map(([k, e]) => [k, { ...e, use: `${metal} ${k}` }]);
  c(`[data-accent="${metal}"],`);
  c(`[data-accent="${metal}"] [data-theme="dark"] {`);
  for (const [k, e] of withUse(night)) c(`  --color-${CSSVAR(k)}: ${oklchCss(e)}; /* ${hex(e)} */`);
  c('}');
  c('');
  c(`[data-theme="light"][data-accent="${metal}"],`);
  c(`[data-accent="${metal}"] [data-theme="light"] {`);
  for (const [k, e] of withUse(day)) c(`  --color-${CSSVAR(k)}: ${oklchCss(e)}; /* ${hex(e)} */`);
  c('}');
  c('');
}
c('@media (prefers-reduced-motion: reduce) {');
c('  :root {');
for (const k of Object.keys(tokens.motion.duration))
  c(`    --dur-${CSSVAR(k)}: ${k === 'instant' ? 0 : tokens.motion.duration.reduced}ms;`);
c('  }');
c('}');

const cssOut = resolve(HERE, '../../launcher/src/design/tokens.css');
mkdirSync(dirname(cssOut), { recursive: true });
writeFileSync(cssOut, css.join('\n') + '\n');

/* ---------- contrast evidence ---------- */
const pick = (n) => resolved.find((r) => r.name === n).hex;
const pickDay = (n) => hex(tokens.colorDay[n]);
const checks = [
  ['ink.primary on surface.base', 'ink.primary', 'surface.base', 4.5],
  ['ink.secondary on surface.base', 'ink.secondary', 'surface.base', 4.5],
  ['ink.tertiary on surface.base', 'ink.tertiary', 'surface.base', 3.0],
  ['ink.onAccent on accent', 'ink.onAccent', 'accent', 4.5],
  ['accent (focus ring) on surface.base', 'accent', 'surface.base', 3.0],
  ['accent (focus ring) on surface.raised', 'accent', 'surface.raised', 3.0],
  ['status.live on surface.base', 'status.live', 'surface.base', 3.0],
  ['status.warn on surface.base', 'status.warn', 'surface.base', 3.0],
  ['status.danger on surface.base', 'status.danger', 'surface.base', 3.0],
  ['line.hairline on surface.base', 'line.hairline', 'surface.base', 1.15],
  ['ink.primary on surface.raised', 'ink.primary', 'surface.raised', 4.5],
  ['ink.primary on accent.wash', 'ink.primary', 'accent.wash', 4.5],
  ['line.gilt on surface.base', 'line.gilt', 'surface.base', 1.8],
  ['line.lattice on surface.base', 'line.lattice', 'surface.base', 1.1],
  ['accent (corner bracket) on surface.void', 'accent', 'surface.void', 3.0],
  ['ink.primary on ornament.cinnabar (seal)', 'ink.primary', 'ornament.cinnabar', 3.0],
  ['moon.lit on moon.shadow', 'moon.lit', 'moon.shadow', 7.0],
].map(([label, a, b, floor]) => [label, pick(a), pick(b), floor]);

// The launcher sets meta text small and puts the accent in small labels, so its floors are
// the small-text ones, in both palaces and under every metal.
const launcherPairs = [
  ['ink.tertiary', 'surface.base', 4.5],
  ['ink.tertiary', 'surface.sunken', 4.5],
  ['ink.tertiary', 'surface.void', 4.5],
  ['ink.secondary', 'surface.raised', 4.5],
  ['ink.primary', 'surface.overlay', 4.5],
  ['status.live', 'surface.base', 3.0],
  ['status.danger', 'surface.base', 3.0],
  ['line.gilt', 'surface.base', 1.8],
];
for (const [a, b, floor] of launcherPairs) {
  checks.push([`launcher night · ${a} on ${b}`, pick(a), pick(b), floor]);
  checks.push([`launcher day · ${a} on ${b}`, pickDay(a), pickDay(b), floor]);
}
const accentSets = [
  ['gilt', 'night', (n) => pick(n), (n) => pick(n)],
  ['gilt', 'day', (n) => pickDay(n), (n) => pickDay(n)],
  ...metals.flatMap(([metal, set]) => [
    [metal, 'night', (n) => hex(set.night[n]), (n) => pick(n)],
    [metal, 'day', (n) => hex(set.day[n]), (n) => pickDay(n)],
  ]),
];
for (const [metal, hour, own, base] of accentSets) {
  checks.push([`${metal} ${hour} · ink.onAccent on accent`, base('ink.onAccent'), own('accent'), 4.5]);
  checks.push([`${metal} ${hour} · ink.onAccent on accent.pressed`, base('ink.onAccent'), own('accent.pressed'), 4.5]);
  checks.push([`${metal} ${hour} · accent on surface.base`, own('accent'), base('surface.base'), 4.5]);
  checks.push([`${metal} ${hour} · accent on surface.void`, own('accent'), base('surface.void'), 3.0]);
  checks.push([`${metal} ${hour} · ink.primary on accent.wash`, base('ink.primary'), own('accent.wash'), 4.5]);
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
