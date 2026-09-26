# Evidence

Captures are taken off the workspace, on a headless X server, from the dev client built out of
this tree. Nothing here is a mockup.

```sh
tools/capture.py /tmp/shots --geometry 1920x1080 --scale 2 kit:F7 focus-1:Tab focus-2:Tab
```

Each `NAME:KEYS` taps its keys and photographs the result, cumulatively on one client: a
traversal capture only means anything if it is the same surface at every stop, so the screen is
never reopened between shots and the pointer is parked in a corner where no control is. The P0
files predate the tool and were taken by hand with the same Xvfb, the same `import` off the root
window, and XTEST for the keys.

Geometry is per capture and the filename carries it: `-640x360` is a 1280×720 window, `-960x540`
a 1920×1080 one and `-960x600` a 1920×1200 one, all at GUI scale 2, so every layout number in the
mod is half the pixel position in any of them. It became an argument in P1-C, when the kit outgrew
360 GUI px, and grew again in P1-D: the list page puts a sweep, a readout and a five-row well in
one column, and the well's floor lands at 546 where `footerY(540)` is 505. It sizes the Xvfb and
the client window off the one number, because a client smaller than its display is a photograph
with a mat around it.

## P0 — render layer and design specimen

| file | what it settles |
| --- | --- |
| `p0-specimen-640x360.png` | The specimen opens from the title screen and draws: five type roles, six shapes off one SDF pipeline, nineteen colour tokens, the figure pair. |
| `p0-specimen-type-roll-2x.png` | All three baked faces rasterise — Noto Serif CJK KR for `display`, Pretendard for the rest — with real space advances and no vanilla bitmap glyphs anywhere. |
| `p0-specimen-figures-2.5x.png` | The tabular column sets `002273` 3 px wider than the proportional one: digits are on the widest-digit cell, centred in it. |

Two defects these captures caught and the fix for each:

- Every Latin glyph and digit came from vanilla's `ascii.png`, and spaces collapsed to a
  ~0 advance, because the five font jsons declared `minecraft:include/default` first and
  `FontSet` takes the first provider that answers. `FontManager.getFontSetRaw` falls back
  silently, so there is no log line for this — only the render shows it. The intended face is
  now declared first and `include/default` is gone.
- The `display` sample lost the top-left serif of its `F` and floated above its own row,
  because the row clipped vertically and the sample was placed by its draw origin. The game
  hangs every provider off one 9 px line box, so a 22 px face draws ~8 px above its origin:
  placement now goes through `Typeset.centred`, and only the column is clipped.

## P1-B — surface event routing, button and switch

| file | what it settles |
| --- | --- |
| `p1b-kit-640x360.png` | Thirty-two matrix cells and the live band in one frame, through one `draw(Painter, State)`: a cell that lied about a state would have to lie in the band too. Eight states are eight states, not eight screenshots of the one the mouse was in. |
| `p1b-kit-traversal-band.png` | Four Tab stops, no mouse anywhere. The ring is up at every stop, the order is the registration order, and the fourth wraps to the first. The footer readout is the surface naming its own keyboard holder, not my reading of the ring. |
| `p1b-switch-in-flight-2x.png` | `loading` against `disabled` at 2×, all four rows. The button trades its label for three dots; the switch parks its knob mid-track. |

Three defects this slice caught:

- The masthead's accent bar sat under the wordmark instead of beside it, and the section heads'
  ticks were a pixel low for the same reason: both were sized against the role's nominal box
  while the baseline is 7 px below the draw origin whatever the face is. `Typeset.capTop` and
  `capHeight` now name the band a role's capitals actually occupy, and the rules are measured off
  them. The committed P0 specimen shows the same bug, because that screen carried its own copy of
  the masthead; `p1d-specimen-960x600.png` is the same screen on the shared chrome with the bar
  where it belongs.
- A control with a request in flight still fired on Enter and still took typing, because
  `Surface` gated the pointer on `live()` and the keyboard on nothing. Tab deliberately stays
  outside the gate: a request that leaves the player unable to leave the control they fired it
  from has taken the surface away, not just the control.
