# Fullmoon 1.0.2 beta RC1 integration

Prepared on 2026-11-11 KST. This branch is a **release candidate**, not an
approved tag, deployed backend or public release.

## Branch provenance

- Base: upstream Fullmoon Client `origin/main` at `bfe89dc`.
- Imported the existing, uncommitted launcher changes from
  `feat/beta102-launcher` without changing that checkout.
- Imported the existing, uncommitted Fabric mod changes from `feat/beta102-mod`
  without changing that checkout.
- Resolved the combined cross-subsystem HUD contract: the mod's newly
  registered `season` element is represented in launcher ordering,
  `catalog.json`, the Rust HUD contract, the standalone mock, both translations
  and the editor's visible sample. The mod's player-facing HUD label is
  localized. This prevents the 3 frontend and 3 Rust regressions observed
  immediately after integrating the two independent worktrees.

## Verified commands

From this checkout, on Fedora 45:

- `cd i3/mod && ./gradlew --no-daemon --max-workers=2 --rerun-tasks
  build check jacocoTestReport --console=plain`: success, 503 JUnit tests,
  zero failures/errors/skips; Java compile and coverage gates passed.
- `cd launcher && npm ci && npm test && npm run build`: success, 85
  frontend tests, zero failures, TypeScript check and Vite build passed.
- `cd launcher/src-tauri && cargo test --workspace`: 128 Rust tests passed,
  zero failures, one ignored.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings`
  and `cargo fmt --all -- --check`: success.
- `git diff --check`: success.

The pinned development artifact bundle is maintained outside Git at
`~/.local/state/fullmoon-beta102/rc1-candidate/` with `SHA256SUMS`
for the combined mod, rebuilt FullmoonBridge, beta CoinBridge and beta proxy.
The mod's declared version remains `3.3.0-beta.1`; the launcher is
`1.3.0-beta.1`. The network version is independently `1.0.2-beta.1`.

## Verification checkpoint — 2026-11-12 KST

The initial integrated feature-branch candidate is `050f309`. Its Windows clean-install job in
[CI run 43045111228](https://github.com/fullmoon-network/fullmoon-client/actions/runs/43045111228)
passed `.github/scripts/windows-smoke.ps1` and uploaded
`Fullmoon_1.3.0-beta.1_x64-setup.exe`. Its downloaded SHA-256 matches the
smoke result, `0d1d6fbfcf1927df2e945f90018cdc352b247505a01438b9c7f7286cddf65155`.
The bundled mod hash matches the pinned RC1 mod. The recorded launch took
6.1 seconds. This proves installer startup and first-run state, not an
authenticated Minecraft session.

Only the failed and cancelled Linux jobs were requested again with
`gh run rerun 43045111228 --repo Fullmoon-OSS/fullmoon-client --failed`.
Attempt 2 completed successfully across all five jobs. Its Linux runtime
reported `SMOKE OK`, and the captured launcher rendered its first-run screen.
The prior successful Windows, frontend and Fabric jobs were preserved.

Artifact inspection then found `AppRun.wrapped` packaged with mode `0770`.
The same-user CI smoke did not catch that other users lack read/execute access.
The packaging fix grants every user read/execute access before repacking, and
the runtime smoke now rejects nonportable entrypoint permissions. A red
regression rejected the downloaded artifact with
`SMOKE FAIL: AppImage entrypoint permissions are not portable: 770`.
The fixed package must pass a fresh CI run before the Linux artifact is accepted.

Fresh local checks at the same source revision:

- `cargo test --workspace`: 128 passed, zero failed, one existing live
  Microsoft-auth test ignored. Unlike CI's existing command, this run did not
  filter out HUD tests.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings` and
  `cargo fmt --all -- --check`: both exited zero.
- `npm test`: 86 passed, zero failed or skipped, including the packaging regression.
- `npm run build`: TypeScript and Vite passed. The existing Vite warning
  `Some chunks are larger than 500 kB after minification.` remains.
  ESLint is not configured and was not run.

## Outstanding release gates

1. Rebuild the Linux AppImage/deb/rpm with portable entrypoint permissions and
   inspect its runtime smoke result and capture. Windows installation is no
   longer an open gate.
2. Confirm a real authenticated Minecraft client's lobby -> isolated beta ->
   lobby round trip, native season UI, zero stale bridge/HUD state and
   resource pack detection on the actual approved beta service.
3. The beta service must install the reviewed FullmoonBridge candidate and
   retain a documented beta-only rollback before claiming native season
   support. The isolated Paper/PostgreSQL rehearsal succeeded but did not
   deploy to the running beta backend.
4. Review the final changelog, commit/tag naming, binary checksums and
   GitHub Release assets before public publication.

**Do not announce RC1 while any of these gates remain unverified.**
