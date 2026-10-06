/* Installing a version, in the order the game needs it:

     manifest → libraries (+ natives) → assets → fabric → mods → done

   Everything shared lives under `shared/` and is content-addressed by the
   metadata's own SHA1, so two instances on the same version download it once.
   The instance directory only ever holds what is genuinely per-instance:
   extracted natives, mods, config, saves.

   Stages are reported as they start, and the bar inside a stage is driven by
   bytes. A stage that fails aborts the install with the real reason — a
   half-installed instance is never marked installed. */
use std::{
    path::{Path, PathBuf},
    sync::{atomic::Ordering, Arc},
};

use serde::Deserialize;
use tauri::{AppHandle, Emitter};

use crate::{
    download::{self, Item, Progress},
    error::{Error, Result},
    fabric,
    meta::{self},
    model::{InstallStage, InstallStageEvent},
    paths, version,
    version::{Artifact, VersionJson},
};

const RESOURCES: &str = "https://resources.download.minecraft.net";

#[derive(Debug, Deserialize)]
struct AssetIndex {
    objects: std::collections::BTreeMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

fn emit(app: &AppHandle, instance_id: &str, stage: InstallStage, pct: f64) {
    let _ = app.emit(
        "install://stage",
        InstallStageEvent {
            instance_id: instance_id.to_string(),
            stage,
            pct: (pct * 100.0).clamp(0.0, 100.0),
        },
    );
}

/// Resolve a version id to its full JSON, fetching and caching the file the
/// manifest points at. The cache is keyed by id and validated by the
/// manifest's own sha1, so a partial download can't poison it.
pub async fn version_json(client: &reqwest::Client, id: &str) -> Result<VersionJson> {
    let manifest = meta::manifest(client).await?;
    let entry = meta::find(&manifest, id)
        .ok_or_else(|| Error::NotFound(format!("version {id} in the Mojang manifest")))?;

    let file = version::json_file(id);
    let item = Item {
        url: entry.url.clone(),
        path: file.clone(),
        sha1: Some(entry.sha1.clone()),
        size: 0,
    };
    if !download::already_good(&item).await {
        let progress = Arc::new(Progress::default());
        download::fetch_all(client, vec![item], 1, progress).await?;
    }
    let bytes = tokio::fs::read(&file).await?;
    Ok(serde_json::from_slice(&bytes)?)
}

/// Vanilla, or vanilla with the Fabric overlay merged on top.
pub async fn resolve(client: &reqwest::Client, game: &str, loader: &str) -> Result<VersionJson> {
    let vanilla = version_json(client, game).await?;
    if loader != "fabric" {
        return Ok(vanilla);
    }
    let loader_version = fabric::latest_loader(client, game).await?;
    let overlay = fabric::profile(client, game, &loader_version).await?;
    Ok(VersionJson::inherit(overlay, vanilla))
}

fn library_items(v: &VersionJson) -> Vec<Item> {
    let root = paths::libraries_dir();
    v.usable_libraries()
        .filter_map(|lib| {
            Some(Item {
                url: lib.download_url()?,
                path: root.join(lib.rel_path()?),
                sha1: lib.checksum(),
                size: lib.byte_size(),
            })
        })
        .collect()
}

fn client_item(v: &VersionJson) -> Option<Item> {
    let a: &Artifact = v.client_jar()?;
    Some(Item {
        url: a.url.clone(),
        path: version::client_jar_file(&v.id),
        sha1: Some(a.sha1.clone()),
        size: a.size,
    })
}

async fn asset_items(client: &reqwest::Client, v: &VersionJson) -> Result<Vec<Item>> {
    let Some(idx) = v.asset_index.as_ref() else {
        return Ok(Vec::new());
    };
    let index_file = paths::assets_dir().join("indexes").join(format!("{}.json", idx.id));
    let item = Item {
        url: idx.url.clone(),
        path: index_file.clone(),
        sha1: Some(idx.sha1.clone()),
        size: idx.size,
    };
    if !download::already_good(&item).await {
        download::fetch_all(client, vec![item], 1, Arc::new(Progress::default())).await?;
    }

    let bytes = tokio::fs::read(&index_file).await?;
    let index: AssetIndex = serde_json::from_slice(&bytes)?;
    let objects = paths::assets_dir().join("objects");

    Ok(index
        .objects
        .values()
        .map(|o| Item {
            url: format!("{RESOURCES}/{}/{}", &o.hash[..2], o.hash),
            path: objects.join(&o.hash[..2]).join(&o.hash),
            sha1: Some(o.hash.clone()),
            size: o.size,
        })
        .collect())
}

/// Native jars hold the DLLs the game dlopen()s; they have to exist as loose
/// files. Anything outside the jar's own class tree is skipped so a malicious
/// entry cannot write outside the instance.
async fn extract_natives(v: &VersionJson, target: &Path) -> Result<usize> {
    // fully derived from the version json — a stale dll from an earlier
    // resolve (or an earlier bug) must not survive into this launch
    let _ = tokio::fs::remove_dir_all(target).await;
    paths::ensure_dir(target).await?;
    let root = paths::libraries_dir();
    let jars: Vec<PathBuf> = v
        .usable_libraries()
        .filter(|l| l.is_native())
        .filter_map(|l| l.rel_path().map(|p| root.join(p)))
        .collect();

    let target = target.to_path_buf();
    tokio::task::spawn_blocking(move || -> Result<usize> {
        let mut written = 0;
        for jar in jars {
            let Ok(file) = std::fs::File::open(&jar) else { continue };
            let mut zip = zip::ZipArchive::new(file)
                .map_err(|e| Error::Invalid(format!("{}: {e}", jar.display())))?;
            for i in 0..zip.len() {
                let mut entry = zip
                    .by_index(i)
                    .map_err(|e| Error::Invalid(format!("{}: {e}", jar.display())))?;
                let Some(name) = entry.enclosed_name().map(|p| p.to_path_buf()) else { continue };
                let ext = name.extension().and_then(|e| e.to_str()).unwrap_or_default();
                if !matches!(ext, "dll" | "so" | "dylib" | "jnilib") {
                    continue;
                }
                // flatten: the loader looks for bare filenames on the library path
                let Some(base) = name.file_name() else { continue };
                let out = target.join(base);
                let mut sink = std::fs::File::create(&out)?;
                std::io::copy(&mut entry, &mut sink)?;
                written += 1;
            }
        }
        Ok(written)
    })
    .await
    .map_err(|e| Error::Invalid(format!("native extraction panicked: {e}")))?
}

pub fn natives_dir(instance_id: &str) -> PathBuf {
    paths::instance_dir(instance_id).join("natives")
}

/// Where an instance's resolved version json is parked, so launching does not
/// have to hit the network or re-merge Fabric.
pub fn resolved_file(instance_id: &str) -> PathBuf {
    paths::instance_dir(instance_id).join("version.json")
}

/// The whole install, answering with the number of files actually fetched.
/// `concurrency` comes from settings — the user's own throttle, not a number
/// invented here.
pub async fn run(
    app: &AppHandle,
    client: &reqwest::Client,
    instance_id: &str,
    game: &str,
    loader: &str,
    concurrency: usize,
) -> Result<usize> {
    emit(app, instance_id, InstallStage::Manifest, 0.0);
    // the pristine per-version json is already on disk, sha1-checked, from
    // version_json(); re-serializing our own struct over it would drop the keys
    // this model does not model and break that hash on the next install
    let resolved = resolve(client, game, loader).await?;
    emit(app, instance_id, InstallStage::Manifest, 1.0);

    // ── libraries + the client jar ─────────────────────────────
    let mut items = library_items(&resolved);
    if let Some(jar) = client_item(&resolved) {
        items.push(jar);
    }
    let lib_progress = Arc::new(Progress::default());
    lib_progress
        .total_bytes
        .store(download::total_size(&items), Ordering::Relaxed);
    let mut fetched = pump(
        app,
        client,
        instance_id,
        InstallStage::Libraries,
        items,
        concurrency,
        lib_progress,
    )
    .await?;

    let extracted = extract_natives(&resolved, &natives_dir(instance_id)).await?;
    if extracted == 0 {
        return Err(Error::Invalid(
            "no native libraries were extracted — the game cannot start without them".into(),
        ));
    }

    // ── assets ─────────────────────────────────────────────────
    let assets = asset_items(client, &resolved).await?;
    let asset_progress = Arc::new(Progress::default());
    asset_progress
        .total_bytes
        .store(download::total_size(&assets), Ordering::Relaxed);
    fetched += pump(
        app,
        client,
        instance_id,
        InstallStage::Assets,
        assets,
        concurrency,
        asset_progress,
    )
    .await?;

    // ── loader + bundled mods ──────────────────────────────────
    emit(app, instance_id, InstallStage::Fabric, 1.0);
    emit(app, instance_id, InstallStage::Mods, 0.0);
    paths::ensure_dir(&paths::instance_minecraft_dir(instance_id)).await?;
    crate::hud::seed(instance_id).await?;
    crate::mods::apply(
        client,
        &paths::resources(app),
        instance_id,
        game,
        loader,
        concurrency,
        crate::mods::Policy::Refresh,
    )
    .await?;
    emit(app, instance_id, InstallStage::Mods, 1.0);

    crate::store::write(&resolved_file(instance_id), &resolved).await?;
    emit(app, instance_id, InstallStage::Done, 1.0);

    Ok(fetched)
}

/// Run one stage while reporting its byte progress on a timer — the download
/// tasks stay hot instead of stopping to talk to the UI.
async fn pump(
    app: &AppHandle,
    client: &reqwest::Client,
    instance_id: &str,
    stage: InstallStage,
    items: Vec<Item>,
    concurrency: usize,
    progress: Arc<Progress>,
) -> Result<usize> {
    emit(app, instance_id, stage, 0.0);
    if items.is_empty() {
        emit(app, instance_id, stage, 1.0);
        return Ok(0);
    }

    let ticker = {
        let app = app.clone();
        let id = instance_id.to_string();
        let progress = Arc::clone(&progress);
        tokio::spawn(async move {
            let mut tick = tokio::time::interval(std::time::Duration::from_millis(220));
            loop {
                tick.tick().await;
                emit(&app, &id, stage, progress.fraction());
            }
        })
    };

    let out = download::fetch_all(client, items, concurrency, Arc::clone(&progress)).await;
    ticker.abort();
    let fetched = out?;
    emit(app, instance_id, stage, 1.0);
    Ok(fetched)
}
