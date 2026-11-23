/* The clip library: OBS replay-buffer saves, the index that lists them, the
files they live in, and the hotkey that triggers one.

The index is a convenience, never the source of truth — a clip whose file is
gone is dropped the next time the list is read, and every id or path that
arrives over IPC is sanitized and canonicalized before it can touch the disk.
Nothing outside `<data root>/clips/` is reachable from here. */
use std::{
    path::{Path, PathBuf},
    time::Duration,
};

use serde::Deserialize;
use serde_json::json;
use tauri::{AppHandle, Emitter};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, Shortcut, ShortcutState};

use crate::{
    error::{Error, Result},
    model::{Clip, ClipObsState, ClipSettings, ClipStatus},
    obs, paths,
};

const DEFAULT_OBS_URL: &str = "ws://127.0.0.1:4455";
const DEFAULT_HOTKEY: &str = "F8";

/// What OBS can plausibly hand us. Anything else is not a clip we can play.
const VIDEO_EXTENSIONS: &[&str] = &["mkv", "mp4", "flv", "mov", "ts", "m4v", "webm", "avi"];

#[derive(Debug, Clone, serde::Serialize)]
struct ClipError {
    message: String,
}

// ── settings ──────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipSettingsPatch {
    pub enabled: Option<bool>,
    pub obs_url: Option<String>,
    pub obs_password: Option<String>,
    pub hotkey: Option<String>,
    /// `Some(None)` clears the path, `None` leaves it alone.
    #[serde(default, deserialize_with = "double_option")]
    pub ffmpeg_path: Option<Option<String>>,
}

fn double_option<'de, D, T>(de: D) -> std::result::Result<Option<Option<T>>, D::Error>
where
    D: serde::Deserializer<'de>,
    T: Deserialize<'de>,
{
    Deserialize::deserialize(de).map(Some)
}

fn valid_obs_url(url: &str) -> bool {
    let url = url.trim();
    (url.starts_with("ws://") || url.starts_with("wss://")) && url.len() > 5
}

fn parse_hotkey(raw: &str) -> std::result::Result<Shortcut, String> {
    let text = raw.trim();
    if text.is_empty() {
        return Err("the hotkey is empty".into());
    }
    text.parse::<Shortcut>()
        .map_err(|_| format!("`{text}` is not a key combination the launcher understands"))
}

fn settings_from_bytes(bytes: &[u8]) -> ClipSettings {
    let mut settings: ClipSettings = serde_json::from_slice(bytes).unwrap_or_default();
    normalize(&mut settings);
    settings
}

fn normalize(settings: &mut ClipSettings) {
    if !valid_obs_url(&settings.obs_url) {
        settings.obs_url = DEFAULT_OBS_URL.into();
    }
    if settings.hotkey.trim().is_empty() {
        settings.hotkey = DEFAULT_HOTKEY.into();
    }
}

pub async fn load_settings() -> ClipSettings {
    match tokio::fs::read(paths::clips_settings_file()).await {
        Ok(bytes) => settings_from_bytes(&bytes),
        Err(_) => ClipSettings::default(),
    }
}

pub async fn save_settings(settings: &ClipSettings) -> Result<()> {
    write_settings_to(&paths::clips_dir(), settings).await
}

async fn write_settings_to(dir: &Path, settings: &ClipSettings) -> Result<()> {
    paths::ensure_dir(dir).await?;
    let bytes = serde_json::to_vec_pretty(settings)?;
    let tmp = dir.join("settings.json.tmp");

    let mut opts = tokio::fs::OpenOptions::new();
    opts.write(true).create(true).truncate(true);
    // the OBS password lives in this file, so it is not world-readable
    #[cfg(unix)]
    {
        opts.mode(0o600);
    }
    {
        use tokio::io::AsyncWriteExt;
        let mut file = opts.open(&tmp).await?;
        file.write_all(&bytes).await?;
        file.sync_all().await?;
    }

    let file = dir.join("settings.json");
    let _ = tokio::fs::remove_file(&file).await;
    tokio::fs::rename(&tmp, &file).await?;
    Ok(())
}

