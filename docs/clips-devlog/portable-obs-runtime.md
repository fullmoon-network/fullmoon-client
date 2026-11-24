# Portable OBS runtime provisioning (2026-11-24)

Isolated slice on `fullmoon-clips/portable-obs-runtime`, based on `feat/clips-next`
integration head `9ac05ac`. Local commit only: no push, merge, deploy or package.

## What landed

- `launcher/src-tauri/src/obs_runtime.rs` (new): a Tauri-free provisioning and launch-plan
  module. `provision()` works under `<data root>/obs`; `provision_in(obs_root, preferred_port)`
  is the testable core and reads/writes only under the supplied root.
- `<obs_root>/runtime.json` holds `{ "port", "password" }`. A valid existing file is reused
  verbatim (same port, same password), so restarts never rotate credentials; a missing or
  unparsable file is reprovisioned.
- The obs-websocket plugin config is rewritten from the runtime values on every provision so
  it always matches `runtime.json`.
- The password is two `uuid::Uuid::new_v4().simple()` values (64 hex characters). It is never
  logged, named in an error, sent over IPC or printed by `Debug` (`<redacted>`), and no test
  assertion message contains it. The two credential files are written atomically through a
  local temp-then-rename helper with `OpenOptions::mode(0o600)` on Unix.
- The port starts at `4466`; 20 consecutive 127.0.0.1 ports are tried with
  `TcpListener::bind` then drop. `4455` and `0` are normalised to `4466`, so OBS's default is
  never handed out. Exhaustion is `Error::Invalid`, never a silently shared port.
- Provisioning creates the platform config root, the obs-websocket plugin config directory,
  `basic/profiles/fullmoon` and `basic/scenes`. Config roots: `<obs_root>/appdata` (`APPDATA`)
  on Windows, `<obs_root>/home` (`HOME`) on macOS, `<obs_root>/config` (`XDG_CONFIG_HOME`)
  on Linux; the macOS path inserts `Library/Application Support` before `obs-studio`.
- `launch_plan` produces `--multi --disable-shutdown-check --minimize-to-tray
  --startreplaybuffer --profile fullmoon --collection fullmoon`, plus exactly one child-only
  config-root env var pointing under `obs_root`. `launch` spawns with stdin/stdout/stderr
  null and rejects a non-file executable with an actionable `Error::Invalid`.
- `paths.rs` gained only `obs_dir()` and `obs_runtime_file()` next to `clips_dir()`;
  `main.rs` gained only `mod obs_runtime;`. No crate was added.

## Why a generated, private password and port

The runtime belongs to one launcher data root, not to a player's OBS setup. A user-entered
password would put a credential back into `clips/settings.json` and into UI state; a fixed
port would collide with an installed OBS, Dor or Outplayed. Generating both per data root
keeps them private, prevents credential rotation across restarts, and lets the next slice
connect without asking the player to configure anything.

## Why env isolation + `--multi`, not `--portable`

OBS has no `--config-dir` flag, so the child gets exactly one environment variable
(`APPDATA`/`HOME`/`XDG_CONFIG_HOME`, platform-dependent) pointing at a directory under
`<data root>/obs`. The parent environment is never mutated and the user's real OBS folder is
never written. `--multi` is required so an already-running OBS instance stays untouched and
a second engine can start; `--disable-shutdown-check`, `--minimize-to-tray` and
`--startreplaybuffer` keep it invisible and ready. `--portable` is deliberately **not** used:
it keeps settings beside the executable, which can collide with an installed OBS and does not
give a path under the launcher data root. obs-websocket has no `-p`/`--port` flag, so the plan
does not invent one — the port lives only in the generated plugin config.

## Deliberately not wired

Capture still uses `ClipSettings.obs_url` (default `ws://127.0.0.1:4455`) and the existing
settings password. `commands.rs`, `bindings.ts`, the mock core, the Clips screen, i18n,
`clips.rs` and `share.rs` are untouched; no IPC command or event changes. `provision`,
`launch`, `obs_dir` and `obs_runtime_file` carry `#[allow(dead_code)]` until the next slice
calls them. No OBS binary is bundled or downloaded, no scene sources or encoder settings are
created, and nothing is deleted. Wiring the runtime into capture is the next slice, after
`share-encode-runner` merges. Upload and share encode remain blocked on that slice and, for
upload, on the `game` discriminator in `web/docs/clips-api.md`.

