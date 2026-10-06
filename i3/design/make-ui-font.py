#!/usr/bin/env python3
"""Bake the three Fullmoon UI faces out of Pretendard.

Three weights and no more, because the type roles need exactly three: Regular carries body,
SemiBold carries row, strong and micro, Bold carries figure. Display is a different face
entirely — see make-display-font.py.

The faces are renamed. Pretendard ships under the OFL with the Reserved Font Name
'Pretendard', and a subset whose outlines have been rewritten is a Modified Version, which
the licence forbids from carrying that name. The upstream notice travels with the jar in
NOTICE.md and OFL-Pretendard.txt.
"""

import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

from _glyf import to_glyf

SOURCE_DIR = pathlib.Path(__file__).resolve().parents[1].parent / "launcher/fonts-src"
OUT_DIR = pathlib.Path(__file__).resolve().parents[1] / "mod/src/main/resources/assets/fullmoon/font"
FACES = [
    ("Pretendard-Regular.otf", "sans-regular.ttf", "Regular"),
    ("Pretendard-SemiBold.otf", "sans-semibold.ttf", "SemiBold"),
    ("Pretendard-Bold.otf", "sans-bold.ttf", "Bold"),
]
FAMILY = "Fullmoon Sans"

UNICODE_RANGES = [
    (0x0020, 0x007E),  # basic latin
    (0x00A0, 0x00FF),  # latin-1 supplement
    (0x0100, 0x017F),  # latin extended-a, for european player names
    (0x2000, 0x206F),  # general punctuation: real dashes, real quotes, the ellipsis
    (0x20A0, 0x20BF),  # currency, for the store
    (0x2190, 0x21FF),  # arrows
    (0x2200, 0x22FF),  # maths operators
    (0x2500, 0x257F),  # box drawing, for the log view
    (0x25A0, 0x25FF),  # geometric shapes
    (0x3000, 0x303F),  # cjk symbols and punctuation
    (0x3130, 0x318F),  # hangul compatibility jamo: standalone letters, as in chat
    (0xAC00, 0xD7A3),  # every modern precomposed syllable
    (0xFF01, 0xFF5E),  # fullwidth forms
]


def bake(source: pathlib.Path, out: pathlib.Path, style: str) -> None:
    font = TTFont(str(source), lazy=False)

    options = subset.Options()
    # The game maps a codepoint straight to a glyph and advances by hmtx; there is no
    # shaping engine anywhere in its text path, so every layout table is dead weight.
    options.layout_features = []
    options.drop_tables += ["BASE", "DSIG", "VORG", "vhea", "vmtx"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.notdef_outline = True
    options.recalc_bounds = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes={cp for lo, hi in UNICODE_RANGES for cp in range(lo, hi + 1)})
    subsetter.subset(font)

    to_glyf(font)

    names = font["name"]
    # Typographic and WWS names would reassert the upstream family behind our back.
    names.names = [n for n in names.names if n.nameID not in (16, 17, 18, 20, 21, 22)]
    postscript = f"FullmoonSans-{style}"
    for value, name_id in ((FAMILY, 1), (style, 2), (f"{FAMILY} {style}", 4), (postscript, 6)):
        names.setName(value, name_id, 3, 1, 0x409)
    names.setName(f"{postscript};fullmoon", 3, 3, 1, 0x409)

    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(out))
    kb = out.stat().st_size / 1024
    print(f"  {out.name:20s} {len(font.getGlyphOrder()):5d} glyphs  {kb:6.0f} KiB  {FAMILY} {style}")


def main() -> int:
    missing = [name for name, _, _ in FACES if not (SOURCE_DIR / name).exists()]
    if missing:
        print(f"missing sources in {SOURCE_DIR}: {', '.join(missing)}", file=sys.stderr)
        return 1
    print(f"wrote {OUT_DIR.relative_to(OUT_DIR.parents[6])}")
    for source, out, style in FACES:
        bake(SOURCE_DIR / source, OUT_DIR / out, style)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