fn apply_patch(mut settings: ClipSettings, patch: ClipSettingsPatch) -> Result<ClipSettings> {
    if let Some(v) = patch.enabled {
        settings.enabled = v;
    }
    if let Some(v) = patch.obs_url {
        settings.obs_url = v.trim().to_owned();
    }
    if let Some(v) = patch.obs_password {
        settings.obs_password = v;
    }
    if let Some(v) = patch.hotkey {
        settings.hotkey = v.trim().to_owned();
    }
    if let Some(v) = patch.ffmpeg_path {
        settings.ffmpeg_path = v.map(|p| p.trim().to_owned()).filter(|p| !p.is_empty());
    }

    if !valid_obs_url(&settings.obs_url) {
        return Err(Error::Invalid(
            "the OBS address must start with ws:// or wss://".into(),
        ));
    }
    parse_hotkey(&settings.hotkey).map_err(Error::Invalid)?;
    Ok(settings)
}

pub async fn update_settings(patch: ClipSettingsPatch) -> Result<ClipSettings> {
    let settings = apply_patch(load_settings().await, patch)?;
    save_settings(&settings).await?;
    Ok(settings)
}

// ── index ─────────────────────────────────────────────────────

pub async fn load_index() -> Vec<Clip> {
    load_index_from(&paths::clips_dir()).await
}

/// The file as written, without dropping entries. Deleting a clip whose file
/// has already vanished has to succeed — the user asked for it gone, and it is.
async fn read_raw_index(dir: &Path) -> Vec<Clip> {
    match tokio::fs::read(dir.join("index.json")).await {
        Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_default(),
        Err(_) => Vec::new(),
    }
}

async fn load_index_from(dir: &Path) -> Vec<Clip> {
    let saved = read_raw_index(dir).await;

    let mut kept = Vec::with_capacity(saved.len());
    let mut changed = false;
    for mut clip in saved {
        if !tokio::fs::try_exists(&clip.path).await.unwrap_or(false) {
            changed = true;
            continue;
        }
        if let Some(thumb) = &clip.thumbnail_path {
            if !tokio::fs::try_exists(thumb).await.unwrap_or(false) {
                clip.thumbnail_path = None;
                changed = true;
            }
        }
        kept.push(clip);
    }
    kept.sort_by(|a, b| b.created_at.cmp(&a.created_at));

    if changed {
        let _ = write_index_to(dir, &kept).await;
    }
    kept
}

async fn write_index_to(dir: &Path, clips: &[Clip]) -> Result<()> {
    paths::ensure_dir(dir).await?;
    let bytes = serde_json::to_vec_pretty(clips)?;
    let tmp = dir.join("index.json.tmp");
    tokio::fs::write(&tmp, &bytes).await?;
    let file = dir.join("index.json");
    let _ = tokio::fs::remove_file(&file).await;
    tokio::fs::rename(&tmp, &file).await?;
    Ok(())
}

async fn write_index(clips: &[Clip]) -> Result<()> {
    write_index_to(&paths::clips_dir(), clips).await
}

/// Newest first, which is the order the grid shows and the order the file
/// itself is kept in.
fn order_newest_first(mut clips: Vec<Clip>) -> Vec<Clip> {
    clips.sort_by(|a, b| b.created_at.cmp(&a.created_at));
    clips
}

// ── ids and containment ───────────────────────────────────────

/// An id from the UI is a lookup key, not a path component.
fn sanitize_id(id: &str) -> String {
    let cleaned: String = id
        .chars()
        .map(|c| match c {
            'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' => c,
            _ => '_',
        })
        .collect();
    if cleaned.is_empty() {
        "clip".into()
    } else {
        cleaned
    }
}

/// True only for a path that resolves to a file strictly inside `dir`.
async fn contained(dir: &Path, candidate: &Path) -> bool {
    let Ok(dir) = tokio::fs::canonicalize(dir).await else {
        return false;
    };
    let Ok(candidate) = tokio::fs::canonicalize(candidate).await else {
        return false;
    };
    candidate.starts_with(&dir) && candidate != dir
}

