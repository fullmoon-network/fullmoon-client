# Share-encode runner (backend slice)

## What was built

`launcher/src-tauri/src/share.rs` now has a callable encode path in addition to
the existing argument builder:

- `run_share_encode(ffmpeg, input, output, target, duration_s, cancel) -> Result<PathBuf>`
  validates the paths, resolves the executable, runs ffmpeg through
  `tokio::process`, and returns the final `.share` path on success. It needs no
  `AppHandle` and adds no Tauri command, IPC surface or UI wiring — that is the
  next supervisor pass. `share.rs` carries a module-level `allow(dead_code)`
  until that caller exists.
- `EncodeCancel` carries a bounded timeout (15 minutes by default) and an
  optional `watch::Receiver<bool>` stop signal; `cancel_channel()` creates the
  pair. The child is spawned with `kill_on_drop(true)`.
- The ffmpeg executable is passed in as a `Path` (resolved from
  `ClipSettings.ffmpeg_path` by the eventual caller). A bare name is searched
  on `PATH`, so an unset custom path still works; a missing file becomes an
  `Error::Invalid` with a message that points at clip settings, before any
  output is created.
- `encode_args()` reuses `build_share_args` for every flag and its ordering,
  then re-attaches the master and temp paths as `OsStr` operands so non-UTF8
  file names are not routed through string joining. No flags were changed.
- Input must be an existing file inside `<data root>/clips`; the output's
  parent must canonicalize inside the same directory, and an output that names
  the master itself is refused. These mirror `clips.rs`'s `contained` /
  `checked_path` semantics; `clips.rs` was deliberately left untouched.

No other module was edited. `Clip`/`ClipSettings` serde shapes, `bindings.ts`,
the mock core, the web UI, `docs/CLIPS.md`, and `Cargo.toml` are unchanged.
No new crates were added.

## Why the master is untouched

The replay file is the master and stays the master. It is passed only as ffmpeg's
`-i` input; the runner never opens it for writing, never deletes it, and never
uses it as a temp path. The second encode is a derived, disposable copy. Losing
or corrupting that copy on a failed upload costs nothing; losing the replay
would lose the clip. Deleting the master is still deferred until a confirmed
upload, which is outside this slice.

## Temp then rename

All ffmpeg writes go to `<output>.part-<pid>-<uuid>` in the same directory as
the final output, so the rename is within one filesystem. `-y` can only replace
that unique temp file, never the master or a good prior `.share` file. After a
clean exit with a non-empty temp file, the runner renames temp to `output`
(`output_name(input)` in normal use). On Windows, where rename cannot replace
an existing destination, a prior output is removed just before a retry — only
after a successful, verified encode.

## Failure and cancel semantics

Every failure is `Error::Invalid` with an actionable message; cleanup removes
only the failed temp output:

- missing or non-spawnable executable: error before spawn, no output created;
- nonzero exit (or signal): exit code in the message, first 4 KiB of stderr
  attached (240 characters shown), temp removed, master and any pre-existing
  `.share` left as they were;
- exit 0 but no output file, or a zero-byte output: error, temp removed;
- timeout or explicit cancel: child is killed, awaited, temp removed, error
  returned. Stderr is drained concurrently with a 4 KiB cap and a 2-second
  join bound, so a chatty ffmpeg cannot wedge the runner;
- rename failure: temp removed, original output untouched.

Nothing is logged, so no settings value (including the OBS password) can reach
a log through this path.

## Tests

The original nine `share::tests` (argument order/flags, dimensions, threshold,
`output_name`, duration) are unchanged and still pass. Nine new tests were
added (81 total passed in the crate, up from 72):

Cross-platform (no shell needed):

1. `encode_args_attach_the_master_and_output_as_os_strings` — `-i` operand and
   final operand survive as `OsStr`, flags still come from `build_share_args`.
2. `a_missing_executable_fails_without_writing_anything` — injected missing
   binary errors with "not found" / "clip settings", master unchanged.
3. `paths_outside_the_clip_folder_are_refused` — outside master, outside
   output, and output == master are all rejected before spawn.

Unix-only fake-process module (`#[cfg(unix)]`, temp dirs under
`std::env::temp_dir()`, scripts that record argv, write the last argv path and
exit with a chosen code):

4. `fake_ffmpeg_receives_the_shared_arguments` — `-nostdin`, `-y`, `-i`,
   master, codec flags and the `.part-` output under the `.share` name.
5. `success_returns_the_share_path_and_leaves_no_temp` — `.share` path
   returned, encoded bytes present, master bytes unchanged, no temp left.
6. `nonzero_exit_removes_the_partial_and_keeps_a_good_prior_share` — exit 3,
   partial temp removed, message names code 3 and stderr, prior share intact.
7. `a_zero_byte_success_is_a_failure` — exit 0 with no file is an error.
8. `a_timeout_kills_the_child_and_removes_the_partial` — 400 ms timeout,
   process confirmed gone via `kill -0`, master untouched.
9. `an_explicit_cancel_signal_stops_the_encode` — `watch` signal after
   200 ms, same cleanup and kill guarantees.

## Commands run and results

From `launcher/src-tauri` unless noted:

| Command | Result |
| --- | --- |
| `cargo test --bin fullmoon` | **PASS** — 82 run, 81 passed, 0 failed, 1 ignored (pre-existing ignored auth-network test). Baseline before this slice: 72 passed, 0 failed, 1 ignored. |
| `rustfmt --edition 2021 --check src/share.rs` | **PASS** — exit 0. |
| `cargo fmt --all -- --check` | **FAIL (baseline drift)** — exit 1, 129 diff hunks in 23 files, none of them `share.rs`. Before this slice it was 134 hunks in 24 files (the five `share.rs` hunks are now fixed). Untouched files were not reformatted. |
| `cargo clippy --all-targets -- -D warnings` | **FAIL (baseline)** — exit 101, one pre-existing error only: `clippy::too_many_arguments` at `src/mods.rs:314` (8/7 arguments). Nothing in this slice is reported. |
| `cargo clippy --all-targets -- -D warnings -A clippy::too_many_arguments` | **PASS** — exit 0 with the single baseline lint allowed on the command line (no config added). |
| `cd launcher && npm test` | **PASS** — 45 passed, 0 failed. |
| `cd launcher && npm run build` | **PASS** — `tsc --noEmit && vite build`, 77 modules transformed. |

## Unverified / deferred

- No real ffmpeg was run: the process tests use injected fake executables on
  Linux. H.264 output quality and `+faststart` behavior are covered only by the
  argument assertions, not by decoding a real encode.
- No Windows build or run was performed locally. The script-based fake-process
  tests are `#[cfg(unix)]`; the missing-executable, arg-plumbing and path
  validation tests compile cross-platform. The Windows
  remove-then-retry rename branch is type-checked on this host only through
  `cfg!` (the statement is compiled but never executed). Any Windows result
  must come from the manually dispatched `windows-latest` job in
  `.github/workflows/ci.yml`; no workflow was dispatched by this slice.
- UI wiring, a clips.rs public command, IPC/bindings, upload, HEVC/AV1 and
  master deletion are all deferred to the next supervisor pass. This slice
  deliberately changes no UI file and adds no command surface.
