#!/usr/bin/env python3
"""Puts the same fixed, silent herd along the 야생 flight path on the replica (idempotent).

The replica's saved world has few animals along Taecho's streets, and the entity-culling candidates
cannot be judged against a handful. This summons `per_waypoint` NoAI, invulnerable, silent animals on
the surface around each waypoint of flights.json["survival"], tagged `fmherd` (and `fmh<waypoint index>`), using forceload so no
player has to stand there. Same positions every time (a fixed sequence, no randomness), replica only.

    BENCH_HOME=~/bench RCON_PASSWORD=... prep-world.py [--check]
"""
import json
import math
import os
import sys
import time
from pathlib import Path

from rcon import Rcon

HERE = Path(__file__).resolve().parent


def main() -> int:
    spec = json.loads((HERE / 'flights.json').read_text())['survival']
    herd = spec['herd']
    rc = Rcon('127.0.0.1', 25576, os.environ['RCON_PASSWORD'])
    seen = set()
    for index, (x, _, z) in enumerate(spec['waypoints']):
        if (x, z) in seen:
            continue
        seen.add((x, z))
        r = herd['radius']
        rc.command(f'forceload add {x - r - 16} {z - r - 16} {x + r + 16} {z + r + 16}')
        for _ in range(60):
            if 'is loaded' in rc.command(f'execute if loaded {x} 64 {z}') or 'passed' in rc.command(f'execute if loaded {x} 64 {z}'):
                break
            time.sleep(1)
        rc.command('kill @e[tag=fmbench]')  # the first draft of this script tagged its herd fmbench
        have = rc.command(f'execute if entity @e[tag=fmh{index}]')
        if 'passed' in have:
            print(f'waypoint {index} ({x},{z}): herd present ({have})')
        else:
            for k in range(herd['per_waypoint']):
                kind = herd['types'][k % len(herd['types'])]
                angle = k * 2.399963  # golden angle: a fixed, even scatter
                dx, dz = int(r * math.sqrt((k + 0.5) / herd['per_waypoint']) * math.cos(angle)), int(r * math.sqrt((k + 0.5) / herd['per_waypoint']) * math.sin(angle))
                nbt = '{NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:["fmherd","fmh%d"]}' % index
                rc.command(f'execute positioned {x + dx} 0 {z + dz} positioned over world_surface run summon minecraft:{kind} ~ ~ ~ {nbt}')
            print(f'waypoint {index} ({x},{z}): summoned, now {rc.command(f"execute if entity @e[tag=fmh{index}]")}')
        time.sleep(1)
    rc.command('save-all flush')
    for x, _, z in [tuple(p) for p in spec['waypoints']]:
        r = herd['radius']
        rc.command(f'forceload remove {x - r - 16} {z - r - 16} {x + r + 16} {z + r + 16}')
    print('(entities only count while their chunks are loaded; the herd is saved with the world)')
    rc.close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
