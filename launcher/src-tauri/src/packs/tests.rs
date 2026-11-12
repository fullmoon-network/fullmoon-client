use super::*;
use sha1::{Digest, Sha1};
use std::{collections::HashMap, path::PathBuf, sync::Arc, time::Duration};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
    sync::Mutex,
};

mod atomic;
mod bundled;

const HASH: &str = "175de05a740e46b47a2db6533c3d217c1c14cb19";
const GAME: &str = "26.1.2";

fn sha1_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    hex::encode(hasher.finalize())
}

struct TempDir(PathBuf);

impl TempDir {
    fn new(tag: &str) -> Self {
        Self(std::env::temp_dir().join(format!(
            "fullmoon_packs_{tag}_{}",
            uuid::Uuid::new_v4().simple()
        )))
    }

    fn path(&self) -> &Path {
        &self.0
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

struct Fixture {
    base: String,
    paths: Arc<Mutex<Vec<String>>>,
}

impl Fixture {
    async fn start(routes: Vec<(&str, u16, Vec<u8>)>) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let routes: HashMap<String, (u16, Vec<u8>)> = routes
            .into_iter()
            .map(|(path, status, body)| (path.to_string(), (status, body)))
            .collect();
        let routes = Arc::new(routes);
        let paths = Arc::new(Mutex::new(Vec::new()));
        let seen = Arc::clone(&paths);
        tokio::spawn(async move {
            loop {
                let Ok((mut socket, _)) = listener.accept().await else {
                    break;
                };
                let routes = Arc::clone(&routes);
                let seen = Arc::clone(&seen);
                tokio::spawn(async move {
                    let mut raw = Vec::new();
                    let mut buf = [0u8; 1024];
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
                    let request = String::from_utf8_lossy(&raw);
                    let path = request
                        .lines()
                        .next()
                        .and_then(|line| line.split_whitespace().nth(1))
                        .unwrap_or("/")
                        .to_string();
                    seen.lock().await.push(path.clone());
                    let (status, body) = routes
                        .get(&path)
                        .cloned()
                        .unwrap_or((404, b"not found".to_vec()));
                    let reason = if status == 200 { "OK" } else { "Not Found" };
                    let head = format!(
                        "HTTP/1.1 {status} {reason}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                        body.len()
                    );
                    let _ = socket.write_all(head.as_bytes()).await;
                    let _ = socket.write_all(&body).await;
                });
            }
        });
        Self {
            base: format!("http://{addr}"),
            paths,
        }
    }

    async fn paths(&self) -> Vec<String> {
        self.paths.lock().await.clone()
    }
}

fn source(base: &str, file: &str) -> PackSource {
    PackSource {
        id: "fullmoon-gui".into(),
        file: file.into(),
        url: format!("{base}/fullmoon-gui.zip"),
        sha1_url: format!("{base}/fullmoon-gui.sha1"),
        game: GAME.into(),
        bundled: false,
    }
}

async fn write_pack(dir: &Path, bytes: &[u8]) {
    let packs = dir.join("resourcepacks");
    tokio::fs::create_dir_all(&packs).await.unwrap();
    tokio::fs::write(packs.join("fullmoon-gui.zip"), bytes)
        .await
        .unwrap();
}

async fn read_pack(dir: &Path) -> Option<Vec<u8>> {
    tokio::fs::read(dir.join("resourcepacks").join("fullmoon-gui.zip"))
        .await
        .ok()
}

async fn read_options(dir: &Path) -> Option<String> {
    tokio::fs::read_to_string(dir.join("options.txt"))
        .await
        .ok()
}

#[test]
fn sha1_line_accepts_one_forty_digit_word_only() {
    assert_eq!(sha1_line(&format!("{HASH}\n")), Some(HASH.into()));
    assert_eq!(
        sha1_line(&format!("  {}  fullmoon-gui.zip\r\n", HASH.to_uppercase())),
        Some(HASH.into())
    );
    assert_eq!(sha1_line(&HASH[..39]), None);
    assert_eq!(sha1_line("<html>not found</html>"), None);
    assert_eq!(sha1_line(""), None);
    assert_eq!(sha1_line(&"z".repeat(40)), None);
}

#[test]
fn unsafe_pack_file_names_are_rejected() {
    assert!(safe_file("fullmoon-gui.zip"));
    assert!(!safe_file("../escape.zip"));
    assert!(!safe_file("a/escape.zip"));
    assert!(!safe_file(".."));
    assert!(!safe_file(".hidden.zip"));
    assert!(!safe_file("fullmoon-gui.jar"));
    assert!(!safe_file(""));
}

#[test]
fn the_catalogue_names_the_gui_pack_for_the_game_it_was_made_for() {
    let pack = catalog::get()
        .packs
        .iter()
        .find(|pack| pack.id == "fullmoon-gui")
        .expect("the catalogue must list the GUI pack");

    assert_eq!(pack.file, "fullmoon-gui.zip");
    assert_eq!(pack.game, spec::FAST_OPTIONS_VERSION);
    assert!(pack.sha1_url.ends_with(".sha1"));
    assert_eq!(pack.url.rsplit('/').next(), Some("fullmoon-gui.zip"));
}

#[test]
fn with_pack_keeps_the_users_list_and_line_endings() {
    let text = "version:4790\r\nresourcePacks:[\"vanilla\",\"file/user.zip\"]\r\nlang:ko_kr\r\n";
    assert_eq!(
        with_pack(Some(text), "file/fullmoon-gui.zip", None),
        PackListEdit::Updated(
            "version:4790\r\nresourcePacks:[\"vanilla\",\"file/user.zip\",\"file/fullmoon-gui.zip\"]\r\nlang:ko_kr\r\n"
                .into()
        )
    );
    assert_eq!(
        with_pack(
            Some("resourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n"),
            "file/fullmoon-gui.zip",
            None
        ),
        PackListEdit::Unchanged
    );
    assert_eq!(
        with_pack(
            Some("resourcePacks:vanilla\n"),
            "file/fullmoon-gui.zip",
            None
        ),
        PackListEdit::Unreadable
    );
}

#[test]
fn with_pack_creates_options_only_with_a_data_version() {
    assert_eq!(
        with_pack(None, "file/fullmoon-gui.zip", Some(4790)),
        PackListEdit::Updated(
            "version:4790\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n".into()
        )
    );
    assert_eq!(
        with_pack(None, "file/fullmoon-gui.zip", None),
        PackListEdit::Unchanged
    );
}

#[tokio::test]
async fn an_up_to_date_pack_is_not_downloaded() {
    let dir = TempDir::new("up_to_date");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    write_pack(dir.path(), b"already current").await;
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}  fullmoon-gui.zip\n", sha1_hex(b"already current")).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, b"must not be fetched".to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(
        report.warnings.is_empty(),
        "unexpected warnings: {:?}",
        report.warnings
    );
    assert!(report.updated.is_empty());
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(b"already current".as_slice())
    );
    assert_eq!(fixture.paths().await, vec!["/fullmoon-gui.sha1"]);
    assert_eq!(switched, vec!["fullmoon-gui".to_string()]);
    assert_eq!(
        read_options(dir.path()).await.as_deref(),
        Some("version:4790\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n")
    );
}

