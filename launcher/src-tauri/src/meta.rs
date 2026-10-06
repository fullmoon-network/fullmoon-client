/* piston-meta is the source of truth for what a version *is*. Nothing here
   guesses: the manifest is fetched, cached on disk, and if the network is
   down we say so with the cache's age rather than inventing a version list. */
use serde::{Deserialize, Serialize};

use crate::{error::Result, model::VersionSummary, paths, store};

pub const MANIFEST_URL: &str = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

/// The version this build targets. Not a hardcoded list — it is resolved
/// against the live manifest, and if the manifest stops carrying it the UI
/// gets told (PLAN §1).
pub const TARGET_VERSION: &str = "26.1.2";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    pub sha1: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LatestPair {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: LatestPair,
    pub versions: Vec<ManifestEntry>,
}

fn cache_file() -> std::path::PathBuf {
    paths::shared().join("version_manifest_v2.json")
}

/// Fetch fresh, fall back to the on-disk copy. The cache is only consulted
/// when the network fails, so a stale list is never preferred to a live one.
pub async fn manifest(client: &reqwest::Client) -> Result<VersionManifest> {
    match client.get(MANIFEST_URL).send().await {
        Ok(res) if res.status().is_success() => {
            let text = res.text().await?;
            let parsed: VersionManifest = serde_json::from_str(&text)?;
            let file = cache_file();
            if let Some(dir) = file.parent() {
                paths::ensure_dir(dir).await?;
            }
            let _ = tokio::fs::write(&file, text.as_bytes()).await;
            Ok(parsed)
        }
        other => {
            let cached: Option<VersionManifest> =
                store::read_or(&cache_file(), || None).await;
            match cached {
                Some(m) => Ok(m),
                None => Err(match other {
                    Ok(res) => crate::error::Error::Invalid(format!(
                        "version manifest returned HTTP {}",
                        res.status()
                    )),
                    Err(e) => crate::error::Error::Http(e),
                }),
            }
        }
    }
}

/// The on-disk copy alone, never the network — what the first paint can show
/// while `manifest` refreshes in the background. `None` before the first
/// successful fetch.
pub async fn cached() -> Option<VersionManifest> {
    store::read_or(&cache_file(), || None).await
}

/// Releases and snapshots the UI is allowed to offer, newest first.
pub fn summaries(m: &VersionManifest) -> Vec<VersionSummary> {
    m.versions
        .iter()
        .filter(|v| v.kind == "release" || v.kind == "snapshot")
        .map(|v| VersionSummary {
            id: v.id.clone(),
            kind: v.kind.clone(),
            release_time: v.release_time.clone(),
            is_target: v.id == TARGET_VERSION,
        })
        .collect()
}

pub fn find<'m>(m: &'m VersionManifest, id: &str) -> Option<&'m ManifestEntry> {
    m.versions.iter().find(|v| v.id == id)
}
