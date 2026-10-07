/* Turning an installed instance into a running game.

   The command line is built from the version JSON's own argument lists —
   nothing is hardcoded, because Mojang moves flags between releases and a
   launcher that hardcodes 1.20's arguments breaks on 26.x. Rules are honoured
   (an arg for another OS never appears), placeholders are substituted from one
   table, and anything still unsubstituted is dropped rather than passed to the
   game as a literal `${…}`.

   stdout and stderr are streamed to the UI as they arrive. The process is
   owned by the launcher: closing the window kills nothing, but 게임 종료 does. */
use std::{
    collections::{HashMap, VecDeque},
    path::PathBuf,
    process::Stdio,
    sync::Arc,
};

use tauri::{AppHandle, Emitter};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
    sync::Mutex,
};

use crate::{
    auth::Session,
    error::{Error, Result},
    install,
    model::{Account, GameState, GameStateValue, Instance, LogEvent, Settings, StateEvent},
    paths,
    version::{allowed, Arg, ArgValue, VersionJson},
};

pub const LAUNCHER_NAME: &str = env!("PINION_NAME");
pub const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");

fn sep() -> &'static str {
    if cfg!(windows) {
        ";"
    } else {
        ":"
    }
}

/// `${placeholder}` → value. Unknown placeholders make the argument disappear;
/// the game handles a missing flag far better than a literal `${auth_xuid}`.
fn substitute(raw: &str, vars: &HashMap<&str, String>) -> Option<String> {
    let mut out = String::with_capacity(raw.len());
    let mut rest = raw;
    while let Some(start) = rest.find("${") {
        let (head, tail) = rest.split_at(start);
        out.push_str(head);
        let end = tail.find('}')?;
        let key = &tail[2..end];
        out.push_str(vars.get(key)?);
        rest = &tail[end + 1..];
    }
    out.push_str(rest);
    Some(out)
}

fn expand(args: &[Arg], vars: &HashMap<&str, String>) -> Vec<String> {
    let mut out = Vec::new();
    for arg in args {
        match arg {
            Arg::Plain(v) => {
                if let Some(s) = substitute(v, vars) {
                    out.push(s);
                }
            }
            Arg::Conditional { rules, value } => {
                if !allowed(&Some(rules.clone())) {
                    continue;
                }
                let values = match value {
                    ArgValue::One(v) => vec![v.clone()],
                    ArgValue::Many(v) => v.clone(),
                };
                for v in values {
                    if let Some(s) = substitute(&v, vars) {
                        out.push(s);
                    }
                }
            }
        }
    }
    out
}

pub fn classpath(v: &VersionJson) -> String {
    let root = paths::libraries_dir();
    let mut entries: Vec<String> = v
        .usable_libraries()
        .filter(|l| !l.is_native())
        .filter_map(|l| l.rel_path())
        .map(|p| root.join(p).to_string_lossy().into_owned())
        .collect();
    entries.dedup();
    entries.push(
        crate::version::client_jar_file(&v.id)
            .to_string_lossy()
            .into_owned(),
    );
    entries.join(sep())
}

pub struct Plan {
    pub program: PathBuf,
    pub args: Vec<String>,
    pub cwd: PathBuf,
}