- Tabbing to a button and then moving the mouse over it made the focus ring vanish, because the
  ring was drawn only for `FOCUS_VISIBLE` and a state can only name the loudest thing true about
  a control. The ring is now an orthogonal surface-owned bit, so a hovered or in-flight control
  keeps it.

## P1-C — slider, select and text field

| file | what it settles |
| --- | --- |
| `p1c-kit-960x540.png` | Seven widget rows against eight states, fifty-six cells, and a live band of five controls under them — one frame, one `draw(Painter, State)`. The slider, the select and the text field are drawn by the same call the button and the switch are. |
| `p1c-select-open-960x540.png` | The open list paints over the slider line and the button line below it and is clickable there. Drawing in front and being hit first are one fact, and neither of them is Tab order: the select is the second control the keyboard reaches, the slider under its list the third. |
| `p1c-band-in-flight-960x540.png` | 적용 pressed by keyboard, and every control in the band is in flight at once. The field keeps what the player typed and takes dots beside it, the select trades its value for dots, the slider holds its knob and drops its readout, and 적용 keeps its focus ring while it stops answering — 취소 stays live, because a request the player cannot get out of has taken the surface away. |
| `p1c-field-caret-2x.png` | The caret is the band a capital occupies plus a hairline above and below, so it ends flush with nothing. It is up because the keyboard is in this field, not because the state reads `focus`. |
| `p1c-field-error-2x.png` | One typed space fails the field's rule and the state under it becomes `error` with the caret still live: a field that does not validate is still a field being typed in. |
| `p1c-field-row-1.5x.png` | Eight states of the text field, side by side. `loading` keeps the text and adds the dots, and the caret is in the two focus columns and nowhere else. |

Three defects this slice caught:

- A text field nobody was typing in scrolled its own text out of view. `view()` keeps the caret
  in sight, `loading` narrows the area to make room for the dots, and a blurred field still has a
  caret index sitting at the end of its text — so the `loading` cell read `빛 •••` with the
  `달` shifted off the left edge. What a field nobody is typing in has to show is the head of its
  text: the scroll is now `typing ? view(area) : 0`. Those 177 pixels are the only difference
  between the pre-fix and post-fix runs of all five captures, which is also the rig repeating
  itself to the pixel.
- Paint order and hit order were two different orders. `Surface.at` walked the registration list
  once, back to front, so the slider registered after the select won every click inside the open
  list while the list painted over it. Widgets that are over the surface now get a pass of their
  own on the way in, and Tab order is left out of it. Flipping `Select.overlaying()` back to
  `false` fails `anOpenListIsHitBeforeWhateverItCovers`, so that test is not green by accident.
- The kit did not fit its own capture, and the first 1920×1080 attempt came back letterboxed:
  a 1280×720 client on a black mat, still 640×360 GUI px, footer text over the last matrix row.
  `--geometry` sized the Xvfb, but the client window came from a hard-coded
  `programArgs("--width", "1280", ...)` in `build.gradle.kts`. The size is now `client_width` and
  `client_height` in `gradle.properties`, and the rig passes both.

## P1-D — list, tab rail, tooltip and the dev surface chrome

| file | what it settles |
| --- | --- |
| `p1d-list-960x600.png` | Eight states against chosen and unchosen, sixteen row cells, and no two of them alike. Selection outlives all eight, so a row cannot use a state to say it: the wide accent tick does, and the sweep is where that claim is either kept or broken. |
| `p1d-rail-mark-4x.png` | The rail's mark, in the only two frames that can show it. Top, the mark is on the tab already open and draws nothing — rehearsing an underline that is already there would be a lie. Bottom, one Left later: a strong line under 위젯 at the accent underline's own y, a hairline shy of its weight, 위젯's label brightened, 목록 still underlined and still the page on screen. |
| `p1d-well-960x600.png` | The keyboard in the well, the mark on its first row, and nothing chosen: the readout reads 고른 항목 없음 and 값 복사 stays off. Walking a list is not choosing from it. The hint has flipped above the well, because below it is the footer. |
| `p1d-list-chosen-and-marked-960x600.png` | The chosen row and the marked row in one frame, four rows apart, after the view scrolled to keep the mark in sight. Wash ground and a two-pixel tick against raised ground and a hairline one — the pair the sweep enumerates, here on one live list. |
| `p1d-copy-960x600.png` | A hint at the far side of the column, ending where the masthead's rule ends. The ring is on 값 복사, live now that something is chosen, and the row chosen four keys ago is still wearing its tick with the keyboard gone from the well. |
| `p1d-specimen-960x600.png` | The specimen drawing the shared masthead: accent bar at GUI x 220..222, y 14..31, the same two pixels on the same cap band as every other page. The P0 capture of this screen still shows the bar under the wordmark; this is the P1-B fix reaching it. |