fn video_extension(path: &Path) -> Option<String> {
    let ext = path.extension()?.to_str()?.to_ascii_lowercase();
    VIDEO_EXTENSIONS.contains(&ext.as_str()).then_some(ext)
}

async fn find_clip(id: &str) -> Result<Clip> {
    let id = sanitize_id(id);
    load_index()
        .await
        .into_iter()
        .find(|c| c.id == id)
        .ok_or_else(|| Error::NotFound(format!("clip {id}")))
}

async fn checked_path(raw: &str) -> Result<PathBuf> {
    let dir = paths::clips_dir();
    let candidate = PathBuf::from(raw);
    if !contained(&dir, &candidate).await {
        return Err(Error::Invalid(
            "that clip is not inside the launcher's clip folder".into(),
        ));
    }
    tokio::fs::canonicalize(&candidate)
        .await
        .map_err(Error::from)
}

// ── save / import ─────────────────────────────────────────────

pub async fn save_clip() -> Result<Clip> {
    let settings = load_settings().await;
    if !settings.enabled {
        return Err(Error::Invalid(
            "clip saving is turned off in the clip settings".into(),
        ));
    }

    let mut client = obs::Client::connect(&settings.obs_url, &settings.obs_password).await?;
    if !client.replay_active().await? {
        return Err(Error::Invalid(
            "the OBS replay buffer is not running — start it first, then save".into(),
        ));
    }

    let saved = client.save_replay_file().await?;
    import_file(&settings, Path::new(&saved)).await
}

async fn import_file(settings: &ClipSettings, source: &Path) -> Result<Clip> {
    if !tokio::fs::try_exists(source).await.unwrap_or(false) {
        return Err(Error::Invalid(format!(
            "OBS reported a save at {} but there is no file there",
            source.display()
        )));
    }
    let extension = video_extension(source).ok_or_else(|| {
        Error::Invalid(format!(
            "OBS saved {} with an extension the library does not accept",
            source.display()
        ))
    })?;

    let id = format!("clip-{}", uuid::Uuid::new_v4().simple());
    let dir = paths::clips_dir();
    paths::ensure_dir(&dir).await?;
    let destination = dir.join(format!("{id}.{extension}"));

    move_file(source, &destination).await?;

    let size = tokio::fs::metadata(&destination).await?.len();
    let duration = probe_duration(settings, &destination).await;
    let thumbnail = make_thumbnail(settings, &destination, &id, duration).await;

    let clip = Clip {
        id,
        path: destination.to_string_lossy().into_owned(),
        thumbnail_path: thumbnail.map(|p| p.to_string_lossy().into_owned()),
        created_at: now_iso(),
        duration_sec: duration,
        size_bytes: size,
    };

    let mut index = load_index().await;
    index.insert(0, clip.clone());
    let index = order_newest_first(index);
    write_index(&index).await?;
    Ok(clip)
}

/// Rename when the two are on one filesystem, otherwise copy first and only
/// remove the source once the copy succeeded.
async fn move_file(from: &Path, to: &Path) -> Result<()> {
    if tokio::fs::rename(from, to).await.is_ok() {
        return Ok(());
    }
    tokio::fs::copy(from, to).await?;
    tokio::fs::remove_file(from).await?;
    Ok(())
}

fn tool_for(settings: &ClipSettings, name: &str) -> PathBuf {
    if let Some(ffmpeg) = &settings.ffmpeg_path {
        let given = PathBuf::from(ffmpeg);
        if name == "ffmpeg" && given.is_file() {
            return given;
        }
        if let Some(dir) = given.parent() {
            let exe = if cfg!(windows) {
                format!("{name}.exe")
            } else {
                name.to_owned()
            };
            let sibling = dir.join(exe);
            if sibling.is_file() {
                return sibling;
            }
        }
    }
    PathBuf::from(name)
}

