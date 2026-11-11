## 2026-11-11 - launcher packet L1: publish-driven GUI pack

This packet adds the launcher half of the 1.0.2-beta.1 network beta preparation. The shipped
line is `origin/main` at `bfe89dc` (launcher 1.2.1, mod 3.2.1). The work was cut from that
object as `feat/beta102-launcher`, not from the unshipped 69706ea audit base. No server
deployment, account authentication, production request, commit, push, or merge was performed.

### What changed

- New `launcher/src-tauri/src/packs.rs`. The catalogue supplies the zip URL and the SHA-1 URL;
  the SHA-1 itself is read from the published one-line file at launch and is never baked into
  the launcher or the catalogue. A malformed, missing, oversized, or non-2xx SHA-1 answer is a
  warning and leaves the existing copy alone.
- The pack download is bounded to 64 MiB and 60 seconds. Bytes are streamed into a sibling
  `.part` file while being hashed, and the file is renamed into place only after the hash
  matches. A corrupt or timed-out download removes its temporary file and keeps the previous
  good pack.
- `options.txt` is edited only after a pack is on disk. The entry is appended last, once. Other
  pack choices and their order are preserved, CRLF line endings are preserved, a malformed
  `resourcePacks` value is left byte-for-byte untouched with a warning, and a missing file is
  created only for the known 26.1.2 data version. The write uses a temp file and rename.
- `InstanceModState` records which pack ids were already selected, so a player who switches the
  pack off is not overridden on the next launch.
- `start_game` calls the pack refresh without `?`. Warnings are logged as `WARN` events in the
  launch console (and to stderr) after the console is cleared; they cannot fail the launch.
- The catalogue entry is `fullmoon-gui` for game 26.1.2 with `sha1Url`
  `https://fullmoon.ink/packs/fullmoon-gui.sha1`. The launcher version is 1.3.0-beta.1 in
  `package.json`, `package-lock.json`, `Cargo.toml`, `Cargo.lock`, and `tauri.conf.json`; the
  catalogue's `fullmoon-client` entry is 3.3.0-beta.1. The bundled jar remains the 3.2.1 file
  in this branch and must be replaced by the mod worker's 3.3.0-beta.1 build at integration.

### Failed attempts and corrections

- The red test run compiled `packs.rs` before the API existed and failed with `cannot find type
  PackSource` plus missing `refresh_all`, `sha1_line`, `safe_file`, `with_pack`, and
  `fetch_verified`. That is the intended first failure, recorded in `/tmp/p2-packs-red.log`.
- The first green attempt had one failing test because its fixture published the production
  SHA-1 for unrelated bytes. The implementation was correct; the fixture hash was wrong. The
  test now hashes its own payload, and the 14 pack tests pass.
- Baseline `cargo fmt --all -- --check` failed with 1930 lines of diff on main, and clippy
  failed on `mods::sync` (9 arguments) and an overlapping `0` arm in `spec::heap_mb`. The
  launcher files were formatted, `sync` was given a `SyncInput` struct, and the heap match
  arms became non-overlapping. No lint suppression was added.
- `npm run verify` still carried dashboard selectors from the pre-main UI. The wallet and news
  tabs now use their `#dash-tab-wallet` and `#dash-tab-news` ids. Chromium remains the default;
  `BROWSER_PATH` selects Firefox and `HEADLESS=0` runs it under Xvfb. Headless Firefox on this
  host has no WebGL, so the final journey ran headful under `xvfb-run` with software GL.
- The first Firefox attempt failed at the removed wallet nav label and produced a blank
  dashboard. Under Xvfb the same journey rendered WebGL 2.0 and completed.

### Evidence

All commands were run sequentially from the paths shown.

