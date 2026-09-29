#!/usr/bin/env python3
"""Synthesise the Fullmoon UI sound set.

Provenance: every cue is an original work generated for Fullmoon by this script from numpy
primitives — sine fundamentals with one to three weak partials and a few milliseconds of
low-passed noise for touch. Nothing is recorded, sampled, derived from or meant to reproduce
any third-party product sound. The set ships under the project licence (GPL-3.0).

Output: mono 44.1 kHz OGG Vorbis at assets/fullmoon/sounds/ui/<cue>.ogg, rendered as 24-bit WAV
in a temporary directory and encoded by ffmpeg (libvorbis q5). The render uses a fixed seed and
the encode is bit-exact, so the files reproduce byte for byte on the same ffmpeg build.
"""

import math
import pathlib
import shutil
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
SEED = 20260930
PEAK_DBFS = -6.0
FADE_IN_MS = 3.0
FADE_OUT_MS = 25.0
LIMIT_MS = {"open": 220.0, "close": 220.0}  # every other cue ≤ 260 ms
DEFAULT_LIMIT_MS = 260.0
FFMPEG = shutil.which("ffmpeg") or "/usr/bin/ffmpeg"
OUT_DIR = pathlib.Path(__file__).resolve().parents[1] / "mod/src/main/resources/assets/fullmoon/sounds/ui"


def ms(x: float) -> int:
    return int(round(SR * x / 1000.0))


def ramp(n: int) -> np.ndarray:
    return 0.5 - 0.5 * np.cos(np.linspace(0.0, math.pi, n))


def swell(n: int, up_ms: float) -> np.ndarray:
    up = ms(up_ms)
    return np.concatenate([ramp(up), ramp(n - up)[::-1]])


def glide(n: int, f0: float, f1: float) -> np.ndarray:
    return f0 * (f1 / f0) ** np.linspace(0.0, 1.0, n)


def band(x: np.ndarray, lo_hz: float, hi_hz: float, order: int = 2) -> np.ndarray:
    # Zero-phase Butterworth magnitude; the envelope applied afterwards removes the circular edges.
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    hp = 1.0 / np.sqrt(1.0 + (lo_hz / np.maximum(f, 1e-9)) ** (2 * order))
    lp = 1.0 / np.sqrt(1.0 + (f / hi_hz) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x) * hp * lp, n=len(x))


def unit_noise(rng: np.random.Generator, n: int, lo_hz: float, hi_hz: float) -> np.ndarray:
    x = band(rng.standard_normal(n), lo_hz, hi_hz)
    return x / np.sqrt(np.mean(x * x))


def note(n, freq, partials, tau_ms, start_ms=0.0, level=1.0) -> np.ndarray:
    """sine + weak partials; 3 ms raised-cosine attack, exponential decay. `freq` may be a glide array."""
    s = ms(start_ms)
    m = n - s
    f = np.broadcast_to(np.asarray(freq, dtype=float), (m,))
    phase = 2.0 * math.pi * np.cumsum(f) / SR
    body = sum(a * np.sin(k * phase) for k, a in partials)
    env = np.exp(-np.arange(m) / SR / (tau_ms / 1000.0))
    env[: ms(FADE_IN_MS)] *= ramp(ms(FADE_IN_MS))
    out = np.zeros(n)
    out[s:] = level * body * env
    return out


def touch(rng, n, cutoff_hz, length_ms, start_ms=0.0, level=0.2) -> np.ndarray:
    """2–6 ms Hann-windowed low-passed noise transient."""
    s, m = ms(start_ms), ms(length_ms)
    out = np.zeros(n)
    out[s : s + m] = level * unit_noise(rng, m, 150.0, cutoff_hz) * np.hanning(m)
    return out


def focus(rng):  # single soft tick
    n = ms(35)
    return note(n, 1050.0, [(1, 1.0), (2, 0.08)], 9.0) + touch(rng, n, 3500.0, 3.0, level=0.18)


def tab(rng):  # mid click, brighter touch than focus
    n = ms(55)
    return note(n, 800.0, [(1, 1.0), (2, 0.16), (3, 0.05)], 13.0) + touch(rng, n, 6500.0, 4.0, level=0.3)


