/* What `mods::apply` last put in an instance's `mods/`, and how it knew the
   jars were right. Every Play used to resolve each mod over the network, one
   after another, and re-read every jar to hash it. The lock lets a launch with
   nothing new to learn skip both:

   - a downloaded mod is trusted for `TTL` after the network last confirmed it
     as the newest build, as long as the jar on disk still has the size and
     mtime it had when it was hashed;
   - the bundled mod needs no network, but is only re-hashed when the shipped
     copy or the installed copy changed on disk.

   The TTL is 12 hours: mods for a fixed game version are republished rarely,
   so a player who launches twice in an evening resolves once, and a new build
   still lands within a day at worst. A lock that does not match is never an
   error — it just means "ask again". */
use std::{
    collections::BTreeMap,
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use serde::{Deserialize, Serialize};

use crate::{paths, store};

pub const TTL: Duration = Duration::from_secs(12 * 60 * 60);

/// Cheap fingerprint of a file: if neither moved, the bytes did not change.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Stamp {
    pub size: u64,
    pub mtime_ms: u64,
}

impl Stamp {
    pub async fn of(path: &Path) -> Option<Self> {
        let meta = tokio::fs::metadata(path).await.ok()?;
        if !meta.is_file() {
            return None;
        }
        let mtime_ms = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map_or(0, |d| d.as_millis() as u64);
        Some(Self { size: meta.len(), mtime_ms })
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Locked {
    pub file: String,
    pub version: Option<String>,
    pub sha1: String,
    /// the jar in `mods/` at the moment `sha1` was taken
    pub jar: Stamp,
    /// bundled mods only: the shipped copy that jar was made from
    pub origin: Option<Stamp>,
    /// unix seconds the network last named this file the newest build; 0 when
    /// it was never confirmed (bundled, or kept after a failed lookup)
    pub resolved_at: u64,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModLock {
    pub game: String,
    pub mods: BTreeMap<String, Locked>,
}

pub fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |d| d.as_secs())
}

/// A lock for another game version describes other jars, so it reads as empty.
pub async fn load(instance_id: &str, game: &str) -> ModLock {
    let lock: ModLock =
        store::read_or(&paths::instance_mod_lock_file(instance_id), ModLock::default).await;
    if lock.game == game {
        lock
    } else {
        ModLock { game: game.to_string(), mods: BTreeMap::new() }
    }
}

pub async fn save(instance_id: &str, lock: &ModLock) -> crate::error::Result<()> {
    store::write(&paths::instance_mod_lock_file(instance_id), lock).await
}

impl ModLock {
    /// A downloaded mod whose lookup can be skipped: the same file is still
    /// there, untouched since it was hashed, and the network confirmed it
    /// within `TTL`. A clock set back reads as stale, not as fresh forever.
    pub fn trusted(&self, id: &str, file: &str, jar: &Stamp, now: u64) -> Option<&Locked> {
        let e = self.mods.get(id)?;
        let age = now.checked_sub(e.resolved_at)?;
        (e.file == file && &e.jar == jar && e.resolved_at > 0 && age < TTL.as_secs()).then_some(e)
    }

    /// The bundled copy already in place: neither side moved since it was
    /// compared, so there is nothing to hash.
    pub fn bundled_current(&self, id: &str, file: &str, jar: Option<&Stamp>, origin: &Stamp) -> bool {
        match (self.mods.get(id), jar) {
            (Some(e), Some(jar)) => {
                e.file == file && &e.jar == jar && e.origin.as_ref() == Some(origin)
            }
            _ => false,
        }
    }