Four defects these captures caught:

- The hint on 값 복사 ran from GUI x 689 to 862 with the page's column ending at 740, so a third of
  it hung over the blurred void outside the surface. `Tooltip.place` was clamping to the window,
  which is the one rectangle that is never the ground being drawn on. It now takes the region the
  surface owns.
- The well's hint sat at GUI y 548..570 with `footerY(600)` at 565, crossing the footer hairline and
  crowding the line that tells the player how to leave. Same root cause, same fix: with the column
  as the region there is no room below the well, so the hint flips above it and covers the section
  head of the list it belongs to — which is the group it is describing, and transient.
- The masthead moved when the tab changed. `SpecimenScreen.maxContent()` returns 420, and the
  chrome was measured with it, so the wordmark, the rail and the footer rule all sat at GUI x 270
  on the specimen and 220 on the other two pages: tabbing 표본 ↔ 위젯 slid the whole frame 50 px
  sideways and read as the screen having reloaded. `maxContent()` now governs the page's own body
  only. The body keeps its measure and gives up its right edge instead of the left one every other
  thing on the surface is aligned to.
- Then the fix for the first two put every hint 4 px off that same left edge. Most controls on a
  page sit on the column's left edge, and clamping to `within.x() + GAP` pushed their hints inward
  to buy room against nothing — the rail's hint came away from the line the section head ticks and
  the masthead bar are on, and covered the tick beside it in the bargain. Caught by diffing the
  run against the previous one: the only pixels that moved were hints, all of them by four. The
  sides of a region are edges to align to, not walls to stand off; the gap now belongs to the
  control and to the two rules that close the column above and below.

## P2 — in-game surfaces

| file | what it settles |
| --- | --- |
| `p2-settings-960x540.png` | Settings screen opened via F9: 7 Minecraft options, search bar, toggle switch on detail pane, unified masthead with connection status dot, footer rules. |
| `p2-keybinds-960x540.png` | Key bindings surface: 64 bindings in scrollable well, category grouping, bound key readout, conflict detection with warning indicators, rebind and reset actions. |
| `p2-mods-960x540.png` | Active mods browser: 51 loaded Fabric mods with versions, author credits, descriptions, and environment flags. |
| `p2-account-960x540.png` | Player identity and network session: profile username, UUID, offline/online auth badge, connection status, and clipboard copy actions. |

Two defects these captures caught:

- The connection status string used separate unlocalized keys across screens (`fullmoon.keybinds.server.disconnected`, etc.), leaving raw resource keys in the masthead. Unified into `HubChrome.connection` using the canonical localized string.
- Keybinding category headers rendered raw Identifier paths (e.g. `minecraft:gameplay`). Added `HubChrome.categoryLabel` to resolve against vanilla language keys (`key.category.minecraft.gameplay` → `게임플레이`).

## P3 — HUD and its editor

| file | what it settles |
| --- | --- |
| `p3-hud-editor-960x540.png` | In-game HUD editor opened via F10: 4px grid snapping, gold L-shaped corner ticks on the active selection (Coordinates), FPS, Ping (live dot), Clock, Keystrokes chips, top toolbar with enable toggle and action buttons. |

One defect this capture caught:

- `ClockHud`, `CoordinatesHud`, and `FpsHud` measured only their value strings without accounting for the leading key tag ("TIME", "XYZ", "FPS"), causing the right edge of wider numerals to clip against the chip border. Updated `measureWidth` to measure the compound label + value footprint.



## P6 — versioned Paper channel and legacy fallback

