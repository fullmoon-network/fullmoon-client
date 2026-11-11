/* Test-only scratch directory under the platform temp root. */
use std::path::{Path, PathBuf};

pub(crate) struct TempDir(PathBuf);

impl TempDir {
    pub(crate) fn new(tag: &str) -> Self {
        Self(std::env::temp_dir().join(format!("fullmoon_{tag}_{}", uuid::Uuid::new_v4().simple())))
    }

    pub(crate) fn path(&self) -> &Path {
        &self.0
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

/* A process that forks - spawns a child - momentarily duplicates every open
file description, and a kernel-held lock on one of them is inherited by that
child until it execs. So in a test process, a lock released by one test can
transiently appear held while another test's child is being spawned. The
tests that spawn processes and the tests that verify a release right after a
drop therefore serialize on this mutex. Production is not affected: each
launcher is its own process, the only thing it spawns during a launch is the
game, and that happens while the launch reservation is still held. */
pub(crate) fn fork_window() -> &'static tokio::sync::Mutex<()> {
    static FORK_WINDOW: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
    &FORK_WINDOW
}
