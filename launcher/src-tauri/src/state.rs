use std::{
    collections::{HashMap, VecDeque},
    sync::Arc,
};

use tokio::sync::{oneshot, Mutex};

use crate::{
    auth::DeviceFlow,
    error::Result,
    meta,
    model::{Account, GameState, GameStateValue, Instance, LogEvent, Settings},
    paths, spec, store,
};

const MANAGED_INSTANCE_ID: &str = "fullmoon-managed";
const FULLMOON_SERVER: &str = "play.fullmoon.ink";

pub struct AppState {
    pub http: reqwest::Client,
    pub settings: Mutex<Settings>,
    pub instances: Mutex<Vec<Instance>>,
    pub active_account: Mutex<Option<String>>,
    /// Device-code sign-ins in flight. In memory only — a code that outlives
    /// the launcher is a code the user has to request again anyway.
    pub device_flows: Mutex<HashMap<String, DeviceFlow>>,
    /// Shared with the process watcher, which updates it from its own task.
    pub game: Arc<Mutex<GameState>>,
    /// What the game has said so far. Events alone are not enough: a console
    /// opened after launch would be blank until the game happened to speak,
    /// and a running client can stay quiet for minutes.
    pub log: Arc<Mutex<VecDeque<LogEvent>>>,
    /// Dropping this sender is harmless; sending on it kills the game.
    pub kill: Mutex<Option<oneshot::Sender<()>>>,
}

impl AppState {
    pub async fn load() -> Result<Self> {
        let mut settings: Settings =
            store::read_or(&paths::settings_file(), Settings::default).await;
        let mut saved_instances: Vec<Instance> =
            store::read_or(&paths::instances_file(), Vec::new).await;
        // before the managed instance is made, so a first start gets the tuned heap too
        if spec::tune_heap(&mut settings, &mut saved_instances, spec::total_memory_mb().await) {
            // a save that fails only means the tuning runs again next start
            let _ = store::write(&paths::settings_file(), &settings).await;
            if !saved_instances.is_empty() {
                let _ = store::write(&paths::instances_file(), &saved_instances).await;
            }
        }
        let (instances, created) = bootstrap_instances(saved_instances, &settings, &now_iso());

        if created {
            paths::ensure_dir(&paths::instance_minecraft_dir(MANAGED_INSTANCE_ID)).await?;
            paths::ensure_dir(&paths::instance_mods_dir(MANAGED_INSTANCE_ID)).await?;
            store::write(&paths::instances_file(), &instances).await?;
        }

        let accounts: Vec<Account> = store::read_or(&paths::accounts_file(), Vec::new).await;

        let http = reqwest::Client::builder()
            .user_agent(concat!(env!("PINION_NAME"), "/", env!("CARGO_PKG_VERSION")))
            .connect_timeout(std::time::Duration::from_secs(15))
            .build()
            .expect("http client");

        Ok(Self {
            http,
            settings: Mutex::new(settings),
            instances: Mutex::new(instances),
            active_account: Mutex::new(accounts.first().map(|a| a.uuid.clone())),
            device_flows: Mutex::new(HashMap::new()),
            game: Arc::new(Mutex::new(GameState {
                state: GameStateValue::Idle,
                session_id: None,
                instance_id: None,
                server: None,
                started_at: None,
                exit_code: None,
            })),
            log: Arc::new(Mutex::new(VecDeque::new())),
            kill: Mutex::new(None),
        })
    }
}

fn bootstrap_instances(
    instances: Vec<Instance>,
    settings: &Settings,
    created_at: &str,
) -> (Vec<Instance>, bool) {
    if instances.is_empty() {
        return (
            vec![Instance {
                id: MANAGED_INSTANCE_ID.into(),
                name: format!("{} {}", paths::BRAND_NAME, meta::TARGET_VERSION),
                version_id: meta::TARGET_VERSION.into(),
                loader: "fabric".into(),
                installed: false,
                installing: None,
                memory_mb: settings.memory_mb,
                icon_hue: 45,
                created_at: created_at.into(),
                last_played_at: None,
                quick_play_server: Some(FULLMOON_SERVER.into()),
            }],
            true,
        );
    }

    let recovered = instances
        .into_iter()
        .map(|instance| Instance {
            installing: None,
            ..instance
        })
        .collect();
    (recovered, false)
}

fn now_iso() -> String {
    time::OffsetDateTime::now_utc()
        .format(&time::format_description::well_known::Rfc3339)
        .unwrap_or_else(|_| "1970-01-01T00:00:00Z".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{InstallProgress, InstallStage};

    #[test]
    fn a_clean_profile_gets_the_managed_fullmoon_instance() {
        let settings = Settings {
            memory_mb: 6144,
            ..Settings::default()
        };
        let (instances, created) = bootstrap_instances(
            Vec::new(),
            &settings,
            "2026-08-31T00:00:00Z",
        );

        assert!(created);
        assert_eq!(instances.len(), 1);
        let instance = &instances[0];
        assert_eq!(instance.id, "fullmoon-managed");
        assert_eq!(instance.name, "Fullmoon 26.1.2");
        assert_eq!(instance.version_id, "26.1.2");
        assert_eq!(instance.loader, "fabric");
        assert_eq!(instance.memory_mb, 6144);
        assert_eq!(instance.quick_play_server.as_deref(), Some("play.fullmoon.ink"));
        assert!(!instance.installed);
        assert!(instance.installing.is_none());
    }

    #[test]
    fn an_existing_profile_is_recovered_without_adding_another_instance() {
        let existing = Instance {
            id: "kept".into(),
            name: "Kept".into(),
            version_id: "26.1.2".into(),
            loader: "fabric".into(),
            installed: false,
            installing: Some(InstallProgress {
                stage: InstallStage::Assets,
                pct: 42.0,
            }),
            memory_mb: 4096,
            icon_hue: 12,
            created_at: "earlier".into(),
            last_played_at: None,
            quick_play_server: None,
        };

        let (instances, created) = bootstrap_instances(
            vec![existing],
            &Settings::default(),
            "2026-08-31T00:00:00Z",
        );

        assert!(!created);
        assert_eq!(instances.len(), 1);
        assert_eq!(instances[0].id, "kept");
        assert!(instances[0].installing.is_none());
    }
}
