/* Instance-state transactions against the real persistence layer: every test
here writes `instance.json` through `store::write` and reads it back, in a
scratch directory. */
use super::*;
use crate::test_support::TempDir;
use tokio::io::AsyncBufReadExt;

fn state_with(disabled: &[&str]) -> InstanceModState {
    InstanceModState {
        disabled: disabled.iter().map(|s| s.to_string()).collect(),
        ..InstanceModState::default()
    }
}

async fn read_state(file: &Path) -> InstanceModState {
    serde_json::from_slice(&tokio::fs::read(file).await.unwrap()).unwrap()
}

#[tokio::test]
async fn concurrent_field_changes_all_survive() {
    let dir = TempDir::new("mods_txn_concurrent_fields");
    let file = dir.path().join("instance.json");
    store::write(&file, &state_with(&["older"])).await.unwrap();

    /* Three tasks race on the same file the way a pack selection, a mod
    toggle, and a launch-time managed-map update can. Serialized one at a
    time, each merges its own field onto whatever the last one wrote. */
    let (packs, managed, disabled) = futures::future::join3(
        update_state_at(&file, |fresh| InstanceModState {
            packs: vec!["fullmoon-gui".to_string()],
            ..fresh
        }),
        update_state_at(&file, |fresh| InstanceModState {
            managed: BTreeMap::from([(
                "fullmoon-client".to_string(),
                "fullmoon-client-3.3.0.jar".to_string(),
            )]),
            ..fresh
        }),
        set_disabled(&file, "sodium", false),
    )
    .await;
    packs.unwrap();
    managed.unwrap();
    disabled.unwrap();

    let after = read_state(&file).await;
    assert_eq!(after.packs, vec!["fullmoon-gui".to_string()]);
    assert!(after.managed.contains_key("fullmoon-client"));
    assert_eq!(
        after.disabled,
        vec!["older".to_string(), "sodium".to_string()]
    );
}

#[tokio::test]
async fn a_toggle_merges_into_the_freshest_state_not_a_stale_snapshot() {
    let dir = TempDir::new("mods_txn_stale");
    let file = dir.path().join("instance.json");
    store::write(&file, &state_with(&[])).await.unwrap();

    /* The snapshot a read took before a pack selection landed must not be
    written back after it: a toggle that used the stale snapshot would erase
    the pack field. The transaction reads again under the lock instead. */
    let stale: InstanceModState = store::read_or(&file, InstanceModState::default).await;
    assert!(stale.packs.is_empty());

    let (packs, toggle) = futures::future::join(
        update_state_at(&file, |fresh| InstanceModState {
            packs: vec!["fullmoon-gui".to_string()],
            ..fresh
        }),
        set_disabled(&file, "sodium", false),
    )
    .await;
    packs.unwrap();
    toggle.unwrap();

    let after = read_state(&file).await;
    assert_eq!(after.packs, vec!["fullmoon-gui".to_string()]);
    assert_eq!(after.disabled, vec!["sodium".to_string()]);
}

#[tokio::test]
async fn two_toggles_of_different_mods_are_both_kept() {
    let dir = TempDir::new("mods_txn_two_toggles");
    let file = dir.path().join("instance.json");
    store::write(&file, &state_with(&[])).await.unwrap();

    let (first, second) = futures::future::join(
        set_disabled(&file, "sodium", false),
        set_disabled(&file, "iris", false),
    )
    .await;
    first.unwrap();
    second.unwrap();

    let after = read_state(&file).await;
    for id in ["sodium", "iris"] {
        assert!(
            after.disabled.iter().any(|x| x == id),
            "{id} must survive the other toggle: {:?}",
            after.disabled
        );
    }
}

#[tokio::test]
async fn a_favorite_is_a_field_specific_change_too() {
    let dir = TempDir::new("mods_txn_favorite");
    let file = dir.path().join("instance.json");
    store::write(&file, &state_with(&["other"])).await.unwrap();

    let (favorite, disable) = futures::future::join(
        set_favorite(&file, "sodium", true),
        set_disabled(&file, "iris", false),
    )
    .await;
    favorite.unwrap();
    disable.unwrap();

    let after = read_state(&file).await;
    assert!(after.favorites.contains(&"sodium".to_string()));
    assert!(after.disabled.contains(&"other".to_string()));
    assert!(after.disabled.contains(&"iris".to_string()));
}

#[tokio::test]
async fn an_untouched_field_is_preserved_verbatim_through_a_transaction() {
    let dir = TempDir::new("mods_txn_untouched");
    let file = dir.path().join("instance.json");
    let mut managed = BTreeMap::new();
    managed.insert("kept".to_string(), "kept.jar".to_string());
    store::write(
        &file,
        &InstanceModState {
            managed,
            ..InstanceModState::default()
        },
    )
    .await
    .unwrap();

    update_state_at(&file, |fresh| InstanceModState {
        packs: vec!["fullmoon-gui".to_string()],
        ..fresh
    })
    .await
    .unwrap();

    let after = read_state(&file).await;
    assert_eq!(
        after.managed.get("kept").map(String::as_str),
        Some("kept.jar")
    );
}

