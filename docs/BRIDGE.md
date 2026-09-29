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
C -> S  {"type":"hello","proto":1,"client":"fullmoon","version":"3.1.0"}
S -> C  {"type":"welcome","proto":1,"waypoints":[...]}
```

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
