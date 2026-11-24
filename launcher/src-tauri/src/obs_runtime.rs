use std::path::{Path, PathBuf};

use serde::Deserialize;
use serde_json::json;

use crate::{
    error::{Error, Result},
    paths,
};

pub const PROFILE: &str = "fullmoon";
pub const COLLECTION: &str = "fullmoon";
pub const DEFAULT_PORT: u16 = 4466;

const OBS_DEFAULT_PORT: u16 = 4455;
const PORT_ATTEMPTS: u16 = 20;

#[derive(Deserialize)]
struct StoredRuntime {
    port: u16,
    password: String,
}

pub struct ObsRuntime {
    pub port: u16,
    password: String,
}

impl std::fmt::Debug for ObsRuntime {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ObsRuntime")
            .field("port", &self.port)
            .field("password", &"<redacted>")
            .finish()
    }
}

pub struct LaunchPlan {
    pub program: PathBuf,
    pub args: Vec<String>,
    pub env: Vec<(String, String)>,
}

fn config_root(obs_root: &Path) -> PathBuf {
    if cfg!(windows) {
        obs_root.join("appdata")
    } else if cfg!(target_os = "macos") {
        obs_root.join("home")
    } else {
        obs_root.join("config")
    }
}

fn config_env_key() -> &'static str {
    if cfg!(windows) {
        "APPDATA"
    } else if cfg!(target_os = "macos") {
        "HOME"
    } else {
        "XDG_CONFIG_HOME"
    }
}

fn obs_studio_dir(obs_root: &Path) -> PathBuf {
    let root = config_root(obs_root);
    if cfg!(target_os = "macos") {
        root.join("Library")
            .join("Application Support")
            .join("obs-studio")
    } else {
        root.join("obs-studio")
    }
}

fn websocket_config_path(obs_root: &Path) -> PathBuf {
    obs_studio_dir(obs_root)
        .join("plugin_config")
        .join("obs-websocket")
        .join("config.json")
}

fn generated_password() -> String {
    format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    )
}

async fn pick_port(preferred: u16) -> Result<u16> {
    let start = if preferred == 0 || preferred == OBS_DEFAULT_PORT {
        DEFAULT_PORT
    } else {
        preferred
    };
    for offset in 0..PORT_ATTEMPTS {
        let Some(port) = start.checked_add(offset) else {
            break;
        };
        if tokio::net::TcpListener::bind(("127.0.0.1", port))
            .await
            .is_ok()
        {
            return Ok(port);
        }
    }
    Err(Error::Invalid(format!(
        "no free recording engine port in {PORT_ATTEMPTS} ports from {start}"
    )))
}

async fn write_private_json(path: &Path, value: &serde_json::Value) -> Result<()> {
    if let Some(parent) = path.parent() {
        paths::ensure_dir(parent).await?;
    }
    let bytes = serde_json::to_vec_pretty(value)?;
    let tmp = path.with_extension("json.tmp");

    let mut opts = tokio::fs::OpenOptions::new();
    opts.write(true).create(true).truncate(true);
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

    let _ = tokio::fs::remove_file(path).await;
    tokio::fs::rename(&tmp, path).await?;
    Ok(())
}

fn stored_is_valid(stored: &StoredRuntime) -> bool {
    stored.port != 0 && stored.port != OBS_DEFAULT_PORT && !stored.password.is_empty()
}

#[allow(dead_code)]
pub async fn provision() -> Result<ObsRuntime> {
    provision_in(&paths::obs_dir(), DEFAULT_PORT).await
}

async fn provision_in(obs_root: &Path, preferred_port: u16) -> Result<ObsRuntime> {
    paths::ensure_dir(obs_root).await?;
    paths::ensure_dir(&config_root(obs_root)).await?;
    let studio = obs_studio_dir(obs_root);
    paths::ensure_dir(&studio).await?;
    paths::ensure_dir(&studio.join("plugin_config").join("obs-websocket")).await?;
    paths::ensure_dir(&studio.join("basic").join("profiles").join(PROFILE)).await?;
    paths::ensure_dir(&studio.join("basic").join("scenes")).await?;

    let reused = match tokio::fs::read(obs_root.join("runtime.json")).await {
        Ok(bytes) => serde_json::from_slice::<StoredRuntime>(&bytes)
            .ok()
            .filter(stored_is_valid)
            .map(|stored| ObsRuntime {
                port: stored.port,
                password: stored.password,
            }),
        Err(_) => None,
    };
    let runtime = match reused {
        Some(existing) => existing,
        None => ObsRuntime {
            port: pick_port(preferred_port).await?,
            password: generated_password(),
        },
    };

    write_private_json(
        &obs_root.join("runtime.json"),
        &json!({ "port": runtime.port, "password": runtime.password }),
    )
    .await?;
    write_private_json(
        &websocket_config_path(obs_root),
        &json!({
            "server_enabled": true,
            "server_port": runtime.port,
            "auth_required": true,
            "server_password": runtime.password,
        }),
    )
    .await?;
    Ok(runtime)
}