| file | what it settles |
| --- | --- |
| `p6-live-channel-960x540.png` | A real protocol-1 Paper session completed hello/welcome, rendered measured `TPS 20.0 · 2.2 ms`, and displayed the Korean two-line server notice over the live world. |
| `p6-live-channel-320x180.png` | The same event at 320×180 GUI pixels remains within the viewport; title and body retain their hierarchy without an oversized panel. |
| `p6-legacy-fallback-960x540.png` | A protocol-0 bridge deliberately stayed quiet; after five seconds the mod retained vanilla play, omitted the notice, and rendered unsupported metrics as `TPS —`. |
| `p6-live-client.log` / `p6-live-server.log` | Matching hello, welcome, first HUD revision, and notice identifiers prove the server-to-client round trip. |
| `p6-legacy-client.log` / `p6-legacy-server.log` | The server records the version mismatch and the client records its five-second fallback. |
| `p6-hallmark-audit.md` | The six-axis critique and all 58 Hallmark anti-slop gates, with native-GUI scope called out explicitly. |

The Paper fixture was compiled in a temporary directory and installed only for this run. After the
captures, the original `FullmoonBridge.jar` was restored byte-for-byte and the local server was
stopped. The fixture held the overworld at dusk tick 13000 with clear weather for every committed
P6 frame. These images are direct client captures, not mockups.

Two visual decisions came from the rendered frames:

- The server event is a flat, top-centred strip with one two-pixel severity rule. It uses no icon,
  gradient, blur bloom, animation, or redundant action. The world remains the visual subject.
- Missing support never masquerades as healthy data. The legacy frame visibly says `TPS —` rather
  than keeping the editor's sample or inventing a nominal 20 TPS reading.

## P7 — native server route ledger

| file | what it settles |
| --- | --- |
| `p7-warp-selected-960x540.png` | A real six-route Paper snapshot with `palace_gate` selected. All six rows remain visible, while the detail plane shows only server-published group, ID, world, coordinates, and live distance. |
| `p7-warp-pending-960x540.png` | The ID-only request is in flight. The one action stops answering, retains its keyboard ring, and reports server-waiting state without animation or optimistic teleport copy. |
| `p7-warp-accepted-960x540.png` | Paper accepted `palace_gate`, the client received the matching result, the distance changed from 83 m to 1 m, and the footer reports the accepted decision. |
| `p7-warp-compact-640x360.png` | All routes, facts, the action, and keyboard footer remain contained at 640×360 GUI px. |
| `p7-live-client.log` / `p7-live-server.log` | Matching hello, welcome, `screen_open`, `tp_request palace_gate`, and accepted server teleport. |
| `p7-hallmark-audit.md` | The fresh six-axis critique and all 58 Hallmark gates for the route surface. |

The Paper fixture was a temporary copy of `FullmoonBridgePlugin` that called the plugin's existing
`openWarpScreen` API one second after handshake and delayed the production request handler by 80
ticks so the in-flight state could be photographed. It held only the overworld at dusk tick 13000
with clear weather. The actual permission, cooldown, world, chunk warm-up, teleport, and result
handler remained the production code path. After capture, the installed bridge was restored to
SHA-256 `db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c` and Paper was stopped.

The production plugin currently exposes `openWarpScreen` but does not call it from `/워프` or
another shipped command. P7 changes only the client, so automatic production invocation remains a
server-side integration task rather than an unverified client claim.

One defect was found only in the rendered selection flow. `ListPanel` restored its view with the
selected row at the top even when all six rows fit, so selecting the third route hid the first two
without a scrollbar while the heading still said six. The draw pass now bounds the restored first
row against the actual viewport. A regression test covers both all-rows-fit and scrolled cases,
and the final selected capture visibly contains all six routes.

## P8 — in-game map

