# Fullmoon Client

The dedicated Minecraft **Java** client for the Fullmoon network: a launcher and an in-game Fabric
mod. This is not a cheat client. The server retains authority; the client presents the same features
through native interfaces. Target: Minecraft `26.1.2` with Paper 26.1.2 and Velocity.

- One-click launch directly into the Fullmoon lobby
- Server-driven **native warp and menu interfaces** replace almost every ChestGUI while keeping
  server authority and vanilla fallback
- Microsoft OAuth, one managed instance, and automatic Sodium/Lithium installation
- Coordinates, FPS, ping, clock, keystrokes, server tick, armor, and effects HUD elements with a
  drag editor; in-game settings, key bindings, and a terrain map; launcher edits are adopted by the
  running client
- Server: `play.fullmoon.ink` · Website: [fullmoon.ink](https://fullmoon.ink)

## Repository guide

| Document | Purpose |
|---|---|
| [docs/BRIDGE.md](./docs/BRIDGE.md) | Public `fullmoon:v1` server-to-client protocol contract |
| [docs/MICROSOFT-AUTH.md](./docs/MICROSOFT-AUTH.md) | Microsoft application registration and local test-account setup |
| [THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md) | Bundled and runtime third-party components and licenses |
| [site/](./site/) | GitHub Pages architecture walkthrough |

## Structure

- **`launcher/`** — Tauri v2 launcher with a Rust core for accounts, installation, launching, and
  economy reads, plus a React/TypeScript interface. `src/core/bindings.ts` is the single IPC contract
  between interface and core.
- **`i3/mod/`** — In-game Fabric mod. It renders HUD and settings screens plus native warp and server-menu interfaces
  from data received over the server's `fullmoon:v1` channel. Sibling directories contain the design
  token source and generator (`design/`), capture rig (`tools/`), and build plan, development log, and
  evidence (`docs/`).

## Build

```bash
# Launcher (Node 20+, stable Rust)
cd launcher && npm ci && npm run build        # frontend (tsc + vite)
cd src-tauri && cargo build --release         # core + bundle (NSIS on Windows)

# Mod (JDK 25; Gradle provisions it when absent)
cd i3/mod && ./gradlew build                  # build/libs/fullmoon-client-<version>.jar
```

Workflows under `.github/workflows/` verify both pieces. Pull requests also build the Windows NSIS
package, install it silently into an empty directory, launch it with an empty data profile, and check
the seeded managed instance and bundled mod. A tagged release runs the same smoke test before it
publishes the installer and `SHA256SUMS` on GitHub Releases.

## Trust model

- **Detection is convenience, not trust.** Whether the client opens `fullmoon:v1` has no effect on
  server-side permission and cooldown checks for teleports or commerce. The design assumes a forged
  client. See [docs/BRIDGE.md](./docs/BRIDGE.md) for the complete contract.
- **GitHub Releases is the only distribution path.** Every release includes `SHA256SUMS`. OTA remains
  disabled until the Tauri updater signing key is configured; without it, releases contain only the
  installer and hashes.
- **Project-owned artifacts ship in one executable.** Mojang assets are the sole exception because
  redistribution is prohibited; the launcher downloads them and verifies Mojang's published SHA-1.

## License

GPL-3.0 — see [LICENSE](./LICENSE). Third-party components are listed in
[THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md). Minecraft is a trademark of Mojang Synergies AB;
this project is not affiliated with Mojang.