## Tests

All in `obs_runtime.rs` under `#[cfg(test)]`, with pid + UUID temp dirs cleaned up:

1. `provision_creates_private_runtime_and_websocket_config`
2. `provision_is_idempotent_and_never_rotates_the_password`
3. `a_corrupt_runtime_file_is_reprovisioned`
4. `the_default_port_is_never_obs_websockets_4455`
5. `an_occupied_port_is_skipped`
6. `launch_plan_is_multi_instance_and_isolated`
7. `websocket_config_lands_where_obs_reads_it`
8. `debug_output_redacts_the_password`
9. `generated_passwords_are_long_and_distinct`

No test launches a real OBS; none exists on the build host.

## Verification (exact commands and results)

- `cd launcher/src-tauri && cargo test --bin fullmoon`
  → `test result: ok. 81 passed; 0 failed; 1 ignored; 0 measured; 0 filtered out; finished in 0.06s`.
  All nine `obs_runtime::tests::*` are `ok`; the one ignored test is the pre-existing
  Microsoft device-code test that hits the network.
- `cd launcher/src-tauri && cargo clippy --bin fullmoon -- -D warnings`
  → **fails on 10 pre-existing diagnostics in files this slice does not touch**: nine
  `dead_code` errors in `share.rs` (`MAX_WIDTH`, `MAX_HEIGHT`, `WORTHWHILE_BYTES`,
  `ShareTarget`, `round_even`, `fit_dimensions`, `build_share_args`, `share_is_worthwhile`,
  `output_name`) and one `clippy::too_many_arguments` at `src/mods.rs:314` (`sync`). They come
  with the integration head, not this commit. Re-run with only those baseline lints allowed:
  `cargo clippy --bin fullmoon -- -D warnings -A dead_code -A clippy::too_many_arguments`
  → `Finished dev profile`, exit 0, no diagnostic in `obs_runtime.rs`, `paths.rs` or `main.rs`.
  `share.rs` and `mods.rs` were not edited.
- `cd launcher/src-tauri && rustfmt --check src/obs_runtime.rs src/paths.rs src/main.rs`
  → fails before formatting: standalone rustfmt 1.11.0 defaults to the 2015 edition
  (`error[E0670]: async fn is not permitted in Rust 2015`) and, without `skip_children`,
  follows `main.rs` into the whole module tree. Scoped form:
  `rustfmt --check --edition 2021 --config skip_children=true src/obs_runtime.rs src/paths.rs src/main.rs`
  → exit 1 with **only four pre-existing diffs in `paths.rs`** (lines 1, 24, 116, 127, all
  outside the added helpers at 62–70). `obs_runtime.rs` and `main.rs` are clean, and the new
  helpers produce no diff.
- `cd launcher/src-tauri && cargo fmt --all -- --check`
  → exit 1 on the repository's pre-existing global rustfmt drift (many untouched files such
  as `hud.rs`, `install.rs`, `java.rs`, `launch.rs`, `mods.rs`, `obs.rs`, `version.rs`).
  No file was reformatted by this slice.
- `cd launcher && npm test` → `# pass 45  # fail 0  # skipped 0`.
- `cd launcher && npm run build` → `tsc --noEmit && vite build`, `✓ 77 modules transformed`,
  built successfully. `node_modules` was already present, so `npm ci` was skipped.
  The frontend is unchanged; these commands guard against accidental contract edits.

## Unverified

- A real OBS launch on Windows or macOS: no OBS binary is bundled or installed on the build
  host, and `launch` is intentionally untested against a real OBS. This is **not** claimed as
  a Windows pass from local runs. Windows verification is the manually dispatched
  `windows-installer` job named **Windows clean-install smoke** in `.github/workflows/ci.yml`
  (`workflow_dispatch` only); that job was not dispatched and no package was built here. It
  needs this commit pushed to run in GitHub Actions.
- The obs-websocket config schema against the actual bundled OBS version: field names follow
  the v5 server config shape and the generated JSON is checked by unit tests, but an OBS
  instance has not read it.
- Port contention beyond the 20-attempt test, and packaged-installer behaviour.