| file | what it settles |
| --- | --- |
| `p8-map-960x540.png` | A live Paper session at `칸당 2블록`. The plane carries loaded terrain and unmapped corners in the same frame, the footer reports `불러온 지형 93%`, and all six published routes are listed in the rail with five of them marked in view. |
| `p8-map-survey-960x540.png` | The same session two scales out at `칸당 8블록`. The footer drops to `불러온 지형 8%` and the 92% the client has never loaded draws as empty grid, not as invented ground. Six rings remain; two labels are blanked where they would have landed on a kept one. |
| `p8-map-zoom-960x540.png` | The other half of the zoom pair. The pointer sat at GUI (337, 295) — cell (104, 74) of the 226×140 raster — over world (431, 16) at `칸당 8블록`; after one wheel notch the rail reads `465 -2` at `칸당 4블록`, which puts (431, 16) back under the same cell. |
| `p8-map-route-960x540.png` | Clicking the third rail row centres the plane on it: `동쪽 달빛 게이트` wears the accent wash and bar, and `중심` becomes `564 0` — the coordinates the row itself publishes. No teleport is requested; the map has no such authority. |
| `p8-map-compact-640x360.png` | The compact layout at 640×360 GUI px. The rail fits four of the six rows and says so with `목록 밖 2개`, while the plane still marks `별궁 중앙 홀`, whose row the rail dropped. |
| `p8-map-compact-survey-640x360.png` | The compact rail at `칸당 8블록`: `불러온 지형 20%`, six rings, four labels, and the overflow line still accounting for the two rows it cannot show. |
| `p8-live-client.log` / `p8-live-server.log` | Both sessions' hello, welcome, and `Map open:` provenance against Paper's own handshake and waypoint count. |
| `p8-hallmark-audit.md` | The fresh six-axis critique and all 58 Hallmark gates for the map. |

No fixture was installed for P8 and none was owed. `welcome` already carries the waypoint snapshot,
so the shipped `FullmoonBridge.jar` publishes the six routes on handshake with no test-only code in
the path — the reason the two log files pair on `6 waypoint(s)` and nothing else. Nothing was
restored afterwards because nothing was replaced; Paper was stopped once both sets were taken.

The pairing is tighter than the handshake. Paper logs where it places a joining player, the map
centres on the player when it opens, and `%.0f` rounds that coordinate for the rail — so the
server's `502.5, 72.0, -16.5` is the `503 -17` the 960 frames read, and its `506.5, 72.0, -35.5`
is the `507 -36` of the compact set. The raster sizes in the same lines, 226×140 and 148×88 cells,
are the two window sizes divided by the 3 px cell.

Two defects and one legibility failure came out of these captures:

- Labels collided at coarse scales. At `칸당 8블록` the six routes crowd into a few dozen pixels and
  three names overprinted into an unreadable smear. The rule now lives in pure `MapMarkers.declutter`
  with the measurement injected as a `LabelBox`, because `Typeset.width` needs a running client and a
  collision rule inside `MapCanvas` could not be tested. Ledger order decides who keeps the name, so
  the same labels drop every frame instead of flickering; the ring and dot stay on every marker, so a
  coarse scale costs names, never destinations. A regression test covers it.
- The rail truncated the route list silently. It listed as many rows as fit and said nothing about
  the rest while the heading still counted six. It now prints `목록 밖 2개`, visible in both compact
  frames and correctly absent from the 960 frames where all six fit.
- Names were unreadable over bright terrain. `만월궁 정문` sits on the palace wall and the gate labels
  sit on the plaza, both near-white, and light ink on them was a guess at best. Each label now draws
  its own plate at the same box the collision test uses, so a name is legible over any block the map
  can render. This is why the committed frames are the third capture of this phase, not the first.

## P9 — warp from the map

