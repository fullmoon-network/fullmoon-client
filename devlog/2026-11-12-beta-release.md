# Fullmoon beta release execution — 2026-11-12

## Phase 1: recover the existing client CI

- The operator requested direct, sequential execution of the remaining release
  work. Kept the existing feature branch, candidate artifacts and other sessions.
- Re-read CI run `43045111228` at `050f309` before any mutation. It was completed,
  with a Cargo dependency-install failure and a cancelled Linux package job.
- Requested only failed/cancelled jobs with `gh run rerun --failed`; exit zero.
  Attempt 2 passed the Cargo job. Linux packaging is still running; this is not
  yet a completed CI gate. Existing successful jobs were not restarted.
- Downloaded the previously successful Windows installer and smoke JSON. The
  installer hash matches the report; its bundled mod matches the pinned RC1 mod.
  No Windows test was represented as a new local run.
- Fresh full local Rust checks: 128 passed, zero failed, one existing external
  Microsoft-auth test ignored; Clippy all-target/all-feature warnings-as-errors
  and formatting passed. Ran the HUD tests that the existing CI command filters.
- Frontend: 85 tests passed; TypeScript and Vite build passed. Existing Vite
  output still warns `Some chunks are larger than 500 kB after minification.`
  ESLint is not configured and was not run. No warning suppression was added.
- Requested approval for a beta-only FullmoonBridge installation/restart with
  rollback. No deployment, restart, production data write, commit, push, merge,
  tag or public announcement was performed in this phase.

## Next phases and acceptance

1. Finish Linux CI and inspect its runtime smoke output and screenshot.
2. After explicit maintenance approval, install only the pinned bridge in the
   isolated beta, verify startup and preserve a beta-only rollback. Do not alter
   the production proxy, lobby, wild server, worlds, ledgers or audience timer.
3. Use an admitted authenticated account to verify transfer, native season UI,
   pack rendering/fallback and clean HUD state on return. Fixtures do not close
   this gate.
4. Reconcile current platform items with the intended beta cut and update release
   documents. Do not auto-merge unrelated branches or close reports from tests.
5. Public publication and any additional push remain separately approved gates.

## Artifact review and permission regression

- Attempt 2 finished successfully across all five jobs. Read the actual Linux
  `SMOKE OK` result and inspected its first-run launcher screenshot.
- The packer also warned about `AppRun.wrapped`. Extracting that exact AppImage
  found mode `0770`: other users cannot read or execute its entrypoint. Rejected
  the artifact despite the green same-user CI run.
- Added the portable-permission smoke check and contract regression first.
  The contract failed 1/7; the downloaded entrypoint failed with
  `SMOKE FAIL: AppImage entrypoint permissions are not portable: 770`.
- Granting read/execute to all users before repacking produces mode `0775`.
  The contract then passed 7/7 and the full frontend suite passed 86/86;
  TypeScript/Vite and both shell syntax checks passed. The existing Vite
  chunk-size warning remains. A newly packed CI artifact is still required.
- The operator approved committing/pushing only the RC1 feature-branch fix
  and rerunning CI, and separately approved the isolated beta bridge rollout.
  No main-branch merge, release tag or public posting is authorized.

## Fresh CI and live beta acceptance

- Committed/pushed the approved packaging fix as `43ba4ba` on the RC1 feature
  branch. Fresh CI `43166262370` passed all five jobs. Its Linux smoke reported
  entrypoint permissions `775`, a visible window, valid first-run state and
  `SMOKE OK`; Windows clean-install passed too. No main merge or tag.
- The approved beta-only bridge rollout initially rejected a valid colored
  RCON version (`FullmoonBridge version §a0.1.0§r`). Automatic rollback restored
  the original running beta. Added the exact reply as a failing regression,
  stripped formatting only for version comparison, and waited for the original
  five-minute/two-start limit rather than resetting it.
- Private deployment helpers passed 13 unit tests with 93% branch-aware coverage.
  The second install completed at 15:23:42 KST, matching the pinned bridge hash,
  zero waypoints, native-menu startup, active isolation dependencies and unchanged
  production/proxy JARs. Private backups and the success receipt were retained.