pub fn launch_plan(exe: &Path, runtime: &ObsRuntime, obs_root: &Path) -> LaunchPlan {
    let _ = runtime;
    LaunchPlan {
        program: exe.to_path_buf(),
        args: vec![
            "--multi".into(),
            "--disable-shutdown-check".into(),
            "--minimize-to-tray".into(),
            "--startreplaybuffer".into(),
            "--profile".into(),
            PROFILE.into(),
            "--collection".into(),
            COLLECTION.into(),
        ],
        env: vec![(
            config_env_key().into(),
            config_root(obs_root).to_string_lossy().into_owned(),
        )],
    }
}

#[allow(dead_code)]
pub async fn launch(exe: &Path, runtime: &ObsRuntime) -> Result<tokio::process::Child> {
    if !exe.is_file() {
        return Err(Error::Invalid(format!(
            "the recording engine executable is not a file at {}",
            exe.display()
        )));
    }
    let plan = launch_plan(exe, runtime, &paths::obs_dir());
    let mut command = tokio::process::Command::new(&plan.program);
    command.args(&plan.args);
    for (key, value) in &plan.env {
        command.env(key, value);
    }
    command
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    Ok(command.spawn()?)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "fullmoon-obs-runtime-{name}-{}-{}",
            std::process::id(),
            uuid::Uuid::new_v4().simple()
        ));
        std::fs::create_dir_all(&dir).expect("scratch dir");
        dir
    }

    fn read_json(path: &Path) -> serde_json::Value {
        let bytes = std::fs::read(path).expect("read json");
        serde_json::from_slice(&bytes).expect("parse json")
    }

    #[cfg(unix)]
    fn assert_private(path: &Path) {
        use std::os::unix::fs::PermissionsExt;
        let mode = std::fs::metadata(path)
            .expect("metadata")
            .permissions()
            .mode();
        assert_eq!(mode & 0o777, 0o600, "{} must be private", path.display());
    }

    #[tokio::test]
    async fn provision_creates_private_runtime_and_websocket_config() {
        let root = scratch("create");
        let runtime = provision_in(&root, DEFAULT_PORT).await.expect("provision");

        let runtime_path = root.join("runtime.json");
        let stored = read_json(&runtime_path);
        assert_eq!(stored["port"].as_u64(), Some(runtime.port as u64));
        assert!(stored["password"].as_str() == Some(runtime.password.as_str()));

        let ws_path = websocket_config_path(&root);
        let ws = read_json(&ws_path);
        assert!(ws["server_enabled"].as_bool() == Some(true));
        assert!(ws["auth_required"].as_bool() == Some(true));
        assert_eq!(ws["server_port"].as_u64(), Some(runtime.port as u64));
        assert!(ws["server_password"].as_str() == Some(runtime.password.as_str()));

        #[cfg(unix)]
        {
            assert_private(&runtime_path);
            assert_private(&ws_path);
        }

        let _ = std::fs::remove_dir_all(&root);
    }

    #[tokio::test]
    async fn provision_is_idempotent_and_never_rotates_the_password() {
        let root = scratch("idempotent");
        let first = provision_in(&root, DEFAULT_PORT)
            .await
            .expect("first provision");
        let second = provision_in(&root, DEFAULT_PORT)
            .await
            .expect("second provision");
        assert_eq!(first.port, second.port);
        assert!(first.password == second.password);

        let stored = read_json(&root.join("runtime.json"));
        assert_eq!(stored["port"].as_u64(), Some(second.port as u64));
        let ws = read_json(&websocket_config_path(&root));
        assert_eq!(ws["server_port"].as_u64(), Some(second.port as u64));
        assert!(ws["server_password"].as_str() == Some(second.password.as_str()));

        let _ = std::fs::remove_dir_all(&root);
    }

    #[tokio::test]
    async fn a_corrupt_runtime_file_is_reprovisioned() {
        let root = scratch("corrupt");
        std::fs::write(root.join("runtime.json"), b"{not json").expect("corrupt file");
        let runtime = provision_in(&root, DEFAULT_PORT)
            .await
            .expect("reprovision");
        assert!(!runtime.password.is_empty());

        let stored = read_json(&root.join("runtime.json"));
        assert_eq!(stored["port"].as_u64(), Some(runtime.port as u64));
        assert!(stored["password"].as_str() == Some(runtime.password.as_str()));

        let _ = std::fs::remove_dir_all(&root);
    }

    #[tokio::test]
    async fn the_default_port_is_never_obs_websockets_4455() {
        for preferred in [4455u16, 0] {
            let root = scratch(&format!("default-port-{preferred}"));
            let runtime = provision_in(&root, preferred).await.expect("provision");
            assert_ne!(runtime.port, 4455);

            let ws = read_json(&websocket_config_path(&root));
            assert_ne!(ws["server_port"].as_u64(), Some(4455));

            let _ = std::fs::remove_dir_all(&root);
        }
    }

    #[tokio::test]
    async fn an_occupied_port_is_skipped() {
        let root = scratch("occupied");
        let listener = tokio::net::TcpListener::bind(("127.0.0.1", 0))
            .await
            .expect("hold port");
        let held = listener.local_addr().expect("local addr").port();
        let runtime = provision_in(&root, held).await.expect("provision");
        assert_ne!(runtime.port, held);

        drop(listener);
        assert!(tokio::net::TcpListener::bind(("127.0.0.1", runtime.port))
            .await
            .is_ok());

        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn launch_plan_is_multi_instance_and_isolated() {
        let root = scratch("plan");
        let exe = root.join("obs");
        let runtime = ObsRuntime {
            port: DEFAULT_PORT,
            password: "unit-secret".into(),
        };
        let plan = launch_plan(&exe, &runtime, &root);

        assert_eq!(plan.program, exe);
        assert!(plan.args.iter().any(|a| a == "--multi"));
        assert!(plan.args.iter().any(|a| a == "--profile"));
        assert!(plan.args.iter().any(|a| a == "fullmoon"));
        assert!(plan.args.iter().any(|a| a == "--collection"));
        assert!(plan.args.iter().all(|a| !a.contains("4455")));

        assert_eq!(plan.env.len(), 1);
        let (key, value) = &plan.env[0];
        assert!(matches!(
            key.as_str(),
            "APPDATA" | "HOME" | "XDG_CONFIG_HOME"
        ));
        let scoped = Path::new(value);
        assert!(scoped.starts_with(&root));
        let real = std::env::var_os(key)
            .map(PathBuf::from)
            .or_else(dirs::config_dir)
            .unwrap_or_default();
        assert_ne!(scoped, real.as_path());

        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn websocket_config_lands_where_obs_reads_it() {
        let root = scratch("ws-path");
        let path = websocket_config_path(&root);
        assert!(path.starts_with(&root));
        let flat = path.to_string_lossy().replace('\\', "/");
        assert!(
            flat.ends_with("obs-studio/plugin_config/obs-websocket/config.json"),
            "{flat}"
        );
        #[cfg(target_os = "macos")]
        assert!(
            flat.contains("Library/Application Support/obs-studio"),
            "{flat}"
        );

        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn debug_output_redacts_the_password() {
        let secret = "unit-secret-value";
        let runtime = ObsRuntime {
            port: DEFAULT_PORT,
            password: secret.into(),
        };
        let shown = format!("{runtime:?}");
        assert!(!shown.contains(secret));
        assert!(shown.contains("<redacted>"));
    }

    #[test]
    fn generated_passwords_are_long_and_distinct() {
        let first = generated_password();
        let second = generated_password();
        assert!(first.len() >= 32);
        assert!(second.len() >= 32);
        assert!(first.chars().all(|c| c.is_ascii_hexdigit()));
        assert!(second.chars().all(|c| c.is_ascii_hexdigit()));
        assert!(first != second);
    }
}