/// Everything needed to spawn, assembled without touching the network.
pub fn plan(
    v: &VersionJson,
    inst: &Instance,
    settings: &Settings,
    account: &Account,
    session: Option<&Session>,
    quick_play: Option<&str>,
) -> Result<Plan> {
    let java = settings
        .java_path
        .clone()
        .ok_or_else(|| Error::Invalid("no Java runtime is selected in settings".into()))?;

    let game_dir = paths::instance_minecraft_dir(&inst.id);
    let natives = install::natives_dir(&inst.id);
    let assets_root = paths::assets_dir();
    let asset_index = v
        .asset_index
        .as_ref()
        .map(|a| a.id.clone())
        .or_else(|| v.assets.clone())
        .unwrap_or_else(|| "legacy".into());

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", account.username.clone());
    vars.insert("auth_uuid", account.uuid.replace('-', ""));
    vars.insert(
        "auth_access_token",
        session.map(|s| s.access_token.clone()).unwrap_or_else(|| "0".into()),
    );
    vars.insert(
        "auth_xuid",
        session.and_then(|s| s.xuid.clone()).unwrap_or_default(),
    );
    vars.insert("clientid", String::new());
    vars.insert(
        "user_type",
        if account.source == "microsoft" { "msa".into() } else { "legacy".into() },
    );
    vars.insert("version_name", v.id.clone());
    vars.insert("version_type", LAUNCHER_NAME.to_lowercase());
    vars.insert("game_directory", game_dir.to_string_lossy().into_owned());
    vars.insert("assets_root", assets_root.to_string_lossy().into_owned());
    vars.insert("game_assets", assets_root.to_string_lossy().into_owned());
    vars.insert("assets_index_name", asset_index);
    vars.insert("natives_directory", natives.to_string_lossy().into_owned());
    vars.insert("launcher_name", LAUNCHER_NAME.to_string());
    vars.insert("launcher_version", LAUNCHER_VERSION.to_string());
    vars.insert("classpath", classpath(v));
    vars.insert("classpath_separator", sep().to_string());
    vars.insert(
        "library_directory",
        paths::libraries_dir().to_string_lossy().into_owned(),
    );

    let arguments = v
        .arguments
        .clone()
        .ok_or_else(|| Error::Invalid(format!("version {} carries no argument lists", v.id)))?;

    let mut args = Vec::new();
    // memory first so a user arg can still override it
    args.push(format!("-Xmx{}M", inst.memory_mb));
    args.push(format!("-Xms{}M", (inst.memory_mb / 2).max(512)));
    // product in JDK 25 (JEP 519), unknown to anything older; a game that asks for 25 cannot run on less
    if v.java_version.as_ref().is_some_and(|j| j.major_version >= 25) {
        args.push("-XX:+UseCompactObjectHeaders".into());
    }
    args.extend(
        settings
            .java_args
            .split_whitespace()
            .filter(|s| !s.is_empty())
            .map(str::to_string),
    );
    args.extend(expand(&arguments.jvm, &vars));
    args.push(
        v.main_class
            .clone()
            .ok_or_else(|| Error::Invalid(format!("version {} has no mainClass", v.id)))?,
    );
    args.extend(expand(&arguments.game, &vars));

    if let Some(server) = quick_play {
        args.push("--quickPlayMultiplayer".into());
        args.push(server.to_string());
    }

    Ok(Plan { program: PathBuf::from(java), args, cwd: game_dir })
}

/// Uppercased, because `level_of` matches against an uppercased line.
const OUR_LOGGER_TAG: &str = "(FULLMOON/";

fn level_of(line: &str) -> &'static str {
    // "[19:57:12] [Render thread/INFO]: …" — the tag is what the game means
    let upper = line.to_ascii_uppercase();
    if upper.contains("/ERROR") || upper.contains("EXCEPTION") || upper.contains("\tAT ") {
        "ERROR"
    } else if upper.contains("/WARN") {
        "WARN"
    } else if upper.contains(OUR_LOGGER_TAG) {
        // our own client logs under (Fullmoon/Channel), (Fullmoon/Hud), (Fullmoon/Map) …;
        // the console gives those the accent so a player can find them in vanilla noise
        "OURS"
    } else if upper.contains("/DEBUG") {
        "DEBUG"
    } else {
        "INFO"
    }
}

/// Spawn, wire the log pumps, and own the child until it exits. The caller
/// keeps only a kill switch, so nothing else can leave a zombie behind.
/// Exit is reported on `game://state`, including the code.
pub fn spawn(
    app: &AppHandle,
    game: Arc<Mutex<GameState>>,
    log: Arc<Mutex<VecDeque<LogEvent>>>,
    plan: Plan,
    session_id: String,
) -> Result<tokio::sync::oneshot::Sender<()>> {
    let mut cmd = tokio::process::Command::new(&plan.program);
    cmd.args(&plan.args)
        .current_dir(&plan.cwd)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(false);
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    let mut child = cmd.spawn()?;

    if let Some(out) = child.stdout.take() {
        pump(app.clone(), Arc::clone(&game), Arc::clone(&log), session_id.clone(), out, false);
    }
    if let Some(err) = child.stderr.take() {
        pump(app.clone(), Arc::clone(&game), Arc::clone(&log), session_id.clone(), err, true);
    }

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel::<()>();
    watch(app.clone(), game, session_id, child, kill_rx);
    Ok(kill_tx)
}