- Reused the operator-approved existing licensed Minecraft account. Its expired
  session renewed through the normal Microsoft/Xbox/Minecraft chain; no token
  was logged. The session store is private. Launched the exact candidate mod
  on an owned scratch X display with a separate game directory and no fixtures.
- Actual lobby -> beta transfer produced a fresh hello/welcome and season HUD.
  The shipped GUI pack differs from the beta skin pack, so an optional beta-pack
  prompt correctly appeared. While that prompt was open, a zombie killed the
  player. A later respawn was killed by a spider. Recorded captures and logs;
  beta first-entry safety is now a reproduced stop-ship gate.
- Disconnected safely, reconnected to production lobby, inspected its screenshot
  and confirmed no stale season HUD. Read-only comparisons showed identical
  production inventory, ender items and wallet, with no new ledger rows. This
  does not prove the still-unfinished native season, pack-render or return-menu
  flows. Closed the owned game/display, leaving the user profile and other
  sessions untouched.
- Requested beta overworld spawn-safety approval and separate permission for
  the production lobby's one-line copy/reload. Neither is implicitly approved.
  Classified all 83 platform intake IDs exactly once in the network beta scope
  document; no production Todo status changed and no unrelated PR was merged.
- `graphify update .` could not run: `ModuleNotFoundError: No module named
  'graphify'. Did you mean: 'graphlib'?` The graph is not claimed current.

## Default-bundled pack and real-account follow-up

- The operator explicitly rejected optional server-pack acceptance as the default
  experience. Bundled the existing approved beta archive, not regenerated art,
  in both Fabric resources and the launcher binary. Mod assets load even with
  no external file selected; the launcher seeds/upgrades offline without polling
  the older public GUI pack. Selected external files retain honest digest reporting.
- Red tests first: the launcher tried HTTP and could downgrade to the old pack;
  the mod lacked expanded classpath assets. The implementation fixed those paths.
  Full Fabric gate: 507 tests, no failures/errors/skips; 99 JAR assets match the ZIP.
  Frontend: 86 passed and production build passed, with the existing chunk warning.
  Rust: 131 passed/one external-auth ignore; clippy caught a same-type conversion.
- Real licensed-account test removed the private external GUI ZIP and kept only
  vanilla selected. Beta hello advertised the embedded hash; `/skin pack` replied
  that the pack was already received, with no download dialog despite earlier
  refusal. Wardrobe and season rendered; 83 seconds at full health; normal return
  command cleared the season HUD. Stage actions and native return-menu activation
  remain distinct open checks. Closed only the owned scratch game/display.
- Corrected two evidence limits: the first no-player spawn cleanup missed loaded
  hostiles later seen at the actual player; only the live-player cleanup plus
  spawn_mobs=false achieved the later safe idle observation. RCON inventory output
  is truncated, so matching replies do not prove complete inventory equality.
- No OP granted and no production lobby copy/reload/restart performed. The
  player-only reload issue was shelved after the operator questioned escalation.
- Native bounded implementation task hit the existing ClinePass monthly cap
  before editing. Preserved that reader and its failed task; used the designated
  existing OpenCode Go route in a separate registered checkout for the remaining
  two-file Rust correction/tests. No new funding or alternate provider was added.

## Codex handoff and final local source gates

- Directly corrected the new bundled writer's unnecessary Rust same-type
  conversion and added two error-path regressions, covering destination
  replacement cleanup, preserving prior options and files, and rejecting
  unrecognized bundled source IDs without network requests.
- Local Rust check: 133 passed, zero failed, one external Microsoft-auth
  test ignored; cargo fmt and strict clippy passed.
- Fabric build/check/coverage completed: 507 JUnit tests passed, no
  failures/errors/skips. Launcher frontend: 86 tests passed; Vite build passed
  with its existing large-chunk advisory.
- Real-account native beta logs from 20:43–20:49 include fullmoon:v1 welcome,
  season-tab and mission actions with advancing revisions and a claim-all
  action. Screenshot filenames exist for wardrobe and return-menu; button
  activation and authoritative reward ledger remain separate release checks.
- The previous fully-green GitHub Actions run 43166262370 predates the
  bundled-resource modifications. A new feature-branch CI and package hashes
  are required; do not reuse older bundle hashes for this revision.
