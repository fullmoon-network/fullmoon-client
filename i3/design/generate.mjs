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
const css = [];
const c = (s = '') => css.push(s);
c('/* Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand. */');
c(':root {');
c('  color-scheme: dark;');
c('');
for (const { name, entry, hex: h } of resolved)
  c(`  --color-${CSSVAR(name)}: oklch(${entry.oklch[0]} ${entry.oklch[1]} ${entry.oklch[2]}); /* ${h} · ${entry.use} */`);
c('');
for (const [k, v] of Object.entries(tokens.space).filter(([k]) => !k.startsWith('$')))
  c(`  --space-${CSSVAR(k)}: ${v * 2}px;`);
c('');
for (const [k, v] of Object.entries(tokens.radius).filter(([k]) => !k.startsWith('$')))
  c(`  --radius-${CSSVAR(k)}: ${k === 'round' ? '999px' : v * 2 + 'px'};`);
c('');
for (const [k, v] of Object.entries(tokens.motion.duration)) c(`  --dur-${CSSVAR(k)}: ${v}ms;`);
for (const [k, v] of Object.entries(tokens.motion.easing))
  c(`  --ease-${CSSVAR(k)}: cubic-bezier(${v.join(', ')});`);
c('');
for (const [k, v] of Object.entries(tokens.layer).filter(([k]) => !k.startsWith('$')))
  c(`  --layer-${CSSVAR(k)}: ${v};`);
c('');
for (const [k, v] of Object.entries(tokens.type).filter(([k]) => !k.startsWith('$'))) {
  c(`  --type-${CSSVAR(k)}-size: ${v.px * 2}px;`);
  c(`  --type-${CSSVAR(k)}-leading: ${v.leading * 2}px;`);
}
c('');
c("  --font-display: 'Fullmoon Serif', 'Noto Serif KR', serif;");
c("  --font-body: 'Pretendard', system-ui, sans-serif;");
c('}');
c('');
c('@media (prefers-reduced-motion: reduce) {');
c('  :root {');
for (const k of Object.keys(tokens.motion.duration))
  c(`    --dur-${CSSVAR(k)}: ${k === 'instant' ? 0 : tokens.motion.duration.reduced}ms;`);
c('  }');
c('}');

const cssOut = resolve(HERE, '../launcher/src/design/tokens.css');
mkdirSync(dirname(cssOut), { recursive: true });
writeFileSync(cssOut, css.join('\n') + '\n');

/* ---------- contrast evidence ---------- */
const pick = (n) => resolved.find((r) => r.name === n).hex;
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
];
let failed = 0;
console.log(`wrote ${javaOut.replace(/.*\/i3\//, 'i3/')}`);
console.log(`wrote ${cssOut.replace(/.*\/i3\//, 'i3/')}`);
console.log('\ncontrast (WCAG 2.x ratio · floor · verdict)');
for (const [label, a, b, floor] of checks) {
  const ratio = contrast(pick(a), pick(b));
  const ok = ratio >= floor;
  if (!ok) failed++;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${ratio.toFixed(2)} : 1  (>= ${floor})  ${label}`);
}
if (failed) {
  console.error(`\n${failed} contrast floor(s) missed — fix tokens.json, not the call site.`);
  process.exit(1);
}
console.log('\nall contrast floors met');
