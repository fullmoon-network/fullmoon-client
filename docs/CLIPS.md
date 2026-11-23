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

Events: `clips://saved` (reload the gallery; global toast when the save came from another screen) and `clips://error` (show the core's message). The same commands are implemented by `MockCore` for the browser build, where clips are in-memory only and no file is written.

## Privacy

Nothing leaves the machine. OBS WebSocket is a local connection; clip files, thumbnails and the password live in the launcher data folder. Telemetry is unrelated to clips and off by default.

## Current limits

- No upload, cloud sync, trimming, editing or sharing.
- OBS must already be running; the launcher does not start it.
- The password is plain text in the local settings file (see above).
- In the browser/mock build, OBS is simulated and saves do not produce real files.
- A real Tauri window was not available in the UI worker's environment, so the layout has not been visually checked there; the mock build and contract tests have.
