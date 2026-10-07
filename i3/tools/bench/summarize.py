#!/usr/bin/env python3
"""One comparable table across configs from the run directories bench.py wrote.

    summarize.py RUNS [RUNS...] [--base base] [--window fly-survival] [--markdown]
    summarize.py RUNS --startup        # time to title / world, from the title/idle/fly/startup runs
    summarize.py RUNS --memory         # RSS, live heap, GC, from every run's end-of-run reading

A cell is the mean over repeats; `±` is the spread of the repeats as half the range over the mean, so
a delta smaller than the spread of the baseline is noise. `Δ` is the config's mean against the base
config's mean for the same window.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bench import frame_stats  # noqa: E402

MIB = 1024 * 1024


def spread_pct(values: list[float]) -> float:
    """Half the range over the mean, in percent; 0 for a single value."""
    if len(values) < 2:
        return 0.0
    mean = sum(values) / len(values)
    return 100.0 * (max(values) - min(values)) / 2 / mean if mean else 0.0


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else float('nan')


def load_runs(dirs: list[Path]) -> list[dict]:
    runs = []
    for base in dirs:
        for rj in sorted(base.glob('*/run.json')):
            run = json.loads(rj.read_text())
            run['dir'] = rj.parent
            runs.append(run)
    return runs


def window_metrics(run: dict, label: str) -> dict | None:
    parse = run['dir'] / f'{label}.parse.json'
    if not parse.exists():
        return None
    try:
        p = json.loads(parse.read_text())
    except json.JSONDecodeError:
        return None
    w = run['windows'][label]
    m = frame_stats(p['tick_start_ns'], p['tick_dur_ns'], p['render_dur_ns'], w['seconds'])
    rt = p['alloc'].get('Render thread')
    if rt and rt['ms'] > 0:
        m['alloc_mib_s'] = rt['bytes'] / MIB / (rt['ms'] / 1000)
    gc = p['gc']
    m.update(gc_n=gc['count'], gc_sum_ms=gc['sum_pause_ms'], gc_max_ms=gc['max_pause_ms'])
    m['cpu_jvm_pct'] = 100 * (p['cpu']['jvm_user'] + p['cpu']['jvm_system']) if p['cpu']['jvm_user'] >= 0 else float('nan')
    m['steal_pct'] = w.get('steal_pct', float('nan'))
    cpu = p['cpu']
    if cpu['render_user'] >= 0 and 'avg_ms' in m:  # Java-side cost of a frame: what a fast GPU would leave as the limit
        m['render_cpu_ms'] = m['avg_ms'] * (cpu['render_user'] + cpu['render_system'])
    m['rss_mib'] = (w.get('rss_kib_end') or 0) / 1024
    for name, v in p['timing'].items():
        m['t_' + name.split('.')[-1].replace('::', '.')] = v['avg_ns'] / 1e6
    return m


FRAME_COLS = [('fps', 'fps', '{:.2f}'), ('avg_ms', 'avg ms', '{:.1f}'), ('p50_ms', 'p50', '{:.1f}'), ('p95_ms', 'p95', '{:.1f}'),
              ('p99_ms', 'p99', '{:.1f}'), ('p999_ms', 'p99.9', '{:.1f}'), ('low1_fps', '1% low fps', '{:.2f}'),
              ('render_avg_ms', 'render ms', '{:.1f}'), ('render_cpu_ms', 'render-thread CPU ms/frame', '{:.1f}'), ('alloc_mib_s', 'alloc MiB/s', '{:.2f}'),
              ('gc_n', 'GCs', '{:.1f}'), ('gc_sum_ms', 'GC ms', '{:.0f}'), ('gc_max_ms', 'GC max', '{:.0f}'),
              ('rss_mib', 'RSS MiB', '{:.0f}'), ('steal_pct', 'steal %', '{:.2f}')]


def table(rows: list[list[str]], header: list[str], markdown: bool) -> str:
    widths = [max(len(str(r[i])) for r in [header] + rows) for i in range(len(header))]
    lines = []
    if markdown:
        lines.append('| ' + ' | '.join(header) + ' |')
        lines.append('|' + '|'.join('---' for _ in header) + '|')
        lines += ['| ' + ' | '.join(map(str, r)) + ' |' for r in rows]
    else:
        lines.append('  '.join(h.ljust(w) for h, w in zip(header, widths)))
        lines += ['  '.join(str(c).ljust(w) for c, w in zip(r, widths)) for r in rows]
    return '\n'.join(lines)


def perf_table(runs: list[dict], window: str, base: str, markdown: bool, cols=FRAME_COLS) -> str:
    by_cfg: dict[str, list[dict]] = defaultdict(list)
    for run in runs:
        if window in run.get('windows', {}):
            m = window_metrics(run, window)
            if m:
                m['_runner'] = run['runner']
                by_cfg[run['config']].append(m)
    if not by_cfg:
        return f'(no runs with window {window})'
    order = [base] + sorted(c for c in by_cfg if c != base)
    header = ['config', 'n'] + [c[1] for c in cols]
    rows = []
    for cfg in order:
        if cfg not in by_cfg:
            continue
        ms = by_cfg[cfg]
        row = [cfg, str(len(ms))]
        for key, _, fmt in cols:
            vals = [m[key] for m in ms if key in m and m[key] == m[key]]
            if not vals:
                row.append('-')
                continue
            cell = fmt.format(mean(vals))
            if len(vals) > 1:
                cell += f' ±{spread_pct(vals):.1f}%'
            if cfg != base and base in by_cfg:
                bv = [m[key] for m in by_cfg[base] if key in m and m[key] == m[key]]
                if bv and mean(bv):
                    cell += f' ({100 * (mean(vals) / mean(bv) - 1):+.1f}%)'
            row.append(cell)
        rows.append(row)
    return table(rows, header, markdown)


def startup_table(runs: list[dict], markdown: bool) -> str:
    keys = [('title_s', 'to title s'), ('mcp_up_s', 'MCP up s'), ('in_game_s', 'logged in s'), ('world_visible_s', 'world visible s')]
    by: dict[tuple, list[dict]] = defaultdict(list)
    for run in runs:
        if run.get('status') == 'ok' and run['scenario'] in ('title', 'idle', 'fly', 'startup'):
            by[(run['config'], run['scenario'])].append(run['events'])
    rows = []
    for (cfg, scen), evs in sorted(by.items()):
        row = [cfg, scen, str(len(evs))]
        for key, _ in keys:
            vals = [e[key] for e in evs if key in e]
            row.append(f'{mean(vals):.1f} ±{spread_pct(vals):.1f}%' if vals else '-')
        rss = [e['rss_kib_at_title_plus10s'] / 1024 for e in evs if 'rss_kib_at_title_plus10s' in e]
        anon = [e['smaps_rollup_kib']['Anonymous'] / 1024 for e in evs if 'smaps_rollup_kib' in e and 'Anonymous' in e['smaps_rollup_kib']]
        row += [f'{mean(rss):.0f} ±{spread_pct(rss):.1f}%' if rss else '-', f'{mean(anon):.0f} ±{spread_pct(anon):.1f}%' if anon else '-']
        rows.append(row)
    return table(rows, ['config', 'scenario', 'n'] + [k[1] for k in keys] + ['RSS MiB @title+10s', 'anon MiB'], markdown)


def traces_table(runs: list[dict], markdown: bool) -> str:
    """The `startup` runs: how often and for how long each traced method ran, and where the samples fell."""
    rows = []
    for run in sorted(runs, key=lambda r: r['run']):
        parse = run['dir'] / 'startup.parse.json'
        if run['scenario'] != 'startup' or not parse.exists():
            continue
        p = json.loads(parse.read_text())
        samples = p['all_samples']
        total = samples.get('total', 0) or 1
        for name, v in sorted(p['traces'].items()):
            short = name.split('.')[-1]
            span = (v['last_end_ms'] - v['first_ms']) if v['n'] else 0
            rows.append([run['run'], short, str(v['n']), f"{v['total_ns'] / 1e6 / max(v['n'], 1):.1f}", f"{v['total_ns'] / 1e6:.0f}", f'{span}',
                         ''])
        rows.append([run['run'], 'samples: fullmoon / truetype / total', '', '', '', '',
                     f"{samples.get('fullmoon', 0)} / {samples.get('truetype', 0)} / {samples.get('total', 0)} ({100 * (samples.get('fullmoon', 0) + samples.get('truetype', 0)) / total:.1f}%)"])
    return table(rows, ['run', 'traced method', 'calls', 'mean ms', 'sum ms (all threads)', 'wall span ms', 'note'], markdown)


def memory_table(runs: list[dict], markdown: bool) -> str:
    by: dict[tuple, list[dict]] = defaultdict(list)
    for run in runs:
        if run.get('status') == 'ok' and 'end' in run:
            by[(run['config'], run['scenario'])].append(run['end'])
    rows = []
    for (cfg, scen), ends in sorted(by.items()):
        def col(f, scale=1.0):
            vals = [f(e) / scale for e in ends if f(e) is not None]
            return f'{mean(vals):.0f} ±{spread_pct(vals):.1f}%' if vals else '-'
        rows.append([cfg, scen, str(len(ends)), col(lambda e: e.get('rss_kib'), 1024), col(lambda e: e.get('rss_after_gc_kib'), 1024),
                     col(lambda e: e.get('smaps_rollup_kib', {}).get('Pss'), 1024),
                     col(lambda e: e.get('smaps_rollup_kib', {}).get('Anonymous'), 1024), col(lambda e: e.get('live_heap_mib'))])
    return table(rows, ['config', 'scenario', 'n', 'RSS MiB', 'RSS after GC', 'PSS MiB', 'anon MiB', 'live heap MiB'], markdown)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('runs', nargs='+', type=Path)
    ap.add_argument('--base', default='base')
    ap.add_argument('--window', action='append')
    ap.add_argument('--startup', action='store_true')
    ap.add_argument('--memory', action='store_true')
    ap.add_argument('--traces', action='store_true')
    ap.add_argument('--markdown', action='store_true')
    a = ap.parse_args()
    runs = load_runs(a.runs)
    if a.startup:
        print(startup_table(runs, a.markdown))
        return 0
    if a.memory:
        print(memory_table(runs, a.markdown))
        return 0
    if a.traces:
        print(traces_table(runs, a.markdown))
        return 0
    windows = a.window or sorted({w for r in runs for w in r.get('windows', {})})
    for w in windows:
        print(f'\n## {w}\n')
        print(perf_table(runs, w, a.base, a.markdown))
    return 0


if __name__ == '__main__':
    sys.exit(main())
