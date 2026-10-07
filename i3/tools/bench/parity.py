#!/usr/bin/env python3
"""Pixel parity of Fullmoon's own screens between a candidate config and the baseline.

    parity.py RUNS --base base CONFIG [CONFIG...] [--out parity.md]

Compares the screenshots bench.py leaves in each run directory (title with the sidebar fixture, the
dev pages, the first-join welcome menu, the in-world HUD with sidebar, the HUD editor, the map, the
native /warp menu), candidate against baseline from the same runner. Two baselines that differ by
themselves (animated water, clouds, moon-phase fades) are the noise floor: a pixel counts against a
candidate only when it differs from the baseline AND the baseline's own two captures agree there
(hud-a/hud-b four seconds apart in the same run, or the same screen from the other runner's baseline).

Verdict per screen: IDENTICAL (zero pixels beyond the noise floor), NOISE-ONLY (differs, but only where
the baseline differs from itself), or DIFFERS with the bounding boxes of the clusters.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SHOTS = {'parity-title': ['title-sidebar', 'specimen', 'kit', 'list', 'hudeditor-title'],
         'parity-hud': ['welcome', 'hud-a', 'hudeditor', 'map', 'menu']}


# Live values on the HUD (x0, y0, x1, y1): fps, ping, wall clock, play time. They differ between any two runs.
DYNAMIC = {'hud-a': [(24, 84, 141, 131), (1072, 84, 1257, 131), (1090, 24, 1257, 72), (880, 375, 1245, 415)],
           'hudeditor': [(24, 84, 141, 131), (1072, 84, 1257, 131), (1090, 24, 1257, 72), (880, 375, 1245, 415)],
           'hudeditor-title': [(24, 84, 141, 131), (1072, 84, 1257, 131), (1090, 24, 1257, 72)],
           'title-sidebar': [(500, 240, 840, 278), (880, 480, 1245, 515)]}  # ping, play time
# The title's background is a panorama that moves between any two captures, under translucent panels, so
# whole-frame pixels cannot match. For these shots only the bright pixels (the text and glyph strokes) are compared.
BRIGHT = {'title-sidebar'}
BRIGHT_LUMA = 170


def load(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert('RGB')).astype(int)


def clusters(mask: np.ndarray, cell: int = 24) -> list[tuple[int, int, int, int, int]]:
    """Flood-fills 24 px cells that contain a differing pixel; returns (pixels, x0, y0, x1, y1), biggest first."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    if not len(ys):
        return []
    grid = np.zeros((h // cell + 1, w // cell + 1), bool)
    grid[ys // cell, xs // cell] = True
    seen = np.zeros_like(grid)
    out = []
    for cy, cx in zip(*np.nonzero(grid)):
        if seen[cy, cx]:
            continue
        stack, comp = [(cy, cx)], []
        seen[cy, cx] = True
        while stack:
            y, x = stack.pop()
            comp.append((y, x))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < grid.shape[0] and 0 <= nx < grid.shape[1] and grid[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
        x0, x1 = min(c[1] for c in comp) * cell, (max(c[1] for c in comp) + 1) * cell
        y0, y1 = min(c[0] for c in comp) * cell, (max(c[0] for c in comp) + 1) * cell
        sub = mask[y0:y1, x0:x1]
        yy, xx = np.nonzero(sub)
        out.append((int(sub.sum()), int(x0 + xx.min()), int(y0 + yy.min()), int(x0 + xx.max()), int(y0 + yy.max())))
    return sorted(out, reverse=True)


def diff_mask(a: Path, b: Path, bright: bool = False) -> np.ndarray | None:
    x, y = load(a), load(b)
    if x.shape != y.shape:
        return None
    if bright:
        lum = lambda im: im @ np.array([0.299, 0.587, 0.114])  # noqa: E731
        return (lum(x) > BRIGHT_LUMA) ^ (lum(y) > BRIGHT_LUMA)
    return np.abs(x - y).sum(axis=2) > 0


def compare(candidate: Path, base: Path, noise_masks: list[np.ndarray], dynamic=(), bright: bool = False) -> tuple[str, int, int, list]:
    """Verdict of candidate vs base given masks of how the base differs from itself (other captures of it).

    n_cand: pixels that differ (outside the HUD's live-value boxes); n_noise: the most the base differs from
    itself under the same rule. IDENTICAL: n_cand == 0. WITHIN NOISE: every differing pixel is one the base
    also moves, or n_cand does not exceed the base's own movement. Otherwise DIFFERS, with the clusters of
    pixels the base never moved."""
    diff = diff_mask(candidate, base, bright)
    if diff is None:
        return 'DIFFERS (size)', -1, 0, []

    def blank(mask):
        mask = mask.copy()
        for x0, y0, x1, y1 in dynamic:
            mask[y0:y1 + 1, x0:x1 + 1] = False
        return mask
    diff = blank(diff)
    n_cand = int(diff.sum())
    if n_cand == 0:
        return 'IDENTICAL', 0, 0, []
    noise = np.zeros_like(diff)
    n_noise = 0
    for m in noise_masks:
        m = blank(m)
        noise |= m
        n_noise = max(n_noise, int(m.sum()))
    beyond = diff & ~noise
    if not beyond.any() or n_cand <= n_noise:
        return 'WITHIN NOISE', n_cand, n_noise, []
    return 'DIFFERS', n_cand, n_noise, clusters(beyond)[:4]


def noise_masks(run_dir: Path, name: str, others: list[Path], bright: bool = False) -> list[np.ndarray]:
    """How the baseline differs from itself: hud-a vs hud-b in the same run, and its other captures."""
    pairs = []
    if name == 'hud-a' and (run_dir / 'hud-b.png').exists():
        pairs.append((run_dir / 'hud-a.png', run_dir / 'hud-b.png'))
    pairs += [(run_dir / f'{name}.png', o / f'{name}.png') for o in others if (o / f'{name}.png').exists()]
    return [m for x, y in pairs if x.exists() and y.exists() and (m := diff_mask(x, y, bright)) is not None]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('runs', type=Path)
    ap.add_argument('--base', default='base')
    ap.add_argument('configs', nargs='+')
    ap.add_argument('--out', type=Path)
    a = ap.parse_args()
    import json
    meta = {p: json.loads((p / 'run.json').read_text()) for p in sorted(a.runs.iterdir()) if (p / 'run.json').exists()}
    meta = {p: m for p, m in meta.items() if m.get('status') == 'ok'}

    def find(cfg: str, scen: str) -> list[Path]:
        return [d for d, m in meta.items() if m['config'] == cfg and m['scenario'] == scen]

    lines = ['| config | screen | verdict | differing px | base self-diff px | clusters the base never moves (px, x0,y0,x1,y1) |', '|---|---|---|---|---|---|']
    for cfg in a.configs:
        for scen in ('parity-title', 'parity-hud'):
            for cand in find(cfg, scen):
                runner = meta[cand]['runner']
                bases = find(a.base, scen)
                same = [d for d in bases if meta[d]['runner'] == runner]
                if not same:
                    continue
                others = [d for d in bases if d is not same[0]]
                for name in SHOTS[scen]:
                    if not (cand / f'{name}.png').exists() or not (same[0] / f'{name}.png').exists():
                        continue
                    verdict, n, nn, boxes = compare(cand / f'{name}.png', same[0] / f'{name}.png', noise_masks(same[0], name, others, name in BRIGHT), DYNAMIC.get(name, ()), name in BRIGHT)
                    lines.append(f'| {cfg} ({runner}) | {name} | {verdict} | {n} | {nn} | {boxes if boxes else ""} |')
    text = '\n'.join(lines)
    print(text)
    if a.out:
        a.out.write_text(text + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
