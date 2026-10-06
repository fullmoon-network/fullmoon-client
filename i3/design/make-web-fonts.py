#!/usr/bin/env python3
"""Bake the launcher's web fonts.

The launcher ships WOFF2, not the upstream OTF/TTF files, and not the whole of them. The sources
live in launcher/fonts-src (outside public/, so they are not bundled into the installer) and are
also what make-ui-font.py and make-display-font.py bake the game's faces from.

What is dropped is only what the launcher cannot reach: the layout features nothing asks for
(stylistic sets, character variants, small caps ...) and the scripts and private-use glyphs the
UI never sets. Every precomposed Hangul syllable the source carries stays: server names, news and
player text can contain any of them. `verify` below fails the run if a syllable goes missing.

Fullmoon Sans is the mod's own baked cut (assets/fullmoon/font); only its wrapper changes.

Needs fonttools and brotli:  python3 -m venv v && v/bin/pip install fonttools brotli
"""

import hashlib
import pathlib
import sys
import time

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "launcher/fonts-src"
MOD_FONTS = ROOT / "i3/mod/src/main/resources/assets/fullmoon/font"
OUT = ROOT / "launcher/public/fonts"
# which source bytes each shipped file was baked from; launcher/scripts/web-fonts.test.ts fails
# once a source moves on without this script being run again
MANIFEST = SRC / "web-fonts.sha256"

HANGUL = range(0xAC00, 0xD7A4)

# the web UI's own text: everything the game's baked cut covers, plus Greek and Cyrillic for
# names, kana, Vietnamese, circled numbers and symbols that copy actually uses
KEEP = [
    (0x0020, 0x007E), (0x00A0, 0x024F), (0x02B0, 0x036F), (0x0370, 0x052F),
    (0x1E00, 0x1EFF), (0x2000, 0x206F), (0x20A0, 0x20BF), (0x2100, 0x214F),
    (0x2190, 0x22FF), (0x2460, 0x24FF), (0x2500, 0x257F), (0x25A0, 0x27BF),
    (0x3000, 0x30FF), (0x3130, 0x318F), (0xFE00, 0xFE0F), (0xFF01, 0xFF5E),
]

# what shaping the UI can ask for: kerning and mark placement, ligatures and contextual forms,
# and the numeral forms `font-variant-numeric` selects
FEATURES = [
    "calt", "ccmp", "clig", "liga", "locl", "rvrn", "rclt", "rlig", "kern", "mark", "mkmk", "curs",
    "tnum", "pnum", "lnum", "onum", "case", "zero",
]

PRETENDARD = ["Regular", "SemiBold", "Bold", "ExtraBold"]


def rename(font: TTFont, family: str, style: str) -> None:
    """A subset is a Modified Version, which the OFL forbids from keeping the upstream Reserved
    Font Name. Only the file's own name changes; the CSS family the launcher asks for does not."""
    names = font["name"]
    names.names = [n for n in names.names if n.nameID not in (16, 17, 18, 20, 21, 22)]
    postscript = f"{family.replace(' ', '')}-{style}"
    for value, name_id in ((family, 1), (style, 2), (f"{family} {style}", 4), (postscript, 6)):
        names.setName(value, name_id, 3, 1, 0x409)
    names.setName(f"{postscript};fullmoon-web", 3, 3, 1, 0x409)


def slim(src: pathlib.Path, out: pathlib.Path, unicodes: set[int], family: str, style: str) -> None:
    font = TTFont(str(src), lazy=False)
    opts = subset.Options()
    opts.layout_features = FEATURES
    opts.drop_tables += ["DSIG"]
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.glyph_names = False
    sub = subset.Subsetter(options=opts)
    sub.populate(unicodes=unicodes)
    sub.subset(font)
    rename(font, family, style)
    font.flavor = "woff2"
    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(out))


def rewrap(src: pathlib.Path, out: pathlib.Path) -> None:
    font = TTFont(str(src), lazy=False)
    font.flavor = "woff2"
    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(out))


def cmap(path: pathlib.Path) -> set[int]:
    return set(TTFont(str(path)).getBestCmap())


def verify(src: pathlib.Path, out: pathlib.Path, whole: bool) -> str:
    before, after = cmap(src), cmap(out)
    want = {cp for cp in before if cp in HANGUL}
    lost = want - after
    if lost:
        raise SystemExit(f"{out.name}: lost {len(lost)} hangul syllables, e.g. U+{min(lost):04X}")
    if whole and before - after:
        raise SystemExit(f"{out.name}: lost {len(before - after)} codepoints")
    return f"{len(want)}/{len(want)} hangul syllables ({len(want & after)} present of {len(HANGUL)} in the block)"


def write_manifest(pairs: list[tuple[pathlib.Path, pathlib.Path]]) -> None:
    lines = [
        f"{hashlib.sha256(src.read_bytes()).hexdigest()}  {src.relative_to(ROOT).as_posix()}  {out.relative_to(ROOT).as_posix()}"
        for src, out in pairs
    ]
    MANIFEST.write_text("\n".join(lines) + "\n")


def main() -> int:
    baked = []
    wanted = {cp for lo, hi in KEEP for cp in range(lo, hi + 1)} | set(HANGUL)
    jobs = []
    for w in PRETENDARD:
        jobs.append((SRC / f"Pretendard-{w}.otf", OUT / f"Pretendard-{w}.woff2", wanted, False, "Fullmoon Figures", w))
    # Hahmlet is a small variable file already: it keeps its whole character set
    hah = SRC / "Hahmlet[wght].ttf"
    jobs.append((hah, OUT / "Hahmlet-Variable.woff2", cmap(hah), True, "Fullmoon Display", "Regular"))
    for src, out, unicodes, whole, family, style in jobs:
        t = time.time()
        slim(src, out, unicodes, family, style)
        baked.append((src, out))
        print(f"{out.name:28s} {src.stat().st_size:>9,} -> {out.stat().st_size:>9,} B  "
              f"{time.time() - t:4.1f}s  {verify(src, out, whole)}")
    for name, style in (("sans-regular", "Regular"), ("sans-semibold", "SemiBold")):
        src, out = MOD_FONTS / f"{name}.ttf", OUT / f"FullmoonSans-{style}.woff2"
        rewrap(src, out)
        baked.append((src, out))
        print(f"{out.name:28s} {src.stat().st_size:>9,} -> {out.stat().st_size:>9,} B  "
              f"{verify(src, out, True)}")
    write_manifest(baked)
    return 0


if __name__ == "__main__":
    sys.exit(main())