| Command | Exit | Result |
| --- | --- | --- |
| `cargo test packs` (`launcher/src-tauri`) | 0 | 14 passed, 0 failed |
| `cargo test --workspace` (`launcher/src-tauri`) | 0 | 72 passed, 0 failed, 1 ignored |
| `cargo clippy --workspace --all-targets --all-features -- -D warnings` | 0 | clean |
| `cargo fmt --all -- --check` | 0 | clean |
| `npm run build` (`launcher`) | 0 | `tsc --noEmit` and Vite built |
| `npm test` (`launcher`) | 0 | 85 passed, 0 failed |
| `HEADLESS=0 LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a ... npm run verify` | 0 | 13 screenshots, mock core |

The browser run is MOCKED evidence: a plain browser selects `MockCore`, so it proves the React
surfaces and version label, not Rust pack installation. The native pack behavior is covered by
the Rust fixture tests, which use a loopback HTTP server and temporary game directories.

### Still open

- The new mod jar is not in this checkout. The catalogue version and the bundled jar therefore
  disagree until integration stages `fullmoon-client-3.3.0-beta.1.jar`.
- No beta endpoint, beta wallet, server deployment, or i3/mod change is part of this packet.


## Focused review follow-up: atomic temp ownership and options ambiguity

A coordinator review of the same uncommitted launcher diff found six concentrated issues in
the pack path. This follow-up fixes them without changing the packet's scope, branch, or
artifact expectations.

### What changed

- Pack bytes and `options.txt` now go through `TempFile`, an exclusively created sibling with
  a random name owned by one refresh. `create_new` is atomic, an occupied candidate is retried,
  and a pre-existing symlink is never followed or truncated. The guard removes only its own
  path. Rename failure keeps the destination and reports both the rename error and any failure
  to remove the temp file. Timeout and fetch failures discard explicitly through the same guard.
  On Windows, replacement uses `MoveFileExW` with `MOVEFILE_REPLACE_EXISTING`; Unix keeps the
  atomic `rename`.
- `options.txt` reads are capped at 1 MiB. Duplicate `resourcePacks` keys make the effective
  choice ambiguous, so both the duplicate and any malformed list are treated as unreadable:
  the original bytes stay untouched and the launch warning says so. An early line that already
  lists the pack no longer short-circuits a later line. Blank lines, missing trailing newlines,
  and CRLF endings are handled explicitly.
- `packs::apply` builds a replacement `InstanceModState` with the new pack list instead of
  assigning `st.packs` in place. `PackReport` is only constructed at the end of a refresh; the
  old `PackReport::warn(&mut self)` helper is gone.
- The launch warning loop now logs a contextual diagnostic if `app.emit` fails. The stored
  launch log and stderr still carry the original pack failure, and delivery failure remains
  non-fatal.
- Narrating comments were removed from the new pack code. Comments that state protocol,
  lifecycle, hashing, atomic-write, or user-choice constraints remain.

### Regression tests

The red phase added four tests against the previous code and recorded four failures: duplicate
keys returned `Unchanged`, the oversized options file was rewritten, and both the occupied
`.part` path and the symlink case were reused. The green phase now has 26 pack tests, including
exclusive creation on an occupied path, a symlink refusal, an installed pack that is a regular
file, explicit rename-failure cleanup for pack and options writes, a 100 ms download timeout,
and four concurrent refreshes that converge on one pack and one selection.

### Gates after the follow-up

| Command | Exit | Result |
| --- | --- | --- |
| `cargo test packs` (`launcher/src-tauri`) | 0 | 26 passed, 0 failed |
| `cargo test --workspace` (`launcher/src-tauri`) | 0 | 84 passed, 0 failed, 1 ignored |
| `cargo clippy --workspace --all-targets --all-features -- -D warnings` | 0 | clean |
| `cargo fmt --all -- --check` | 0 | clean |
| `npm run build` (`launcher`) | 0 | `tsc --noEmit` and Vite built |
| `npm test` (`launcher`) | 0 | 85 passed, 0 failed |