#[tokio::test]
async fn a_changed_pack_is_hashed_and_replaced() {
    let dir = TempDir::new("changed");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    write_pack(dir.path(), b"old art").await;
    let fresh = b"new art";
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(fresh)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, fresh.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, _) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(
        report.warnings.is_empty(),
        "unexpected warnings: {:?}",
        report.warnings
    );
    assert_eq!(report.updated, vec!["fullmoon-gui".to_string()]);
    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(fresh.as_slice())
    );
    assert!(fixture
        .paths()
        .await
        .iter()
        .any(|p| p == "/fullmoon-gui.zip"));
}

#[tokio::test]
async fn corrupt_bytes_keep_the_existing_pack() {
    let dir = TempDir::new("corrupt");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    write_pack(dir.path(), b"good old art").await;
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(b"expected art")).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, b"corrupt bytes".to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(b"good old art".as_slice())
    );
    assert!(switched.is_empty());
    assert!(report
        .warnings
        .iter()
        .any(|w| w.contains("checksum mismatch")));
    assert!(read_options(dir.path()).await.is_none());
}

#[tokio::test]
async fn malformed_missing_and_offline_hashes_keep_the_existing_pack() {
    for (tag, route) in [
        (
            "malformed",
            Some(("/fullmoon-gui.sha1", 200, b"not a hash\n".to_vec())),
        ),
        ("missing", None),
    ] {
        let dir = TempDir::new(tag);
        tokio::fs::create_dir_all(dir.path()).await.unwrap();
        write_pack(dir.path(), b"good old art").await;
        let fixture = Fixture::start(
            route
                .into_iter()
                .chain(std::iter::once((
                    "/fullmoon-gui.zip",
                    200,
                    b"must not arrive".to_vec(),
                )))
                .collect(),
        )
        .await;
        let pack = source(&fixture.base, "fullmoon-gui.zip");

        let (report, switched) = refresh_all(
            &reqwest::Client::new(),
            dir.path(),
            GAME,
            &[pack],
            Vec::new(),
        )
        .await;

        assert_eq!(
            read_pack(dir.path()).await.as_deref(),
            Some(b"good old art".as_slice()),
            "{tag}"
        );
        assert!(switched.is_empty(), "{tag}");
        assert!(!report.warnings.is_empty(), "{tag}");
        assert!(
            !fixture
                .paths()
                .await
                .iter()
                .any(|p| p == "/fullmoon-gui.zip"),
            "{tag}: the zip must not be fetched"
        );
    }

    let dir = TempDir::new("offline");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    write_pack(dir.path(), b"good old art").await;
    let closed = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = closed.local_addr().unwrap().port();
    drop(closed);
    let pack = PackSource {
        id: "fullmoon-gui".into(),
        file: "fullmoon-gui.zip".into(),
        url: format!("http://127.0.0.1:{port}/fullmoon-gui.zip"),
        sha1_url: format!("http://127.0.0.1:{port}/fullmoon-gui.sha1"),
        game: GAME.into(),
        bundled: false,
    };

    let (report, switched) = tokio::time::timeout(
        Duration::from_secs(10),
        refresh_all(
            &reqwest::Client::new(),
            dir.path(),
            GAME,
            &[pack],
            Vec::new(),
        ),
    )
    .await
    .expect("the lookup must time out or be refused");

    assert_eq!(
        read_pack(dir.path()).await.as_deref(),
        Some(b"good old art".as_slice())
    );
    assert!(switched.is_empty());
    assert!(!report.warnings.is_empty());
}

