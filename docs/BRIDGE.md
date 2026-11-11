# `fullmoon:v1` bridge protocol

`fullmoon:v1` is the public plugin-message contract between the Paper
`fullmoon-bridge` plugin and the Fullmoon Fabric mod. It selects a presentation layer; it never
changes server authority. Treat every client message as forged. Permissions, cooldowns, prices,
inventory effects, and every other gameplay rule remain server-side.

## Channel and framing

- Channel: `fullmoon:v1`, using Bukkit Messenger and Fabric custom payloads.
- A registered channel is only a candidate. A player is supported only after a valid handshake.
- The wire payload is one UTF-8 JSON object prefixed by Minecraft's unsigned VarInt byte length.
  The framing matches `FriendlyByteBuf.writeByteArray`. The server also accepts bare JSON from older
  clients.
- Payloads must fit the plugin-message packet limit of 32,767 bytes.
- Every object has a `type`. Operational server messages may omit `proto`, which defaults to `1`.

## Handshake

```text
C -> S  {"type":"hello","proto":1,"client":"fullmoon","version":"3.3.0-beta.1",
         "features":["season_v1","season_hud"],
         "packs":[{"id":"fullmoon-gui","sha1":"175de05a740e46b47a2db6533c3d217c1c14cb19"}]}
S -> C  {"type":"welcome","proto":1,"waypoints":[...]}
```

`features` and `packs` are optional and additive under protocol 1. `features` is an array of at
most 32 names matching `[a-z][a-z0-9_]{0,31}`; duplicates collapse. `packs` is an array of at most
8 objects with `id` matching `[a-z0-9][a-z0-9._-]{0,63}` and `sha1` as 40 lowercase hex digits;
the first entry for a duplicate id wins. A missing or malformed field is not an error on the
server: the player simply has no advertised capability. The client reports only capabilities it
implements and only a resource pack that is actually selected, hashed from the file on disk.

The beta server uses `season_v1` for the native season screen and `season_hud` for the season HUD
widget. It compares a reported pack SHA-1 with its current GUI pack and treats an exact match as
"bundled"; a pack reported while it is not selected would be a false claim and must not be sent.

The client waits five seconds for `welcome`, then disables bridge-only presentation for that login
session. The server never opens a native surface for a player that has only registered the channel
without completing the handshake. There is no renegotiation within a session.

A protocol mismatch disables bridge features for the session and leaves the vanilla fallback active.
Additive fields that old readers can safely ignore do not require a protocol bump.

## Messages

| Direction | Type | Purpose |
|---|---|---|
| S -> C | `welcome` | Complete the handshake and provide the full waypoint snapshot. |
| S -> C | `waypoint_sync` | Replace the full waypoint snapshot. |
| C -> S | `tp_request` | Request a registered waypoint by opaque ID. |
| S -> C | `tp_result` | Report an accepted or rejected teleport request. |
| S -> C | `screen_open` | Open a named native surface. Protocol 1 defines `warp`. |
| S -> C | `menu_open` | Open or replace a server-owned native menu snapshot. |
| C -> S | `menu_action` | Request one action advertised by the current menu snapshot. |
| C -> S | `menu_close` | Notify the server that the current native menu was dismissed. |
| S -> C | `casino_result` | Present one bet the server has already settled. |
| S -> C | `season_open` | Open the native season pass with a complete snapshot. |
| S -> C | `season_progress` | Update the level, tracked mission, and short-lived HUD lines. |
| C -> S | `season_action` | Claim, claim all, buy Plus, track or untrack a mission, or switch a tab. |
| C -> S | `season_close` | Notify the server that the native pass was dismissed. |
| C -> S | `season_hud` | Tell the server whether the season widget is on the HUD. |

### Waypoints

```json
{
  "id": "palace_gate",
  "name": "Fullmoon Palace Gate",
  "icon": "moon",
  "x": 500,
  "y": 72,
  "z": -140,
  "world": "lobby",
  "group": "palace",
  "perm": "warp.palace"
}
```

`perm` is a server permission key. Hiding a waypoint in the client is a convenience, not an access
check. The server accepts only IDs from its registry; arbitrary coordinates are not part of the
protocol.

### Server-owned menus

`menu_open` carries a complete immutable snapshot. Slots preserve the vanilla 9-column geometry so
existing server menu layouts remain recognizable while the Fullmoon client renders them as its own
screen.

```json
{
  "type": "menu_open",
  "proto": 1,
  "id": "2ac6f3ea-8f45-4d72-bb9c-cbded41b57d1",
  "revision": 4,
  "title": "Casino",
  "rows": 6,
  "items": [
    {
      "slot": 19,
      "label": "Coin Table",
      "material": "minecraft:gold_nugget",
      "count": 1,
      "details": ["Choose heads or tails", "Server-verified result"],
      "actions": ["left", "shift_left"],
      "icon": "fullmoon.casino.coinflip"
    }
  ]
}
```