/// The event and the state the UI polls have to agree, so both are written
/// here — a `game://state` the store never sees would make a stale HUD.
async fn emit_state(
    app: &AppHandle,
    game: &Mutex<GameState>,
    session_id: &str,
    state: GameStateValue,
    exit_code: Option<i32>,
) {
    let _ = app.emit(
        "game://state",
        StateEvent { session_id: session_id.to_string(), state, exit_code },
    );
    let mut guard = game.lock().await;
    if guard.session_id.as_deref() == Some(session_id) {
        guard.state = state;
        guard.exit_code = exit_code;
    }
}

/// How much of the game's output the console can scroll back through. Long
/// enough to hold a crash's stack trace, short enough that a chatty mod cannot
/// grow the launcher without bound.
const LOG_KEEP: usize = 4000;

fn pump<R>(
    app: AppHandle,
    game: Arc<Mutex<GameState>>,
    log: Arc<Mutex<VecDeque<LogEvent>>>,
    session_id: String,
    stream: R,
    is_err: bool,
) where
    R: tokio::io::AsyncRead + Unpin + Send + 'static,
{
    tokio::spawn(async move {
        let mut lines = BufReader::new(stream).lines();
        let mut first = true;
        while let Ok(Some(line)) = lines.next_line().await {
            if first && !is_err {
                // the game printing anything at all means the JVM came up
                first = false;
                emit_state(&app, &game, &session_id, GameStateValue::Running, None).await;
            }
            let level = if is_err { "ERROR" } else { level_of(&line) };
            let event =
                LogEvent { session_id: session_id.clone(), level: level.into(), line };
            {
                let mut kept = log.lock().await;
                if kept.len() >= LOG_KEEP {
                    kept.pop_front();
                }
                kept.push_back(event.clone());
            }
            let _ = app.emit("game://log", event);
        }
    });
}