#[tokio::test]
async fn the_users_pack_list_survives_and_the_pack_is_added_once() {
    let dir = TempDir::new("user_list");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let bytes = b"pack art";
    tokio::fs::write(
        dir.path().join("options.txt"),
        "version:4790\nresourcePacks:[\"vanilla\",\"file/user.zip\"]\n",
    )
    .await
    .unwrap();
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (_, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        std::slice::from_ref(&pack),
        Vec::new(),
    )
    .await;
    assert_eq!(switched, vec!["fullmoon-gui".to_string()]);
    assert_eq!(
        read_options(dir.path()).await.as_deref(),
        Some("version:4790\nresourcePacks:[\"vanilla\",\"file/user.zip\",\"file/fullmoon-gui.zip\"]\n")
    );

    let (_, again) =
        refresh_all(&reqwest::Client::new(), dir.path(), GAME, &[pack], switched).await;
    assert_eq!(again, vec!["fullmoon-gui".to_string()]);
    assert_eq!(
        read_options(dir.path()).await.as_deref(),
        Some("version:4790\nresourcePacks:[\"vanilla\",\"file/user.zip\",\"file/fullmoon-gui.zip\"]\n")
    );
}

#[tokio::test]
async fn an_unreadable_pack_list_is_not_overwritten() {
    let dir = TempDir::new("bad_list");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let original = "version:4790\nresourcePacks:not-json\nlang:en_us\n";
    tokio::fs::write(dir.path().join("options.txt"), original)
        .await
        .unwrap();
    let bytes = b"pack art";
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert_eq!(read_options(dir.path()).await.as_deref(), Some(original));
    assert!(switched.is_empty());
    assert!(report.warnings.iter().any(|w| w.contains("unreadable")));
}

#[tokio::test]
async fn repeated_install_is_idempotent() {
    let dir = TempDir::new("idempotent");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let bytes = b"pack art";
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (first, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        std::slice::from_ref(&pack),
        Vec::new(),
    )
    .await;
    let options = read_options(dir.path()).await;
    let (second, switched_again) =
        refresh_all(&reqwest::Client::new(), dir.path(), GAME, &[pack], switched).await;

    assert_eq!(first.updated, vec!["fullmoon-gui".to_string()]);
    assert!(second.updated.is_empty());
    assert_eq!(read_options(dir.path()).await, options);
    assert_eq!(switched_again, vec!["fullmoon-gui".to_string()]);
    assert_eq!(
        fixture
            .paths()
            .await
            .iter()
            .filter(|p| *p == "/fullmoon-gui.zip")
            .count(),
        1
    );
}