| file | what it settles |
| --- | --- |
| `p9-map-hint-960x540.png` | The pointer at GUI (362, 455), the cell `MapViewport.project` puts `별궁 중앙 홀` in when the plane is centred on `만월궁 정문` at `칸당 2블록`. The hint names the marker and its coordinates — `별궁 중앙 홀 · X 500 Z 16` — while the chosen route is still `만월궁 정문`, so hovering reads and does not choose. |
| `p9-map-chosen-960x540.png` | The same pointer, one click later. `별궁 중앙 홀` is now chosen in the band and lit in the rail, and `중심` still reads `500 -100`: a marker click chooses where the marker stands, so the plane does not move out from under the cursor. A rail row still centres, which is why both behaviours needed separate frames. |
| `p9-map-accepted-960x540.png` | `이동 승인됨` in the live ink after one confirm action, with the player mark now on the aux_palace marker at (362, 455). The banner and the teleport are the same event, not two. |
| `p9-map-denied-960x540.png` | The shipped plugin's `COOLDOWN_MS = 4000` refusing a second request three seconds after the first: `요청 거절 · 재사용 대기 중` in the danger ink, which is `WarpRoutes.reasonKey("cooldown")` resolved through `fullmoon.warp.reason.*`. `이동 요청` stays live underneath, because a cooldown is a wait and not a wall. |
| `p9-map-inflight-960x540.png` | The window between the request and the answer: `서버 응답 대기 중` in the warn ink with the button in its loading state and the chosen route unchanged. Reachable only with the fixture disclosed below — on loopback the real round trip is shorter than the rig's shutter. |
| `p9-map-inflight-resolved-960x540.png` | The same session four seconds later: `이동 승인됨`, the button live again, the player mark on the marker. The pair is what shows the fixture delayed the answer without choosing it. |
| `p9-map-compact-640x360.png` | The compact layout, where the arithmetic is different and the contract is not: raster origin (12, 60), 148×88 cells, `만월궁 대전` at GUI (233, 131) and its hint reading `만월궁 대전 · X 500 Z -140`. Two rail rows fit, `목록 밖 4개` accounts for the rest, and the band and the live `이동 요청` are uncropped. |
| `p9-map-compact-accepted-640x360.png` | The same route accepted after a bare `Return` with the pointer on the marker and nothing holding the keyboard — the keyboard road for a player whose hand is on the mouse — with the player mark landing on (233, 131). |
| `p9-live-client.log` / `p9-live-server.log` | All three sessions' `Map open:`, `Map route chosen:`, `Sent … warp request` and `Received … warp result` lines against Paper's own `warp <player> -> <id>` and `warp denied for <player>: cooldown` for the same ids at the same seconds. |
| `p9-hallmark-audit.md` | The fresh six-axis critique and all 58 Hallmark gates, with 15, 17 and 46 answered anew for a map that now carries a focusable button, a tooltip, and a teleport request. |

One fixture was installed, for the in-flight pair only. It was built from the shipped bridge source
with a single insertion — `handleTpRequest`'s body renamed `handleTpRequestNow`, a
`runTaskLater(..., 80L)` in front of it — so unknown id, permission, cooldown, world, chunk warm-up,
teleport and result all remain the production path and only the start is 4 s late, inside the client's
5 s timeout. Its jar was SHA-256
`1d8a2e6e66341836c56a8fe2ec659c03314151071cb1d7005a808cb83567c376`. The other six frames ran the
shipped jar, and afterwards the installed bridge was restored to
`db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c`, the value P6 and P7 also recorded
restoring to. Paper was stopped over RCON with no JVM left on `:25566` or `:25577`. Nothing was
installed on the production Oracle host.

The pairing is on the route id and the answer, not just the handshake. The client logs the id it sent
and the verdict it received; Paper logs `warp Player475 -> aux_palace` for the accept and
`warp denied for Player475: cooldown` for the refusal three seconds later, so the accepted and denied
frames are two halves of one exchange rather than two moods of one screen. The join lines in the same
file are why every run clicks rail row 0 before anything else: Paper placed the three players at
`500.5 -35.5`, `501.5 -35.5` and `495.5 -20.5`, and a marker pixel measured from a drifting spawn is
not a number a reader can redo. Centred on `palace_gate`, it is.

These captures corrected nothing. Every frame the phase owed came out right on the first attempt of
all three sessions, which is worth stating plainly rather than dressing a clean run as a discovery.
What they carry that the tests cannot is the hint text over a live raster, the plane holding still
under a marker click, the loading and resolved states of one button, and a teleport that visibly moved
the player.

## P9-R — the MapLayout extraction, held against P9's own frames

`MapScreen` shipped in P9 with the sentence "not gated and has no unit test" attached to it. Its
layout arithmetic and its hit test are pure and now live in `MapLayout`, gated at 100% of 44 lines
and 14 branches. A refactor that keeps a screen's behaviour has to be shown keeping it, so these
three frames are the P9 baselines retaken on the extracted code.