/* A corrupt, oversized, or unreadable state file must not be reset to
defaults by a transaction: the user's choices live in it. Every test here
asserts the file survives byte for byte and the error names a recovery. */

#[tokio::test]
async fn a_malformed_state_file_is_left_untouched_and_reported() {
    let dir = TempDir::new("mods_txn_malformed");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let file = dir.path().join("instance.json");
    let broken = b"{ this is not json";
    tokio::fs::write(&file, broken).await.unwrap();

    let refused = set_disabled(&file, "sodium", false).await;

    match refused {
        Err(Error::Invalid(message)) => {
            assert!(
                message.contains("left untouched"),
                "the error must offer recovery, not silence: {message}"
            );
        }
        other => panic!("expected a named recovery error, got {other:?}"),
    }
    assert_eq!(tokio::fs::read(&file).await.unwrap(), broken);
}

#[tokio::test]
async fn a_state_file_with_the_wrong_schema_is_left_untouched() {
    let dir = TempDir::new("mods_txn_schema");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let file = dir.path().join("instance.json");
    let wrong = b"[\"an array is not instance state\"]";
    tokio::fs::write(&file, wrong).await.unwrap();

    let refused = set_disabled(&file, "sodium", false).await;

    assert!(matches!(refused, Err(Error::Invalid(_))), "got {refused:?}");
    assert_eq!(tokio::fs::read(&file).await.unwrap(), wrong);
}

#[tokio::test]
async fn an_oversized_state_file_is_left_untouched() {
    let dir = TempDir::new("mods_txn_oversized");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let file = dir.path().join("instance.json");
    // one byte past the bound, even though it would parse as an empty document
    let oversized = vec![b' '; MAX_STATE_BYTES + 1];
    tokio::fs::write(&file, &oversized).await.unwrap();

    let refused = set_disabled(&file, "sodium", false).await;

    match refused {
        Err(Error::Invalid(message)) => {
            assert!(
                message.contains("state limit"),
                "unexpected message: {message}"
            );
        }
        other => panic!("expected a size-bound error, got {other:?}"),
    }
    assert_eq!(tokio::fs::read(&file).await.unwrap(), oversized);
}

#[tokio::test]
async fn an_unreadable_state_file_is_reported_without_a_write() {
    let dir = TempDir::new("mods_txn_unreadable");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    // a directory cannot be read as state; the failure must be named, not
    // silently treated as an empty state
    let file = dir.path().join("instance.json");
    tokio::fs::create_dir_all(&file).await.unwrap();

    let refused = set_disabled(&file, "sodium", false).await;

    assert!(matches!(refused, Err(Error::Invalid(_))), "got {refused:?}");
    assert!(
        file.is_dir(),
        "the failed read must not have replaced the path"
    );
}

#[tokio::test]
async fn a_missing_state_file_legitimately_starts_from_defaults() {
    let dir = TempDir::new("mods_txn_fresh");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let file = dir.path().join("instance.json");

    set_disabled(&file, "sodium", false).await.unwrap();

    let after = read_state(&file).await;
    assert_eq!(after.disabled, vec!["sodium".to_string()]);
    assert!(after.packs.is_empty() && after.managed.is_empty() && after.favorites.is_empty());
}

#[tokio::test]
async fn a_failed_transaction_keeps_every_existing_field() {
    let dir = TempDir::new("mods_txn_retained");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let file = dir.path().join("instance.json");
    let full = InstanceModState {
        disabled: vec!["sodium".to_string()],
        favorites: vec!["iris".to_string()],
        managed: BTreeMap::from([("fullmoon-client".to_string(), "fullmoon.jar".to_string())]),
        packs: vec!["fullmoon-gui".to_string()],
    };
    store::write(&file, &full).await.unwrap();
    // corrupt the file in place, past the JSON header the store wrote
    tokio::fs::write(&file, b"{").await.unwrap();

    let refused = set_favorite(&file, "sodium", true).await;
    assert!(refused.is_err(), "a corrupt file must refuse: {refused:?}");
    assert_eq!(tokio::fs::read(&file).await.unwrap(), b"{");
}

/* The state guard file gets the same link protection as launch.lock: a
symlink there must not make a transaction truncate whatever it points at. */
#[tokio::test]
async fn a_symlinked_state_guard_is_refused_and_its_target_is_untouched() {
    let dir = TempDir::new("mods_txn_guard_symlink");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let sentinel = dir.path().join("sentinel.txt");
    tokio::fs::write(&sentinel, b"keep me").await.unwrap();
    let state_file = dir.path().join("instance.json");
    store::write(&state_file, &state_with(&["sodium"]))
        .await
        .unwrap();
    let guard = state_guard_file(&state_file);
    #[cfg(unix)]
    std::os::unix::fs::symlink(&sentinel, &guard).unwrap();
    #[cfg(windows)]
    std::os::windows::fs::symlink_file(&sentinel, &guard).unwrap();

    let refused = set_disabled(&state_file, "iris", false).await;

    assert!(
        refused.is_err(),
        "a symlinked guard must refuse: {refused:?}"
    );
    assert_eq!(
        tokio::fs::read(&sentinel).await.unwrap(),
        b"keep me",
        "the link's target must not be truncated by a transaction"
    );
    assert!(
        state_guard_file(&state_file).exists(),
        "the link must survive"
    );
    // the state file itself was never written either
    let untouched: InstanceModState =
        serde_json::from_slice(&tokio::fs::read(&state_file).await.unwrap()).unwrap();
    assert_eq!(untouched.disabled, vec!["sodium".to_string()]);
}

