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

SHOTS = ['title-sidebar', 'specimen', 'kit', 'list', 'hudeditor-title', 'welcome', 'hud-a', 'hudeditor', 'map', 'menu']


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


def compare(candidate: Path, base: Path, noise: np.ndarray | None) -> tuple[str, int, list]:
    a, b = load(candidate), load(base)
    if a.shape != b.shape:
        return 'DIFFERS (size)', -1, []
    diff = (np.abs(a - b).sum(axis=2) > 0)
    total = int(diff.sum())
    if total == 0:
        return 'IDENTICAL', 0, []
    beyond = diff & ~noise if noise is not None else diff
    if not beyond.any():
        return 'NOISE-ONLY', total, []
    return 'DIFFERS', int(beyond.sum()), clusters(beyond)[:4]


def noise_mask(run_dir: Path, name: str, other_runs: list[Path]) -> np.ndarray | None:
    """Pixels where the baseline disagrees with itself: hud-a vs hud-b, and the other baseline runs."""
    mask = None
    pairs = []
    if name == 'hud-a' and (run_dir / 'hud-b.png').exists():
        pairs.append((run_dir / 'hud-a.png', run_dir / 'hud-b.png'))
    pairs += [(run_dir / f'{name}.png', o / f'{name}.png') for o in other_runs if (o / f'{name}.png').exists()]
    for x, y in pairs:
        if x.exists() and y.exists():
            a, b = load(x), load(y)
            if a.shape == b.shape:
                d = (np.abs(a - b).sum(axis=2) > 0)
                mask = d if mask is None else (mask | d)
    return mask


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('runs', type=Path)
    ap.add_argument('--base', default='base')
    ap.add_argument('configs', nargs='+')
    ap.add_argument('--out', type=Path)
    a = ap.parse_args()
    dirs = sorted(p for p in a.runs.iterdir() if (p / 'run.json').exists())

    def find(cfg: str, scen: str) -> list[Path]:
        return [d for d in dirs if d.name.split('-')[1] == cfg and d.name.split('-')[2] == scen]

    lines = ['| config | screen | verdict | pixels beyond noise | clusters (px, x0,y0,x1,y1) |', '|---|---|---|---|---|']
    for cfg in a.configs:
        for scen in ('parity-title', 'idle'):
            for cand in find(cfg, scen):
                runner = cand.name.split('-')[0]
                bases = find(a.base, scen)
                same = [d for d in bases if d.name.split('-')[0] == runner]
                if not same:
                    continue
                others = [d for d in bases if d is not same[0]]
                for name in SHOTS:
                    if not (cand / f'{name}.png').exists() or not (same[0] / f'{name}.png').exists():
                        continue
                    verdict, n, boxes = compare(cand / f'{name}.png', same[0] / f'{name}.png', noise_mask(same[0], name, others))
                    lines.append(f'| {cfg} ({runner}) | {name} | {verdict} | {n} | {boxes if boxes else ""} |')
    text = '\n'.join(lines)
    print(text)
    if a.out:
        a.out.write_text(text + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
