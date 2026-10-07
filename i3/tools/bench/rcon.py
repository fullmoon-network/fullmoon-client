"""Source RCON, stdlib only, with a pipelined send for fixed-rate command streams.

A flythrough is one `tp` per tick of a clock, so the sender must not wait for the reply to the
previous one: with an ssh relay in the path a round trip is a few hundred milliseconds and a
send-and-wait loop would cap the rate well below the schedule. Replies are drained by a reader
thread; `command()` is the send-and-wait form for the occasional query.
"""
from __future__ import annotations

import socket
import struct
import threading
import time

AUTH, EXEC, RESPONSE = 3, 2, 0


def encode(request_id: int, kind: int, payload: str) -> bytes:
    body = struct.pack('<ii', request_id, kind) + payload.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(body)) + body


def decode(buffer: bytes) -> tuple[list[tuple[int, int, str]], bytes]:
    """Splits whole packets off the front of the buffer; returns (packets, remainder)."""
    packets = []
    while len(buffer) >= 4:
        (length,) = struct.unpack_from('<i', buffer)
        if length < 10 or len(buffer) < 4 + length:
            break
        request_id, kind = struct.unpack_from('<ii', buffer, 4)
        packets.append((request_id, kind, buffer[12:4 + length - 2].decode('utf-8', 'replace')))
        buffer = buffer[4 + length:]
    return packets, buffer


class Rcon:
    def __init__(self, host: str, port: int, password: str, timeout: float = 20.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.lock = threading.Lock()
        self.next_id = 1
        self.replies: dict[int, str] = {}
        self.sent = 0
        self.received = 0
        self.buffer = b''
        self.closed = False
        self.sock.sendall(encode(0, AUTH, password))
        deadline = time.time() + timeout
        while True:
            packets, self.buffer = decode(self.buffer)
            hit = next((p for p in packets if p[1] == EXEC), None)
            if hit:
                if hit[0] == -1:
                    raise PermissionError('rcon: wrong password')
                break
            if time.time() > deadline:
                raise TimeoutError('rcon: no auth reply')
            self.buffer += self.sock.recv(4096)
        self.sock.settimeout(None)
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self) -> None:
        try:
            while not self.closed:
                data = self.sock.recv(65536)
                if not data:
                    return
                self.buffer += data
                packets, self.buffer = decode(self.buffer)
                with self.lock:
                    for request_id, kind, payload in packets:
                        if kind == RESPONSE:
                            self.received += 1
                            self.replies[request_id] = payload
        except OSError:
            return

    def send(self, command: str) -> int:
        """Fire and forget; the reply lands in `replies` under the returned id."""
        with self.lock:
            request_id = self.next_id
            self.next_id += 1
            self.sent += 1
        self.sock.sendall(encode(request_id, EXEC, command))
        return request_id

    def command(self, command: str, timeout: float = 20.0) -> str:
        request_id = self.send(command)
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                if request_id in self.replies:
                    return self.replies.pop(request_id)
            time.sleep(0.01)
        raise TimeoutError(f'rcon: no reply to {command!r}')

    def close(self) -> None:
        self.closed = True
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()