async fn probe_duration(settings: &ClipSettings, file: &Path) -> Option<f64> {
    let mut command = tokio::process::Command::new(tool_for(settings, "ffprobe"));
    command
        .args([
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
        ])
        .arg(file);

    let output = tokio::time::timeout(Duration::from_secs(10), command.output())
        .await
        .ok()?
        .ok()?;
    if !output.status.success() {
        return None;
    }
    parse_ffprobe_duration(&String::from_utf8_lossy(&output.stdout))
}

fn parse_ffprobe_duration(stdout: &str) -> Option<f64> {
    let seconds: f64 = stdout.lines().next()?.trim().parse().ok()?;
    (seconds.is_finite() && seconds > 0.0).then_some(seconds)
}

async fn make_thumbnail(
    settings: &ClipSettings,
    file: &Path,
    id: &str,
    duration: Option<f64>,
) -> Option<PathBuf> {
    let dir = paths::clips_thumbs_dir();
    paths::ensure_dir(&dir).await.ok()?;
    let out = dir.join(format!("{id}.jpg"));
    let at = duration.map(|d| (d * 0.5).max(0.1)).unwrap_or(0.5);

    let mut command = tokio::process::Command::new(tool_for(settings, "ffmpeg"));
    command
        .args(["-hide_banner", "-loglevel", "error", "-y", "-ss"])
        .arg(format!("{at:.3}"))
        .arg("-i")
        .arg(file)
        .args(["-frames:v", "1", "-vf", "scale=480:-2", "-q:v", "4"])
        .arg(&out);

    let status = tokio::time::timeout(Duration::from_secs(20), command.status())
        .await
        .ok()?
        .ok()?;
    if status.success() && tokio::fs::try_exists(&out).await.unwrap_or(false) {
        Some(out)
    } else {
        None
    }
}

// ── delete / reveal / open ────────────────────────────────────

pub async fn delete(id: &str) -> Result<()> {
    delete_from(&paths::clips_dir(), id).await
}

async fn delete_from(dir: &Path, id: &str) -> Result<()> {
    let id = sanitize_id(id);
    // the raw index, not the pruned one: a clip whose file is already gone must
    // still be deletable, because the user asked for it to be gone
    let mut index = read_raw_index(dir).await;
    let position = index
        .iter()
        .position(|c| c.id == id)
        .ok_or_else(|| Error::NotFound(format!("clip {id}")))?;
    let clip = index.remove(position);

    let targets = std::iter::once(Some(PathBuf::from(&clip.path)))
        .chain(std::iter::once(
            clip.thumbnail_path.as_deref().map(PathBuf::from),
        ))
        .flatten();
    for target in targets {
        if contained(dir, &target).await {
            let _ = tokio::fs::remove_file(&target).await;
        }
    }

    write_index_to(dir, &order_newest_first(index)).await
}

pub async fn reveal(id: &str) -> Result<()> {
    let clip = find_clip(id).await?;
    let path = checked_path(&clip.path).await?;
    spawn(reveal_command(&path))
}

pub async fn open(id: &str) -> Result<()> {
    let clip = find_clip(id).await?;
    let path = checked_path(&clip.path).await?;
    spawn(open_command(&path))
}

fn spawn(mut command: tokio::process::Command) -> Result<()> {
    command.spawn().map(|_| ()).map_err(Error::from)
}

#[cfg(windows)]
fn reveal_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("explorer");
    command.arg(format!("/select,{}", path.display()));
    command
}

#[cfg(windows)]
fn open_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("cmd");
    command.args(["/C", "start", ""]).arg(path);
    command
}

#[cfg(target_os = "macos")]
fn reveal_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("open");
    command.arg("-R").arg(path);
    command
}

#[cfg(target_os = "macos")]
fn open_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("open");
    command.arg(path);
    command
}

#[cfg(all(unix, not(target_os = "macos")))]
fn reveal_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("xdg-open");
    command.arg(path.parent().unwrap_or(path));
    command
}

