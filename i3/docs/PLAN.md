# i3 build plan

Eleven phases. Each one ends in a commit whose evidence is a capture or a test run, not a
description. Captures come off `Xvfb :9` (see `docs/evidence/README.md`); no phase is reported
done on a plan or a green compile alone.

Two rules hold across all of them: no colour, radius, duration or motion literal outside
`design/tokens.json` (`design/verify-tokens.mjs` fails the build on one), and no vanilla chrome
— no drop-shadowed text, no re-drawn vanilla widget, no bitmap font in our stacks.

## P0 — render layer and design specimen · done

Shape `VertexFormat`, `RenderPipeline` and `shape.vsh`/`shape.fsh`; one SDF covering rect,
rounded rect, hairline rule, ring and dot. `Painter` over `GuiGraphicsExtractor` with a clip
stack and the blurred stratum. `Typeset`: five roles on five baked ttf providers, tabular
figures, baseline placement. `tokens.json` → `Tokens.java` + `tokens.css` with a contrast gate.
`SpecimenScreen` on F6, reachable from the title screen without a mixin.

Evidence: `docs/evidence/p0-*.png` — the specimen rendered at 640×360 GUI px, the type roll at
2× showing all three faces, the figure pair. Ten contrast floors PASS in `generate.mjs`.

## P1 — layout, event bus, widget kit · done

Box layout (the left spine and the master–detail split the surfaces need), focus traversal with
a visible ring, an event bus for mouse/key/scroll/char. Widgets: button, toggle, slider, select,
text field, list row, tab rail, tooltip — each with all eight states (rest, hover, active,
focus, focus-visible, disabled, loading, error).

Evidence: one state-matrix capture per widget, all eight cells in frame. JUnit over the layout
arithmetic and the focus order. A keyboard-only traversal capture: no mouse, ring visible at
every stop.

## P2 — in-game surfaces · done

Mod menu, settings with search, keybind editor with conflict detection, account surface. All on
the blurred stratum, all `Esc`/`Tab` correct, all strings in `ko_kr` and `en_us`.

Evidence: captures taken while connected to the local Paper server on `:25566`, a keybind
conflict shown resolving, and a resource-pack reload that leaves the metrics correct (the
reload listener ordering against `FONTS` is what this proves).

## P3 — HUD and its editor · done

Elements through `HudElementRegistry`: coordinates, clock, ping, server tick, effects. A drag
editor with snapping on a 4 px grid, L-shaped corner ticks, per-element toggles, anchors that
survive a resolution change. Config as json.

Evidence: HUD in world, editor with its snap guides, a config round-trip test, and a resolution
change that keeps every element on its anchor. Frame cost is quoted from the game's own
profiler output or not claimed at all.

## P4 — launcher core · done

Tauri v2 with the work in Rust: version manifest fetch, asset and library download with sha1
verification and resume, Fabric loader install, instance layout, offline account, JVM argument
composition, spawn, log streaming to the front end. The Microsoft device-code path was written
against an empty `msClientId`; `brand.json` has carried one since 2026-09-05 and the full chain was
verified against the live services on 2026-09-12 (`docs/MICROSOFT-AUTH.md`, `i3/docs/DEVLOG.md`).

Evidence: the launcher spawning a game that reaches the title screen, with the spawn command
and the client log quoted. A test that corrupts one asset's sha1 and fails the install.

## P5 — launcher UI · done

The front end on the same tokens: first run, install progress, instance list, play, settings,
error surfaces. Eight states again, and every animation on transform/opacity with a
`prefers-reduced-motion` collapse.

Evidence: per-state captures, a first-run-to-play sequence, and a reduced-motion pair.

## P6 — server channel and the slop test · done

A `fullmoon:v1` plugin channel: server-pushed HUD values and notices, versioned payloads, a
client that degrades silently on an older server. Then the 58-gate slop test read for the first
time and answered gate by gate, and the six-axis self-critique stamp.

Evidence: a packet round trip against local Paper with both logs, a gate-by-gate pass table,
the stamp.

## P7 — native server route ledger · done

Complete the remaining `fullmoon:v1` warp contract: validated waypoint snapshots, full-snapshot
replacement, ID-only requests, matched server results, timeouts, and server-directed screen open.
The screen is a master-detail route ledger over live Paper data with one request action and no
client-owned teleport authority.

Evidence: route selection, in-flight, accepted, and compact captures from a real Paper session;
matching client and server logs for `palace_gate`; reducer and payload boundary tests; a fresh
58-gate Hallmark audit.

## P8 — in-game map · done

A north-up map of the terrain the client already has, and nothing else: a column outside the
client cache draws as unmapped rather than as invented ground, and the footer says what fraction
of the frame is real. Five survey scales with cursor-anchored zoom, arrow panning, one key back
to the player. The only markers are the routes the server published over `fullmoon:v1`, filtered
to the dimension the player is standing in, and clicking one centres the map on it — the map asks
for no teleport of its own. `MapCanvas` draws the plane for the screen and stays shaped for a HUD
minimap, which is not in this phase.

