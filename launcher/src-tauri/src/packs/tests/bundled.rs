use super::*;

const BUNDLED_PACK: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../i3/mod/src/main/resources/fullmoon/gui-pack.zip"
));

fn bundled_source(base: &str) -> PackSource {
    serde_json::from_value(serde_json::json!({
        "id": "fullmoon-gui",
        "file": "fullmoon-gui.zip",
        "url": format!("{base}/fullmoon-gui.zip"),
        "sha1Url": format!("{base}/fullmoon-gui.sha1"),
        "game": GAME,
        "bundled": true,
    }))
    .unwrap()
}

#[tokio::test]
async fn a_fresh_instance_gets_the_pack_and_selection_without_network() {
    let fixture = Fixture::start(Vec::new()).await;
    let dir = TempDir::new("bundled_fresh");
    let state = dir.path().join("instance.json");
    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &state,
        GAME,
        &[bundled_source(&fixture.base)],
    )
    .await;

    assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    assert!(
        read_pack(dir.path()).await.as_deref() == Some(BUNDLED_PACK),
        "bundled bytes differ"
    );
    assert!(read_options(dir.path())
        .await
        .unwrap()
        .contains("file/fullmoon-gui.zip"));
    assert!(fixture.paths().await.is_empty());
}

#[tokio::test]
async fn the_legacy_pack_is_upgraded_without_discarding_other_pack_choices() {
    let fixture = Fixture::start(Vec::new()).await;
    let dir = TempDir::new("bundled_upgrade");
    write_pack(dir.path(), b"old GUI pack").await;
    tokio::fs::write(
        dir.path().join("options.txt"),
        "lang:ko_kr\nresourcePacks:[\"vanilla\",\"file/user.zip\"]\n",
    )
    .await
    .unwrap();
    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &dir.path().join("instance.json"),
        GAME,
        &[bundled_source(&fixture.base)],
    )
    .await;

    assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    assert!(
        read_pack(dir.path()).await.as_deref() == Some(BUNDLED_PACK),
        "bundled bytes differ"
    );
    assert!(read_options(dir.path())
        .await
        .unwrap()
        .contains("[\"vanilla\",\"file/user.zip\",\"file/fullmoon-gui.zip\"]"));
    assert!(fixture.paths().await.is_empty());
}

#[tokio::test]
async fn a_published_older_pack_cannot_downgrade_the_bundled_art() {
    let old = b"older published pack".to_vec();
    let hash = sha1_hex(&old).into_bytes();
    let fixture = Fixture::start(vec![
        ("/fullmoon-gui.sha1", 200, hash),
        ("/fullmoon-gui.zip", 200, old),
    ])
    .await;
    let dir = TempDir::new("bundled_no_downgrade");
    write_pack(dir.path(), BUNDLED_PACK).await;
    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &dir.path().join("instance.json"),
        GAME,
        &[bundled_source(&fixture.base)],
    )
    .await;

    assert!(report.warnings.is_empty());
    assert!(
        read_pack(dir.path()).await.as_deref() == Some(BUNDLED_PACK),
        "bundled bytes differ"
    );
    assert!(fixture.paths().await.is_empty());
}

#[tokio::test]
async fn failed_replacement_preserves_existing_destination_and_options() {
    let fixture = Fixture::start(Vec::new()).await;
    let dir = TempDir::new("bundled_replace_failure");
    let pack_dir = dir.path().join("resourcepacks");
    let destination = pack_dir.join("fullmoon-gui.zip");
    // A nonempty directory cannot be atomically replaced by a file on either
    // Windows or Unix. This exercises the real TempFile failure cleanup path.
    tokio::fs::create_dir_all(&destination).await.unwrap();
    tokio::fs::write(destination.join("keep.txt"), b"untouched")
        .await
        .unwrap();
    let options = dir.path().join("options.txt");
    let previous_options = b"resourcePacks:[\"vanilla\"]\n";
    tokio::fs::write(&options, previous_options).await.unwrap();

    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &dir.path().join("instance.json"),
        GAME,
        &[bundled_source(&fixture.base)],
    )
    .await;

    assert!(report.updated.is_empty());
    assert_eq!(report.warnings.len(), 1, "{:?}", report.warnings);
    assert_eq!(
        tokio::fs::read(destination.join("keep.txt")).await.unwrap(),
        b"untouched"
    );
    assert_eq!(tokio::fs::read(options).await.unwrap(), previous_options);
    let mut files = tokio::fs::read_dir(&pack_dir).await.unwrap();
    let mut names = Vec::new();
    while let Some(entry) = files.next_entry().await.unwrap() {
        names.push(entry.file_name().to_string_lossy().to_string());
    }
    assert_eq!(names, vec!["fullmoon-gui.zip"]);
    assert!(fixture.paths().await.is_empty());
}

#[tokio::test]
async fn unrecognized_bundled_pack_is_rejected_without_writing_or_network() {
    let fixture = Fixture::start(Vec::new()).await;
    let dir = TempDir::new("bundled_invalid_metadata");
    let mut invalid = bundled_source(&fixture.base);
    invalid.id = "other-pack".into();

    let report = apply_with(
        &reqwest::Client::new(),
        dir.path(),
        &dir.path().join("instance.json"),
        GAME,
        &[invalid],
    )
    .await;

    assert!(report.updated.is_empty());
    assert_eq!(report.warnings.len(), 1);
    assert!(report.warnings[0].contains("no bundled resource pack exists"));
    assert!(!dir.path().join("resourcepacks").exists());
    assert!(fixture.paths().await.is_empty());
}
