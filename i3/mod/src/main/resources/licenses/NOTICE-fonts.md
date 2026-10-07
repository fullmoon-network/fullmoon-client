# Fonts

Both faces this client ships are Modified Versions of OFL-1.1 fonts, subset by the scripts under
`i3/design/`. Pretendard carries a Reserved Font Name, and a subset is a Modified Version, so
both derivatives are renamed as the licence requires.

## Fullmoon Sans — `assets/fullmoon/font/sans-{regular,semibold,bold}.ttf`

Derived from **Pretendard** 1.309, © 2023 Kil Hyung-jin, RFN `Pretendard`.
<https://github.com/orioncactus/pretendard> — licence in `OFL-Pretendard.txt`.

Baked by `i3/design/make-ui-font.py`: subset to Latin, punctuation, currency, arrows, box
drawing, CJK punctuation, compatibility jamo and all 11172 modern Hangul syllables; layout
tables dropped; outlines converted from CFF to `glyf`. Regular carries body, SemiBold carries
row, strong and micro, Bold carries figure.

## Fullmoon Serif — `assets/fullmoon/font/serif-{semibold,bold}.ttf`

Derived from **Hahmlet** (variable), © 2020 The Hahmlet Project Authors.
<https://github.com/hyper-type/hahmlet> — licence in `OFL-Hahmlet.txt`.

Baked by `i3/design/make-display-font.py`: `wght` instanced at 600 and 700, subset to the same
Latin and punctuation plus the 2350 syllables of KS X 1001, layout and variation tables dropped.
A syllable outside KS X 1001 falls through to the sans face listed after it in each provider.
SemiBold carries title, Bold carries display and the mark.

## One provider per GUI scale

`assets/fullmoon/font/<role>_x{2,3,4}.json` bake each role at oversample 2, 3 and 4. The game
rasterises a ttf provider once and samples its atlas with nearest filtering, so a glyph is only
pixel-exact where the oversample equals the GUI scale; `Typeset` picks the provider whose suffix
is the window's scale.

Only the providers of the scale the window is at are opened when the game loads its resources.
The other two scales' providers are opened when the window first moves to that scale (resizing it,
changing the GUI scale option, toggling fullscreen), on a worker thread; see
`dev.fullmoon.client.text.fonts` and the mixins under `dev.fullmoon.client.mixin`. Providers that
name the same file share one native buffer, and the game's per-glyph warm-up of these font sets is
skipped (the glyphs are loaded as they are drawn). `-Dfullmoon.fonts.eager=true` restores the
game's own loading.

## Why the sans outlines are converted

`TrueTypeGlyphProviderDefinition` asks FreeType for `FT_Get_Font_Format` and refuses anything
that does not answer `TrueType`. Pretendard is OpenType/CFF, which answers `CFF`; Hahmlet is
TrueType already. `i3/design/ftcheck.sh` runs that same check against the shipped files.
