"""Headless Fabric client plumbing for the bench: launch, MCP calls, X11 input, /proc readings.

The launch half follows the replica walkthrough harness (servers-network/scripts/walk/client.py in
the server repo): one offline account, its own game dir, Xvfb, Mesa llvmpipe, and the
minecraft-fabric-mcp mod listening on a local port so the driver can ask what the client is showing.
"""
from __future__ import annotations

import base64
import ctypes
import hashlib
import json
import os
import signal
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

VERSION = 'fabric-loader-0.19.3-26.1.2'
ENGINE = Path(os.environ.get('BENCH_ENGINE', Path.home() / '.minecraft'))
JAVA_HOME = ENGINE / 'runtime/java-runtime-epsilon/linux/java-runtime-epsilon'
JAVA, JCMD = str(JAVA_HOME / 'bin/java'), str(JAVA_HOME / 'bin/jcmd')
DISPLAY, MCP_PORT = ':97', 8766
SERVER = '127.0.0.1:48291'

OPTIONS = {
    'renderDistance': 6, 'simulationDistance': 5, 'maxFps': 260, 'enableVsync': 'false',
    'guiScale': 3, 'pauseOnLostFocus': 'false', 'skipMultiplayerWarning': 'true',
    'onboardAccessibility': 'false', 'tutorialStep': 'none', 'narrator': 0,
    'soundCategory_master': '0.0', 'overrideWidth': 1280, 'overrideHeight': 720,
}


def offline_uuid(name: str) -> uuid.UUID:
    digest = bytearray(hashlib.md5(f'OfflinePlayer:{name}'.encode()).digest())
    digest[6] = digest[6] & 0x0F | 0x30
    digest[8] = digest[8] & 0x3F | 0x80
    return uuid.UUID(bytes=bytes(digest))


def rpc(tool: str, arguments: dict | None = None, timeout: float = 10, port: int = MCP_PORT) -> dict:
    body = json.dumps({'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call',
                       'params': {'name': tool, 'arguments': arguments or {}}}).encode()
    request = urllib.request.Request(f'http://127.0.0.1:{port}/mcp', body, {
        'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream',
        'Host': f'127.0.0.1:{port}'})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read().decode('utf-8', 'replace').strip()
    if not raw.startswith('{'):
        raw = next(line[5:].strip() for line in raw.splitlines() if line.startswith('data:'))
    document = json.loads(raw)
    if document.get('error'):
        raise RuntimeError(f'{tool}: {document["error"]}')
    return document.get('result') or {}


def text_of(result: dict) -> str:
    return '\n'.join(b.get('text', '') for b in result.get('content', []) if b.get('type') == 'text')


def screen_class() -> str | None:
    """The class of the open screen, '' for none, None when the MCP server does not answer."""
    try:
        text = text_of(rpc('sense_screen', timeout=3))
    except Exception:
        return None
    for line in text.splitlines():
        if line.startswith('screen_class:'):
            return line.split(':', 1)[1].strip()
    return ''


def in_game() -> bool | None:
    try:
        return 'in_game: true' in text_of(rpc('client_status', timeout=3))
    except Exception:
        return None


def ensure_display(log: Path) -> None:
    sock = Path('/tmp/.X11-unix') / f'X{DISPLAY.lstrip(":")}'
    if not sock.exists():
        with log.open('a') as handle:
            subprocess.Popen(['Xvfb', DISPLAY, '-screen', '0', '1280x720x24', '-nolisten', 'tcp'],
                             stdout=handle, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(50):
        if sock.exists():
            return
        time.sleep(0.2)
    raise RuntimeError(f'Xvfb did not create {DISPLAY}')


def launch(name: str, game_dir: Path, jvm_args: list[str], server: str | None, env_extra: dict | None = None,
           console: Path | None = None) -> subprocess.Popen:
    import minecraft_launcher_lib.command as launcher
    options = {
        'username': name, 'uuid': offline_uuid(name).hex, 'token': '0', 'launcherName': 'bench',
        'launcherVersion': '1.0', 'gameDirectory': str(game_dir), 'jvmArguments': jvm_args,
        'customResolution': True, 'resolutionWidth': '1280', 'resolutionHeight': '720',
        'executablePath': JAVA,
    }
    if server:
        options['quickPlayMultiplayer'] = server
    command = launcher.get_minecraft_command(VERSION, str(ENGINE), options)
    env = {**os.environ, 'DISPLAY': DISPLAY, 'MCP_CLIENT_PORT': str(MCP_PORT), 'LIBGL_ALWAYS_SOFTWARE': '1',
           'MESA_GL_VERSION_OVERRIDE': '4.5', 'MESA_GLSL_VERSION_OVERRIDE': '450', **(env_extra or {})}
    env.pop('WAYLAND_DISPLAY', None)
    log = (console or game_dir / 'console.log').open('ab')
    return subprocess.Popen(command, cwd=game_dir, env=env, stdin=subprocess.DEVNULL, stdout=log,
                            stderr=subprocess.STDOUT, start_new_session=True)


def stop(proc: subprocess.Popen, grace: int = 60) -> float:
    """SIGTERM the process group so shutdown hooks run (the AOT cache is written at exit)."""
    t0 = time.time()
    if proc.poll() is None:
        os.killpg(proc.pid, signal.SIGTERM)
        try:
            proc.wait(grace)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL)
            proc.wait()
    return time.time() - t0