The browser journey remains mock-core UI evidence and is not proof of native Rust IPC. Native
Tauri UI and Windows installer execution remain unverified on this host.


## Final-review packet: state ownership and launch reservation (peer findings 10-13)

The ClinePass worker that was finishing the final-review packet hit its monthly quota mid-edit
and its process exited with `FULLMOON_LAUNCHER_FINAL_REVIEW_EXIT=1`. This recovery completed
its unfinished packet in the same worktree on the same uncommitted diff. The first build
failed on the interrupted worker's half-finished call: `packs.rs` already called
`mods::update_state_at`, which did not exist yet. Nothing was reset; the missing pieces were
written around the preserved edits.

### What changed

- `store::write` (and now `download::fetch_one`) writes through `atomic::TempFile`: an
  exclusively created sibling with a random name, replaced onto the destination without the
  destination being removed first. Two writers, or two launcher processes, never share a temp
  path, and a failed replacement keeps the previous good file.
- New `mods::update_state_at`: a serialized instance-state transaction. Under a per-state-file
  async lock it reads the freshest `InstanceModState`, folds one field's change in, and
  persists. `packs::apply` merges only `packs`, `mods::apply` merges only `managed` after its
  network requests, and the toggle/favorite commands merge `disabled`/`favorites` - none of
  them writes back a snapshot taken before a network call. `save_state` is gone.
- New `launchguard.rs`. A launch reserves an in-process slot with `try_lock_owned` (held
  across awaits, no std mutex across await) and an exclusively created `launch.lock` beside
  the instance state that names the reserving pid. A second launch is refused with a sentence;
  a crashed launcher's lock is recognized by its dead pid and taken over; an unidentified
  holder's lock is never deleted and release only removes the file that still names us. The
  reservation is dropped by unwinding on every error path, and otherwise moves into
  `launch::watch` so the instance is owned until the game exits.
- Windows replacement keeps the bounded retry policy: only access-denied, sharing, and lock
  violations are retried (5 attempts, growing 40 ms backoff), with a final error naming the
  attempt count; everything else fails immediately. The new `process_alive` helper for lock
  takeover was typechecked in isolation for `x86_64-pc-windows-msvc` - the check caught three
  real API mistakes (CloseHandle/STILL_ACTIVE live in `Win32::Foundation`, HANDLE is `isize`,
  `STILL_ACTIVE` is `i32` against a `u32` exit code) before any Windows machine saw them.
- Pack warnings are surfaced by `launch::show_pack_warnings` immediately after the pack
  refresh, under a provisional session id, before every step that can fail the launch. On
  success the same warnings are re-surfaced under the final session id in the fresh console. A
  failed UI emit stays non-fatal and logged.

### Tests

107 Rust tests pass (106 native, 1 ignored), including: concurrent packs/managed/disabled
transactions all surviving through the real `store::write` layer; a toggle merging onto the
freshest state instead of a stale snapshot; the pack selection merge not erasing a concurrent
mod toggle; six launch-guard cases (polite refusal, release, dead-pid takeover, live foreign
holder named, no foreign-lock deletion, unreadable holder kept); store concurrency with 16
writers; the Windows retry policy in platform-neutral tests; and warning retention across a
failed later launch step. `npm test` is 85/85.

### Gates

`cargo test --workspace` 0, `cargo clippy --workspace --all-targets --all-features --
-D warnings` 0, `cargo fmt --all -- --check` 0, `git diff --check` 0, `npm run build` 0,
`npm test` 0. A manual fixture run wrote `instance.json` through the real persistence layer
and showed every merged field plus a released `launch.lock`. Logs are
`l2-*.log` under `/home/person/.local/state/fullmoon-beta102/logs/`.

### Still open

- Full Windows native execution is unverified on this host (no MSVC toolchain; `lib.exe`
  absent): the isolated typecheck and the platform-neutral retry tests ran, the real sharing-
  violation and lock-takeover behavior did not. It stays a platform gate.
