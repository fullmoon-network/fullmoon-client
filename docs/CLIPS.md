# Clips (launcher UI)

The **클립 / Clips** screen is the visible half of the clip feature. It lets a player save the current OBS replay buffer with one button (or the configured hotkey), inspect OBS / buffer / hotkey status, configure the OBS connection, and browse saved clips with a thumbnail (or a generated cover), duration, local timestamp and size. Each clip can be opened, shown in its folder, or deleted.

Everything stays on the machine: there is no upload, cloud, social sharing or editing anywhere in this surface.

## OBS prerequisites

The launcher talks to OBS over OBS WebSocket (v5). Before saving:

1. Start OBS.
2. Enable OBS's WebSocket server (Tools → WebSocket Server Settings) and enter the same password in the launcher's **OBS connection setup** panel.
3. Set OBS's replay buffer length to roughly **30 seconds** and start the replay buffer. The screen's **Start replay buffer** button can do the last step; the gap between a hotkey press and the save is otherwise limited by the buffer length.
4. Clear OBS's own **save replay** hotkey. If OBS and the launcher both save on the same key, one press writes two files.

The default connection is `ws://127.0.0.1:4455` with hotkey `F8`.

## Where things live

- Clips: the launcher's own clip folder under its data root (absolute path in each `Clip.path`; optional generated thumbnail in `Clip.thumbnailPath`).
- Clip settings: `<data root>/clips/settings.json`, deliberately separate from the main launcher settings file so clip work never rewrites it.
- The OBS password is stored **locally, in plain text**, inside that settings file. It is entered through a password field and is not printed elsewhere in the UI or in logs. Restrict access to the data folder accordingly.

## IPC surface

The UI calls only the frozen contract in `launcher/src/core/bindings.ts`:

| Command | Purpose |
| --- | --- |
| `clips_list()` | list saved clips |
| `clips_status()` | OBS state, replay buffer, hotkey registration, status message |
| `clips_save()` | save the replay buffer now; rejects with the core's reason |
| `clips_replay_start()` / `clips_replay_stop()` | control the buffer |
| `clips_open(id)` / `clips_reveal(id)` | open a clip or reveal it in the file manager |
| `clips_delete(id)` | remove a clip |
| `clips_settings_get()` / `clips_settings_set(patch)` | read and write connection settings |
| `clips_thumbnail(id)` | the stored thumbnail as a `data:` URL, or `null`; the webview cannot read files itself |

Events: `clips://saved` (reload the gallery; global toast when the save came from another screen) and `clips://error` (show the core's message). The same commands are implemented by `MockCore` for the browser build, where clips are in-memory only and no file is written.

## Privacy

Nothing leaves the machine. OBS WebSocket is a local connection; clip files, thumbnails and the password live in the launcher data folder. Telemetry is unrelated to clips and off by default.

## Timestamps and clock skew

The clip is the replay buffer itself: no external event time is mapped onto the recording, so a
wrong local clock cannot move a cut. `createdAt` is local wall time and is display metadata only.
If a later slice clips on a server-sent event (kill, death, objective), that timestamp and the
recording must be aligned against a shared clock, and the offset has to be measured rather than
assumed.

## OBS coexistence

This slice attaches to the user's existing OBS WebSocket (`ws://127.0.0.1:4455`) and only reads
state and asks for a save; it never rewrites OBS configuration. That already avoids the worst
conflicts with other OBS-backed tools, but it still requires the user to enable the WebSocket
server, and the UI names OBS. The next slice should run a dedicated portable OBS profile/instance
on its own port and config directory (so Dor/Outplayed and the user's own OBS are untouched), and
treat OBS as an implementation detail the player never has to configure. That is not implemented
here.

## Current limits

- No upload, cloud sync, trimming, editing or sharing. The next slice adds the upload API and the
  website/Discord surfaces.
- No transcoding yet; the saved file is whatever OBS wrote. A share/upload path needs a
  quality-preserving encode (H.264 CRF for universal playback, HEVC/AV1 for size) and must keep the
  original until the upload is confirmed.
- OBS must already be running; the launcher does not start it.
- The password is plain text in the local settings file (see above).
- In the browser/mock build, OBS is simulated and saves do not produce real files.

## Next slice (planned, not implemented)

- **Invisible OBS**: provision a portable OBS instance under `<data root>/obs`, with its own
  profile and scene collection, a generated WebSocket password and a non-default port, launched
  with `--multi` so an already-running OBS or Dor session is untouched. The clip UI then never
  says OBS, and a failure reads as "녹화 엔진을 준비하지 못했어요", not as an OBS configuration
  lesson.
- **Share encode**: keep the replay file as the master and produce an upload copy with ffmpeg —
  H.264 High, CRF 20, preset medium, at most 1080p60, AAC 160 kbps, `+faststart` — for universal
  playback. HEVC/AV1 are only worth their compatibility and encode-time cost when measured against
  that baseline. The master is deleted only after a confirmed upload.
- **Upload**: per-user token, retryable POST to the clip API, then a Discord post on success. The
  PUBG contract is [`web/docs/clips-api.md`](../../web/docs/clips-api.md); Minecraft clips need a
  `game` discriminator there (or a separate endpoint) before the launcher can use it.

## Verification (2026-11-23)

- `cargo test --bin fullmoon --offline` — 63 passed, 0 failed, 1 ignored.
- `obs::tests::authenticates_and_saves_against_a_fake_obs` runs the real client against a fake
  obs-websocket v5 server: Hello, auth, Identify, status request, and a save whose
  `ReplayBufferSaved` event arrives before its own response.
- `npm run build` and `npm test` — 45 tests passed.
- Headless Chromium screenshots of the empty library, a saved clip, and the setup panel were taken
  against the mock core.
- Unverified: a real OBS handshake (no OBS on the build host), the Windows/macOS file-manager
  branches (compiled everywhere now, but not executed), and a packaged Windows build.