#[tokio::test]
async fn an_oversized_download_is_refused() {
    let dir = TempDir::new("oversized");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let fixture = Fixture::start(vec![("/fullmoon-gui.zip", 200, b"0123456789".to_vec())]).await;
    let mut temp = TempFile::create(dir.path(), "download").await.unwrap();
    let tmp = temp.path().to_owned();

    let result = fetch_verified(
        &reqwest::Client::new(),
        &format!("{}/fullmoon-gui.zip", fixture.base),
        HASH,
        &mut temp,
        4,
    )
    .await;

    assert!(result.is_err());
    if let Err(e) = result {
        let _ = temp.discard(e).await;
    }
    assert!(!tmp.exists());
}

#[tokio::test]
async fn a_path_traversal_file_name_is_refused() {
    let dir = TempDir::new("traversal");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let fixture = Fixture::start(vec![(
        "/fullmoon-gui.sha1",
        200,
        format!("{HASH}\n").into_bytes(),
    )])
    .await;
    let mut pack = source(&fixture.base, "../escape.zip");
    pack.url = format!("{}/fullmoon-gui.zip", fixture.base);

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert!(switched.is_empty());
    assert!(report.warnings.iter().any(|w| w.contains("not a safe")));
    assert!(!dir.path().join("escape.zip").exists());
    assert!(!dir.path().parent().unwrap().join("escape.zip").exists());
}

#[test]
fn with_pack_treats_duplicate_keys_as_unreadable() {
    let selected_then_disabled = "version:4790\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\nresourcePacks:[\"vanilla\"]\n";
    assert_eq!(
        with_pack(Some(selected_then_disabled), "file/fullmoon-gui.zip", None),
        PackListEdit::Unreadable
    );
    let disabled_then_selected = "version:4790\nresourcePacks:[\"vanilla\"]\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n";
    assert_eq!(
        with_pack(Some(disabled_then_selected), "file/fullmoon-gui.zip", None),
        PackListEdit::Unreadable
    );
}

#[test]
fn with_pack_handles_blank_lines_and_missing_trailing_newlines() {
    let entry = "file/fullmoon-gui.zip";
    let added = "resourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n";
    assert_eq!(
        with_pack(Some(""), entry, None),
        PackListEdit::Updated(added.into())
    );
    assert_eq!(
        with_pack(Some("\n"), entry, None),
        PackListEdit::Updated(format!("\n{added}"))
    );
    assert_eq!(
        with_pack(Some("version:4790"), entry, None),
        PackListEdit::Updated(format!("version:4790\n{added}"))
    );
    assert_eq!(
        with_pack(Some("version:4790\r\n"), entry, None),
        PackListEdit::Updated(
            "version:4790\r\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\r\n".into()
        )
    );
}

#[tokio::test]
async fn duplicate_resource_pack_keys_leave_options_untouched() {
    let dir = TempDir::new("duplicate_keys");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let original = "version:4790\r\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\r\nresourcePacks:[\"vanilla\"]\r\n";
    tokio::fs::write(dir.path().join("options.txt"), original)
        .await
        .unwrap();
    let bytes = b"pack art";
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert_eq!(read_options(dir.path()).await.as_deref(), Some(original));
    assert!(switched.is_empty());
    assert!(report.warnings.iter().any(|w| w.contains("unreadable")));
}

#[tokio::test]
async fn an_oversized_options_file_is_not_rewritten() {
    let dir = TempDir::new("oversized_options");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let original = format!("version:4790\n{}", "x".repeat(1024 * 1024 + 16));
    tokio::fs::write(dir.path().join("options.txt"), &original)
        .await
        .unwrap();
    let bytes = b"pack art";
    let fixture = Fixture::start(vec![
        (
            "/fullmoon-gui.sha1",
            200,
            format!("{}\n", sha1_hex(bytes)).into_bytes(),
        ),
        ("/fullmoon-gui.zip", 200, bytes.to_vec()),
    ])
    .await;
    let pack = source(&fixture.base, "fullmoon-gui.zip");

    let (report, switched) = refresh_all(
        &reqwest::Client::new(),
        dir.path(),
        GAME,
        &[pack],
        Vec::new(),
    )
    .await;

    assert_eq!(
        read_options(dir.path()).await.as_deref(),
        Some(original.as_str())
    );
    assert!(switched.is_empty());
    assert!(report.warnings.iter().any(|w| w.contains("unreadable")));
}