#[cfg(all(unix, not(target_os = "macos")))]
fn open_command(path: &Path) -> tokio::process::Command {
    let mut command = tokio::process::Command::new("xdg-open");
    command.arg(path);
    command
}

// ── status / replay buffer ────────────────────────────────────

pub async fn replay_start() -> Result<()> {
    let settings = load_settings().await;
    let mut client = obs::Client::connect(&settings.obs_url, &settings.obs_password).await?;
    if !client.replay_active().await? {
        client.request("StartReplayBuffer", json!({})).await?;
    }
    Ok(())
}

pub async fn replay_stop() -> Result<()> {
    let settings = load_settings().await;
    let mut client = obs::Client::connect(&settings.obs_url, &settings.obs_password).await?;
    if client.replay_active().await? {
        client.request("StopReplayBuffer", json!({})).await?;
    }
    Ok(())
}

pub async fn status(app: &AppHandle) -> ClipStatus {
    let settings = load_settings().await;
    let hotkey_registered = settings.enabled
        && parse_hotkey(&settings.hotkey)
            .map(|shortcut| app.global_shortcut().is_registered(shortcut))
            .unwrap_or(false);

    match obs::Client::connect(&settings.obs_url, &settings.obs_password).await {
        Ok(mut client) => match client.replay_active().await {
            Ok(active) => ClipStatus {
                obs: ClipObsState::Connected,
                replay_buffer_active: active,
                hotkey_registered,
                message: None,
            },
            Err(e) => ClipStatus {
                obs: ClipObsState::Error,
                replay_buffer_active: false,
                hotkey_registered,
                message: Some(e.to_string()),
            },
        },
        Err(e) => {
            let state = match e {
                obs::Error::Auth(_) | obs::Error::Protocol(_) => ClipObsState::Error,
                obs::Error::Transport(_) => ClipObsState::Disconnected,
            };
            ClipStatus {
                obs: state,
                replay_buffer_active: false,
                hotkey_registered,
                message: Some(e.to_string()),
            }
        }
    }
}

// ── hotkey ────────────────────────────────────────────────────

/// Rebuilds the registration from settings. A shortcut another application
/// already owns is reported, not fatal — the rest of the clip surface still
/// works from the UI.
pub async fn install_hotkey(app: &AppHandle) -> bool {
    let settings = load_settings().await;
    let manager = app.global_shortcut();
    if let Err(e) = manager.unregister_all() {
        eprintln!("[clips] could not release the old hotkey: {e}");
    }
    if !settings.enabled {
        return false;
    }

    let shortcut = match parse_hotkey(&settings.hotkey) {
        Ok(shortcut) => shortcut,
        Err(e) => {
            eprintln!("[clips] hotkey not installed: {e}");
            return false;
        }
    };

    match manager.on_shortcut(shortcut, |app, _shortcut, event| {
        if event.state != ShortcutState::Pressed {
            return;
        }
        let app = app.clone();
        tauri::async_runtime::spawn(async move {
            match save_clip().await {
                Ok(clip) => {
                    let _ = app.emit("clips://saved", clip);
                }
                Err(e) => {
                    let _ = app.emit(
                        "clips://error",
                        ClipError {
                            message: e.to_string(),
                        },
                    );
                }
            }
        });
    }) {
        Ok(()) => true,
        Err(e) => {
            eprintln!("[clips] could not register {}: {e}", settings.hotkey);
            false
        }
    }
}

