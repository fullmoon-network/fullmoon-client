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
The fix was committed and pushed only to the RC1 feature branch as `43ba4ba`.
[CI run 43166262370](https://github.com/fullmoon-network/fullmoon-client/actions/runs/43166262370)
passed all five jobs. The rebuilt Linux artifact reported
`appimage entrypoint permissions ok: 775`, a visible 1280x820 window,
`first-run state ok` and `SMOKE OK`. The Windows clean-install smoke also
passed at this revision. No main-branch merge, tag or public release occurred.

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

## Bundled resource checkpoint — 2026-11-12 20:39 KST

The operator rejected optional-download dependence. The beta skin/GUI ZIP
(SHA-1 `175de05a740e46b47a2db6533c3d217c1c14cb19`) now ships intact in
the Fabric mod, with its 99 asset files expanded into unconditional mod resources.
The launcher embeds the identical archive and seeds/upgrades its local copy
without HTTP. Other pack selections remain ordered and are not reset.
A selected external GUI archive is still reported by its actual hash rather
than falsely claiming the bundled version is active.

- `./gradlew --no-daemon --max-workers=2 --rerun-tasks build check
  jacocoTestReport --console=plain`: `BUILD SUCCESSFUL`; JUnit XML totals
  507 tests, zero failures/errors/skips, including four new bundle regressions.
- Artifact inspection compared every archive asset to the built JAR and the
  intact embedded ZIP: 99 matching files. Local candidate JAR SHA-256:
  `26d6a90a865a6047363505810eb9e626aca567e67bf81209f7a56ee2b02bbd48`.
- `npm test`: 86 passed, zero failed/skipped. `npm run build`: passed;
  the existing Vite chunk-size warning remains. ESLint is not configured/run.
- Initial `cargo test --workspace`: 131 passed with one existing external
  auth test ignored. The initial Clippy run rejected an unnecessary
  same-type conversion in the new bundled writer.

### Direct handoff verification — 2026-11-12 KST

After the bounded Codex handoff, the Rust writer's unnecessary conversion was
removed and two deterministic failure-path regressions were added: a failed
destination replacement preserves the existing directory and options and
cleans the temporary file; unrecognized bundled metadata cannot create files
or make network requests. Fresh verification from this checkout:

- `cargo fmt --all -- --check`: passed.
- `cargo test --workspace`: 133 passed, zero failed, one existing external
  Microsoft-auth test ignored.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings`:
  passed without warnings.
- Fabric `./gradlew --no-daemon --max-workers=2 --rerun-tasks build check
  jacocoTestReport --console=plain`: success; 507 JUnit tests, zero
  failures/errors/skips.
- Launcher `npm test`: 86 passed; `npm run build`: passed with the existing
  Vite large-chunk warning.
- Bundled source ZIP SHA-1: `175de05a740e46b47a2db6533c3d217c1c14cb19`.

At 20:43–20:49, real-account client logs also recorded accepted native beta
welcome responses, season-tab transitions, mission tracking, untracking and
claim actions with increasing server revisions. Captures of native season and
return-menu screens exist. This demonstrates interactive native protocol
responses; it does **not** independently verify the return-menu button's action,
all wardrobe stage selections, or an authoritative reward-ledger delta.

Real-account test used the new JAR in the private scratch game directory,
removed the external GUI ZIP, selected only `vanilla`, and retained the earlier
server-pack refusal. At 20:35:02 the beta hello advertised the bundled hash;
no resource-pack confirmation appeared. `/skin pack` replied
`리소스팩을 이미 받았어요.` The native wardrobe and season screen rendered.
Health remained 20.0 over 83 seconds. `/warp production-lobby` returned normally;
the inspected lobby capture had no stale season HUD. The owned game/display
exited normally. Native wardrobe stage-selection actions were not accepted
by this check; do not infer them from the rendered screen.

The earlier zero-player force-load cleanup was insufficient: an existing
spider killed the player again at 20:06. A subsequent cleanup at the actual
live player removed loaded, unnamed, non-NPC hostiles within 96 blocks while
preserving player health, friendly entities and named/NoAI NPCs. Natural
spawning is disabled only in the beta overworld. A separate 100.5-second
full-health idle observation was not a resource-dialog survival test.
No production OP was granted, lobby copy changed, or production server restarted.

Inventory/ender RCON responses on normal return matched the earlier baseline.
The Inventory response contains an ellipsis, so this is not a complete
inventory-byte comparison. Earlier wording claiming exact inventory equality
was stronger than that evidence supports.

## Outstanding release gates

1. Build and inspect fresh distributable packages at the bundled-resource
   commit. Existing CI 43166262370 validated the **previous** source revision.
   Windows real-device installation is assigned to the external tester.
2. Verify wardrobe stage selection, native return-menu activation (the normal
   return command is verified), vanilla fallback, and server-authoritative
   reward accounting separately. Native season actions have live request and
   response-revision evidence. Retain observed hardcoded English season labels
   as a UI defect until a localized screenshot is verified.
3. Review final source/artifact provenance, changelog, checksums and GitHub
   Release assets. No main merge, tag, public release or announcement is approved.

The approved beta-only bridge rollout completed at 2026-11-12 15:23:42 KST.
The exact pinned bridge loaded with zero waypoints and CoinBridge logged
native menus enabled. The first attempt rolled back because the readiness
probe did not strip Minecraft color codes; its real RCON reply became a
red/green regression. The second attempt waited for the unchanged native
start-limit window. Both backups were retained; no production game/proxy
JAR changed. See the network checkout's
`docs/releases/1.0.2-beta-rc1-acceptance-20261112.md` for runtime evidence.

**Do not announce RC1 while any of these gates remain unverified.**
