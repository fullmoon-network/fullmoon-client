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
