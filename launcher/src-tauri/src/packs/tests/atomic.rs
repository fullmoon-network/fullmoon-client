use super::*;
use std::{path::Path, time::Duration};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
    task::JoinHandle,
};

async fn serving(bytes: &[u8]) -> Fixture {
    Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await
}

async fn part_names(dir: &Path) -> Vec<String> {
    let mut names = Vec::new();
    let mut entries = tokio::fs::read_dir(dir).await.unwrap();
    while let Some(entry) = entries.next_entry().await.unwrap() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.ends_with(".part") {
            names.push(name);
        }
    }
    names.sort();
    names
}

#[cfg(unix)]
#[tokio::test]
async fn a_preexisting_part_symlink_is_not_followed() {
    use std::os::unix::fs::symlink;

    let dir = TempDir::new("part_symlink");
    tokio::fs::create_dir_all(dir.path().join("resourcepacks"))
        .await
        .unwrap();
    let pack_sentinel = dir.path().join("pack-sentinel.txt");
    tokio::fs::write(&pack_sentinel, b"keep pack")
        .await
        .unwrap();
    symlink(
        &pack_sentinel,
        dir.path().join("resourcepacks/fullmoon-gui.zip.part"),
    )
    .unwrap();
    let options_sentinel = dir.path().join("options-sentinel.txt");
    tokio::fs::write(&options_sentinel, b"keep options")
        .await
        .unwrap();
    symlink(&options_sentinel, dir.path().join("options.txt.part")).unwrap();
    tokio::fs::write(
        dir.path().join("options.txt"),
        "version:4790\nresourcePacks:[\"vanilla\"]\n",
    )
    .await
    .unwrap();
    let bytes = b"pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, _) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    assert_eq!(tokio::fs::read(&pack_sentinel).await.unwrap(), b"keep pack");
    assert_eq!(
        tokio::fs::read(&options_sentinel).await.unwrap(),
        b"keep options"
    );
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(bytes.as_slice())
    );
    let metadata = tokio::fs::symlink_metadata(dir.path().join("resourcepacks/fullmoon-gui.zip"))
        .await
        .unwrap();
    assert!(
        metadata.file_type().is_file(),
        "the installed pack must be a regular file"
    );
    assert!(read_options(dir.path())
        .await
        .unwrap()
        .contains("file/fullmoon-gui.zip"));
}

#[tokio::test]
async fn occupied_predictable_part_paths_are_not_reused() {
    let dir = TempDir::new("occupied_parts");
    tokio::fs::create_dir_all(dir.path().join("resourcepacks"))
        .await
        .unwrap();
    tokio::fs::write(
        dir.path().join("resourcepacks/fullmoon-gui.zip.part"),
        b"another owner",
    )
    .await
    .unwrap();
    tokio::fs::write(dir.path().join("options.txt.part"), b"another options")
        .await
        .unwrap();
    let bytes = b"pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, _) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    assert_eq!(
        tokio::fs::read(dir.path().join("resourcepacks/fullmoon-gui.zip.part"))
            .await
            .unwrap(),
        b"another owner"
    );
    assert_eq!(
        tokio::fs::read(dir.path().join("options.txt.part"))
            .await
            .unwrap(),
        b"another options"
    );
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(bytes.as_slice())
    );
    assert_eq!(
        part_names(&dir.path().join("resourcepacks")).await,
        vec!["fullmoon-gui.zip.part"]
    );
    assert_eq!(part_names(dir.path()).await, vec!["options.txt.part"]);
}

#[tokio::test]
async fn a_failed_pack_rename_keeps_the_destination_and_cleans_up() {
    let dir = TempDir::new("pack_rename_failure");
    let destination = dir.path().join("resourcepacks/fullmoon-gui.zip");
    tokio::fs::create_dir_all(&destination).await.unwrap();
    tokio::fs::write(destination.join("marker"), b"existing")
        .await
        .unwrap();
    let bytes = b"new pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(switched.is_empty());
    assert!(report
        .warnings
        .iter()
        .any(|w| w.contains("could not move temporary file")));
    assert!(destination.is_dir());
    assert_eq!(
        tokio::fs::read(destination.join("marker")).await.unwrap(),
        b"existing"
    );
    assert!(part_names(&dir.path().join("resourcepacks"))
        .await
        .is_empty());
    assert!(read_options(dir.path()).await.is_none());
}

#[tokio::test]
async fn a_failed_options_rename_keeps_the_destination_and_cleans_up() {
    let dir = TempDir::new("options_rename_failure");
    let destination = dir.path().join("options.txt");
    tokio::fs::create_dir_all(&destination).await.unwrap();
    tokio::fs::write(destination.join("marker"), b"existing")
        .await
        .unwrap();

    match write_atomic(&destination, b"new options").await {
        Err(Error::Invalid(message)) => {
            assert!(
                message.contains("could not move temporary file"),
                "{message}"
            )
        }
        other => panic!("expected a rename error, got {other:?}"),
    }
    assert!(destination.is_dir());
    assert_eq!(
        tokio::fs::read(destination.join("marker")).await.unwrap(),
        b"existing"
    );
    assert!(part_names(dir.path()).await.is_empty());
}

#[tokio::test]
async fn concurrent_refreshes_use_distinct_temps_and_converge() {
    let dir = TempDir::new("concurrent");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let bytes = b"pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");
    let client = reqwest::Client::new();

    let runs = (0..4).map(|_| {
        refresh_all(
            &client,
            dir.path(),
            GAME,
            std::slice::from_ref(&pack),
            Vec::new(),
        )
    });
    let results = futures::future::join_all(runs).await;

    for (report, _) in &results {
        assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    }
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(bytes.as_slice())
    );
    let options = read_options(dir.path()).await.unwrap();
    assert_eq!(options.matches("file/fullmoon-gui.zip").count(), 1);
    assert!(part_names(dir.path()).await.is_empty());
    assert!(part_names(&dir.path().join("resourcepacks"))
        .await
        .is_empty());
}