`icon` is optional. It names a bespoke client-side mark (the casino lobby ships
`fullmoon.casino.{coinflip,dice,roulette,slots,moonfall,jackpot}`); an item without one — or a
client that does not know the name — renders the `material` item instead. Presentation only:
the icon never carries authority, exactly like `label` and `details`.

`chance` is optional: the item's win probability as a finite number in `[0, 1]` (the casino sends
it for coinflip, dice and roulette at the advertised bet, and for slots from its weights; pachinko
and the jackpot have no single probability and omit it). The client draws it as a moon filled that
far beside the figure. A `chance` that is present but not such a number rejects the whole menu, like
any other malformed field. Presentation only: the server settles every bet itself.

The client sends only the opaque session ID, revision, selected slot, and an advertised click:

```text
C -> S  {"type":"menu_action","id":"...","revision":4,"slot":19,"click":"left"}
C -> S  {"type":"menu_close","id":"...","revision":4}
```

Rules:

1. `id` is an unpredictable server-generated session identifier.
2. `revision` is monotonic. The server rejects stale or replayed requests.
3. The server accepts only a slot and click combination advertised in that exact snapshot.
4. The server re-runs all domain validation before changing state. Menu data is not authorization.
5. A successful action produces a fresh snapshot or a `menu_close`; the client does not predict the
   result.
6. `rows` is `1..6`, slots are within `rows * 9`, and duplicate slots are invalid.
7. Supported clicks in protocol 1 are `left` and `shift_left`.

Casino, shops, selling, enhancement, potential, guild, mail, titles, raid selection, kit selection,
tutorial prompts, crafting, and lift selection use this native path. A synchronized player-to-player
item trade remains a real inventory container because it transfers item stacks rather than selecting
server menu commands.

### Casino results

The server pushes `casino_result` after the bet has settled and the money has moved. The payload
is presentation only: the client plays a short reveal of an outcome it cannot influence, and the
server's chat line stays the record, so a client that drops or rejects the event loses nothing.

```json
{"type":"casino_result","game":"slots","won":true,"payout_multiplier":12,
 "detail":{"reels":["moon","moon","moon"],"matched":3}}
{"type":"casino_result","game":"dice","won":false,"payout_multiplier":0,
 "detail":{"roll":73,"target":50}}
{"type":"casino_result","game":"roulette","won":true,"payout_multiplier":2,
 "detail":{"pocket":17,"bet_type":"black"}}
{"type":"casino_result","game":"coinflip","won":true,"payout_multiplier":1.98}
```

Rules:

1. `proto` may be omitted and then means the current version; when present it must match the
   accepted server protocol, like every operational message.
2. `game` is `coinflip`, `dice`, `roulette`, or `slots`. Other games are rejected, not guessed at.
3. `payout_multiplier` is finite, `0..1000`, and positive when `won` is true. A loss carries `0`.
4. `dice`: `roll` is `0..99`, `target` is `0..100`, and the bet wins when `roll < target`.
5. `roulette`: `pocket` is `0..36` on a single-zero wheel. `bet_type` is the server's own id
   (`red`, `black`, `even`, `odd`, `low`, `high`, `straight:N`, `column:N`, `dozen:N`), at most
   32 characters; the client names the known ids and shows any other id as sent.
6. `slots`: `reels` holds 1 to 5 symbol ids matching `[a-z0-9][a-z0-9_-]{0,31}`, and `matched`
   is the largest count of one symbol, `1..reels`.
7. A newer result replaces the one on screen. The card lives for 4.5 seconds and is also drawn
   over open screens, since the bet is placed from a menu.

## Backend transfer lifecycle

A proxy server switch is a new login packet, and Fabric reports it as
`ClientPlayConnectionEvents.JOIN` on the new backend. A death, respawn, or ordinary dimension
change is a `ClientboundRespawnPacket` handled by the same `ClientPacketListener`; it is not a
login and must not reset the bridge snapshot. The client keys its reset on the login event and
records level changes only as evidence.

On a login (including a transfer) the client:

1. Discards waypoints, metrics, notices, pending warps, casino results, season HUD state, and the
   previous protocol mode.
2. Dismisses any native menu, warp, or season screen locally, without sending `menu_close` or
   `season_close` to the new backend.
3. Registers the channel again through the Fabric networking layer and sends a fresh `hello` with
   the current version, supported features, and the selected pack.
4. Enters `WAITING` and falls back after 5,000 ms if the new backend has no bridge.

A refused transfer never produces a login packet; the lobby session and its native surfaces stay
as they were. Returning to a bridge-capable backend produces a fresh `hello` and `welcome`, so
native menus work again without a relog.

## Native season pass

The season family is additive under protocol 1 and is only sent to a client that advertised the
matching feature. The client does not infer, scale, or format money; every amount, duration, and
point name arrives finished from the server.

`season_open` fields:

