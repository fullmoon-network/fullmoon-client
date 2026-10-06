# Third-party notices

Everything in this repository is GPL-3.0 (see LICENSE) except the items below.
Each entry lists what we ship, where it comes from, and under what terms it
reaches you. There are no other bundled third-party assets: the Minecraft
client itself and its assets are downloaded at runtime from Mojang's official
piston-meta/CDN endpoints and are never redistributed by us — the launcher
verifies every download against Mojang's published SHA1 before use.

## Runtime dependencies (fetched at install time, not redistributed)

| Component | Source | Terms |
|---|---|---|
| Minecraft: Java Edition client & assets | Mojang piston-meta / piston-data CDN | Mojang EULA; downloaded, hash-verified, never bundled |
| Fabric Loader / Fabric API | fabricmc.net maven | Apache-2.0 |
| Fabric Loader / Fabric API | fabricmc.net maven (`net.fabricmc.fabric-api:fabric-api`), fetched at install time | Apache-2.0 |
| Sodium | Modrinth (`project: sodium`), fetched at install time | LGPL-3.0; source at github.com/CaffeineMC/sodium-fabric |
| Lithium | Modrinth (`project: lithium`), fetched at install time | LGPL-3.0; source at github.com/CaffeineMC/lithium-fabric |

## Fonts

| Component | Source | Terms |
|---|---|---|
| Pretendard (Regular/SemiBold/Bold/ExtraBold, sources in launcher `fonts-src/`, shipped as subset WOFF2 renamed "Fullmoon Figures" in `public/fonts/`; Regular/SemiBold/Bold subset and renamed "Fullmoon Sans" in the mod, see `i3/mod/src/main/resources/licenses/NOTICE-fonts.md`) | github.com/orioncactus/pretendard | SIL Open Font License 1.1 |
| Hahmlet (variable, sources in launcher `fonts-src/`, shipped as WOFF2 renamed "Fullmoon Display" in `public/fonts/`; instanced at 600/700, subset and renamed "Fullmoon Serif" in the mod) | github.com/hyper-type/hahmlet | SIL Open Font License 1.1 |

## Sounds

| Component | Source | Terms |
|---|---|---|
| UI cues (`assets/fullmoon/sounds/ui/{focus,confirm,back,open,close,error,tab}.ogg`) | Original works synthesised for Fullmoon by `i3/design/make-ui-sounds.py`; no recordings, samples or third-party material | GPL-3.0, with the project |

## Build-time only (never shipped)

Rust crates, npm packages and Gradle plugins resolve from their registries at
build time under their own licenses; none are modified by us. See
`launcher/src-tauri/Cargo.lock`, `launcher/package-lock.json` and
`pinion-mod/gradle.lockfile` for exact pinned versions.

## Upstream lineage

This project began as a fork of Pinion (github.com/RedHatOnTop/pinion), which
is itself the work of the same author. The full history is preserved in this
repository's git log.