/* Subprocess entry point for the cross-process exclusion test: spawned by
`a_transaction_waits_for_another_process` with FULLMOON_STATE_TXN_DIR set.
Without that variable this test does nothing. */
#[tokio::test]
async fn state_transaction_subprocess() {
    let Some(dir) = std::env::var("FULLMOON_STATE_TXN_DIR").ok() else {
        return;
    };
    let hold_ms: u64 = std::env::var("FULLMOON_STATE_TXN_HOLD_MS")
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(400);
    let state_file = std::path::PathBuf::from(dir).join("instance.json");

    // own the transaction guard the same production call would take
    let _owner = crate::filelock::take(state_guard_file(&state_file))
        .await
        .unwrap();
    println!("STATE LOCKED {}", std::process::id());
    tokio::time::sleep(std::time::Duration::from_millis(hold_ms)).await;
    /* Still holding the guard: the merge runs against real persistence while
    another process is being kept out. */
    merge_locked(&state_file, |fresh| InstanceModState {
        packs: vec!["fullmoon-gui".to_string()],
        ..fresh
    })
    .await
    .unwrap();
    drop(_owner);
    println!("STATE DONE");
}

/* Cross-process exclusion on the real persistence layer: while another
launcher process owns the transaction guard, this process's transaction
waits instead of reading the same original and erasing the other's field. */
#[tokio::test]
async fn a_transaction_waits_for_another_process_and_no_field_is_lost() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("mods_txn_cross_process");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let state_file = dir.path().join("instance.json");
    store::write(&state_file, &state_with(&["sodium"]))
        .await
        .unwrap();

    let (mut child, mut output) = spawn_state_subprocess(dir.path().to_str().unwrap(), 400).await;
    let line = output.line_containing("STATE LOCKED").await;
    assert!(line.contains("STATE LOCKED"), "unexpected: {line}");

    let started = std::time::Instant::now();
    set_disabled(&state_file, "iris", false).await.unwrap();
    let waited = started.elapsed();
    assert!(
        waited >= std::time::Duration::from_millis(250),
        "this process's transaction must wait out the foreign holder, took {waited:?}"
    );

    release_subprocess(&mut child, output).await;

    let after = read_state(&state_file).await;
    assert_eq!(
        after.packs,
        vec!["fullmoon-gui".to_string()],
        "the other process's pack selection must survive"
    );
    assert_eq!(
        after.disabled,
        vec!["sodium".to_string(), "iris".to_string()]
    );
    // the guard is a persistent sibling, never removed by a transaction
    assert!(state_guard_file(&state_file).exists());
}

async fn spawn_state_subprocess(
    dir: &str,
    hold_ms: u64,
) -> (tokio::process::Child, SubprocessLines) {
    let exe = std::env::current_exe().expect("this test's own executable");
    let mut child = tokio::process::Command::new(exe)
        .args(["--exact", "mods::tests::state_transaction_subprocess"])
        .arg("--test-threads=1")
        .arg("--nocapture")
        .env("FULLMOON_STATE_TXN_DIR", dir)
        .env("FULLMOON_STATE_TXN_HOLD_MS", hold_ms.to_string())
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::null())
        .spawn()
        .expect("subprocess");
    let stdout = child.stdout.take().expect("piped stdout");
    (child, SubprocessLines::new(stdout))
}

/* The subprocess's own stdout, kept open for the child's whole life: the
test harness prints its summary after our marker, and closing this pipe
early would turn that summary into a write error in the child. */
struct SubprocessLines {
    lines: tokio::io::Lines<tokio::io::BufReader<tokio::process::ChildStdout>>,
}

impl SubprocessLines {
    fn new(stdout: tokio::process::ChildStdout) -> Self {
        Self {
            lines: tokio::io::BufReader::new(stdout).lines(),
        }
    }

    async fn line_containing(&mut self, marker: &str) -> String {
        let deadline = tokio::time::Instant::now() + std::time::Duration::from_secs(60);
        loop {
            match tokio::time::timeout_at(deadline, self.lines.next_line()).await {
                Err(_) => panic!("the subprocess did not report in time"),
                Ok(Ok(None)) => panic!("the subprocess exited without reporting"),
                Ok(Ok(Some(line))) => {
                    if line.contains(marker) {
                        return line;
                    }
                }
                Ok(Err(e)) => panic!("reading the subprocess failed: {e}"),
            }
        }
    }
}

async fn release_subprocess(child: &mut tokio::process::Child, output: SubprocessLines) {
    drop(child.stdin.take());
    let status = child.wait().await.expect("subprocess");
    assert!(
        status.success(),
        "the subprocess must exit cleanly: {status}"
    );
    drop(output);
}
