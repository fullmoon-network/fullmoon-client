use super::*;
use crate::test_support::TempDir;
use std::sync::Arc;
use std::time::Duration;
use tokio::io::AsyncBufReadExt;

fn slot() -> Arc<tokio::sync::Mutex<()>> {
    Arc::new(tokio::sync::Mutex::new(()))
}

async fn seeded_dir(tag: &str, owner_line: &[u8]) -> TempDir {
    let dir = TempDir::new(tag);
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    tokio::fs::write(dir.path().join("launch.lock"), owner_line)
        .await
        .unwrap();
    dir
}

#[tokio::test]
async fn a_second_concurrent_reservation_is_refused_politely() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("guard_second");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let slot = slot();

    let first = reserve(&slot, dir.path()).await.unwrap();
    let second = reserve(&slot, dir.path()).await;

    match second {
        Err(Error::Invalid(message)) => {
            assert!(
                message.contains("already being prepared"),
                "unexpected message: {message}"
            )
        }
        Err(e) => panic!("expected the preparation message, got: {e}"),
        Ok(_) => panic!("a second concurrent reservation must be refused"),
    }
    drop(first);
}

/* The launch slot only serializes users of one AppState. Two reservations
that do not share it - two AppState instances in one process - must still
contend, and the kernel-held file lock is what refuses the second. */
#[tokio::test]
async fn reservations_in_one_process_contend_without_a_shared_slot() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("guard_two_slots");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();

    let first = reserve(&slot(), dir.path()).await.unwrap();
    let second = reserve(&slot(), dir.path()).await;

    match second {
        Err(Error::Invalid(message)) => assert!(
            message.contains(&format!("pid {}", std::process::id())),
            "the holder must be named: {message}"
        ),
        Err(e) => panic!("expected the holder message, got: {e}"),
        Ok(_) => panic!("the second reservation must be refused"),
    }
    drop(first);
}

#[tokio::test]
async fn a_dropped_reservation_releases_the_instance_lock() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("guard_release");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let slot = slot();

    let first = reserve(&slot, dir.path()).await.unwrap();
    assert!(dir.path().join("launch.lock").exists());
    drop(first);

    let again = reserve(&slot, dir.path()).await;
    if let Err(e) = &again {
        panic!("the lock must be free again: {e}");
    }
    // the lock file is persistent: release never removes it
    assert!(dir.path().join("launch.lock").exists());
}

/* Crash recovery is the OS releasing the kernel lock, not deleting the path:
a launch.lock left behind with a stale owner line is simply a free lock, and
acquiring it does not even need to touch its contents beyond the diagnostic. */
#[tokio::test]
async fn a_stale_owner_line_does_not_block_a_free_lock() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = seeded_dir("guard_stale_text", b"pid 999999999\n").await;
    let slot = slot();

    let reservation = reserve(&slot, dir.path()).await;
    if let Err(e) = &reservation {
        panic!("a free lock must be taken despite stale text: {e}");
    }
    assert!(
        dir.path().join("launch.lock").exists(),
        "the persistent lock file must not be removed"
    );
    let bytes = tokio::fs::read(dir.path().join("launch.lock"))
        .await
        .unwrap();
    assert_eq!(
        bytes,
        format!("pid {}\n", std::process::id()).into_bytes(),
        "the new owner's diagnostic replaces the stale line"
    );
}

/* A refused launch reads the holder's diagnostic, but the pid line is only a
hint: an unidentifiable one is still a held lock, and the file is never
deleted because of what it says. */
#[tokio::test]
async fn an_unidentifiable_holder_line_is_still_a_refusal() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = seeded_dir("guard_unreadable", b"not a pid line\n").await;
    let slot = slot();

    let holder = crate::filelock::take(dir.path().join("launch.lock"))
        .await
        .unwrap();
    let refused = reserve(&slot, dir.path()).await;
    match refused {
        Err(Error::Invalid(message)) => assert!(
            message.contains("owner line cannot be read"),
            "unexpected message: {message}"
        ),
        Err(e) => panic!("expected the holder message, got: {e}"),
        Ok(_) => panic!("the held lock must be refused"),
    }
    assert_eq!(
        tokio::fs::read(dir.path().join("launch.lock"))
            .await
            .unwrap(),
        b"not a pid line\n",
        "a refusal must not rewrite or remove the holder's file"
    );
    drop(holder);
}