| file | what it settles |
| --- | --- |
| `p9r-map-hint-960x540.png` | The same session shape as `p9-map-hint-960x540.png`: rail row 0 clicked, then the pointer parked at GUI (362, 455). Rail rows, `중심 500 -100`, `축척 칸당 2블록`, `항로 6곳`, the hint plate `별궁 중앙 홀 · X 500 Z 16`, the marker plates and the footer legend all land where the baseline has them. `MapLayout.cellAt` reaches the marker `MapScreen`'s deleted copy reached. |
| `p9r-map-chosen-960x540.png` | One click later, `별궁 중앙 홀` chosen in the band and lit in the rail with `중심` still `500 -100` — P9's sharpest frame, redrawn from `MapLayout.plot` instead of `MapCanvas.plot`. |
| `p9r-map-compact-640x360.png` | The compact branch of `MapLayout.of`, which is the one worth a second frame: edge `Tokens.Space.LOOSE`, raster origin (12, 60), 148×88 cells, `routeCapacity` 2, so two rail rows and `목록 밖 4개`, band and live `이동 요청` uncropped. |
| `p9r-live-client.log` / `p9r-live-server.log` | `Map open: 226x140 cells` and `Map open: 148x88 cells` — the same two rasters P8 and P9 logged — against Paper's handshake and `6 waypoint(s)` for both sessions. |

No fixture was installed and nothing was owed one. Both sessions ran the shipped
`FullmoonBridge.jar` at `db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c`, the
value P9 recorded restoring to, so there was nothing to restore afterwards. Neither session asks for
a teleport: the extraction touched where a marker is, not what happens when one is confirmed, and
P9's four warp-state frames still carry that. Nothing was installed on the production Oracle host.

The frames differ from the baselines in two places, both session facts rather than layout. The
footer reads `불러온 지형 81%` against the baseline's `89%`, because chunk load is a race with the
rig's shutter; and the player mark sits where Paper placed this run — `502.5, 72.0, -15.5` and
`503.5, 72.0, -16.5` — not where it placed the P9 runs. The compact frame also has `만월궁 정문`
chosen where the baseline has `만월궁 대전`: this session clicked rail row 0 and then only hovered
the 대전 marker, which is the hint-without-choosing half of the contract at the compact size.

## P10 — one HUD layout shared by launcher and client

| file | what it settles |
| --- | --- |
| `p10-launcher-hud-editor.png` | The real Chromium-rendered launcher at 1280×820: the HUD tab is discoverable in settings, the 640×360 client plane, all eight element rows, selected coordinate chip, anchor inspector and offset controls are visible together. |
| `p10-launcher-browser.log` | DOM probes at the Tauri minimum, default and wide window sizes: no horizontal overflow, eight rows, zero console errors, reset with zero toasts, and a focused row moved from `(16,56)` to `(20,56)` by `ArrowRight`. |
| `p10-hud-boot-640x360.png` | The live Fabric client before an external edit, with the default edge-relative layout over the world. |
| `p10-hud-xyz-moved-640x360.png` | The same client after the shared file moved coordinates to `BOTTOM_LEFT (40,44)` without a restart. |
| `p10-hud-fps-moved-640x360.png` | A second external edit in the same session moved FPS to `TOP_CENTER (31,16)`. |
| `p10-hud-carried-960x540.png` | The edited layout carried to a 960×540 GUI frame; edge anchors, not launcher-stage percentages, determine the new positions. |
| `p10-hud-tps-off-960x540.png` | The same running client adopted an external `enabled: false` for the server-tick element. |
| `p10-hud-time-moved-960x540.png` | A further external edit moved the clock to `TOP_RIGHT (63,104)` without relaunching the client. |
| `p10-live-client-640x360.log` / `p10-live-client-960x540.log` | Four `Adopted hud.json edited outside the game` lines land between the corresponding before and after frames. |
| `p10-final-hud.json` | The exact complete eight-element file left by the live run, including disabled elements and scale values the renderer currently preserves but does not consume. |
| `p10-hallmark-audit.md` | The six-axis critique and all 58 Hallmark gates for the HUD editor and the shell visible around it. |