- Live `start_game` sequencing needs a native Tauri run; the warning-retention property is
  tested at the seam.
- The bundled mod jar and the mod-side token literals remain with the mod worker and
  integration, as before. Source stays uncommitted.


## Coordinator follow-up: kernel-held file ownership, gate evidence, native run

The coordinator read `launchguard.rs` and found the ownership race: the launch guard read a
dead holder's pid, unlinked the lock file, and retried - so two contenders could both read
the same stale line and one would delete the other's fresh lock; a matching pid could delete
a live reservation; process-query failures were sometimes read as absence; and a failed
owner-info write leaked an unidentifiable lock.

### What changed

- Ownership is no longer inferred from file contents at all. `launch.lock` is a persistent
  file whose advisory exclusive kernel lock (`std::fs::File` locking, stable since 1.89)
  decides who owns the instance. The open handle is kept for the whole preparation/game
  lifetime; release drops the handle, the OS releases the lock on crash, and the launcher
  never unlinks another owner's file. The pid beside the lock is a diagnostic the current
  owner rewrites. Unsupported or erroring lock observations fail closed. The blocking
  `File::lock` shape runs on the blocking thread pool.
- `mods::update_state_at` got the same treatment across processes: the in-process async
  mutex stays (no std mutex across await), and a kernel-held lock on a persistent
  `.instance.json.lock` sibling serializes read-modify-write transactions between launcher
  processes, so no packs/managed/disabled/favorites field can be lost cross-process.