/* The pid beside the lock is a diagnostic: writing it is not allowed to fail
silently. A reservation whose diagnostic cannot be written fails closed and
releases the kernel lock it had taken. */
#[tokio::test]
async fn a_failed_owner_info_write_fails_closed() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = seeded_dir("guard_stamp", b"pid 1\n").await;
    let path = dir.path().join("launch.lock");
    // a read-only handle can hold the kernel lock but cannot be written to
    let file = std::fs::OpenOptions::new().read(true).open(&path).unwrap();

    let refused = own(path.clone(), file).await;

    match refused {
        Err(Error::Io(_)) => {}
        other => panic!("the stamp failure must fail the reservation: {other:?}"),
    }
    // the lock must not stay held by the failed reservation
    let next = std::fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(&path)
        .unwrap();
    assert!(
        matches!(
            crate::filelock::try_lock(next).unwrap(),
            crate::filelock::Take::Owned(_)
        ),
        "the failed reservation must release the kernel lock"
    );
}

/* A real second launcher process: the contention tests spawn this test
binary again with FULLMOON_LOCK_HOLDER_DIR set. Without that variable this
entry point does nothing. */
#[tokio::test]
async fn lock_holder_subprocess() {
    let Some(dir) = std::env::var("FULLMOON_LOCK_HOLDER_DIR").ok() else {
        return;
    };
    let dir = std::path::PathBuf::from(dir);
    match acquire(&dir).await {
        Ok(_lock) => {
            println!("HOLDER LOCKED {}", std::process::id());
            hold_until_stdin_closes().await;
        }
        Err(e) => println!("HOLDER REFUSED {e}"),
    }
}

/* A symlinked lock path must be refused before anything is written: the
reservation would otherwise lock - and stamp-truncate - the target of the
link, which belongs to someone else. */
#[tokio::test]
async fn a_symlinked_lock_path_is_refused_and_its_target_is_untouched() {
    let dir = TempDir::new("guard_symlink");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let sentinel = dir.path().join("sentinel.txt");
    tokio::fs::write(&sentinel, b"keep me").await.unwrap();
    let link = dir.path().join("launch.lock");
    #[cfg(unix)]
    std::os::unix::fs::symlink(&sentinel, &link).unwrap();
    #[cfg(windows)]
    std::os::windows::fs::symlink_file(&sentinel, &link).unwrap();
    let slot = slot();

    let refused = reserve(&slot, dir.path()).await;

    match refused {
        Err(e) => {
            let message = e.to_string();
            assert!(
                message.contains("symlink") || message.contains("regular file"),
                "unexpected message: {message}"
            );
        }
        Ok(_) => panic!("a symlinked lock path must be refused"),
    }
    assert_eq!(
        tokio::fs::read(&sentinel).await.unwrap(),
        b"keep me",
        "the link's target must not be truncated by a reservation"
    );
    let metadata = tokio::fs::symlink_metadata(&link).await.unwrap();
    assert!(
        metadata.file_type().is_symlink(),
        "the launcher must not have removed or replaced the link"
    );
}

/* A directory at the lock path is not a lock: refuse it, leave it alone. */
#[tokio::test]
async fn a_directory_at_the_lock_path_is_refused() {
    let dir = TempDir::new("guard_dir_lock");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let link = dir.path().join("launch.lock");
    tokio::fs::create_dir_all(&link).await.unwrap();
    tokio::fs::write(link.join("marker"), b"existing")
        .await
        .unwrap();
    let slot = slot();

    let refused = reserve(&slot, dir.path()).await;

    assert!(refused.is_err(), "a directory cannot be a lock");
    assert!(link.is_dir());
    assert_eq!(
        tokio::fs::read(link.join("marker")).await.unwrap(),
        b"existing"
    );
}

/* Separate-process contention against the real acquisition path: a child
test binary takes the lock and holds it until its stdin closes. */
#[tokio::test]
async fn a_separate_launcher_process_is_refused_and_names_its_holder() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("guard_subprocess");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let slot = slot();

    let (mut holder, mut output) = spawn_holder(dir.path().to_str().unwrap()).await;
    let line = output.line_containing("HOLDER").await;
    assert!(line.contains("HOLDER LOCKED"), "unexpected: {line}");
    let holder_pid: u32 = line
        .split_whitespace()
        .last()
        .and_then(|word| word.parse().ok())
        .expect("the holder prints its pid");

    let refused = reserve(&slot, dir.path()).await;
    match refused {
        Err(Error::Invalid(message)) => assert!(
            message.contains(&format!("pid {holder_pid}")),
            "the foreign holder must be named: {message}"
        ),
        Err(e) => panic!("expected the holder message, got: {e}"),
        Ok(_) => panic!("the foreign holder must be refused"),
    }
    assert_eq!(
        tokio::fs::read(dir.path().join("launch.lock"))
            .await
            .unwrap(),
        format!("pid {holder_pid}\n").into_bytes(),
        "a refusal must not touch the holder's file"
    );

    release_holder(&mut holder, output).await;
    let again = reserve(&slot, dir.path()).await;
    if let Err(e) = &again {
        panic!("an exited holder's lock must be free: {e}");
    }
}

