/* Owning an instance for one launch.

Two rapid clicks, or two launcher processes pointed at the same instance,
must not both run the preparation: both would refresh packs, rewrite
`options.txt`, and install mods over each other. A reservation is two locks
taken together, and the second launch is refused with a sentence, not a wait:

- an in-process try-lock on the launcher's shared launch slot, which catches
  the two-clicks case without touching the disk;
- a kernel-held advisory lock on a persistent `launch.lock` beside the
  instance state, which covers separate launcher processes. Ownership is the
  open file handle itself: it is released when the handle is dropped and by
  the OS when a crashed launcher dies, so recovery never inspects or deletes
  the file. The pid written beside the lock is a diagnostic for operators
  only; an unreadable or unidentifiable one is still a held lock, refused
  politely.

The reservation is dropped when preparation fails (unwinding does it), and
otherwise when the game it reserved exits. */
use std::path::Path;
use std::sync::Arc;

use crate::{
    error::{Error, Result},
    filelock,
};

/// Owned by the caller for the whole prepared launch: the in-process guard
/// and the instance's file lock are both released when this is dropped.
pub(crate) struct Reservation {
    _launch_slot: tokio::sync::OwnedMutexGuard<()>,
    /// Held for its `Drop`: the kernel releases the instance's file lock.
    _instance: filelock::OwnedFile,
}

/// Reserve the launch slot in this process and the instance against other
/// launcher processes. `launch_slot` is the launcher-wide mutex; `instance_dir`
/// is the directory that holds the instance's own files.
pub(crate) async fn reserve(
    launch_slot: &std::sync::Arc<tokio::sync::Mutex<()>>,
    instance_dir: &Path,
) -> Result<Reservation> {
    let _launch_slot = Arc::clone(launch_slot)
        .try_lock_owned()
        .map_err(|_| Error::Invalid("a launch is already being prepared".into()))?;
    let _instance = acquire(instance_dir).await?;
    Ok(Reservation {
        _launch_slot,
        _instance,
    })
}

async fn acquire(instance_dir: &Path) -> Result<filelock::OwnedFile> {
    crate::paths::ensure_dir(instance_dir).await?;
    let path = instance_dir.join("launch.lock");
    let file = filelock::open_lock_path(path.clone()).await?;
    own(path, file).await
}

async fn own(path: std::path::PathBuf, file: std::fs::File) -> Result<filelock::OwnedFile> {
    let lock = match filelock::try_lock(file)? {
        filelock::Take::Owned(lock) => lock,
        filelock::Take::Held => return Err(Error::Invalid(holder_message(&path).await)),
    };
    /* The pid beside the lock is a diagnostic, not the ownership: the kernel
    lock is. The handle was verified to be the checked regular file before
    any write, so the stamp cannot reach another file through a link. A
    failure to write it is therefore not cosmetic - the reservation fails
    closed, and dropping `lock` releases the kernel lock. */
    stamp_owner(&lock)?;
    Ok(lock)
}

/// What a refused launch says about the holder it found. The pid line is
/// only a hint, so the read is bounded: a file whose owner line cannot be
/// found within a few kilobytes is still a held lock, just an unnamed one.
async fn holder_message(path: &Path) -> String {
    use tokio::io::AsyncReadExt;

    const OWNER_LINE_BOUND: usize = 4 * 1024;
    let holder = match tokio::fs::File::open(path).await {
        Ok(file) => {
            let mut bytes = Vec::new();
            let read = file
                .take(OWNER_LINE_BOUND as u64 + 1)
                .read_to_end(&mut bytes)
                .await;
            match read {
                Ok(_) if bytes.len() <= OWNER_LINE_BOUND => holder_pid(&bytes),
                _ => None,
            }
        }
        Err(_) => None,
    };
    match holder {
        Some(pid) => format!("another launcher process (pid {pid}) owns this instance's launch"),
        None => "another launcher process owns this instance's launch, but its owner line cannot be read".into(),
    }
}

/// Rewrite the diagnostic line with this process. The lock file may be reused
/// from an earlier run, so it is truncated first.
fn stamp_owner(lock: &filelock::OwnedFile) -> Result<()> {
    use std::io::{Seek, SeekFrom, Write};

    let mut file = lock.file();
    file.set_len(0)?;
    file.seek(SeekFrom::Start(0))?;
    file.write_all(format!("pid {}\n", std::process::id()).as_bytes())?;
    file.flush()?;
    Ok(())
}

/// The lock file's first line is `pid <n>`; anything else means the holder
/// cannot be identified.
fn holder_pid(bytes: &[u8]) -> Option<u32> {
    let line = String::from_utf8_lossy(bytes);
    let rest = line.strip_prefix("pid ")?.split_whitespace().next()?;
    rest.parse().ok()
}

#[cfg(test)]
mod tests;
