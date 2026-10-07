#!/usr/bin/env python3
"""Flight daemon: runs the scripted `tp` stream next to the server, where an RCON round trip is ~1 ms.

Over the ssh relay a round trip is a few hundred milliseconds and Minecraft's RCON reader drops a
connection that delivers two pipelined packets in one read, so a 10 Hz stream cannot be driven from the
client runner. This runs ON the replica runner; the client asks over one more relayed port:

    flightd.py [--port 48394] [--secrets ~/replica/.secrets]
    client -> "FLY <flight> <seconds> <player>\\n"      daemon -> "STARTED\\n" ... "DONE sent=N max_lag_ms=X\\n"
"""
from __future__ import annotations

import argparse
import json
import socket
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rcon import Rcon  # noqa: E402

RCON_PORTS = {'lobby': 25575, 'survival': 25576}


def load_flight_class():
    # bench.py needs the client-side modules; the geometry lives in a copy kept here so this file stands alone
    import math

    class Flight:
        def __init__(self, spec):
            self.points, self.speed, self.rate, self.pitch = spec['waypoints'], spec['speed'], spec['rate_hz'], spec['pitch']
            self.legs, total = [], 0.0
            for a, b in zip(self.points, self.points[1:]):
                length = math.dist(a, b)
                if length > 0:
                    self.legs.append((total, length, a, b))
                    total += length
            self.length = total

        def pose(self, t):
            s = (self.speed * t) % self.length
            for start, length, a, b in self.legs:
                if s <= start + length or (start, length, a, b) == self.legs[-1]:
                    f = min(1.0, max(0.0, (s - start) / length))
                    x, y, z = (a[i] + (b[i] - a[i]) * f for i in range(3))
                    return x, y, z, math.degrees(math.atan2(-(b[0] - a[0]), b[2] - a[2])), self.pitch
    return Flight


def serve(conn: socket.socket, flights: dict, password: str) -> None:
    Flight = load_flight_class()
    with conn, conn.makefile('rw', encoding='utf-8') as io:
        verb, name, seconds, player = io.readline().split()
        assert verb == 'FLY'
        flight = Flight(flights[name])
        rc = Rcon('127.0.0.1', RCON_PORTS[flights[name]['server']], password)
        io.write('STARTED\n')
        io.flush()
        t0, i, lag = time.monotonic(), 0, 0.0
        while i / flight.rate < float(seconds):
            due = t0 + i / flight.rate
            wait = due - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            else:
                lag = max(lag, -wait * 1000)
            x, y, z, yaw, pitch = flight.pose(i / flight.rate)
            rc.command(f'tp {player} {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}')
            i += 1
        rc.close()
        io.write(f'DONE sent={i} max_lag_ms={lag:.1f} length={flight.length:.1f}\n')
        io.flush()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=48394)
    ap.add_argument('--secrets', type=Path, default=Path.home() / 'replica/.secrets')
    ap.add_argument('--flights', type=Path, default=Path(__file__).with_name('flights.json'))
    a = ap.parse_args()
    password = next(l.split('=', 1)[1].strip() for l in a.secrets.read_text().splitlines() if l.startswith('RCON_PASSWORD='))
    flights = json.loads(a.flights.read_text())
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('127.0.0.1', a.port))
    srv.listen(4)
    print('flightd listening', a.port, flush=True)
    while True:
        conn, _ = srv.accept()
        threading.Thread(target=lambda c=conn: serve(c, flights, password), daemon=True).start()


if __name__ == '__main__':
    sys.exit(main())
