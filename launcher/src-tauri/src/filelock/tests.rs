use super::*;
use crate::test_support::TempDir;

#[tokio::test]
async fn try_lock_owns_a_free_file_and_blocks_a_second_handle() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("filelock_take");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let path = dir.path().join("guard");

    let first = match try_lock(open_for_lock(&path).unwrap()).unwrap() {
        Take::Owned(owned) => owned,
        Take::Held => panic!("a fresh file must be free"),
    };

    let second = try_lock(open_for_lock(&path).unwrap()).unwrap();
    assert!(matches!(second, Take::Held), "a held file must refuse");

    drop(first);
    let third = try_lock(open_for_lock(&path).unwrap()).unwrap();
    assert!(matches!(third, Take::Owned(_)), "a released file is free");
}

#[tokio::test]
async fn take_waits_for_a_live_holder_and_proceeds_when_it_releases() {
    let dir = TempDir::new("filelock_wait");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let path = dir.path().join("guard");

    // a live holder in another blocking task, standing in for a foreign process
    let held = {
        let path = path.clone();
        tokio::task::spawn_blocking(move || {
            let file = std::fs::OpenOptions::new()
                .read(true)
                .write(true)
                .create(true)
                .truncate(false)
                .open(&path)
                .unwrap();
            file.lock().unwrap();
            file
        })
        .await
        .unwrap()
    };

    let waiter = tokio::spawn(take(path.clone()));
    tokio::time::sleep(std::time::Duration::from_millis(100)).await;
    assert!(
        !waiter.is_finished(),
        "the waiter must still be waiting while the lock is held"
    );

    drop(held);
    let owned = waiter.await.unwrap().unwrap();
    drop(owned);
}

/// The lock file itself is persistent: owning and releasing it never removes
/// or truncates it, so a stale owner line left on disk is inert.
#[tokio::test]
async fn ownership_leaves_the_persistent_file_in_place() {
    let dir = TempDir::new("filelock_persistent");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let path = dir.path().join("guard");
    tokio::fs::write(&path, b"pid 123\n").await.unwrap();

    let owned = take(path.clone()).await.unwrap();
    drop(owned);

    assert!(path.exists());
    assert_eq!(tokio::fs::read(&path).await.unwrap(), b"pid 123\n");
}

#[tokio::test]
async fn a_symlinked_lock_path_is_refused_without_following_it() {
    let dir = TempDir::new("filelock_symlink");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let sentinel = dir.path().join("sentinel.txt");
    tokio::fs::write(&sentinel, b"keep me").await.unwrap();
    let link = dir.path().join("guard");
    #[cfg(unix)]
    std::os::unix::fs::symlink(&sentinel, &link).unwrap();
    #[cfg(windows)]
    std::os::windows::fs::symlink_file(&sentinel, &link).unwrap();

    match open_lock_path(link.clone()).await {
        Err(e) => assert!(e.to_string().contains("symlink"), "unexpected error: {e}"),
        other => panic!("a symlinked lock path must be refused: {other:?}"),
    }
    // neither the sentinel nor the link was touched
    assert_eq!(tokio::fs::read(&sentinel).await.unwrap(), b"keep me");
    assert!(tokio::fs::symlink_metadata(&link)
        .await
        .unwrap()
        .file_type()
        .is_symlink());
}

#[tokio::test]
async fn a_directory_at_a_lock_path_is_refused() {
    let dir = TempDir::new("filelock_dir");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let path = dir.path().join("guard");
    tokio::fs::create_dir_all(&path).await.unwrap();
    tokio::fs::write(path.join("marker"), b"existing")
        .await
        .unwrap();

    let refused = open_lock_path(path.clone()).await;

    assert!(
        refused.is_err(),
        "a directory cannot be a lock: {refused:?}"
    );
    assert!(path.is_dir());
    assert_eq!(
        tokio::fs::read(path.join("marker")).await.unwrap(),
        b"existing"
    );
}

#[tokio::test]
async fn a_regular_file_with_stale_text_is_a_valid_lock_path() {
    let dir = TempDir::new("filelock_stale");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let path = dir.path().join("guard");
    tokio::fs::write(&path, b"pid 999999999\n").await.unwrap();

    let file = open_lock_path(path.clone()).await.unwrap();
    let metadata = file.metadata().unwrap();
    assert!(metadata.is_file());
}

/// The caller opens the lock file; ownership is the kernel lock. Opening
/// read-only is enough to hold the lock, which is how the owner-write
/// failure is injected in launchguard's tests.
fn open_for_lock(path: &std::path::Path) -> std::io::Result<std::fs::File> {
    std::fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(path)
}