Evidence: captures of a real Paper session at 960×540 and 640×360 with loaded and unloaded
terrain in the same frame, the published routes marked against the ledger's own six destinations,
and a zoom pair that keeps the block under the cursor fixed. The pure core is at 100% line and
branch on every gated map class; `TerrainSampler` has no unit test, because it needs a live
`ClientLevel`, so those frames are the only evidence it has. A fresh 58-gate Hallmark audit.

## P9 — warp from the map · done

The map stops being a read-only instrument: the routes it already marks become the way a player
asks to be moved. A marker under the pointer names itself and its coordinates; a click chooses it
without moving the plane, because a map that jumps under the cursor loses the thing that was just
aimed at, while a rail row still centres on the route it picks. One confirm action sends the id,
and it is the request P7 already defined — the client still owns no teleport, and the server still
answers with an id, an ok and a reason. The denial vocabulary moves into the pure core so two
surfaces cannot drift on it.

This supersedes P8's contract that "the map asks for no teleport of its own". P8's captures and
its audit stand as taken; the sentence they were true for is answered here rather than rewritten,
and the gates whose answers change (15, 17, 46) change in P9's audit.

Evidence: a live Paper session where the hint, the chosen marker, the in-flight request and the
server's answer are frames of one flow, with the client and server log lines for the id that was
sent; the hit test and the reason vocabulary at 100% line and branch in the pure core; a fresh
58-gate Hallmark audit.

P9 shipped noting that `MapScreen` itself had no unit test. That is answered after the fact: its
geometry and hit test were extracted into `MapLayout`, gated at 100% of both counters, leaving only
client runtime outside the gate. Same day, same frames retaken to show behaviour held — see the
DEVLOG entry and the `p9r-*` evidence.

## P10 — the launcher writes the client's HUD · done

One file, one shape, one owner. The launcher's HUD editor and the in-game editor write and read the
same `config/fullmoon/hud.json` inside the managed instance, in the client's own anchor-and-offset
shape, and a running client picks up an edit without a restart: `HudWatch` compares the file's mtime
every 500 ms, ignores the write the client itself just made, and `applyConfig` adopts the rest. The
launcher merges per element over the catalogue default, so an id it does not know survives its write,
and it writes all eight elements whole, so the file never carries half a layout. The percent contract
P5's front end used — positions as fractions of the frame — is deleted rather than translated: an
anchor and two integer offsets is what the client draws from, and a second representation of a
position is a second source of truth.

Two limits are the shape of the thing rather than gaps in it. The editor's stage is one frame,
640×360 GUI px, while the contract it writes is edge-relative: a layout authored there lands on the
same edge at any window size, and what the stage cannot show is how far apart two chips anchored to
opposite edges end up in the player's own window. And neither editor prevents overlap — two elements
with the same anchor and offsets draw on top of each other, in the stage and in the game.

What does not cross the seam is named here so it is not mistaken for wiring that exists. The
launcher's cosmetics screen equips capes, wings and trails against the wallet and draws its own
preview; the client renders none of them, and `cosmetics.rs` says so at the top of the file. Zoom and
fullbright are in no phase and in no source file of the mod. The standalone CPS module is retired —
CPS survives only inside the keystrokes element. `scale` round-trips through the file and
`BaseHudElement` and no renderer reads it. `서버 틱` reads `—` against the shipped bridge, which
sends `welcome`, `tp_result` and `screen_open` and never the `hud_sync` the client's metrics come
from, so the element is drawable and unfed. And the release pipeline pointed at `pinion-mod` and
`pinion-hud-*.jar` — paths from before the mod moved — so the jar it staged into the bundle was never
the jar it had just built; that is fixed in this phase's commit, along with the guard that keeps the
sources jar from winning the copy.

This qualifies P5's "the front end on the same tokens". The launcher's palette is the website's
design system carried to desktop density, with its own vocabulary (`--sky-*`, `--moon-*`), and
`i3/design/generate.mjs` emits its CSS to `i3/launcher/src/design/tokens.css`, which nothing imports.
The two halves share one gold by value — the mod's `--color-accent` #f5d06e is the site's
`--moon-300` — and nothing else. `verify-tokens.mjs` scans `i3/` only, so the gate that forbids a
colour literal never looked at the launcher.

Evidence: two live sessions on one unbroken dev client each — 640×360 GUI px, the frame the stage
models, and 960×540, a frame it does not — where the launcher moved a chip and switched an element off
while the client stood in the world, and the mod's own `Adopted hud.json edited outside the game` line
lands between two consecutive frames every time. The jar the launcher bundles is byte-identical to the
jar this tree builds. The editor was also driven in Chromium over Vite's mock core and photographed at
the Tauri default size; minimum and wide window probes have no horizontal overflow, the selected row
keeps keyboard focus, and reset changes the visible layout without a redundant success toast. The mock
core does not prove Tauri IPC, so the real disk write and hot adoption remain the job of the live Fabric
frames and logs. A fresh 58-gate Hallmark audit answers the DOM gates for that explicit surface.