/// A pack selection written through the real state layer must not erase a
/// mod toggle that landed while the download was in flight, and the toggle
/// must not erase the selection: both are field-specific transactions against
/// the freshest state.
#[tokio::test]
async fn a_pack_selection_merges_onto_state_without_losing_a_concurrent_mod_change() {
    let dir = TempDir::new("state_merge");
    tokio::fs::create_dir_all(dir.path().join("resourcepacks"))
        .await
        .unwrap();
    let state_file = dir.path().join("instance.json");
    store::write(
        &state_file,
        &mods::InstanceModState {
            disabled: vec!["sodium".to_string()],
            ..mods::InstanceModState::default()
        },
    )
    .await
    .unwrap();

    let bytes = b"pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (apply, toggle) = futures::future::join(
        apply_with(
            &reqwest::Client::new(),
            dir.path(),
            &state_file,
            GAME,
            &[pack],
        ),
        mods::set_disabled(&state_file, "iris", false),
    )
    .await;

    assert!(
        apply.warnings.is_empty(),
        "unexpected warnings: {:?}",
        apply.warnings
    );
    toggle.unwrap();
    let after: mods::InstanceModState =
        serde_json::from_slice(&tokio::fs::read(&state_file).await.unwrap()).unwrap();
    assert!(
        after.packs.contains(&"fullmoon-gui".to_string()),
        "the pack selection must survive the toggle: {:?}",
        after.packs
    );
    for id in ["sodium", "iris"] {
        assert!(
            after.disabled.iter().any(|x| x == id),
            "{id} must survive the pack selection: {:?}",
            after.disabled
        );
    }
    assert!(part_names(&dir.path().join("resourcepacks"))
        .await
        .is_empty());

    let options = read_options(dir.path()).await.unwrap();
    assert_eq!(options.matches("file/fullmoon-gui.zip").count(), 1);
}

/// A corrupt instance state file is never reset: the pack refresh becomes a
/// warning (an optional pack never grounds a launch), no download wastes the
/// user's time, and the corrupt bytes survive untouched.
#[tokio::test]
async fn a_corrupt_state_file_becomes_a_warning_and_is_never_written() {
    let dir = TempDir::new("state_corrupt");
    tokio::fs::create_dir_all(dir.path().join("resourcepacks"))
        .await
        .unwrap();
    let state_file = dir.path().join("instance.json");
    let broken = b"{ this is not instance state";
    tokio::fs::write(&state_file, broken).await.unwrap();
    let bytes = b"pack art";
    let fixture = serving(bytes).await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &state_file,
        GAME,
        &[pack],
    )
    .await;

    assert!(
        report.warnings.len() == 1 && report.warnings[0].contains("could not be read"),
        "expected one named recovery warning: {:?}",
        report.warnings
    );
    assert!(report.updated.is_empty());
    assert_eq!(tokio::fs::read(&state_file).await.unwrap(), broken);
    assert!(read_options(dir.path()).await.is_none());
    assert!(part_names(&dir.path().join("resourcepacks"))
        .await
        .is_empty());
}

async fn respond(socket: &mut TcpStream, status: u16, body: &[u8]) {
    let reason = if status == 200 { "OK" } else { "Not Found" };
    let head = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        body.len()
    );
    let _ = socket.write_all(head.as_bytes()).await;
    let _ = socket.write_all(body).await;
}

async fn hanging_fixture(sha1: String) -> (String, JoinHandle<()>) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    let task = tokio::spawn(async move {
        loop {
            let Ok((mut socket, _)) = listener.accept().await else {
                break;
            };
            let mut raw = Vec::new();
            let mut buf = [0_u8; 1024];
            loop {
                let n = socket.read(&mut buf).await.unwrap_or(0);
                if n == 0 {
                    break;
                }
                raw.extend_from_slice(&buf[..n]);
                if raw.windows(4).any(|w| w == b"\r\n\r\n") || raw.len() > 8192 {
                    break;
                }
            }
            let path = String::from_utf8_lossy(&raw)
                .lines()
                .next()
                .and_then(|line| line.split_whitespace().nth(1))
                .unwrap_or("/")
                .to_string();
            if path == "/fullmoon-gui.sha1" {
                respond(&mut socket, 200, sha1.as_bytes()).await;
            } else {
                tokio::time::sleep(Duration::from_secs(30)).await;
                respond(&mut socket, 200, b"late").await;
            }
        }
    });
    (format!("http://{addr}"), task)
}

#[tokio::test]
async fn a_timed_out_download_removes_its_temporary_file() {
    let dir = TempDir::new("timeout");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    write_pack(dir.path(), b"old pack").await;
    let (base, task) = hanging_fixture(format!("{}\n", sha1_hex(b"slow art"))).await;
    let pack = source(&base, "fullmoon-gui.zip");

    let result = refresh_one_with(
        &reqwest::Client::new(),
        &pack,
        dir.path(),
        Duration::from_millis(100),
    )
    .await;
    task.abort();

    match result {
        Err(Error::Invalid(message)) => {
            assert!(message.contains("timed out downloading"), "{message}")
        }
        other => panic!("expected a timeout, got {other:?}"),
    }
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(b"old pack".as_slice())
    );
    assert!(part_names(&dir.path().join("resourcepacks"))
        .await
        .is_empty());
}
