"""Unit tests for the pure parts of the bench (flight geometry, frame statistics, RCON framing,
summary grouping). Run: python3 -m unittest discover -s i3/tools/bench/tests"""
import json
import math
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
os.environ.setdefault('BENCH_HOME', '/nonexistent')
import bench  # noqa: E402
import rcon  # noqa: E402
import summarize  # noqa: E402


class FlightTest(unittest.TestCase):
    def test_straight_leg_and_yaw(self):
        f = bench.Flight({'waypoints': [[0, 80, 0], [0, 80, 100]], 'speed': 10, 'rate_hz': 10, 'pitch': 5})
        self.assertEqual(f.pose(0)[:3], (0, 80, 0))
        x, y, z, yaw, pitch = f.pose(5)
        self.assertAlmostEqual(z, 50)
        self.assertAlmostEqual(yaw, 0)  # +z is yaw 0
        self.assertEqual(pitch, 5)

    def test_yaw_faces_travel(self):
        east = bench.Flight({'waypoints': [[0, 80, 0], [100, 80, 0]], 'speed': 10, 'rate_hz': 10, 'pitch': 0})
        self.assertAlmostEqual(east.pose(1)[3], -90)  # +x is yaw -90 (west is +90)

    def test_loops(self):
        f = bench.Flight({'waypoints': [[0, 80, 0], [0, 80, 100]], 'speed': 10, 'rate_hz': 10, 'pitch': 0})
        self.assertAlmostEqual(f.pose(12)[2], 20)

    def test_shipped_flights_cover_the_window(self):
        for name, spec in json.loads((Path(bench.HERE) / 'flights.json').read_text()).items():
            if name.startswith('_'):
                continue
            f = bench.Flight(spec)
            self.assertGreater(f.length, 300, name)
            self.assertEqual(spec['waypoints'][0], spec['waypoints'][-1], f'{name} must close its loop')


class StatsTest(unittest.TestCase):
    def test_percentile_nearest_rank(self):
        v = list(range(1, 101))
        self.assertEqual(bench.percentile(v, 50), 50)
        self.assertEqual(bench.percentile(v, 99), 99)
        self.assertEqual(bench.percentile(v, 100), 100)

    def test_frame_stats(self):
        # 100 frames, 100 ms apart, one 1000 ms hitch
        starts, t = [], 0
        for i in range(101):
            starts.append(t)
            t += (1000 if i == 50 else 100) * 1_000_000
        s = bench.frame_stats(starts, [90_000_000] * 101, [80_000_000] * 101, window_s=10.9)
        self.assertEqual(s['frames'], 101)
        self.assertAlmostEqual(s['p50_ms'], 100)
        self.assertAlmostEqual(s['max_ms'], 1000)
        self.assertAlmostEqual(s['low1_fps'], 1.0)  # the worst 1% is the single hitch
        self.assertAlmostEqual(s['render_avg_ms'], 80)


class RconTest(unittest.TestCase):
    def test_roundtrip(self):
        pkt = rcon.encode(7, rcon.EXEC, 'tp A 1 2 3')
        packets, rest = rcon.decode(pkt + rcon.encode(8, rcon.RESPONSE, 'ok')[:5])
        self.assertEqual(packets, [(7, rcon.EXEC, 'tp A 1 2 3')])
        self.assertEqual(len(rest), 5)


class ConfigTest(unittest.TestCase):
    def test_every_config_resolves(self):
        for name in bench.CONFIGS['configs']:
            r = bench.resolve(name)
            self.assertTrue(all(j in bench.CONFIGS['jars'].values() for j in r['jars']))
        self.assertNotIn('fullmoon', bench.resolve('nomod')['mods'])
        self.assertIn('-XX:+UseZGC', bench.resolve('zgc')['jvm'])
        self.assertNotIn('-XX:+UseG1GC', bench.resolve('zgc')['jvm'])


class SummaryTest(unittest.TestCase):
    def test_spread(self):
        self.assertAlmostEqual(summarize.spread_pct([100, 102]), 1.0 / 101 * 100, places=3)
        self.assertEqual(summarize.spread_pct([5]), 0.0)


if __name__ == '__main__':
    unittest.main()
