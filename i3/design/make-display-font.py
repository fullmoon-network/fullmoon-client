#!/usr/bin/env python3
"""Bake the two Fullmoon display faces out of the Hahmlet variable font.

The display roles are a serif on purpose: the mark, the result verdict and every title are
set in it, and everything else stays on Pretendard. Hahmlet is a TrueType variable font
(glyf + gvar); the game's ttf provider cannot instance one and applies no weight axis, so
the two weights the roles need are frozen here, at build time, into static faces.

The subset is Latin plus the KS X 1001 syllables; a syllable outside it falls through to
the Pretendard SemiBold provider listed after this one in the font json. The faces are
renamed: the upstream is OFL-1.1, and a subset of it is a Modified Version. The upstream
notice travels with the jar in NOTICE-fonts.md and OFL-Hahmlet.txt.
"""

import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

SOURCE = pathlib.Path(__file__).resolve().parents[1].parent / "launcher/public/fonts/Hahmlet[wght].ttf"
OUT_DIR = pathlib.Path(__file__).resolve().parents[1] / "mod/src/main/resources/assets/fullmoon/font"
FAMILY = "Fullmoon Serif"
FACES = [
    (600, "serif-semibold.ttf", "SemiBold"),
    (700, "serif-bold.ttf", "Bold"),
]

UNICODE_RANGES = [
    (0x0020, 0x007E),  # basic latin
    (0x00A0, 0x00FF),  # latin-1 supplement
    (0x2000, 0x206F),  # general punctuation: real dashes, real quotes, the ellipsis
    (0x20A0, 0x20BF),  # currency, for the store
    (0x2190, 0x21FF),  # arrows
    (0x2200, 0x22FF),  # maths operators
    (0x3000, 0x303F),  # cjk symbols and punctuation
    (0x3130, 0x318F),  # hangul compatibility jamo: standalone letters, as in chat
]


def ks_x_1001_syllables() -> set[int]:
    """The 2350 precomposed syllables of KS X 1001, derived rather than tabulated."""
    out = set()
    for cp in range(0xAC00, 0xD7A4):
        try:
            encoded = chr(cp).encode("euc_kr")
        except UnicodeEncodeError:
            continue
        # Python's euc_kr codec is the UHC superset and encodes all 11172 syllables, so
        # the wansung lead-byte range is what actually selects KS X 1001.
        if len(encoded) == 2 and 0xB0 <= encoded[0] <= 0xC8:
            out.add(cp)
    return out


def bake(weight: int, out: pathlib.Path, style: str) -> None:
    font = TTFont(str(SOURCE), lazy=False)
    codepoints = {cp for lo, hi in UNICODE_RANGES for cp in range(lo, hi + 1)}
    codepoints |= ks_x_1001_syllables()

    options = subset.Options()
    # The game maps a codepoint straight to a glyph and advances by hmtx; there is no
    # shaping engine anywhere in its text path, so every layout table is dead weight.
    options.layout_features = []
    options.drop_tables += ["BASE", "DSIG", "VORG", "vhea", "vmtx", "VVAR"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.notdef_outline = True
    options.recalc_bounds = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)

    frozen = instancer.instantiateVariableFont(font, {"wght": weight}, inplace=True, updateFontNames=False)
    for table in ("STAT", "fvar", "avar", "gvar", "HVAR", "MVAR", "cvar"):
        if table in frozen:
            del frozen[table]

    names = frozen["name"]
    names.names = [n for n in names.names if n.nameID not in (16, 17, 18, 20, 21, 22, 25)]
    postscript = f"FullmoonSerif-{style}"
    for value, name_id in ((FAMILY, 1), (style, 2), (f"{FAMILY} {style}", 4), (postscript, 6)):
        names.setName(value, name_id, 3, 1, 0x409)
    names.setName(f"{postscript};fullmoon", 3, 3, 1, 0x409)
    frozen["OS/2"].usWeightClass = weight

    out.parent.mkdir(parents=True, exist_ok=True)
    frozen.save(str(out))
    kb = out.stat().st_size / 1024
    print(f"  {out.name:20s} {len(frozen.getGlyphOrder()):5d} glyphs  {kb:6.0f} KiB  {FAMILY} {style}  wght {weight}")


def main() -> int:
    if not SOURCE.exists():
        print(f"missing source font: {SOURCE}", file=sys.stderr)
        return 1
    print(f"wrote {OUT_DIR.relative_to(OUT_DIR.parents[6])}")
    for weight, out, style in FACES:
        bake(weight, OUT_DIR / out, style)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