fn now_iso() -> String {
    time::OffsetDateTime::now_utc()
        .format(&time::format_description::well_known::Rfc3339)
        .unwrap_or_else(|_| "1970-01-01T00:00:00Z".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "fullmoon-clips-{name}-{}-{}",
            std::process::id(),
            uuid::Uuid::new_v4().simple()
        ));
        std::fs::create_dir_all(&dir).expect("scratch dir");
        dir
    }

    fn clip(id: &str, created: &str, path: PathBuf) -> Clip {
        Clip {
            id: id.into(),
            path: path.to_string_lossy().into_owned(),
            thumbnail_path: None,
            created_at: created.into(),
            duration_sec: Some(12.5),
            size_bytes: 1024,
        }
    }

    #[test]
    fn settings_default_to_a_local_obs_and_f8() {
        let settings = ClipSettings::default();
        assert!(settings.enabled);
        assert_eq!(settings.obs_url, "ws://127.0.0.1:4455");
        assert_eq!(settings.obs_password, "");
        assert_eq!(settings.hotkey, "F8");
        assert!(settings.ffmpeg_path.is_none());
    }

    #[test]
    fn a_patch_changes_only_what_it_names() {
        let patched = apply_patch(
            ClipSettings::default(),
            ClipSettingsPatch {
                enabled: Some(false),
                obs_url: Some("wss://obs.example:4455".into()),
                obs_password: Some("secret".into()),
                hotkey: Some("Ctrl+Shift+F9".into()),
                ffmpeg_path: Some(Some("/opt/ffmpeg/bin/ffmpeg".into())),
            },
        )
        .expect("valid patch");

        assert!(!patched.enabled);
        assert_eq!(patched.obs_url, "wss://obs.example:4455");
        assert_eq!(patched.obs_password, "secret");
        assert_eq!(patched.hotkey, "Ctrl+Shift+F9");
        assert_eq!(
            patched.ffmpeg_path.as_deref(),
            Some("/opt/ffmpeg/bin/ffmpeg")
        );

        let cleared = apply_patch(
            patched,
            ClipSettingsPatch {
                enabled: None,
                obs_url: None,
                obs_password: None,
                hotkey: None,
                ffmpeg_path: Some(None),
            },
        )
        .expect("clearing is valid");
        assert!(cleared.ffmpeg_path.is_none());
        assert_eq!(cleared.obs_password, "secret");
    }

    #[test]
    fn a_bad_address_or_hotkey_is_refused() {
        let bad_url = apply_patch(
            ClipSettings::default(),
            ClipSettingsPatch {
                enabled: None,
                obs_url: Some("http://127.0.0.1:4455".into()),
                obs_password: None,
                hotkey: None,
                ffmpeg_path: None,
            },
        );
        assert!(bad_url.is_err());

        let bad_key = apply_patch(
            ClipSettings::default(),
            ClipSettingsPatch {
                enabled: None,
                obs_url: None,
                obs_password: None,
                hotkey: Some("   ".into()),
                ffmpeg_path: None,
            },
        );
        assert!(bad_key.is_err());
    }

    #[test]
    fn corrupt_settings_fall_back_to_defaults() {
        let settings = settings_from_bytes(b"{ not json");
        assert_eq!(settings.obs_url, ClipSettings::default().obs_url);
        assert_eq!(settings.hotkey, "F8");
    }

    #[tokio::test]
    async fn settings_round_trip_and_are_private_on_unix() {
        let dir = scratch("settings");
        let settings = ClipSettings {
            enabled: false,
            obs_url: "ws://10.0.0.5:4455".into(),
            obs_password: "hunter2".into(),
            hotkey: "F9".into(),
            ffmpeg_path: None,
        };
        write_settings_to(&dir, &settings).await.expect("write");

        let bytes = std::fs::read(dir.join("settings.json")).expect("read");
        assert_eq!(settings_from_bytes(&bytes), settings);

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = std::fs::metadata(dir.join("settings.json"))
                .expect("metadata")
                .permissions()
                .mode();
            assert_eq!(mode & 0o777, 0o600);
        }

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[tokio::test]
    async fn the_index_keeps_existing_clips_newest_first_and_drops_ghosts() {
        let dir = scratch("index");
        let present = dir.join("kept.mkv");
        std::fs::write(&present, b"x").expect("clip file");

        let entries = vec![
            clip("older", "2026-01-01T00:00:00Z", present.clone()),
            clip("ghost", "2026-02-01T00:00:00Z", dir.join("gone.mkv")),
            clip("newer", "2026-03-01T00:00:00Z", present.clone()),
        ];
        write_index_to(&dir, &entries).await.expect("write index");

        let listed = load_index_from(&dir).await;
        assert_eq!(
            listed.iter().map(|c| c.id.as_str()).collect::<Vec<_>>(),
            ["newer", "older"]
        );

        // the pruned list is written back, so the ghost does not return
        let reread = load_index_from(&dir).await;
        assert_eq!(reread.len(), 2);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn ids_cannot_carry_a_path_out_of_the_clip_folder() {
        assert_eq!(sanitize_id("../../etc/passwd"), "______etc_passwd");
        assert!(!sanitize_id("..\\..\\windows").contains('\\'));
        assert_eq!(sanitize_id(""), "clip");
        assert_eq!(sanitize_id("clip-abc_123"), "clip-abc_123");
    }

    #[tokio::test]
    async fn containment_is_true_only_inside_the_folder() {
        let root = scratch("contain");
        let inside = root.join("clips");
        std::fs::create_dir_all(&inside).expect("clips dir");
        let good = inside.join("one.mkv");
        let outside = root.join("escape.mkv");
        std::fs::write(&good, b"x").expect("inside file");
        std::fs::write(&outside, b"x").expect("outside file");

        assert!(contained(&inside, &good).await);
        assert!(!contained(&inside, &outside).await);
        assert!(!contained(&inside, &inside).await);

        let _ = std::fs::remove_dir_all(&root);
    }

    #[tokio::test]
    async fn deleting_drops_the_entry_even_when_the_file_is_already_gone() {
        let dir = scratch("delete-gone");
        std::fs::write(dir.join("thumb.jpg"), b"x").expect("thumb");
        let entries = vec![Clip {
            thumbnail_path: Some(dir.join("thumb.jpg").to_string_lossy().into_owned()),
            ..clip("gone", "2026-01-01T00:00:00Z", dir.join("gone.mkv"))
        }];
        write_index_to(&dir, &entries).await.expect("write index");

        delete_from(&dir, "gone").await.expect("delete");
        assert!(read_raw_index(&dir).await.is_empty());
        assert!(
            !dir.join("thumb.jpg").exists(),
            "the thumbnail goes with it"
        );

        assert!(delete_from(&dir, "gone").await.is_err(), "and only once");

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn only_known_video_extensions_are_accepted() {
        assert_eq!(
            video_extension(Path::new("/tmp/a.MKV")).as_deref(),
            Some("mkv")
        );
        assert_eq!(
            video_extension(Path::new("/tmp/a.mp4")).as_deref(),
            Some("mp4")
        );
        assert!(video_extension(Path::new("/tmp/a.exe")).is_none());
        assert!(video_extension(Path::new("/tmp/a")).is_none());
    }

    #[test]
    fn ffprobe_duration_is_read_from_its_first_line() {
        assert_eq!(parse_ffprobe_duration("12.345000\n"), Some(12.345));
        assert_eq!(parse_ffprobe_duration("0.5"), Some(0.5));
        assert!(parse_ffprobe_duration("N/A").is_none());
        assert!(parse_ffprobe_duration("0").is_none());
        assert!(parse_ffprobe_duration("").is_none());
    }

    #[test]
    fn a_hotkey_must_parse_to_something_registerable() {
        assert!(parse_hotkey("F8").is_ok());
        assert!(parse_hotkey("Ctrl+Shift+F9").is_ok());
        assert!(parse_hotkey("").is_err());
        assert!(parse_hotkey("   ").is_err());
        assert!(parse_hotkey("not-a-key").is_err());
    }

    #[tokio::test]
    async fn saving_without_obs_reports_the_missing_connection() {
        // nothing listens on port 1, so this is the same failure `clips_save`
        // hands back when OBS is not running
        let error = obs::Client::connect("ws://127.0.0.1:1", "")
            .await
            .expect_err("no OBS on port 1");
        assert!(matches!(error, obs::Error::Transport(_)));
        let surfaced = Error::from(error).to_string();
        assert!(surfaced.contains("127.0.0.1:1"), "{surfaced}");
    }
}