| Field | Type | Rule |
|---|---|---|
| `id` | string | session id matching `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` |
| `revision` | integer | non-negative; every accepted action increments it |
| `tab` | string | `track`, `missions`, or `plus` |
| `season.id`, `season.name` | string | at most 32 and 64 characters |
| `season.phase` | string | `upcoming`, `live`, `claim`, or `ended` |
| `season.endsIn`, `season.endsAt` | string | server-formatted, at most 32 characters |
| `terms.point` | string | season point name, at most 16 characters |
| `me.level`, `me.maxLevel` | integer | `0..maxLevel` and `1..200` |
| `me.xp`, `me.into`, `me.step` | integer | non-negative; `into` is `0..step`, `step` at least 1 |
| `me.plus`, `me.claimable` | boolean, integer | Plus ownership and `0..maxLevel*2` |
| `me.balance` | string or null | server-formatted display string; never parsed or scaled |
| `levels[]` | array | `{level, at, free, plus}`; level increases; either cell may be null |
| `reward.kind` | string | `won`, `title`, or `cosmetic` |
| `reward.label`, `reward.material` | string | server text and namespaced item id |
| `reward.state` | string | `claimed`, `claimable`, `locked`, `plus_locked`, or `pending` |
| `reward.color` | optional string | title colour name |
| `reward.id`, `reward.model` | string | required for `cosmetic`; ignored for other kinds |
| `missions.daily/weekly/season` | array | at most 16 rows each |
| `missions.resets` | object or null | server-formatted `daily` and `weekly` strings |
| `plus.price`, `plus.reason` | string | formatted price and optional blocked reason |
| `notice` | object or null | `{tone: "ok"|"error", text}` |

`season_progress` carries `level`, `into`, `step`, optional `tracked`, `recent`, and `flash`.
`level: 0` means the HUD must not draw. A malformed field rejects the whole message, like a
malformed menu. Unknown message types are ignored.

Client actions name the snapshot they were made against:

```text
C -> S  {"type":"season_action","id":"...","revision":4,"action":"claim","level":10,"track":"plus"}
C -> S  {"type":"season_action","id":"...","revision":4,"action":"claim_all"}
C -> S  {"type":"season_action","id":"...","revision":4,"action":"buy_plus"}
C -> S  {"type":"season_action","id":"...","revision":4,"action":"track","mission":"..."}
C -> S  {"type":"season_action","id":"...","revision":4,"action":"untrack"}
C -> S  {"type":"season_action","id":"...","revision":4,"action":"tab","tab":"missions"}
C -> S  {"type":"season_close","id":"...","revision":4}
C -> S  {"type":"season_hud","visible":true}
```

The server still validates the session, revision, advertised action, permission, cooldown, price,
and inventory state. The client never predicts a claim or purchase; it waits for a fresh snapshot
and shows the server's notice. The season HUD reports `visible` once per change so the server can
suppress its own progress display.

### Client display bounds and request discipline

The client keeps strict bounds on everything it must answer for — session ids, mission ids,
materials, colours, revisions, counts, level and mission limits — and rejects a message that
breaks them. A display line a player only reads (a season or mission name, a reward label, a perk
line, the notice text, a blocked reason, a formatted figure) is presented with its tail
ellipsized rather than costing the whole snapshot, because the producer does not bind every
display line and one long name must not blind the player to a valid snapshot.

A snapshot that is truly malformed is refused, the server is told nothing (the protocol has no
error direction), and the client does not open or fake a surface. If the refused message was a
`season_open` or `menu_open`, the player gets one chat line saying so and how to reopen; a
surface that already shows a good snapshot keeps it.

The pass has one request out at a time. While it waits — dots on the control, no second request —
a tab change is refused with the reason instead of drifting the local page from the server's
session page. If the bridge is unreachable, the send fails, or no snapshot answers within five
seconds, the foot shows a persistent local problem with the way out (close and reopen the pass);
the words stay until a fresh authoritative snapshot arrives, and no claim or purchase is ever
resent by the client on its own.

## Vanilla fallback

- `/항로` (alias `/route`) lists and executes the same registered waypoint IDs through the same
  permission and cooldown path as `tp_request`. `/워프` and `/warp` belong to coin-bridge's
  `/텔레포트` on the server, so the bridge does not claim them. A server with no reviewed waypoints
  publishes an empty list, and the route screen says so instead of failing.
- Every server-owned menu retains its ChestGUI inventory. The server opens it when the player has no
  completed handshake, the bridge is unavailable, or a menu snapshot exceeds the channel limit.
- Native and fallback surfaces expose the same actions. Client detection never changes the feature
  set.

## Server authority requirements

1. Validate waypoint permission, the shared 4,000 ms cooldown, world, and registry coordinates at
   execution time.
2. Use registration and handshake state only to choose a rendering surface.
3. Return `tp_result{ok:false}` for every denied teleport so the client does not infer outcomes.
4. Validate menu IDs, revisions, slots, click types, and the underlying domain operation.
5. Log handshake and rejected or completed gameplay requests for debugging and abuse analysis.

## Implementation notes

Bukkit silently discards a server payload sent before the player registers the channel. The server
therefore waits asynchronously for registration before replying to `hello`; it must not block the
main thread, because channel registration is processed there. The client may send `hello` from
`ClientPlayConnectionEvents.JOIN`; the server-side wait absorbs that race.