# ---- X11 input through XTEST ------------------------------------------------------------------
_x11 = _xtst = None


def _libs() -> None:
    global _x11, _xtst
    if _x11 is None:  # loaded on first use so the module imports on a machine without X11 (unit tests)
        _x11 = ctypes.CDLL('libX11.so.6')
        _xtst = ctypes.CDLL('libXtst.so.6')
        _x11.XOpenDisplay.restype = ctypes.c_void_p


def _display():
    _libs()
    d = _x11.XOpenDisplay(DISPLAY.encode())
    assert d, f'cannot open {DISPLAY}'
    return ctypes.c_void_p(d)


def _keycode(d, name: str) -> int:
    return _x11.XKeysymToKeycode(d, ctypes.c_ulong(_x11.XStringToKeysym(name.encode())))


def press(name: str, shift: bool = False, move: bool = False, hold: float = 0.12) -> None:
    d = _display()
    if move:  # a pointer inside the window, otherwise GLFW ignores the key on some screens
        _xtst.XTestFakeMotionEvent(d, 0, 640, 360, 0)
        _x11.XFlush(d)
        time.sleep(0.3)
    code, shift_code = _keycode(d, name), _keycode(d, 'Shift_L')
    if shift:
        _xtst.XTestFakeKeyEvent(d, shift_code, 1, 0)
    for down in (1, 0):
        _xtst.XTestFakeKeyEvent(d, code, down, 0)
        _x11.XFlush(d)
        time.sleep(hold)
    if shift:
        _xtst.XTestFakeKeyEvent(d, shift_code, 0, 0)
    _x11.XFlush(d)
    _x11.XCloseDisplay(d)


def say(text: str) -> None:
    """Types a chat command: the slash opens the chat line, Return sends it."""
    press('slash', move=True)
    time.sleep(0.8)
    for ch in text:
        if ch == ' ':
            press('space')
        elif ch.isupper():
            press(ch.lower(), shift=True)
        else:
            press(ch)
    time.sleep(0.3)
    press('Return')


_THIEF = """import ctypes, time
x = ctypes.CDLL('libX11.so.6'); x.XOpenDisplay.restype = ctypes.c_void_p
x.XDefaultRootWindow.restype = ctypes.c_ulong; x.XCreateSimpleWindow.restype = ctypes.c_ulong
d = ctypes.c_void_p(x.XOpenDisplay(b':97')); r = x.XDefaultRootWindow(d)
w = x.XCreateSimpleWindow(d, ctypes.c_ulong(r), 0, 0, 10, 10, 0, 0, 0)
x.XMapWindow(d, ctypes.c_ulong(w)); x.XSetInputFocus(d, ctypes.c_ulong(w), 1, 0); x.XFlush(d)
time.sleep(3600)
"""


def focus_away() -> subprocess.Popen:
    """Takes X input focus with a window the client does not own, so GLFW reports focus lost.
    Kill the returned process to give the focus back (it reverts to the window under the pointer)."""
    proc = subprocess.Popen([sys.executable, '-c', _THIEF])
    time.sleep(1.0)
    return proc


def screenshot(path: Path) -> None:
    subprocess.run(['import', '-display', DISPLAY, '-window', 'root', str(path)], check=True)


# ---- /proc ------------------------------------------------------------------------------------
def rss_kib(pid: int) -> int | None:
    try:
        for line in Path(f'/proc/{pid}/status').read_text().splitlines():
            if line.startswith('VmRSS:'):
                return int(line.split()[1])
    except OSError:
        return None
    return None


def smaps_rollup(pid: int) -> dict[str, int]:
    out = {}
    try:
        for line in Path(f'/proc/{pid}/smaps_rollup').read_text().splitlines():
            parts = line.split()
            if len(parts) >= 2 and parts[0].endswith(':') and parts[1].isdigit():
                out[parts[0][:-1]] = int(parts[1])
    except OSError:
        pass
    return out


def cpu_ticks() -> dict[str, int]:
    """Aggregate cpu line of /proc/stat: user nice system idle iowait irq softirq steal."""
    fields = Path('/proc/stat').read_text().splitlines()[0].split()[1:]
    names = ['user', 'nice', 'system', 'idle', 'iowait', 'irq', 'softirq', 'steal']
    return dict(zip(names, map(int, fields)))


def steal_percent(before: dict[str, int], after: dict[str, int]) -> float:
    total = sum(after.values()) - sum(before.values())
    return 100.0 * (after['steal'] - before['steal']) / total if total else 0.0


def jcmd(pid: int, *args: str, timeout: int = 120) -> str:
    r = subprocess.run([JCMD, str(pid), *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout + r.stderr).strip()