- `process_alive` and the Windows process-query helper are gone; the MSRV became 1.89
  because that is where the stable file-lock API landed (clippy's MSRV check caught it).
  Three pre-existing `map_or` calls were rewritten to `is_none_or` when the newer clippy
  flagged them - no suppression was added.

### Tests

115 Rust tests pass (114 native, 1 ignored). The old lock regression cases were adapted,
not deleted: dead-pid takeover became "stale text does not block a free lock"; live-holder
and unreadable-holder cases became "refusal names the holder, the file is never touched";
foreign-release-deletion became "release leaves the persistent file in place". New cases
cover: same-process reservations with different mutex instances; real separate-process
contention (the test binary spawns itself as a lock holder and is refused by pid);
two racing subprocess contenders with exactly one owner and OS-release recovery; a failed
owner-info write failing closed; and a cross-process state transaction that waits out a
foreign holder (>= 250 ms measured) with both processes' fields surviving in the real
`instance.json`.

One flake was chased to ground: a test process that spawns any child duplicates its open
file descriptions into the forked child for the fork-to-exec instant, so a flock released
by one test can transiently look held. The process-spawning tests and the
release-right-after-drop tests serialize on a test-support mutex. Production is immune:
the only thing a launcher spawns during a launch is the game, inside the reservation.

### Gates with real exit codes

Every command's own exit code, captured by direct file redirection (the previous evidence
piped through `tee`/`grep`/`tail` and printed the pipe's status instead):
`cargo test --workspace` 0 (115 passed, 0 failed, 1 ignored), clippy
`--workspace --all-targets --all-features -- -D warnings` 0, `cargo fmt --all -- --check` 0,
`git diff --check` 0, `npm run build` 0, `npm test` 0 (85/85). Logs are `l3-*.log` under
`/home/person/.local/state/fullmoon-beta102/logs/`.

### Native run (real binary under Xvfb, isolated)

The actual binary (rebuilt with `custom-protocol`; the plain debug build first showed a real
"Could not connect to 127.0.0.1" error screen against the missing dev server) ran under
`xvfb-run` + `dbus-run-session` with an isolated `FULLMOON_DATA_ROOT`. Captured:

- the real native window with the title bar `Fullmoon 1.3.0-beta.1`, Minecraft 26.1.2 ·
  Fabric, and a loopback-only server list seeded into `servers.json` so the boot ping went
  to `127.0.0.1:1` (`shots/l3/native-isolated.png`);
- an error-recovery boot with garbage `settings.json` and `instances.json`: the binary
  failed open to defaults, detected the local JDK, rewrote valid settings, and recreated the
  managed instance (`shots/l3/native-error-recovery.png`);
- the real user data root verified untouched by mtime diff.

Disclosed: the first attempt used the wrong override name (`PINION_DATA_ROOT` instead of
`FULLMOON_DATA_ROOT`) so the app read the real user root - it wrote nothing, verified by
mtimes - and before the loopback fixture existed, the Home screen's boot ping made read-only
status queries to the bundled production address `play.fullmoon.ink:48291` (no auth, no
join, no install, no launch). The UI's boot also fetches Mojang's public version manifest;
that call is unconditional. No click automation exists on this host (no xdotool), so no
click-driven error toast was captured.

### Still open

- Native Windows build/installer stays an access/platform gate (MSVC `lib.exe` absent); no
  Windows-specific helper is left in the lock path.
- Click-driven UI flows need input automation this host lacks.
- The bundled mod jar and mod-side token literals remain with the mod worker and
  integration. Source stays uncommitted.


## Final data-safety packet: corrupt state is never reset, lock paths reject links

The coordinator's read of the kernel-lock packet found two concrete data-safety holes:
`mods::merge_locked` read the instance state fail-open (a corrupt `instance.json` would be
overwritten with defaults by the next pack or toggle transaction, deleting the user's
choices), and both persistent lock files were opened with plain `OpenOptions` while the
launch reservation stamps its owner line with a truncate - so a pre-existing symlink at a
lock path would have had its target truncated.

### What changed

- New `store::read_bounded(file, max_bytes)`: missing file means defaults are legitimate;
  an existing file that cannot be opened or read, is over the 1 MiB bound, or does not hold
  the expected shape is a named error that names the file and leaves it byte-for-byte
  untouched. `merge_locked` uses it, `mods::apply` reads fail-closed before touching any
  jar, and `packs::apply` turns a corrupt state file into a single named warning with no
  download and no write - an optional pack still never grounds a launch.
- New `filelock::open_lock_path`, shared by `launch.lock` and `.instance.json.lock`:
  exclusive creation the first time, an existing path rejected as a symlink or non-regular
  file before the open, and the opened handle verified to be exactly the checked file
  (dev/ino identity on Unix, handle-level is-file on Windows) after it - so no write can
  reach a link's target. The owner-info read in a refusal is now bounded at 4 KiB. Kernel
  locking and fail-closed semantics unchanged; no path is ever unlinked by the launcher.

### Tests and evidence

128 Rust tests pass (0 failed, 1 ignored). New cases: malformed JSON, wrong schema
(array), oversized file one byte past the bound, an unreadable directory path reported
without a write, missing-file initialization from defaults, field retention on a failed
transaction, the pack-side corrupt-state warning with untouched bytes, and symlinked
launch.lock / state guard refused with the sentinel's bytes intact. A manual fixture run
against real files captured the exact recovery messages (malformed JSON names the parse
error; the oversized file names the limit; both leave the bytes unchanged), then the
fixture was removed.

Gates with the steps' own exit codes (direct redirection, `l4-*.log` under
`/home/person/.local/state/fullmoon-beta102/logs/`): `cargo test --workspace` 0 (128/0/1),
clippy `-D warnings` 0, fmt check 0, `git diff --check` 0, `npm run build` 0, `npm test` 0
(85/85).

The native Tauri evidence from the previous packet remains actual-native (real binary,
isolated data root) and the browser journey remains mock-core; nothing relabeled. The
Windows build/installer stays an access/platform gate: the new Windows-side lock checks
use std metadata only and have not run on Windows. Source stays uncommitted, ready for
final integration once the mod worker's 3.3.0-beta.1 jar is staged.