/// Wait for exit — or for the kill switch — off the command path.
fn watch(
    app: AppHandle,
    game: Arc<Mutex<GameState>>,
    session_id: String,
    mut child: tokio::process::Child,
    kill: tokio::sync::oneshot::Receiver<()>,
) {
    tokio::spawn(async move {
        let mut asked_to_stop = false;
        let status = tokio::select! {
            s = child.wait() => s,
            _ = kill => {
                asked_to_stop = true;
                let _ = child.start_kill();
                child.wait().await
            }
        };
        // a killed process exits non-zero; that is the kill working, not a crash
        let (state, code) = match status {
            Ok(s) if s.success() || asked_to_stop => (GameStateValue::Closed, s.code()),
            Ok(s) => (GameStateValue::Crashed, s.code()),
            Err(_) if asked_to_stop => (GameStateValue::Closed, None),
            Err(_) => (GameStateValue::Crashed, None),
        };
        emit_state(&app, &game, &session_id, state, code).await;
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    #[test]
    fn substitutes_known_keys() {
        let vars = HashMap::from([("a", "1".to_string())]);
        assert_eq!(substitute("x${a}y", &vars).unwrap(), "x1y");
    }

    #[test]
    fn drops_args_with_unknown_placeholders() {
        let vars: HashMap<&str, String> = HashMap::new();
        assert!(substitute("--width ${resolution_width}", &vars).is_none());
    }

    #[test]
    fn error_lines_are_classified() {
        assert_eq!(level_of("[12:00:00] [main/ERROR]: boom"), "ERROR");
        assert_eq!(level_of("[12:00:00] [main/WARN]: hmm"), "WARN");
        assert_eq!(level_of("[12:00:00] [main/INFO]: fine"), "INFO");
    }

    #[test]
    fn our_own_clients_lines_are_told_apart_from_the_games() {
        // verbatim from docs/evidence/p9r-live-client.log — the console's accent level had no
        // line that could ever reach it before this
        assert_eq!(
            level_of("[19:55:07] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 hello (proto 1)"),
            "OURS"
        );
        assert_eq!(
            level_of("[19:55:22] [Render thread/INFO] (Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1"),
            "OURS"
        );
        // and a broken one is still an error first: that is what a player has to see
        assert_eq!(
            level_of("[19:55:23] [Render thread/ERROR] (Fullmoon/Map) nope"),
            "ERROR"
        );
    }

    #[test]
    fn plan_composes_jvm_and_game_args_correctly() {
        use crate::version::Arguments;

        let version = VersionJson {
            id: "1.21.4".into(),
            main_class: Some("net.minecraft.client.main.Main".into()),
            inherits_from: None,
            asset_index: None,
            assets: Some("17".into()),
            downloads: BTreeMap::new(),
            libraries: vec![],
            arguments: Some(Arguments {
                jvm: vec![
                    Arg::Plain("-Djava.library.path=${natives_directory}".into()),
                    Arg::Plain("-cp".into()),
                    Arg::Plain("${classpath}".into()),
                ],
                game: vec![
                    Arg::Plain("--username".into()),
                    Arg::Plain("${auth_player_name}".into()),
                    Arg::Plain("--version".into()),
                    Arg::Plain("${version_name}".into()),
                    Arg::Plain("--gameDir".into()),
                    Arg::Plain("${game_directory}".into()),
                ],
            }),
            minecraft_arguments: None,
            java_version: None,
        };

        let inst = Instance {
            id: "test-instance".into(),
            name: "Test Instance".into(),
            version_id: "1.21.4".into(),
            loader: "vanilla".into(),
            installed: true,
            installing: None,
            memory_mb: 4096,
            icon_hue: 200,
            created_at: "2026-08-29T00:00:00Z".into(),
            last_played_at: None,
            quick_play_server: None,
        };

        let settings = Settings {
            java_path: Some("/usr/bin/java".into()),
            java_args: "-XX:+UseG1GC".into(),
            memory_mb: 4096,
            concurrency: 8,
            theme: "dark".into(),
            accent: "#F5D06E".into(),
            language: "ko".into(),
            telemetry: false,
        };

        let account = Account {
            uuid: "00000000-0000-0000-0000-000000000000".into(),
            username: "Player123".into(),
            skin_hue: 200,
            skin_url: None,
            source: "offline".into(),
            capes: vec![],
        };

        let plan = plan(&version, &inst, &settings, &account, None, None).unwrap();
        assert_eq!(plan.program, PathBuf::from("/usr/bin/java"));
        assert!(plan.args.contains(&"-Xmx4096M".to_string()));
        assert!(plan.args.contains(&"-Xms2048M".to_string()));
        assert!(plan.args.contains(&"-XX:+UseG1GC".to_string()));
        assert!(plan.args.contains(&"net.minecraft.client.main.Main".to_string()));
        assert!(plan.args.contains(&"--username".to_string()));
        assert!(plan.args.contains(&"Player123".to_string()));
        assert!(!plan.args.contains(&"-XX:+UseCompactObjectHeaders".to_string()));

        let on = |major| {
            let mut v = version.clone();
            v.java_version = Some(crate::version::JavaVersion {
                component: "java-runtime-epsilon".into(),
                major_version: major,
            });
            super::plan(&v, &inst, &settings, &account, None, None).unwrap().args
        };
        let java25 = on(25);
        let compact = java25
            .iter()
            .position(|a| a == "-XX:+UseCompactObjectHeaders")
            .expect("compact headers on Java 25");
        let user = java25.iter().position(|a| a == "-XX:+UseG1GC").unwrap();
        assert!(compact < user, "a user arg must be able to turn it back off");
        assert!(!on(21).contains(&"-XX:+UseCompactObjectHeaders".to_string()));
    }
}