/* Two subprocess contenders race for one lock: exactly one owns it, the
other is refused, and once the winner exits the OS has already released the
lock - no stale-file inspection involved. */
#[tokio::test]
async fn concurrent_contenders_produce_one_owner_and_os_release_recovers() {
    let _fork_quiet = crate::test_support::fork_window().lock().await;
    let dir = TempDir::new("guard_race");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();

    let (mut first, mut first_output) = spawn_holder(dir.path().to_str().unwrap()).await;
    let (mut second, mut second_output) = spawn_holder(dir.path().to_str().unwrap()).await;
    let line_a = first_output.line_containing("HOLDER").await;
    let line_b = second_output.line_containing("HOLDER").await;

    let locked = [line_a, line_b]
        .into_iter()
        .filter(|line| line.contains("HOLDER LOCKED"))
        .count();
    assert_eq!(
        locked, 1,
        "exactly one contender must own the lock, not both and not neither"
    );

    release_holder(&mut first, first_output).await;
    release_holder(&mut second, second_output).await;

    let slot = slot();
    let recovered = reserve(&slot, dir.path()).await;
    if let Err(e) = &recovered {
        panic!("the released lock must be free: {e}");
    }
}

async fn spawn_holder(dir: &str) -> (tokio::process::Child, HolderLines) {
    let exe = std::env::current_exe().expect("this test's own executable");
    let mut child = tokio::process::Command::new(exe)
        .args(["--exact", "launchguard::tests::lock_holder_subprocess"])
        .arg("--test-threads=1")
        .arg("--nocapture")
        .env("FULLMOON_LOCK_HOLDER_DIR", dir)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::null())
        .spawn()
        .expect("subprocess");
    let stdout = child.stdout.take().expect("piped stdout");
    (child, HolderLines::new(stdout))
}

/* The subprocess's own stdout, kept open for the child's whole life: the
test harness prints its summary after our marker, and closing this pipe
early would turn that summary into a write error in the child. */
struct HolderLines {
    lines: tokio::io::Lines<tokio::io::BufReader<tokio::process::ChildStdout>>,
}

impl HolderLines {
    fn new(stdout: tokio::process::ChildStdout) -> Self {
        Self {
            lines: tokio::io::BufReader::new(stdout).lines(),
        }
    }

    /// The next subprocess line containing `marker`.
    async fn line_containing(&mut self, marker: &str) -> String {
        let deadline = tokio::time::Instant::now() + Duration::from_secs(60);
        loop {
            match tokio::time::timeout_at(deadline, self.lines.next_line()).await {
                Err(_) => panic!("the holder subprocess did not report in time"),
                Ok(Ok(None)) => panic!("the holder subprocess exited without reporting"),
                Ok(Ok(Some(line))) => {
                    if line.contains(marker) {
                        return line;
                    }
                }
                Ok(Err(e)) => panic!("reading the holder subprocess failed: {e}"),
            }
        }
    }
}

/// Closing the child's stdin ends it; its exit releases the kernel lock.
/// The child's stdout reader stays open until after the wait, so the
/// harness's own summary never meets a closed pipe.
async fn release_holder(child: &mut tokio::process::Child, output: HolderLines) {
    drop(child.stdin.take());
    let status = child.wait().await.expect("holder subprocess");
    assert!(
        status.success(),
        "the holder subprocess must exit cleanly: {status}"
    );
    drop(output);
}

/// The holder keeps the lock until its stdin closes or sixty seconds pass,
/// so a leaked child cannot hang the suite forever.
async fn hold_until_stdin_closes() {
    use tokio::io::AsyncReadExt;

    let mut stdin = tokio::io::stdin();
    let mut buf = [0_u8; 512];
    let deadline = tokio::time::Instant::now() + Duration::from_secs(60);
    loop {
        match tokio::time::timeout_at(deadline, stdin.read(&mut buf)).await {
            Ok(Ok(0)) | Ok(Err(_)) | Err(_) => break,
            Ok(Ok(_)) => {}
        }
    }
}
