/* JSON-file persistence. Writes go through an exclusively-owned temp file and
an atomic replacement so a crash mid-save cannot leave a half-written
settings.json, and two writers cannot consume each other's temp path. */
use std::path::Path;

use serde::{de::DeserializeOwned, Serialize};
use tokio::io::AsyncReadExt;

use crate::{
    atomic::TempFile,
    error::{Error, Result},
    paths,
};

pub async fn read_or<T: DeserializeOwned>(file: &Path, fallback: impl FnOnce() -> T) -> T {
    match tokio::fs::read(file).await {
        Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_else(|_| fallback()),
        Err(_) => fallback(),
    }
}

/// Read a state document that must parse if it exists. A missing file is
/// `None` - a fresh instance legitimately starts from defaults. An existing
/// file that cannot be read, is over the size bound, or does not hold the
/// expected shape is a named error, and the file is left exactly as it was:
/// silently resetting a corrupt state file would quietly delete the user's
/// disabled mods, favorites and pack choices.
pub(crate) async fn read_bounded<T: DeserializeOwned>(
    file: &Path,
    max_bytes: usize,
) -> Result<Option<T>> {
    let name = file.display();
    let file = match tokio::fs::File::open(file).await {
        Ok(file) => file,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(Error::Invalid(format!("{name} could not be opened: {e}"))),
    };
    let mut bytes = Vec::new();
    if let Err(e) = file
        .take(max_bytes as u64 + 1)
        .read_to_end(&mut bytes)
        .await
    {
        return Err(Error::Invalid(format!("{name} could not be read: {e}")));
    }
    if bytes.len() > max_bytes {
        return Err(Error::Invalid(format!(
            "{name} is larger than the {max_bytes} byte state limit; it has been left untouched"
        )));
    }
    match serde_json::from_slice(&bytes) {
        Ok(value) => Ok(Some(value)),
        Err(e) => Err(Error::Invalid(format!(
            "{name} does not hold valid state ({e}); it has been left untouched"
        ))),
    }
}

pub async fn write<T: Serialize>(file: &Path, value: &T) -> Result<()> {
    let parent = file
        .parent()
        .filter(|parent| !parent.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    paths::ensure_dir(parent).await?;
    let bytes = serde_json::to_vec_pretty(value)?;
    let stem = file
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("state.json");
    let mut temp = TempFile::create(parent, stem).await?;
    if let Err(e) = temp.write_all(&bytes).await {
        return Err(temp.discard(e).await);
    }
    temp.commit(file).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::TempDir;
    use serde_json::json;

    async fn part_names(dir: &Path) -> Vec<String> {
        let mut names = Vec::new();
        let mut entries = tokio::fs::read_dir(dir).await.unwrap();
        while let Some(entry) = entries.next_entry().await.unwrap() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.ends_with(".part") {
                names.push(name);
            }
        }
        names.sort();
        names
    }

    #[tokio::test]
    async fn a_failed_replacement_keeps_the_previous_file_and_cleans_its_temp() {
        let dir = TempDir::new("store_failed_replace");
        tokio::fs::create_dir_all(dir.path()).await.unwrap();
        let destination = dir.path().join("instance.json");
        tokio::fs::create_dir_all(&destination).await.unwrap();
        tokio::fs::write(destination.join("marker"), b"previous")
            .await
            .unwrap();

        let result = write(&destination, &json!({ "packs": ["new"] })).await;

        assert!(result.is_err());
        assert!(destination.is_dir());
        assert_eq!(
            tokio::fs::read(destination.join("marker")).await.unwrap(),
            b"previous"
        );
        assert!(part_names(dir.path()).await.is_empty());
    }

    #[tokio::test]
    async fn an_occupied_legacy_temp_path_is_neither_used_nor_removed() {
        let dir = TempDir::new("store_occupied_temp");
        tokio::fs::create_dir_all(dir.path()).await.unwrap();
        let destination = dir.path().join("instance.json");
        write(&destination, &json!({ "packs": ["old"] }))
            .await
            .unwrap();
        let legacy = dir.path().join("instance.json.tmp");
        tokio::fs::write(&legacy, b"another owner").await.unwrap();

        write(&destination, &json!({ "packs": ["new"] }))
            .await
            .unwrap();

        assert_eq!(
            tokio::fs::read(&legacy).await.unwrap(),
            b"another owner",
            "a fixed legacy temp path belongs to its owner"
        );
        let written: serde_json::Value =
            serde_json::from_slice(&tokio::fs::read(&destination).await.unwrap()).unwrap();
        assert_eq!(written, json!({ "packs": ["new"] }));
        assert!(part_names(dir.path()).await.is_empty());
    }

    #[tokio::test]
    async fn concurrent_writes_leave_one_complete_document() {
        let dir = TempDir::new("store_concurrent");
        tokio::fs::create_dir_all(dir.path()).await.unwrap();
        let destination = dir.path().join("instance.json");

        let runs = (0..16).map(|writer| {
            let destination = destination.clone();
            tokio::spawn(async move {
                write(
                    &destination,
                    &json!({ "writer": writer, "payload": "x".repeat(4096) }),
                )
                .await
            })
        });
        for run in runs {
            run.await.unwrap().unwrap();
        }

        let written: serde_json::Value =
            serde_json::from_slice(&tokio::fs::read(&destination).await.unwrap()).unwrap();
        assert!(written["writer"].as_u64().unwrap() < 16);
        assert_eq!(written["payload"].as_str().unwrap().len(), 4096);
        assert!(part_names(dir.path()).await.is_empty());
    }

    #[tokio::test]
    async fn dropping_an_uncommitted_temp_removes_it() {
        let dir = TempDir::new("store_interrupted");
        tokio::fs::create_dir_all(dir.path()).await.unwrap();
        let mut temp = TempFile::create(dir.path(), "instance.json").await.unwrap();
        let path = temp.path().to_owned();
        temp.write_all(b"half a document").await.unwrap();
        drop(temp);

        assert!(!path.exists());
        assert!(part_names(dir.path()).await.is_empty());
    }
}