    /// The jar a network lookup named is the one already installed and
    /// verified — nothing to download or re-hash.
    pub fn same_download(&self, id: &str, file: &str, jar: Option<&Stamp>, sha1: Option<&str>) -> bool {
        match (self.mods.get(id), jar) {
            (Some(e), Some(jar)) => {
                e.file == file && &e.jar == jar && sha1.map_or(true, |s| s == e.sha1)
            }
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const H: u64 = 3600;

    fn stamp(size: u64, mtime_ms: u64) -> Stamp {
        Stamp { size, mtime_ms }
    }

    fn lock_with(resolved_at: u64) -> ModLock {
        let mut lock = ModLock { game: "26.1.2".into(), ..ModLock::default() };
        lock.mods.insert(
            "sodium".into(),
            Locked {
                file: "sodium-fabric-0.9.1+mc26.1.2.jar".into(),
                version: Some("0.9.1+mc26.1.2".into()),
                sha1: "aa".into(),
                jar: stamp(100, 5),
                origin: None,
                resolved_at,
            },
        );
        lock
    }

    const F: &str = "sodium-fabric-0.9.1+mc26.1.2.jar";

    #[test]
    fn a_recent_untouched_jar_skips_the_lookup() {
        let lock = lock_with(1_000);
        assert!(lock.trusted("sodium", F, &stamp(100, 5), 1_000 + H).is_some());
    }

    #[test]
    fn the_lookup_returns_once_the_ttl_has_passed() {
        let lock = lock_with(1_000);
        assert!(lock.trusted("sodium", F, &stamp(100, 5), 1_000 + TTL.as_secs() - 1).is_some());
        assert!(lock.trusted("sodium", F, &stamp(100, 5), 1_000 + TTL.as_secs()).is_none());
    }

    #[test]
    fn a_jar_that_changed_on_disk_is_not_trusted() {
        let lock = lock_with(1_000);
        assert!(lock.trusted("sodium", F, &stamp(101, 5), 1_000 + H).is_none());
        assert!(lock.trusted("sodium", F, &stamp(100, 6), 1_000 + H).is_none());
    }

    #[test]
    fn another_file_name_or_an_unknown_mod_is_not_trusted() {
        let lock = lock_with(1_000);
        assert!(lock.trusted("sodium", "sodium-fabric-0.9.0.jar", &stamp(100, 5), 1_000 + H).is_none());
        assert!(lock.trusted("lithium", F, &stamp(100, 5), 1_000 + H).is_none());
    }

    #[test]
    fn a_clock_set_back_or_an_unconfirmed_entry_is_stale() {
        assert!(lock_with(10_000).trusted("sodium", F, &stamp(100, 5), 5_000).is_none());
        assert!(lock_with(0).trusted("sodium", F, &stamp(100, 5), 10).is_none());
    }

    #[test]
    fn a_lookup_naming_the_installed_jar_downloads_nothing() {
        let lock = lock_with(1_000);
        let jar = stamp(100, 5);
        assert!(lock.same_download("sodium", F, Some(&jar), Some("aa")));
        assert!(lock.same_download("sodium", F, Some(&jar), None));
        assert!(!lock.same_download("sodium", F, Some(&jar), Some("bb")));
        assert!(!lock.same_download("sodium", F, Some(&stamp(99, 5)), Some("aa")));
        assert!(!lock.same_download("sodium", F, None, Some("aa")));
    }

    #[test]
    fn the_bundled_jar_is_rehashed_only_when_either_side_moved() {
        let mut lock = lock_with(0);
        let e = lock.mods.get_mut("sodium").unwrap();
        e.origin = Some(stamp(900, 7));
        let jar = stamp(100, 5);
        assert!(lock.bundled_current("sodium", F, Some(&jar), &stamp(900, 7)));
        assert!(!lock.bundled_current("sodium", F, Some(&jar), &stamp(901, 7)), "launcher update");
        assert!(!lock.bundled_current("sodium", F, Some(&stamp(100, 6)), &stamp(900, 7)), "edited jar");
        assert!(!lock.bundled_current("sodium", F, None, &stamp(900, 7)), "deleted jar");
    }

    #[test]
    fn the_lock_survives_a_round_trip_through_json() {
        let lock = lock_with(42);
        let back: ModLock = serde_json::from_str(&serde_json::to_string(&lock).unwrap()).unwrap();
        assert_eq!(back.mods, lock.mods);
        assert_eq!(back.game, "26.1.2");
    }

    #[tokio::test]
    async fn a_stamp_follows_the_file_and_misses_a_directory() {
        let dir = std::env::temp_dir().join("fullmoon_test_modlock_stamp");
        let _ = tokio::fs::create_dir_all(&dir).await;
        let file = dir.join("a.jar");
        tokio::fs::write(&file, b"one").await.unwrap();
        let first = Stamp::of(&file).await.unwrap();
        assert_eq!(first.size, 3);

        tokio::fs::write(&file, b"three").await.unwrap();
        assert_ne!(Stamp::of(&file).await.unwrap(), first);

        assert!(Stamp::of(&dir).await.is_none());
        assert!(Stamp::of(&dir.join("missing.jar")).await.is_none());
        let _ = tokio::fs::remove_dir_all(&dir).await;
    }
}
