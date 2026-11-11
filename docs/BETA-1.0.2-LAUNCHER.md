# Launcher support for the 1.0.2-beta.1 network beta

This document covers the launcher-only packet for the 1.0.2-beta.1 network beta. It does not
describe a server deployment, a Minecraft version change, or an in-game mod change.

## Identity

| Coordinate | Value |
| --- | --- |
| Network beta release | `1.0.2-beta.1` |
| Supported game | Minecraft `26.1.2` |
| Launcher | `1.3.0-beta.1` |
| In-game mod | `3.3.0-beta.1` (owned and built by the mod worker) |
| GUI pack | `fullmoon-gui`, published checksum beside the zip |

The network version is not a Minecraft version and is not the launcher version. The beta is
reached from the production network menu; the launcher does not add a beta address, a beta
wallet, or a beta HTTP endpoint.

## What the launcher does

1. On launch, it reads the catalogue's pack source for the instance's game version.
2. It fetches the published SHA-1 line. A missing, non-2xx, oversized, or malformed line is a
   warning; the existing pack is kept.
3. It hashes the local pack. If the local SHA-1 already equals the published one, no zip is
   fetched.
4. Otherwise it downloads the zip into a sibling temporary file while hashing the stream. The
   download is bounded to 64 MiB and 60 seconds. Bytes are renamed over the old pack only after
   the published hash matches.
5. After a valid pack is on disk, it appends `file/fullmoon-gui.zip` to `resourcePacks` in
   `options.txt` once. Existing choices, their order, and CRLF line endings are preserved.
   A malformed pack list, a duplicate `resourcePacks` key, or an options file larger than
   1 MiB is not rewritten.
6. Failures are reported as `WARN` lines in the launch console and on stderr. They never stop
   the game from starting.

The SHA-1 lives only in the published line. Neither the catalogue nor the binary contains the
current beta hash, so a rebuilt pack needs no launcher release.

## Catalogue entry

`launcher/src-tauri/resources/catalog.json`:

```json
{
  "id": "fullmoon-gui",
  "file": "fullmoon-gui.zip",
  "url": "https://fullmoon.ink/packs/fullmoon-gui.zip",
  "sha1Url": "https://fullmoon.ink/packs/fullmoon-gui.sha1",
  "game": "26.1.2"
}
```

The beta zip is currently byte-identical to the production GUI pack, so the production
`fullmoon-gui.sha1` line is the correct published source. If the beta pack is rebuilt
independently, publish its own SHA-1 line and point `sha1Url` at it; do not hardcode a hash.

## Version coordinates

The following must agree before release:

- `launcher/package.json`
- `launcher/package-lock.json` (root and `packages[""]`)
- `launcher/src-tauri/Cargo.toml`
- `launcher/src-tauri/Cargo.lock` (`pinion` package)
- `launcher/src-tauri/tauri.conf.json`
- the `fullmoon-client` entry in `launcher/src-tauri/resources/catalog.json` for the mod version

`launcher/scripts/version-contract.test.ts` checks these in `npm test`.

## Verification

Launcher:

```bash
cd launcher
npm ci
npm test
npm run build
```

Rust core:

```bash
cd launcher/src-tauri
cargo test packs
cargo test --workspace
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo fmt --all -- --check
```

Browser journey (Chromium default, Firefox on hosts without Chromium):

```bash
cd launcher
npm run preview
BROWSER_PATH=/usr/bin/firefox \
PROFILE_DIR=/tmp/fullmoon-verify-profile \
OUT_DIR=/tmp/fullmoon-verify-shots \
HEADLESS=0 LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a -s "-screen 0 1600x1000x24" npm run verify
```

The browser journey uses the mock core. It proves the UI and version label, not the Rust pack
path; the Rust fixture tests are the native evidence.

## Integration needs

1. Stage `fullmoon-client-3.3.0-beta.1.jar` over
   `launcher/src-tauri/resources/mods/fullmoon-client.jar` from the mod worker's build. The
   catalogue already names 3.3.0-beta.1, but this branch still carries the 3.2.1 jar.
2. Re-run the launcher gates after the jar is staged; the packaged smoke tests hash that file.
3. Confirm the published `fullmoon-gui.sha1` line points at the bytes the beta serves. The
   contract audit recorded `175de05a740e46b47a2db6533c3d217c1c14cb19`, but the launcher must
   take the line at runtime.
4. Do not add a beta wallet, a beta public endpoint, or a beta address to the launcher for this
   packet.

## Out of scope

- `i3/mod` and the mod jar contents.
- Server deployment, proxy policy, and the network menu.
- Remote news or economy restoration.
- Cosmetic redesign or unrelated launcher features.
