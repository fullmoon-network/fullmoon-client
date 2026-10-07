#!/usr/bin/env python3
"""Fullmoon client benchmark driver. Runs ON a disposable runner, never on the workstation.

    bench.py run   --config base --scenario fly --rep 1 --runner r1 --out runs
    bench.py queue plan.txt --runner r1 --out runs        one "<config> <scenario> <rep>" per line

Scenarios (see README.md for what each one measures):
    title    process start -> title screen, no world, no JFR
    idle     rd 6: in-world idle 120 s, map open 60 s, native menu open 60 s (+ parity screenshots)
    flys     rd 12: the 야생 leg of fly only (JVM-flag screens)
    fly      rd 12: lobby flythrough 120 s, then /server survival and the 야생 flythrough 120 s
    mem      rd 6, NativeMemoryTracking=summary, 60 s idle, summary + diff (separate so NMT cannot skew timing)
    startup  JFR from JVM start, traced font/entrypoint methods, for the mod's share of start-up
    parity-hud2    steady-state screenshots of the HUD, HUD editor, map and /warp menu over a static view (no JFR)
    parity-title   title screen + sidebar fixture and the dev pages, for pixel parity
    aot-train      one launch that writes the JDK 25 AOT cache
    dfps     focused vs unfocused 30 s windows (Dynamic FPS functional check)
    smoke    join and idle 40 s, log scan (functional check)

Every JFR window is parsed by Parse3.java once the JVM has exited, so the parse never competes with
a measurement. Raw JFR, GC log, console log and screenshots stay in the run directory.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import mcclient as mc  # noqa: E402
from rcon import Rcon  # noqa: E402

HOME = Path(os.environ.get('BENCH_HOME', Path.home() / 'bench'))
PLAYER = os.environ.get('BENCH_PLAYER', 'BenchA')
CONFIGS = json.loads((HERE / 'configs.json').read_text())
FLIGHTS = json.loads((HERE / 'flights.json').read_text())
RUNTICK, RENDER = 'net.minecraft.client.Minecraft::runTick', 'net.minecraft.client.renderer.GameRenderer::render'
FM_TIMING = ['dev.fullmoon.client.hud.HudOverlay::render', 'dev.fullmoon.client.map.MapScreen::extractRenderState',
             'dev.fullmoon.client.menu.ServerMenuScreen::extractRenderState']
DIAG = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+DebugNonSafepoints', '-XX:FlightRecorderOptions=stackdepth=256']


# ---- pure helpers (unit-tested) ---------------------------------------------------------------
def resolve(name: str) -> dict:
    """A config name -> mod jar file names, JVM args, extra flags."""
    base = CONFIGS['base']
    spec = CONFIGS['configs'][name]
    mods = [m for m in base['mods'] if m not in spec.get('mods_remove', [])] + spec.get('mods_add', [])
    jvm = [a for a in base['jvm'] if a not in spec.get('jvm_remove', [])] + spec.get('jvm_add', [])
    home = str(HOME)
    expand = lambda args: [a.replace('${BENCH_HOME}', home) for a in args]  # noqa: E731
    return {'name': name, 'mods': mods, 'jars': [CONFIGS['jars'][m] for m in mods], 'jvm': expand(jvm),
            'fullmoon': 'fullmoon' in mods, 'props': spec.get('props', {}), 'train': expand(spec.get('train', []))}


class Flight:
    def __init__(self, spec: dict):
        self.points = spec['waypoints']
        self.speed = spec['speed']
        self.rate = spec['rate_hz']
        self.pitch = spec['pitch']
        self.legs = []
        total = 0.0
        for a, b in zip(self.points, self.points[1:]):
            length = math.dist(a, b)
            if length > 0:
                self.legs.append((total, length, a, b))
                total += length
        self.length = total

    def pose(self, t: float) -> tuple[float, float, float, float, float]:
        s = (self.speed * t) % self.length
        for start, length, a, b in self.legs:
            if s <= start + length or (start, length, a, b) == self.legs[-1]:
                f = min(1.0, max(0.0, (s - start) / length))
                x, y, z = (a[i] + (b[i] - a[i]) * f for i in range(3))
                yaw = math.degrees(math.atan2(-(b[0] - a[0]), b[2] - a[2]))
                return x, y, z, yaw, self.pitch
        raise AssertionError


def percentile(sorted_values: list[float], p: float) -> float:
    """Nearest-rank on a sorted list, p in 0..100."""
    if not sorted_values:
        return float('nan')
    k = max(0, min(len(sorted_values) - 1, math.ceil(p / 100 * len(sorted_values)) - 1))
    return sorted_values[k]


def frame_stats(tick_start_ns: list[int], tick_dur_ns: list[int], render_dur_ns: list[int], window_s: float) -> dict:
    """Frame time = interval between consecutive Minecraft.runTick starts (what an fps counter reports)."""
    out: dict = {'frames': len(tick_start_ns)}
    if len(tick_start_ns) < 3:
        return out
    intervals = sorted((b - a) / 1e6 for a, b in zip(tick_start_ns, tick_start_ns[1:]))
    n = len(intervals)
    worst = intervals[-max(1, n // 100):]
    out.update(fps=out['frames'] / window_s, avg_ms=sum(intervals) / n, p50_ms=percentile(intervals, 50),
               p95_ms=percentile(intervals, 95), p99_ms=percentile(intervals, 99), p999_ms=percentile(intervals, 99.9),
               max_ms=intervals[-1], low1_fps=1000.0 / (sum(worst) / len(worst)),
               runtick_avg_ms=sum(tick_dur_ns) / len(tick_dur_ns) / 1e6)
    if render_dur_ns:
        r = sorted(d / 1e6 for d in render_dur_ns)
        out.update(render_avg_ms=sum(r) / len(r), render_p99_ms=percentile(r, 99))
    return out


# ---- the run ------------------------------------------------------------------------------------
class Run:
    def __init__(self, args):
        self.args = args
        self.cfg = resolve(args.config)
        self.name = f'{args.runner}-{args.config}-{args.scenario}-r{args.rep}'
        self.out = Path(args.out).resolve() / self.name
        shutil.rmtree(self.out, ignore_errors=True)
        self.out.mkdir(parents=True)
        self.game = self.out / 'game'
        self.proc = None
        self.pid = 0
        self.t0 = 0.0
        self.log: dict = {'run': self.name, 'config': self.cfg['name'], 'scenario': args.scenario, 'rep': args.rep,
                          'runner': args.runner, 'mods': self.cfg['jars'], 'jvm': self.cfg['jvm'], 'windows': {}, 'events': {}}
        self.rcon: dict[str, Rcon] = {}
        self.rss: list[tuple[float, int]] = []
        self._rss_stop = threading.Event()

    # -- launch ------------------------------------------------------------------------------
    def start(self, render_distance: int, quickplay: bool, extra_jvm=(), props=None, env=None, nmt=False) -> None:
        shutil.rmtree(self.game, ignore_errors=True)
        (self.game / 'mods').mkdir(parents=True)
        for jar in self.cfg['jars']:
            (self.game / 'mods' / jar).symlink_to(HOME / 'jars' / jar)
        options = dict(mc.OPTIONS, renderDistance=render_distance)
        (self.game / 'options.txt').write_text(''.join(f'{k}:{v}\n' for k, v in options.items()))
        jvm = list(self.cfg['jvm']) + DIAG + [f'-Xlog:gc*:file={self.out}/gc.log:time,uptime'] + list(extra_jvm)
        jvm += [f'-D{k}={v}' for k, v in {**self.cfg['props'], **(props or {})}.items()]
        if nmt:
            jvm.append('-XX:NativeMemoryTracking=summary')
        mc.ensure_display(self.out / 'xvfb.log')
        self.log['cpu_model'] = next((l.split(':', 1)[1].strip() for l in Path('/proc/cpuinfo').read_text().splitlines()
                                      if l.startswith('model name')), '?')
        self.cpu0 = mc.cpu_ticks()
        self.t0 = time.time()
        self.proc = mc.launch(PLAYER, self.game, jvm, mc.SERVER if quickplay else None, env, self.out / 'console.log')
        self.pid = self.proc.pid
        threading.Thread(target=self._sample_rss, daemon=True).start()

    def _sample_rss(self) -> None:
        while not self._rss_stop.wait(5):
            r = mc.rss_kib(self.pid)
            if r:
                self.rss.append((time.time() - self.t0, r))

    def wait_for(self, predicate, timeout: float, interval: float = 0.25, label: str = ''):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise RuntimeError(f'client exited ({self.proc.returncode}) while waiting for {label}')
            value = predicate()
            if value:
                return value
            time.sleep(interval)
        raise TimeoutError(f'timed out after {timeout:.0f}s waiting for {label}')

    # -- start-up timeline --------------------------------------------------------------------
    def timeline(self, until_title: bool, timeout: float = 420) -> dict:
        """Polls sense_screen over the MCP port until the title screen (no quickplay) or the world is drawn.

        Quickplay polls once a second and only calls sense_screen: two calls every 250 ms during the login
        froze the client's render thread after the welcome packet (reproduced twice, never at 3 s spacing),
        so the world-side readings are deliberately coarse (1 s)."""
        ev: dict = {}
        seen: list = []
        deadline = time.time() + timeout
        last_ok = time.time()
        connected = False
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise RuntimeError(f'client exited ({self.proc.returncode}) during start-up')
            sc = mc.screen_class()
            now = time.time() - self.t0
            if sc is None and 'mcp_up_s' in ev and time.time() - last_ok > 90:
                self.dump_threads()
                raise TimeoutError('client stopped answering on the MCP port (hung)')
            if sc is not None:
                last_ok = time.time()
                ev.setdefault('mcp_up_s', now)
                if not seen or seen[-1][1] != sc:
                    seen.append((round(now, 2), sc))
                if until_title:
                    if 'TitleScreen' in sc:
                        ev['title_s'] = now
                        break
                else:
                    connected = connected or 'ConnectScreen' in sc
                    if connected and not any(k in sc for k in ('ConnectScreen', 'GenericMessageScreen', 'ProgressScreen')):
                        ev.setdefault('in_game_s', now)
                        if 'LevelLoadingScreen' not in sc:
                            ev['world_visible_s'] = now
                            break
            time.sleep(0.1 if until_title else 1.0)
        else:
            raise TimeoutError('start-up timeline timed out')
        ev['screens'] = seen
        return ev

    def dump_threads(self) -> None:
        try:
            (self.out / 'thread-dump.txt').write_text(mc.jcmd(self.pid, 'Thread.print', timeout=60))
        except Exception as exc:  # the JVM may be too wedged to answer
            (self.out / 'thread-dump.txt').write_text(f'jcmd failed: {exc}\n')

    def clear_screens(self, shot_welcome: bool = False, limit: int = 40) -> bool:
        for _ in range(limit):
            sc = mc.screen_class()
            if sc == '':
                return True
            if sc and 'ServerMenuScreen' in sc:
                if shot_welcome and not (self.out / 'welcome.png').exists():
                    time.sleep(4)
                    mc.screenshot(self.out / 'welcome.png')
                mc.press('Escape', move=True)
            time.sleep(2)
        return mc.screen_class() == ''

    # -- rcon ---------------------------------------------------------------------------------
    def rc(self, server: str) -> Rcon:
        if server not in self.rcon:
            port = {'lobby': 25575, 'survival': 25576}[server]
            self.rcon[server] = Rcon('127.0.0.1', port, os.environ['RCON_PASSWORD'])
        return self.rcon[server]

    def on_survival(self) -> bool:
        """True once the player is on the 야생 server. The first thing done to them there is spectator mode:
        the saved position of the last flight is in the air and a survival player dies of the fall in ~3 s."""
        try:
            rc = self.rc('survival')
            if PLAYER not in rc.command('list'):
                return False
            rc.command(f'gamemode spectator {PLAYER}')
            return True
        except OSError:
            return False

    def fix_world(self, server: str) -> None:
        for c in ('time set noon', 'gamerule advance_time false', 'weather clear', 'gamerule advance_weather false'):
            self.rc(server).command(c)

    # -- windows ------------------------------------------------------------------------------
    def window(self, label: str, seconds: float, driver=None, traces=True, at=None) -> None:
        jfr = self.out / f'{label}.jfr'
        args = ['JFR.start', f'name={label}', 'settings=profile', f'filename={jfr}', 'disk=true']
        args.append(f'jdk.MethodTrace#filter={RUNTICK};{RENDER}')
        args.append('jdk.MethodTrace#stackTrace=false')
        if self.cfg['fullmoon']:
            args.append('jdk.MethodTiming#filter=' + ';'.join(FM_TIMING))
        started = mc.jcmd(self.pid, *args)
        cpu_a, rss_a = mc.cpu_ticks(), mc.rss_kib(self.pid)
        t = time.time()
        thread = None
        if driver:
            thread = threading.Thread(target=driver, args=(seconds,), daemon=True)
            thread.start()
        for offset, fn in sorted((at or {}).items()):
            time.sleep(max(0, t + offset - time.time()))
            fn()
        time.sleep(max(0, t + seconds - time.time()))
        stopped = mc.jcmd(self.pid, 'JFR.stop', f'name={label}')
        elapsed = time.time() - t
        if thread:
            thread.join(10)
        self.log['windows'][label] = {
            'seconds': round(elapsed, 2), 'steal_pct': round(mc.steal_percent(cpu_a, mc.cpu_ticks()), 3),
            'rss_kib_start': rss_a, 'rss_kib_end': mc.rss_kib(self.pid), 'jfr_start': started.splitlines()[-1:],
            'jfr_stop': stopped.splitlines()[-1:], 'file': jfr.name}

    def fly(self, flight_name: str, seconds: float, label: str) -> None:
        """The `tp` stream is sent by flightd.py beside the server (see its docstring for why)."""
        import socket
        spec = FLIGHTS[flight_name]
        flight = Flight(spec)
        rc = self.rc(spec['server'])
        stats: dict = {}

        def drive(duration: float) -> None:
            with socket.create_connection(('127.0.0.1', 48394), timeout=15) as sock:
                sock.settimeout(duration + 60)
                io = sock.makefile('rw', encoding='utf-8')
                io.write(f'FLY {flight_name} {duration} {PLAYER}\n')
                io.flush()
                assert io.readline().strip() == 'STARTED'
                stats['done'] = io.readline().strip()

        x, y, z, yaw, pitch = flight.pose(0)
        rc.command(f'gamemode spectator {PLAYER}')
        rc.command(f'tp {PLAYER} {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}')
        if 'passed' not in rc.command(f'execute if entity @a[name={PLAYER},gamemode=spectator]'):
            raise RuntimeError(f'{PLAYER} is not a spectator on {spec["server"]}')
        if 'passed' in rc.command(f'execute if data entity {PLAYER} {{Health:0.0f}}'):
            # a player who died on the replica is still dead at the next login (death screen, no `tp`), which a
            # flight would happily "measure" at 30 fps; their playerdata has to be deleted on the replica
            raise RuntimeError(f'{PLAYER} is dead on {spec["server"]}; the flight would measure a death screen')
        time.sleep(12)  # let the first ring of chunks arrive and mesh before the window opens
        # what the server holds around the player, for the entity-heavy 야생 path
        self.log['events'][f'{label}_entities_at_start'] = rc.command(
            f'execute at {PLAYER} if entity @e[type=!player,distance=..200]')
        mc.screenshot(self.out / f'{label}-start.png')
        self.window(label, seconds, driver=drive)
        mc.screenshot(self.out / f'{label}-end.png')
        self.log['windows'][label]['flight'] = {**stats, 'rate_hz': flight.rate, 'speed': flight.speed,
                                               'path_length': round(flight.length, 1)}

    # -- end of run ---------------------------------------------------------------------------
    def end_metrics(self) -> None:
        end = {}
        end['rss_kib'] = mc.rss_kib(self.pid)
        end['smaps_rollup_kib'] = mc.smaps_rollup(self.pid)
        end['gc_run'] = mc.jcmd(self.pid, 'GC.run')[-200:]
        info = mc.jcmd(self.pid, 'GC.heap_info')
        end['heap_info'] = info
        m = re.search(r'used (\d+)([KMG])', info)
        if m:
            end['live_heap_mib'] = int(m.group(1)) * {'K': 1 / 1024, 'M': 1, 'G': 1024}[m.group(2)]
        end['rss_after_gc_kib'] = mc.rss_kib(self.pid)
        end['cpu_steal_pct_run'] = round(mc.steal_percent(self.cpu0, mc.cpu_ticks()), 3)
        self.log['end'] = end

    def finish(self) -> None:
        self._rss_stop.set()
        self.log['rss_samples'] = self.rss
        for rc in self.rcon.values():
            try:
                rc.close()
            except OSError:
                pass
        if self.proc:
            self.log['stop_seconds'] = round(mc.stop(self.proc), 1)
        if (self.game / 'logs/latest.log').exists():
            shutil.copy(self.game / 'logs/latest.log', self.out / 'latest.log')
        self.parse_jfrs()
        shutil.rmtree(self.game, ignore_errors=True)  # symlinks and world data; everything kept is copied out above
        (self.out / 'run.json').write_text(json.dumps(self.log, indent=1, ensure_ascii=False) + '\n')

    def parse_jfrs(self) -> None:
        for jfr in sorted(self.out.glob('*.jfr')):
            with (self.out / f'{jfr.stem}.parse.json').open('w') as handle:
                r = subprocess.run([mc.JAVA, str(HERE / 'Parse3.java'), str(jfr)], stdout=handle, stderr=subprocess.PIPE, text=True)
            if r.returncode:
                (self.out / f'{jfr.stem}.parse.err').write_text(r.stderr)

    # -- scenarios ----------------------------------------------------------------------------
    def s_title(self) -> None:
        self.start(6, quickplay=False)
        ev = self.timeline(until_title=True)
        time.sleep(10)
        ev['rss_kib_at_title_plus10s'] = mc.rss_kib(self.pid)
        ev['smaps_rollup_kib'] = mc.smaps_rollup(self.pid)
        self.log['events'].update(ev)

    def join_lobby(self, rd: int, **kw) -> None:
        self.start(rd, quickplay=True, **kw)
        ev = self.timeline(until_title=False)
        self.log['events'].update(ev)
        self.log['events']['screens_cleared'] = self.clear_screens(shot_welcome=True)

    def s_idle(self) -> None:
        self.join_lobby(6)
        self.fix_world('lobby')
        self.rc('lobby').command(f'tp {PLAYER} 0.5 73 80.5 180 0')
        time.sleep(45)
        self.clear_screens()
        self.window('idle', 120, at={60: lambda: mc.screenshot(self.out / 'hud-a.png'),
                                      64: lambda: mc.screenshot(self.out / 'hud-b.png')})
        mc.press('m', move=True)
        time.sleep(3)
        self.log['events']['map_screen'] = mc.screen_class()
        self.window('map', 60)
        mc.screenshot(self.out / 'map.png')  # taken after the window: the map is still fading in at +3 s
        mc.press('m', move=True)
        time.sleep(3)
        self.clear_screens()
        mc.say('warp')
        time.sleep(5)
        self.log['events']['menu_screen'] = mc.screen_class()
        self.window('menu', 60)
        mc.screenshot(self.out / 'menu.png')
        mc.press('Escape', move=True)
        time.sleep(3)
        self.clear_screens()
        mc.press('F10', move=True)  # HUD editor, for parity only
        time.sleep(8)
        mc.screenshot(self.out / 'hudeditor.png')
        mc.press('Escape', move=True)
        time.sleep(3)
        self.end_metrics()

    def s_flys(self) -> None:
        """The 야생 leg only (the JVM-flag screens); same path and settle as `fly`."""
        self.s_fly(lobby=False)

    def s_parity_hud2(self) -> None:
        """Steady-state screenshots of Fullmoon's in-world screens; no JFR, so nothing is timed.

        A spectator looking straight down at the plaza paving, noon and clear: the background is static, so a
        pixel that differs between two configs is the UI (or the config), not drifting clouds, animated water or
        the held compass. (parity-world, the first draft of this scenario, looked at the horizon and could not
        separate the two; parity-hud stood on the spawn where the other runner's client idles and filmed
        its player model.) The HUD's own live values (fps, ping, clock, play time) still change; parity.py masks
        those boxes."""
        self.join_lobby(6)
        self.fix_world('lobby')
        rc = self.rc('lobby')
        rc.command(f'gamemode spectator {PLAYER}')
        rc.command(f'tp {PLAYER} 0.5 73 60.5 180 90')  # 20 blocks off the spawn the other runner idles on
        time.sleep(40)
        self.clear_screens()
        mc.screenshot(self.out / 'hud-a.png')
        time.sleep(4)
        mc.screenshot(self.out / 'hud-b.png')
        mc.press('F10', move=True)
        time.sleep(12)
        mc.screenshot(self.out / 'hudeditor.png')
        mc.press('Escape', move=True)
        time.sleep(4)
        mc.press('m', move=True)
        time.sleep(15)  # the map fades in and its status line cycles; this is the steady state
        mc.screenshot(self.out / 'map.png')
        mc.press('m', move=True)
        time.sleep(4)
        self.clear_screens()
        mc.say('warp')
        time.sleep(15)
        mc.screenshot(self.out / 'menu.png')
        mc.press('Escape', move=True)
        rc.command(f'gamemode adventure {PLAYER}')

    def s_fly(self, lobby: bool = True) -> None:
        self.join_lobby(12)
        self.fix_world('lobby')
        time.sleep(30 if lobby else 10)
        self.clear_screens()
        if lobby:
            self.fly('lobby', 120, 'fly-lobby')
        self.rc('lobby').command(f'gamemode adventure {PLAYER}')
        self.rc('lobby').command(f'tp {PLAYER} 0.5 73 80.5 180 0')
        time.sleep(3)
        mc.say('server survival')
        self.wait_for(self.on_survival, 120, 0.4, 'the player on survival')
        self.log['events']['survival_arrived_s'] = round(time.time() - self.t0, 1)
        time.sleep(10)
        self.clear_screens(limit=20)
        self.fix_world('survival')
        time.sleep(25)
        self.fly('survival', 120, 'fly-survival')
        self.end_metrics()

    def s_mem(self) -> None:
        self.join_lobby(6, nmt=True)
        self.fix_world('lobby')
        self.rc('lobby').command(f'tp {PLAYER} 0.5 73 80.5 180 0')
        time.sleep(45)
        self.clear_screens()
        mc.jcmd(self.pid, 'VM.native_memory', 'baseline')
        time.sleep(60)
        (self.out / 'nmt-summary.txt').write_text(mc.jcmd(self.pid, 'VM.native_memory', 'summary'))
        (self.out / 'nmt-diff.txt').write_text(mc.jcmd(self.pid, 'VM.native_memory', 'summary.diff'))
        self.end_metrics()

    def s_startup(self) -> None:
        filters = CONFIGS['startup_filters']
        jfr = self.out / 'startup.jfr'
        flt = ';'.join(filters)
        self.start(6, quickplay=True, extra_jvm=[
            f'-XX:StartFlightRecording:name=startup,settings=profile,filename={jfr},disk=true,jdk.MethodTrace#filter={flt},jdk.MethodTrace#stackTrace=false'])
        ev = self.timeline(until_title=False)
        self.log['events'].update(ev)
        time.sleep(15)
        mc.jcmd(self.pid, 'JFR.dump', 'name=startup', f'filename={jfr}')
        mc.jcmd(self.pid, 'JFR.stop', 'name=startup')
        self.end_metrics()

    def s_parity_title(self) -> None:
        self.start(6, quickplay=False, props={'fullmoon.devScreen': 'sidebar'})
        self.timeline(until_title=True)
        time.sleep(20)
        mc.screenshot(self.out / 'title-sidebar.png')
        for key, nm in (('F6', 'specimen'), ('F7', 'kit'), ('F8', 'list'), ('F10', 'hudeditor-title')):
            mc.press(key, move=True)
            time.sleep(10)
            mc.screenshot(self.out / f'{nm}.png')

    def s_aot_train(self) -> None:
        cache = HOME / 'aot' / 'fullmoon.aot'
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.unlink(missing_ok=True)
        self.cfg['jvm'] = [a for a in self.cfg['jvm'] if not a.startswith('-XX:AOTCache=')]  # the training run writes it
        self.join_lobby(6, extra_jvm=self.cfg['train'] + [f'-Xlog:aot*=info:file={self.out}/aot-train.log'])
        time.sleep(20)
        t = time.time()
        mc.stop(self.proc, grace=300)
        self.log['events']['aot_dump_seconds'] = round(time.time() - t, 1)
        self.log['events']['aot_cache_bytes'] = cache.stat().st_size if cache.exists() else None

    def s_dfps(self) -> None:
        self.join_lobby(6)
        self.fix_world('lobby')
        time.sleep(30)
        self.clear_screens()
        self.window('focused', 30)
        thief = mc.focus_away()
        time.sleep(3)
        self.window('unfocused', 30)
        thief.kill()
        self.end_metrics()

    def s_smoke(self) -> None:
        self.join_lobby(6)
        time.sleep(40)
        self.end_metrics()

    def run(self) -> None:
        scenario = self.args.scenario
        try:
            getattr(self, 's_' + scenario.replace('-', '_'))()
            self.log['status'] = 'ok'
        except Exception as exc:  # keep what was captured; the queue goes on
            self.log['status'] = f'error: {type(exc).__name__}: {exc}'
            print(self.log['status'], file=sys.stderr)
        finally:
            self.finish()


def load_env() -> None:
    env = HOME / 'rcon.env'
    if env.exists() and 'RCON_PASSWORD' not in os.environ:
        for line in env.read_text().splitlines():
            if line.startswith('RCON_PASSWORD='):
                os.environ['RCON_PASSWORD'] = line.split('=', 1)[1].strip()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    r = sub.add_parser('run')
    r.add_argument('--config', required=True)
    r.add_argument('--scenario', required=True)
    r.add_argument('--rep', type=int, default=1)
    q = sub.add_parser('queue')
    q.add_argument('plan', type=Path)
    for p in (r, q):
        p.add_argument('--runner', default='r1')
        p.add_argument('--out', default=str(HOME / 'runs'))
    a = ap.parse_args()
    load_env()
    if a.cmd == 'run':
        Run(a).run()
        return 0
    done = Path(a.out) / 'queue.done'
    Path(a.out).mkdir(parents=True, exist_ok=True)
    finished = set(done.read_text().split('\n')) if done.exists() else set()
    for line in a.plan.read_text().splitlines():
        line = line.split('#')[0].strip()
        if not line:
            continue
        config, scenario, rep = line.split()
        key = f'{a.runner}-{config}-{scenario}-r{rep}'
        if key in finished:
            continue
        if (Path(a.out) / 'queue.stop').exists():  # pause between runs: touch it, wait for the run in flight, restart later
            print(time.strftime('%H:%M:%S'), 'stop file seen, pausing', flush=True)
            return 0
        print(time.strftime('%H:%M:%S'), 'start', key, flush=True)
        ns = argparse.Namespace(config=config, scenario=scenario, rep=int(rep), runner=a.runner, out=a.out)
        for attempt in (1, 2):  # a client that hangs under software GL is retried once
            run = Run(ns)
            run.run()
            print(time.strftime('%H:%M:%S'), 'end  ', key, f'attempt {attempt}', run.log.get('status'), flush=True)
            if run.log.get('status') == 'ok':
                break
            subprocess.run(['pkill', '-9', '-f', 'KnotClient'])
            time.sleep(5)
        if run.log.get('status') == 'ok':  # a failed run is retried the next time the queue starts
            with done.open('a') as handle:
                handle.write(key + '\n')
    print('queue finished', flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
