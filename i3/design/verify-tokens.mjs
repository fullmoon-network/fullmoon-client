#!/usr/bin/env node
// Fails if a colour, duration, or easing literal appears outside the generated token
// files. Mid-render token improvisation is the tell this guards against: the theme is
// picked once, then a hover state quietly acquires its own hex and cohesion erodes.
// Both halves are held to it — the mod under i3/ and the launcher's front end.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve, relative } from 'node:path';

const REPO = resolve(import.meta.dirname, '../..');
const ROOTS = ['i3', 'launcher/src'];
const FILES = ['launcher/index.html'];
const EXEMPT = [
  'i3/mod/src/main/java/dev/fullmoon/client/design/Tokens.java',
  'launcher/src/design/tokens.css',
  'i3/design/tokens.json',
  'i3/design/generate.mjs',
  'i3/design/_oklch.mjs',
  'i3/design/verify-tokens.mjs',
];
const SKIP_DIR = new Set(['build', 'target', 'node_modules', '.gradle', 'run', 'dist', 'gradle']);
const EXT = /\.(java|kt|ts|tsx|css|glsl|vsh|fsh)$/;

const RULES = [
  { name: 'hex colour literal', re: /0x[0-9A-Fa-f]{8}\b|#[0-9A-Fa-f]{6}\b/g },
  { name: 'inline oklch()/rgb()/hsl()', re: /\b(oklch|rgba?|hsla?)\s*\(/g },
  { name: 'raw cubic-bezier', re: /cubic-bezier\s*\(/g },
  { name: 'new java.awt.Color', re: /new\s+Color\s*\(/g },
];

const files = [];
function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) {
      if (!SKIP_DIR.has(name) && !name.startsWith('.')) walk(p);
    } else if (EXT.test(name)) files.push(p);
  }
}
for (const root of ROOTS) walk(join(REPO, root));
for (const file of FILES) if (existsSync(join(REPO, file))) files.push(join(REPO, file));

let findings = 0;
for (const file of files) {
  const rel = relative(REPO, file);
  if (EXEMPT.includes(rel)) continue;
  const lines = readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, i) => {
    if (/\bTOKENS-OK\b/.test(line)) return;
    for (const { name, re } of RULES) {
      re.lastIndex = 0;
      const hit = re.exec(line);
      if (hit) {
        console.log(`  ${rel}:${i + 1}  ${name} — ${hit[0]}`);
        findings++;
      }
    }
  });
}

console.log(`scanned ${files.length} file(s) under ${[...ROOTS, ...FILES].join(', ')}`);
if (findings) {
  console.error(`\n${findings} literal(s) outside the token block. Lift them into tokens.json.`);
  process.exit(1);
}
console.log('no colour or motion literals outside the token block');