def confirm(rng):  # two notes rising, second softer
    n = ms(140)
    p = [(1, 1.0), (2, 0.12), (3, 0.04)]
    return (note(n, 660.0, p, 40.0) + touch(rng, n, 3000.0, 3.0, level=0.12)
            + note(n, 990.0, p, 40.0, start_ms=60.0, level=0.72) + touch(rng, n, 3000.0, 3.0, 60.0, 0.08))


def back(rng):  # two notes falling
    n = ms(120)
    p = [(1, 1.0), (2, 0.10)]
    return (note(n, 990.0, p, 35.0) + touch(rng, n, 3000.0, 3.0, level=0.12)
            + note(n, 660.0, p, 38.0, start_ms=50.0, level=0.8) + touch(rng, n, 2500.0, 3.0, 50.0, 0.08))


def breath(rng, n, up_ms, dark_first):
    # Airy noise: dark layer cross-faded with a brighter one across the swell.
    dark = unit_noise(rng, n, 200.0, 1500.0)
    bright = unit_noise(rng, n, 400.0, 3200.0)
    mix = np.linspace(0.0, 1.0, n) if dark_first else np.linspace(1.0, 0.0, n)
    return 0.16 * swell(n, up_ms) * ((1.0 - mix) * dark + mix * bright)


def open_(rng):  # swell + faint tone rising
    n = ms(200)
    tone = note(n, glide(n, 440.0, 528.0), [(1, 1.0), (2, 0.08)], 1e6) * swell(n, 150.0)
    return breath(rng, n, 120.0, True) + 0.15 * tone


def close(rng):  # mirrored, shorter, falling
    n = ms(140)
    tone = note(n, glide(n, 528.0, 440.0), [(1, 1.0), (2, 0.08)], 1e6) * swell(n, 20.0)
    return breath(rng, n, 25.0, False) + 0.15 * tone


def error(rng):  # low dull double tap, second muted
    n = ms(200)
    p = [(1, 1.0), (2, 0.2), (3, 0.06)]
    return (note(n, 180.0, p, 28.0) + touch(rng, n, 500.0, 6.0, level=0.5)
            + note(n, 180.0, p, 24.0, start_ms=70.0, level=0.55) + touch(rng, n, 450.0, 5.0, 70.0, 0.3))


CUES = {"focus": focus, "confirm": confirm, "back": back, "open": open_,
        "close": close, "error": error, "tab": tab}


def finish(x: np.ndarray) -> np.ndarray:
    """3 ms fade-in; ≥25 ms exponential release to −60 dB with the last 1 ms tied to zero; peak −6 dBFS."""
    fi, fo, tail = ms(FADE_IN_MS), ms(FADE_OUT_MS), ms(1.0)
    x[:fi] *= ramp(fi)
    x[-fo:] *= np.exp(np.linspace(0.0, math.log(1e-3), fo))
    x[-tail:] *= ramp(tail)[::-1]
    return x * (10 ** (PEAK_DBFS / 20) / np.max(np.abs(x)))


def write_wav(path: pathlib.Path, x: np.ndarray) -> None:
    pcm = np.round(np.clip(x, -1.0, 1.0) * 8388607).astype("<i4").view(np.uint8).reshape(-1, 4)[:, :3]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(3)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(wav: pathlib.Path, ogg: pathlib.Path) -> None:
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags", "+bitexact",
                    "-c:a", "libvorbis", "-q:a", "5", "-ar", "44100", "-ac", "1", str(ogg)], check=True)


def main() -> int:
    rng = np.random.default_rng(SEED)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"wrote {OUT_DIR.relative_to(OUT_DIR.parents[7])}")
    with tempfile.TemporaryDirectory(prefix="fullmoon-ui-sounds-") as tmp:
        for name, make in CUES.items():
            x = finish(make(rng))
            limit = LIMIT_MS.get(name, DEFAULT_LIMIT_MS)
            if len(x) > ms(limit):
                raise SystemExit(f"{name}: {len(x) / SR * 1000:.1f} ms exceeds {limit:.0f} ms")
            wav = pathlib.Path(tmp) / f"{name}.wav"
            write_wav(wav, x)
            ogg = OUT_DIR / f"{name}.ogg"
            encode(wav, ogg)
            peak = 20 * math.log10(np.max(np.abs(x)))
            print(f"  {name + '.ogg':12s} {len(x) / SR * 1000:6.1f} ms  peak {peak:6.2f} dBFS  {ogg.stat().st_size:6d} B")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