The browser surface uses Vite's mock core, so it settles rendering, focus, responsive layout and
feedback rather than Tauri IPC. The live Fabric sessions settle the disk seam: the launcher and client
use one json shape, and the client adopts edits while running. The two evidence sets are intentionally
separate rather than pretending a mock DOM call proves a native file write.

Three defects were found by driving and looking at the launcher rather than by compiling it:

- The fixed atmospheric backdrop painted above ordinary content, leaving settings copy invisible.
  The application now establishes an isolated stacking context and places the backdrop behind it.
- Clicking a ledger row selected the element but left arrow-key nudging unreachable because the key
  handler lived only on the stage node. Rows now take focus and dispatch the same immutable nudge.
- At the declared 1040×680 minimum window, the two-column editor crushed the stage to 254×143 and
  wrapped its dimension label vertically. It now stacks below 1180 px and renders a 510×287 stage.

## P11 — server-owned native menus

| file | what it settles |
| --- | --- |
| `p11-menu-open-960x540.png` | A live coin-bridge session opened the casino menu over `fullmoon:v1`: brand eyebrow above the serif title, a two-column action deck where each game wears its own outlined pixel sprite on a chip disc — stamped coin, five-pip die, pocketed wheel, triple seven, the moon with coins streaking past, a three-chip stack — the context rail naming the first actionable item, and the keyboard footer. No ChestGUI and no raw block sprite anywhere in the frame. |
| `p11-menu-hover-960x540.png` | The pointer on `동전`: accent border and wash on the tile, and the context rail follows the hover — reading, not choosing. |
| `p11-menu-refreshed-960x540.png` | One click later the client's `menu_action` slot 19 met the server's revision 1 snapshot: `동전` now chosen, `승률 · 50.0%` and `배당 · 1.98x` in the rail. The refresh is the server's answer, not a client prediction. |
| `p11-menu-closed-960x540.png` | `menu_close` returns the player to the live world; the surface leaves nothing on screen. |
| `p11-menu-compact-640x360.png` | The same menu at 640×360 GUI px: header, deck, rail and footer all inside the panel, tile copy clipped by measure rather than overprinting. |
| `p11-live-client.log` / `p11-live-server.log` | hello, welcome, `menu_open` revision 0, the slot-19 action, `menu_open` revision 1 and `menu_close` on one menu id, against the server's join and `handshake ok` lines for the same sessions. |
| `p11-hallmark-audit.md` | The six-axis critique and all 58 Hallmark gates for the native menu surface. |

The server side is coin-bridge's `NativeMenuBridge` on the `codex/native-server-menus` branch of
the server repo, with the shipped ChestGUI retained as the fallback for clients that never
complete the handshake. The capture ran the local Paper at `/home/person/mc-local` with that jar
plus a 30-line `NativeMenuAudit` fixture whose only act is running `/casino` once after each
join, so the menu the frames show is the production menu code path. Paper was stopped over RCON
after the captures; nothing was installed on the production Oracle host.

The marks are not the server's block sprites. A raw item render says "block", not "game", so the
snapshot carries an optional `icon` id — the casino lobby names
`fullmoon.casino.{coinflip,dice,roulette,slots,moonfall,jackpot}` — and the client draws each as
a hand-drawn 14×14 pixel sprite, outlined and shaded like the game's own item art, seated on a
chip disc so the deck reads as a table. Unknown ids fall back to the item render. The field is
presentation only; the protocol rules in `docs/BRIDGE.md` say so.

Two defects were found only in the rendered frames of the first redesign session and corrected
before these frames were taken: the display title overprinted the brand eyebrow (the P0 class of
bug — a large face draws above its origin), fixed by centring the title in the band below the
eyebrow; and the context rail's clip sliced the cap tops of the chosen item's title into a
strikethrough, fixed by starting the clip at `Typeset.capTop` of the title. A review of the
finished tree added two more guards: the icon well now scales with its card instead of
overprinting its neighbours in dense menus, and `ServerMenuLayout` shrinks the deck gap — rather
than collapsing cards to zero — whenever a capped frame leaves the deck shorter than its ideal.
