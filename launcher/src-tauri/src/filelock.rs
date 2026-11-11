/* Kernel-held file ownership.

An advisory exclusive lock the kernel holds on an open file decides who owns
a path. It survives exactly as long as the owner keeps its handle, the OS
releases it when the owner crashes, and it needs no inspection of the file's
contents - so no pid heuristic or check-then-delete race can misidentify an
owner. The path stays on disk; a persistent lock file with a stale owner line
is simply a lock that is free.

Both shapes here fail closed: an unsupported or erroring lock observation is
an error, never treated as success. The blocking shape runs off the async
executor because `File::lock` waits on other processes. */
use std::fs::{File, OpenOptions};
use std::path::PathBuf;

/// A kernel-held advisory exclusive lock. The kernel releases it when this
/// handle is dropped or the owning process exits.
#[derive(Debug)]
pub(crate) struct OwnedFile {
    file: File,
}

impl OwnedFile {
    pub(crate) fn file(&self) -> &File {
        &self.file
    }
}

/// What a non-blocking attempt at ownership found.
pub(crate) enum Take {
    /// The file was free and this handle now owns it.
    Owned(OwnedFile),
    /// Another holder owns the file right now.
    Held,
}

/// Try to own `file` without waiting. The caller opened it; ownership is
/// decided by the kernel lock, not by the open.
pub(crate) fn try_lock(file: File) -> Result<Take, std::io::Error> {
    match file.try_lock() {
        Ok(()) => Ok(Take::Owned(OwnedFile { file })),
        Err(std::fs::TryLockError::WouldBlock) => Ok(Take::Held),
        // every erroring observation fails closed; the standard library
        // reports an unsupported platform as an Unsupported-kind error here
        Err(std::fs::TryLockError::Error(e)) => Err(e),
    }
}

/// Open and own `path`, waiting for the current holder to release it. The
/// blocking wait runs on the blocking thread pool; a crashed holder's lock is
/// released by the OS, so this wait is bounded by the live holders.
pub(crate) async fn take(path: PathBuf) -> std::io::Result<OwnedFile> {
    tokio::task::spawn_blocking(move || {
        let file = open_lock_path_sync(path)?;
        file.lock()?;
        Ok(OwnedFile { file })
    })
    .await
    .map_err(|join| std::io::Error::other(format!("file lock task failed: {join}")))?
}

/// Open a persistent lock path without ever following or truncating a link:
/// the file is created exclusively the first time, and an existing path must
/// be a plain regular file. The symlink check runs before the open, so an
/// existing symlink is refused without being followed, and the handle is
/// re-verified against the path afterwards - writes always go through this
/// handle, so a path swapped after the open cannot redirect them either.
pub(crate) async fn open_lock_path(path: PathBuf) -> std::io::Result<File> {
    tokio::task::spawn_blocking(move || open_lock_path_sync(path))
        .await
        .map_err(|join| std::io::Error::other(format!("file lock open task failed: {join}")))?
}

fn open_lock_path_sync(path: PathBuf) -> std::io::Result<File> {
    /* Exclusive creation is atomic: a fresh file cannot be a link, and any
    existing entry - file, link, directory - makes this fail. */
    if let Err(e) = OpenOptions::new()
        .read(true)
        .write(true)
        .create_new(true)
        .open(&path)
    {
        match e.kind() {
            // an existing entry - file, link, directory - is handled below
            std::io::ErrorKind::AlreadyExists => {}
            _ => return Err(e),
        }
    }

    /* An existing path is never followed: a symlink here points at someone
    else's file, and a directory or anything else cannot be a lock. */
    let before = std::fs::symlink_metadata(&path)?;
    if before.file_type().is_symlink() {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            format!(
                "{} is a symlink; a lock path must be a regular file",
                path.display()
            ),
        ));
    }
    if !before.is_file() {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            format!(
                "{} is not a regular file; it cannot be a lock",
                path.display()
            ),
        ));
    }
    let file = OpenOptions::new().read(true).write(true).open(&path)?;
    /* The handle, not the path, is what every write will go through: verify
    it is exactly the regular file that was just checked, so a path swapped
    in between cannot redirect a write onto some other file. */
    let after = std::fs::symlink_metadata(&path)?;
    if after.file_type().is_symlink() {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            format!(
                "{} turned into a symlink while being opened for lock",
                path.display()
            ),
        ));
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;

        let handle = file.metadata()?;
        if handle.dev() != after.dev() || handle.ino() != after.ino() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!(
                    "{} changed identity while being opened for lock",
                    path.display()
                ),
            ));
        }
    }
    #[cfg(windows)]
    {
        if !file.metadata()?.is_file() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!(
                    "{} is not a regular file; it cannot be a lock",
                    path.display()
                ),
            ));
        }
    }
    Ok(file)
}

#[cfg(test)]
mod tests;
