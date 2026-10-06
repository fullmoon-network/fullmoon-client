/* Every path the launcher touches is derived here from one root, so an
   instance can never write outside its own directory (MultiMC-style
   isolation, PLAN §1) and a rebrand moves the whole tree at once. */
use std::path::{Path, PathBuf};

pub const BRAND_NAME: &str = env!("PINION_NAME");
pub const BRAND_SLUG: &str = env!("PINION_SLUG");

/// `%APPDATA%/Pinion` on Windows, `~/.local/share/pinion` elsewhere.
///
/// `<SLUG>_DATA_ROOT` moves the whole tree. `dirs` asks Windows for the known
/// folder rather than reading `%APPDATA%`, so pointing the environment at a
/// scratch profile is otherwise impossible — which is what testing an install
/// the way a new machine meets it needs.
pub fn root() -> PathBuf {
    if let Ok(over) = std::env::var(format!("{}_DATA_ROOT", BRAND_SLUG.to_uppercase())) {
        if !over.trim().is_empty() {
            return PathBuf::from(over.trim());
        }
    }
    let base = if cfg!(windows) {
        dirs::data_dir()
    } else {
        dirs::data_local_dir()
    };
    base.unwrap_or_else(|| PathBuf::from("."))
        .join(if cfg!(windows) { BRAND_NAME } else { BRAND_SLUG })
}

pub fn settings_file() -> PathBuf {
    root().join("settings.json")
}

pub fn instances_file() -> PathBuf {
    root().join("instances.json")
}

pub fn accounts_file() -> PathBuf {
    root().join("accounts.json")
}

/// Session tokens, kept apart from the profiles so nothing the UI reads can
/// ever carry one.
pub fn sessions_file() -> PathBuf {
    root().join("sessions.json")
}

pub fn servers_file() -> PathBuf {
    root().join("servers.json")
}

pub fn cosmetics_file() -> PathBuf {
    root().join("cosmetics.json")
}

/// Shared across instances: version metadata, libraries, assets, JREs.
pub fn shared() -> PathBuf {
    root().join("shared")
}

pub fn versions_dir() -> PathBuf {
    shared().join("versions")
}

pub fn libraries_dir() -> PathBuf {
    shared().join("libraries")
}

pub fn assets_dir() -> PathBuf {
    shared().join("assets")
}

pub fn instances_dir() -> PathBuf {
    root().join("instances")
}

/// The game directory for one instance — its own saves, configs and mods.
pub fn instance_dir(id: &str) -> PathBuf {
    instances_dir().join(sanitize(id))
}

pub fn instance_minecraft_dir(id: &str) -> PathBuf {
    instance_dir(id).join("minecraft")
}

pub fn instance_mods_dir(id: &str) -> PathBuf {
    instance_minecraft_dir(id).join("mods")
}

/// The mod's own namespace (`fabric.mod.json` id), which is what FabricLoader puts under
/// `config/` — not the launcher's brand slug, even where the two read the same.
const MOD_NAMESPACE: &str = "fullmoon";

/// `FabricLoader.getConfigDir()` resolves against the game directory, and `launch::plan` hands
/// the game `instance_minecraft_dir` as both `--gameDir` and its working directory. So this is
/// the exact path the running mod opens.
fn instance_mod_config_dir(id: &str) -> PathBuf {
    instance_minecraft_dir(id).join("config").join(MOD_NAMESPACE)
}

/// Written by the launcher's HUD editor and by the in-game one, read by the mod on a poll.
pub fn instance_hud_file(id: &str) -> PathBuf {
    instance_mod_config_dir(id).join("hud.json")
}

/// The equipped cosmetics of whichever account launched this instance. Written for a reader
/// that does not exist yet — see `cosmetics.rs`.
pub fn instance_cosmetics_file(id: &str) -> PathBuf {
    instance_mod_config_dir(id).join("cosmetics.json")
}

pub fn instance_state_file(id: &str) -> PathBuf {
    instance_dir(id).join("instance.json")
}

/// What `mods::apply` last put in `mods/` and how it was verified — see `modlock.rs`.
pub fn instance_mod_lock_file(id: &str) -> PathBuf {
    instance_dir(id).join("mods.lock.json")
}

/// The launcher's own shipped files — the Pinion mod jar lives here. An
/// installed build has them beside the binary; `tauri dev` runs from the crate,
/// where they are still only in the source tree.
pub fn resources(app: &tauri::AppHandle) -> PathBuf {
    use tauri::Manager;
    if let Ok(dir) = app.path().resource_dir() {
        let nested = dir.join("resources");
        if nested.is_dir() {
            return nested;
        }
        if dir.join("mods").is_dir() {
            return dir;
        }
    }
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("resources")
}

/// Ids come from the UI, so they never get to walk out of the tree.
fn sanitize(id: &str) -> String {
    id.chars()
        .map(|c| match c {
            'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' => c,
            _ => '_',
        })
        .collect()
}

pub async fn ensure_dir(p: &Path) -> std::io::Result<()> {
    tokio::fs::create_dir_all(p).await
}
