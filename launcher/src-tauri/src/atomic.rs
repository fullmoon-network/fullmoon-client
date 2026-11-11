/* Durable file replacement, shared by settings, instance state, packs, and options.

A writer owns one exclusively-created sibling temp file: a random name plus
`create_new` means two writers, or two launcher processes, never share a temp
path. The finished temp is replaced onto the destination without removing the
destination first, so a failed or interrupted replacement leaves the previous
good file in place.

On Windows the replacement can meet a transient sharing or access-denied error
while an antivirus scanner, indexer, or the game briefly holds either file. That
case is retried a bounded number of times with a short backoff; every other
error is returned immediately. Unix keeps the atomic `rename`. */
use std::path::{Path, PathBuf};
#[cfg(any(windows, test))]
use std::time::Duration;

use crate::error::{Error, Result};

const TEMP_ATTEMPTS: usize = 32;

/// `MoveFileExW` replacement attempts before the update is reported as failed.
#[cfg(windows)]
const REPLACE_ATTEMPTS: usize = 5;
/// The first pause between Windows replacement attempts; later pauses grow.
#[cfg(windows)]
const REPLACE_BACKOFF: Duration = Duration::from_millis(40);

/// Owns one exclusively-created sibling temp file. Drop is the last resort;
/// normal failures discard explicitly so a cleanup error can be reported.
pub(crate) struct TempFile {
    path: PathBuf,
    file: Option<tokio::fs::File>,
    armed: bool,
}

impl TempFile {
    pub(crate) async fn create(dir: &Path, stem: &str) -> Result<Self> {
        for _ in 0..TEMP_ATTEMPTS {
            let path = dir.join(format!(
                ".{stem}.{}.{}.part",
                std::process::id(),
                uuid::Uuid::new_v4().simple()
            ));
            match Self::create_at(&path).await {
                Ok(temp) => return Ok(temp),
                Err(Error::Io(e)) if e.kind() == std::io::ErrorKind::AlreadyExists => continue,
                Err(e) => return Err(e),
            }
        }
        Err(Error::Invalid(format!(
            "could not create a unique temporary file in {}",
            dir.display()
        )))
    }

    async fn create_at(path: &Path) -> Result<Self> {
        let file = tokio::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(path)
            .await?;
        Ok(Self {
            path: path.to_owned(),
            file: Some(file),
            armed: true,
        })
    }

    #[cfg(test)]
    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    pub(crate) async fn write_all(&mut self, bytes: &[u8]) -> Result<()> {
        use tokio::io::AsyncWriteExt;

        let Some(file) = self.file.as_mut() else {
            return Err(Error::Invalid("temporary file is already closed".into()));
        };
        file.write_all(bytes).await?;
        Ok(())
    }

    async fn finish(&mut self) -> Result<()> {
        use tokio::io::AsyncWriteExt;

        if let Some(mut file) = self.file.take() {
            file.flush().await?;
            file.sync_all().await?;
        }
        Ok(())
    }

    pub(crate) async fn commit(mut self, dest: &Path) -> Result<()> {
        if let Err(e) = self.finish().await {
            return Err(self.discard(e).await);
        }
        match replace_file(&self.path, dest).await {
            Ok(()) => {
                self.armed = false;
                Ok(())
            }
            Err(e) => {
                let cause = Error::Invalid(format!(
                    "could not move temporary file into {}: {e}",
                    dest.display()
                ));
                Err(self.discard(cause).await)
            }
        }
    }

    pub(crate) async fn discard(mut self, cause: Error) -> Error {
        self.file.take();
        let removed = tokio::fs::remove_file(&self.path).await;
        self.armed = false;
        match removed {
            Ok(()) => cause,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => cause,
            Err(e) => Error::Invalid(format!(
                "{cause}; could not remove temporary file {}: {e}",
                self.path.display()
            )),
        }
    }
}

impl Drop for TempFile {
    fn drop(&mut self) {
        if self.armed {
            self.file.take();
            let _ = std::fs::remove_file(&self.path);
        }
    }
}

#[cfg(not(windows))]
async fn replace_file(from: &Path, to: &Path) -> std::io::Result<()> {
    tokio::fs::rename(from, to).await
}

#[cfg(windows)]
async fn replace_file(from: &Path, to: &Path) -> std::io::Result<()> {
    use windows_sys::Win32::Storage::FileSystem::{MoveFileExW, MOVEFILE_REPLACE_EXISTING};

    fn wide(path: &Path) -> Vec<u16> {
        use std::os::windows::ffi::OsStrExt;

        path.as_os_str()
            .encode_wide()
            .chain(std::iter::once(0))
            .collect()
    }

    fn move_file(from: &Path, to: &Path) -> std::io::Result<()> {
        let from = wide(from);
        let to = wide(to);
        let replaced =
            unsafe { MoveFileExW(from.as_ptr(), to.as_ptr(), MOVEFILE_REPLACE_EXISTING) };
        if replaced == 0 {
            Err(std::io::Error::last_os_error())
        } else {
            Ok(())
        }
    }

    replace_with_retry(
        || async { move_file(from, to) },
        is_transient_windows_error,
        REPLACE_ATTEMPTS,
        REPLACE_BACKOFF,
    )
    .await
}

/// Sharing and access-denied are transient because another process may release
/// the file; every other Windows error is a permanent failure for this attempt.
#[cfg(windows)]
fn is_transient_windows_error(error: &std::io::Error) -> bool {
    const ERROR_ACCESS_DENIED: i32 = 5;
    const ERROR_SHARING_VIOLATION: i32 = 32;
    const ERROR_LOCK_VIOLATION: i32 = 33;

    matches!(
        error.raw_os_error(),
        Some(ERROR_ACCESS_DENIED | ERROR_SHARING_VIOLATION | ERROR_LOCK_VIOLATION)
    )
}

/// Shared retry policy so the bounded behavior is tested on every platform, not
/// only where the Windows helper happens to compile. The final transient error
/// says how many attempts were made, while permanent errors pass through.
#[cfg(any(windows, test))]
async fn replace_with_retry<F, Fut>(
    mut replace: F,
    is_transient: fn(&std::io::Error) -> bool,
    max_attempts: usize,
    backoff: Duration,
) -> std::io::Result<()>
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = std::io::Result<()>>,
{
    debug_assert!(max_attempts > 0);
    let mut attempt = 1;
    loop {
        match replace().await {
            Ok(()) => return Ok(()),
            Err(e) if is_transient(&e) && attempt < max_attempts => {
                tokio::time::sleep(backoff.saturating_mul(attempt as u32)).await;
                attempt += 1;
            }
            Err(e) if is_transient(&e) => {
                return Err(std::io::Error::new(
                    e.kind(),
                    format!(
                        "{e}; gave up after {attempt} attempts: the destination or its directory is still held"
                    ),
                ));
            }
            Err(e) => return Err(e),
        }
    }
}

#[cfg(test)]
mod tests;
