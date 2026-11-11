use super::*;
use crate::test_support::TempDir;
use std::{
    io::{Error as IoError, ErrorKind},
    sync::atomic::{AtomicUsize, Ordering},
    time::Duration,
};

#[tokio::test]
async fn create_at_refuses_an_occupied_path() {
    let dir = TempDir::new("atomic_occupied");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let target = dir.path().join("temp.part");
    tokio::fs::write(&target, b"another owner").await.unwrap();

    match TempFile::create_at(&target).await {
        Err(Error::Io(e)) => assert_eq!(e.kind(), ErrorKind::AlreadyExists),
        _ => panic!("expected AlreadyExists"),
    }
    assert_eq!(tokio::fs::read(&target).await.unwrap(), b"another owner");
}

#[cfg(unix)]
#[tokio::test]
async fn create_at_refuses_a_symlink_without_following_it() {
    use std::os::unix::fs::symlink;

    let dir = TempDir::new("atomic_symlink");
    tokio::fs::create_dir_all(dir.path()).await.unwrap();
    let sentinel = dir.path().join("sentinel.txt");
    tokio::fs::write(&sentinel, b"keep me").await.unwrap();
    let link = dir.path().join("temp.part");
    symlink(&sentinel, &link).unwrap();

    match TempFile::create_at(&link).await {
        Err(Error::Io(e)) => assert_eq!(e.kind(), ErrorKind::AlreadyExists),
        _ => panic!("expected AlreadyExists"),
    }
    assert_eq!(tokio::fs::read(&sentinel).await.unwrap(), b"keep me");
    let metadata = tokio::fs::symlink_metadata(&link).await.unwrap();
    assert!(metadata.file_type().is_symlink());
}

#[tokio::test]
async fn transient_replace_errors_are_retried_until_success() {
    let attempts = AtomicUsize::new(0);
    let result = replace_with_retry(
        || async {
            let attempt = attempts.fetch_add(1, Ordering::SeqCst) + 1;
            if attempt < 3 {
                Err(IoError::new(ErrorKind::PermissionDenied, "sharing"))
            } else {
                Ok(())
            }
        },
        |_| true,
        5,
        Duration::ZERO,
    )
    .await;

    assert!(result.is_ok());
    assert_eq!(attempts.load(Ordering::SeqCst), 3);
}

#[tokio::test]
async fn permanent_replace_errors_are_not_retried() {
    let attempts = AtomicUsize::new(0);
    let result = replace_with_retry(
        || async {
            attempts.fetch_add(1, Ordering::SeqCst);
            Err::<(), _>(IoError::new(ErrorKind::NotFound, "missing source"))
        },
        |_| false,
        5,
        Duration::ZERO,
    )
    .await;

    let error = result.unwrap_err();
    assert_eq!(attempts.load(Ordering::SeqCst), 1);
    assert_eq!(error.kind(), ErrorKind::NotFound);
    assert!(!error.to_string().contains("gave up"));
}

#[tokio::test]
async fn exhausted_transient_retries_report_the_attempt_count() {
    let attempts = AtomicUsize::new(0);
    let result = replace_with_retry(
        || async {
            attempts.fetch_add(1, Ordering::SeqCst);
            Err::<(), _>(IoError::new(ErrorKind::PermissionDenied, "still held"))
        },
        |_| true,
        4,
        Duration::ZERO,
    )
    .await;

    let error = result.unwrap_err();
    assert_eq!(attempts.load(Ordering::SeqCst), 4);
    assert!(error.to_string().contains("gave up after 4 attempts"));
    assert!(error.to_string().contains("still held"));
}
