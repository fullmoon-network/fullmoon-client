/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V4 */

# P11 Hallmark anti-slop audit

The six-axis critique covers the server-owned native menu surface (`ServerMenuScreen`) that
replaces the vanilla ChestGUI for Fullmoon clients. Philosophy is 5 because the screen renders
only what the server's `menu_open` snapshot carries — items, actions, facts and revision — and
answers every click by asking the server again; hierarchy is 5 because header, action deck,
context rail and footer read as four distinct planes at a glance; execution is 5 because the
committed frames come from one live Paper session over the real `fullmoon:v1` channel at two
GUI resolutions; specificity is 5 because each casino game wears a hand-drawn pixel mark named by
the server's icon id — a stamped coin, a die, a wheel — and the copy is the server's menu text
with only its ChestGUI decoration stripped;
restraint is 5 because the surface adds no animation, gradient, toast or optimistic copy — the
footer says `서버 승인 후 반영` and means it; variety is 4 because it deliberately reuses the
established `SurfaceScreen` chrome and token set rather than inventing a second design language.

Audit basis: the [Hallmark 58-gate slop test](https://raw.githubusercontent.com/Nutlope/hallmark/main/skills/hallmark/references/slop-test.md).
The scope is the native menu surface visible in `p11-menu-open-960x540.png` and its companion
frames. This is an in-game Fabric screen, not a web page, so every browser-, phone- and
page-structure gate is N/A. Every applicable answer is **No**.

| Gate | Answer | Observed or inspected evidence |
| --- | --- | --- |
| 1 | No | Pretendard and Noto Serif CJK are self-hosted as `fullmoon:*` font providers; no vanilla bitmap UI text on the surface. |
| 2 | No | No gradient text and no indigo-violet wash; the panel is warm near-black with one gold accent. |
| 3 | No | The action deck is a server-driven grid whose tiles carry distinct bespoke marks and server copy; the context rail beside it breaks any card-grid symmetry. |
| 4 | No | One panel with a hairline border and a sunken context well; no card nested inside a card. |
| 5 | No | Hover and focus use an accent border plus wash on the tile ground; no coloured side-stripe. |
| 6 | N/A | The surface has no hero. |
| 7 | No | The neutral ramp is warm chromatic (`SURFACE_BASE` #1B1914 family), not aurora blobs. |
| 8 | No | A server-authoritative menu instrument; no marketing template or landing structure. |
| 9 | N/A | There is no sequence of page sections. |
| 10 | No | There is no CSS and no transition; state changes redraw immediately. |
| 11 | No | Hover changes ground and line colour; nothing scales. |
| 12 | N/A | Nothing animates; there is no easing to overshoot. |
| 13 | No | A tile state swaps ground, line and ring values; no target stacks transform, shadow and colour effects. |
| 14 | No | No layout animation; the revision swap redraws the deck in place. |
| 15 | No | The surface's keyboard ring is drawn immediately on Tab focus, orthogonal to hover. |
| 16 | No | No toast anywhere; the footer status line changes copy and ink between `서버 응답 대기` and `서버 승인 후 반영`. |
| 17 | No | Hover detail lives in the persistent context rail, drawn the same frame as the hover; it is not a delayed floating tooltip. |
| 18 | N/A | There is no carousel or rotating statistic. |
| 19 | No | Every label and fact is the server's own menu text; no fictional user or company appears. |
| 20 | N/A | No stylesheet; the design tokens live in `Tokens.java`. |
| 21 | No | A menu instrument, not a catalogue or portfolio layout. |
| 22 | No | The panel ramp is warm near-black, not a zero-chroma grey ramp. |
| 23 | No | Gold is limited to the eyebrow, the selection border and the action hint; the frame is dominated by the dark panel and the live world behind it. |
| 24 | No | All spacing comes from `Tokens.Space`; the layout test gates the deck arithmetic. |
| 25 | No | `p11-menu-compact-640x360.png` keeps header, deck, rail and footer inside the frame at 640×360 GUI px. |
| 26 | No | `ServerMenuTile` draws rest, hover, focus-visible, active and loading from one `State` switch; the in-flight frame shows the deck busy while the request is out. |
| 27 | N/A | The surface has no motion to reduce. |
| 28 | N/A | There is no video. |
| 29 | N/A | The blurred stratum is Minecraft's own background blur over the live world; no aurora or mesh gradient. |
| 30 | No | The six casino games draw as hand-drawn 14×14 pixel sprites — outlined and shaded the way the game's own item art is — each seated on a chip disc: a stamped coin, a five-pip die, a pocketed wheel, triple seven, the moon with coins streaking past, a stack of three chips. Named by the server's optional icon id; unknown ids fall back to real item renders. No emoji. |
| 31 | N/A | No generated illustration or Lottie asset. |
| 32 | No | An immutable, revision-stamped server snapshot rendered as a native screen is specific to this product's bridge contract. |
| 33 | N/A | No decorative SVG figure. |
| 34 | N/A | Not a browser; the two GUI resolutions are captured instead and both contain the surface. |
| 35 | No | Hairlines state the header, footer and context boundaries; nothing is underlined decoratively. |
| 36 | No | The deck count and item counts set through `tabularRight`; digits cannot jitter. |
| 37 | No | One interface family plus the serif display face, the same pair every other surface uses. |
| 38 | No | The display role is spent on the server's menu title only. |
| 38a | No | No display copy is italic. |
| 39 | N/A | The surface has no text input, textarea or select. |
| 40 | No | The tertiary ink on the panel meets the 4.5:1 floor enforced by `i3/design/generate.mjs`. |
| 41 | No | Primary, secondary and tertiary inks remain legible on the panel ground in every committed frame. |
| 42 | N/A | No browser navigation. |
| 43 | No | The footer keeps `Tab 이동 · Enter 실행 · Esc 닫기` visible at every state, so the way out is always on screen. |
| 44 | N/A | There is no hero or fold. |
| 45 | No | No centred rules or decorative grids; every line bounds a region the surface owns. |
| 46 | No | The surface never invents data: an empty deck says `실행할 수 있는 항목이 없습니다.`, the footer reports the wait, and a click only marks tiles busy until the server's next snapshot arrives. |
| 47 | No | The frames are direct framebuffer captures off a headless X display, taken by `tools/capture.py` from a live session. |
| 48 | No | Every colour on the surface is a `Tokens.Color` reference; the verify-tokens gate scans the tree. |
| 49 | No | Korean copy is clipped by measured `pushClip` regions; nothing overprints a neighbour at either resolution. |
| 50 | N/A | No phone layout. |
| 51 | N/A | No display headline whose wrapping must be controlled. |
| 52 | N/A | No themed marketing heading. |
| 53 | N/A | No radio-input navigation. |
| 54 | No | The eyebrow appears once; the title is the server's menu name, not a repeated slogan. |
| 55 | No | Uppercase is confined to the `FULLMOON / SERVER MENU` brand eyebrow; Korean copy does not shout. |
| 56 | N/A | No sticky CTA; the one primary action is the server's own item. |
| 57 | N/A | No approved studied-DNA diagnosis was replaced with a catalogue theme. |

## Visual and runtime evidence

- `p11-menu-open-960x540.png` shows the casino menu opened by the live channel: eyebrow above the
  serif title with no collision, the two-column deck, the context rail naming the first actionable
  item, and the footer hints.
- `p11-menu-hover-960x540.png` moves the pointer onto `동전`; the tile takes the accent border and
  wash and the context rail follows the hover without choosing.
- `p11-menu-refreshed-960x540.png` is the same session after one click: the client sent
  `menu_action` slot 19, the server answered with revision 1, and the rail now reads the fresh
  `승률 · 50.0%` / `배당 · 1.98x` facts.
- `p11-menu-closed-960x540.png` shows the world after `menu_close`; the surface leaves nothing on
  screen.
- `p11-menu-compact-640x360.png` repeats the open frame at 640×360 GUI px with every region inside
  the panel.
- `p11-live-client.log` carries hello, welcome, `menu_open` revision 0, the slot-19 action,
  `menu_open` revision 1 and `menu_close` for the same menu id; `p11-live-server.log` pairs the
  session's join and `handshake ok` lines.

Two defects were found only in the rendered frames and fixed before this audit was committed. The
eyebrow and the display title were drawn into the same band — the P0 class of bug where a large
face draws above its origin — so the title's capitals overprinted the eyebrow; the title now sits
centred in the band below the eyebrow line via `Typeset.centred`. And the context rail's clip began
at the icon top while the title's cap band begins three pixels above it, slicing the tops of the
glyphs into a strikethrough; the clip now starts at `Typeset.capTop` of the title. A review of the
finished tree added two more guards the frames could not show: the icon well scales with its card
so a dense deck never overprints its neighbours, and `ServerMenuLayout` shrinks the deck gap —
clamped at zero — whenever a capped frame leaves the deck shorter than its ideal, so every card
keeps at least one clickable pixel; both are gated by `ServerMenuLayoutTest`.

Result: **PASS — 58 gates answered; no applicable gate answered Yes.**
