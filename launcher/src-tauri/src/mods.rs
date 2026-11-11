/* Mod delivery.

The catalogue the UI reads is product copy; this is where a listed mod turns
into a jar in `<instance>/minecraft/mods`. Three kinds of source:

- `bundled` — Pinion's own mod, shipped inside the launcher, no network;
- `maven`   — Fabric's maven, newest build carrying the game version;
- `modrinth`— whatever the author published for this version + loader.

What ended up on disk is written back into `instance.json`, so a later sync
knows which files it owns and can take away a mod the user switched off
without ever touching a jar the user dropped in by hand. A mod with no build
for the instance's game version is skipped, not faked: the catalogue lists a
product, the version index decides what exists. */
use std::{
    collections::{BTreeMap, HashMap},
    path::{Path, PathBuf},
    sync::{Arc, OnceLock},
};

use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;

use crate::{
    catalog,
    download::{self, Item, Progress},
    error::{Error, Result},
    filelock,
    modlock::{self, Locked, ModLock, Stamp},
    paths, store,
};

/// Per-instance mod bookkeeping, stored in `<instance>/instance.json`.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstanceModState {
    #[serde(default)]
    pub disabled: Vec<String>,
    #[serde(default)]
    pub favorites: Vec<String>,
    /// mod id → the jar this launcher put in `mods/` for it.
    #[serde(default)]
    pub managed: BTreeMap<String, String>,
    /// Resource packs this launcher has switched on in `options.txt`. Each is
    /// switched on once; after that the game's pack list is the player's.
    #[serde(default)]
    pub packs: Vec<String>,
}

pub async fn state(instance_id: &str) -> InstanceModState {
    store::read_or(
        &paths::instance_state_file(instance_id),
        InstanceModState::default,
    )
    .await
}

/// One lock per state file, for the lifetime of the process. Two tasks
/// changing different fields of the same instance must not read the same
/// original, or the second write erases the first. The kernel lock below
/// covers separate launcher processes; this async mutex keeps in-process
/// transactions from spinning against each other.
fn state_lock(file: &Path) -> Arc<Mutex<()>> {
    static LOCKS: OnceLock<std::sync::Mutex<HashMap<PathBuf, Arc<Mutex<()>>>>> = OnceLock::new();
    let locks = LOCKS.get_or_init(|| std::sync::Mutex::new(HashMap::new()));
    let mut map = locks.lock().expect("state lock registry");
    Arc::clone(
        map.entry(file.to_path_buf())
            .or_insert_with(|| Arc::new(Mutex::new(()))),
    )
}

/// A hidden persistent sibling of the state file whose kernel lock owns the
/// read-modify-write transaction across processes: `.instance.json.lock`.
fn state_guard_file(state_file: &Path) -> PathBuf {
    let name = state_file
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("state");
    state_file.with_file_name(format!(".{name}.lock"))
}

/// A serialized instance-state transaction: read the freshest state, fold one
/// field's change into it, and persist - owned first by the process's async
/// mutex, then by a kernel-held advisory lock on a persistent sibling file, so
/// concurrent launcher processes wait their turn instead of each writing from
/// the same original and erasing the other's field. The change is a function
/// over the whole state that touches only its own field, so a pack selection
/// written while a mod toggle is in flight survives both.
pub(crate) async fn update_state_at(
    state_file: &Path,
    update: impl FnOnce(InstanceModState) -> InstanceModState,
) -> Result<()> {
    let lock = state_lock(state_file);
    let _transaction = lock.lock().await;
    let _owner = filelock::take(state_guard_file(state_file)).await?;
    merge_locked(state_file, update).await
}

/// The transaction body, run by a caller that already owns the guard lock.
/// Separate so a cross-process test can hold the lock from another process
/// while this merge runs against real persistence.
///
/// A missing state file starts from defaults; an existing one that is
/// corrupt, oversized, or unreadable is an error and is left untouched -
/// writing defaults over it would quietly delete the user's choices.
async fn merge_locked(
    state_file: &Path,
    update: impl FnOnce(InstanceModState) -> InstanceModState,
) -> Result<()> {
    let fresh = store::read_bounded(state_file, MAX_STATE_BYTES)
        .await?
        .unwrap_or_default();
    store::write(state_file, &update(fresh)).await
}

/// An instance's state is a few hundred bytes at most; anything bigger is not
/// something this launcher wrote, and the file is left for the user.
pub(crate) const MAX_STATE_BYTES: usize = 1024 * 1024;

/// Switch a mod on or off in the freshest state, not the snapshot some earlier
/// read produced.
pub(crate) async fn set_disabled(state_file: &Path, mod_id: &str, enabled: bool) -> Result<()> {
    update_state_at(state_file, |fresh| {
        let mut disabled = fresh.disabled;
        disabled.retain(|x| x != mod_id);
        if !enabled {
            disabled.push(mod_id.to_string());
        }
        InstanceModState { disabled, ..fresh }
    })
    .await
}

/// Record or clear a favorite the same way.
pub(crate) async fn set_favorite(state_file: &Path, mod_id: &str, favorite: bool) -> Result<()> {
    update_state_at(state_file, |fresh| {
        let mut favorites = fresh.favorites;
        favorites.retain(|x| x != mod_id);
        if favorite {
            favorites.push(mod_id.to_string());
        }
        InstanceModState { favorites, ..fresh }
    })
    .await
}

/// The mod toggle command: state first, then the jar move that matches it.
pub async fn toggle(instance_id: &str, mod_id: &str, enabled: bool) -> Result<()> {
    set_disabled(&paths::instance_state_file(instance_id), mod_id, enabled).await
}

/// The favorite command: a pure state change.
pub async fn favorite(instance_id: &str, mod_id: &str, favorite: bool) -> Result<()> {
    set_favorite(&paths::instance_state_file(instance_id), mod_id, favorite).await
}

/// Which managed jars are on disk this second, by catalogue id. The state file
/// records intent; only the directory can say what a launch would actually
/// load, and a user is free to delete a jar behind our back.
pub async fn installed(instance_id: &str) -> BTreeMap<String, String> {
    let dir = paths::instance_mods_dir(instance_id);
    let mut present = BTreeMap::new();
    for (id, file) in state(instance_id).await.managed {
        if tokio::fs::metadata(dir.join(&file)).await.is_ok() {
            present.insert(id, file);
        }
    }
    present
}

/// `sodium-fabric-0.9.1+mc26.1.2.jar` → `0.9.1+mc26.1.2`. The catalogue's
/// version is product copy written months ago; this is the build that shipped.
/// The version starts at the first dash followed by a digit, which is the one
/// convention every artifact here follows.
pub fn version_from_file(file: &str) -> Option<String> {
    let stem = file.strip_suffix(".jar")?;
    stem.as_bytes()
        .windows(2)
        .position(|w| w[0] == b'-' && w[1].is_ascii_digit())
        .map(|i| stem[i + 1..].to_string())
}

#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum ModSource {
    /// Shipped with the launcher — `resources/mods/<file>`.
    Bundled {
        file: String,
    },
    Maven {
        group: String,
        artifact: String,
    },
    Modrinth {
        project: String,
    },
}

/// One resolved jar: where to get it, what to call it, how to check it.
#[derive(Debug, Clone)]
pub struct Resolved {
    pub file: String,
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub local: Option<PathBuf>,
    pub size: u64,
}

pub async fn resolve(
    client: &reqwest::Client,
    source: &ModSource,
    game: &str,
    resources: &std::path::Path,
) -> Result<Option<Resolved>> {
    match source {
        ModSource::Bundled { file } => {
            let local = resources.join("mods").join(file);
            if !local.is_file() {
                return Err(Error::NotFound(format!(
                    "bundled mod {} is missing from the install ({})",
                    file,
                    local.display()
                )));
            }
            let size = tokio::fs::metadata(&local)
                .await
                .map(|m| m.len())
                .unwrap_or(0);
            Ok(Some(Resolved {
                file: file.clone(),
                url: None,
                sha1: None,
                local: Some(local),
                size,
            }))
        }
        ModSource::Maven { group, artifact } => maven(client, group, artifact, game).await,
        ModSource::Modrinth { project } => modrinth(client, project, game).await,
    }
}

/// Newest maven build whose version carries `+<game>` — Fabric's convention for
/// "this build is for that game version".
async fn maven(
    client: &reqwest::Client,
    group: &str,
    artifact: &str,
    game: &str,
) -> Result<Option<Resolved>> {
    let base = format!(
        "https://maven.fabricmc.net/{}/{artifact}",
        group.replace('.', "/")
    );
    let meta = client
        .get(format!("{base}/maven-metadata.xml"))
        .send()
        .await?;
    if !meta.status().is_success() {
        return Err(Error::Invalid(format!(
            "{group}:{artifact} maven-metadata → HTTP {}",
            meta.status()
        )));
    }
    let xml = meta.text().await?;

    let suffix = format!("+{game}");
    // maven-metadata lists oldest first, so the last match is the newest build
    let Some(version) = xml
        .split("<version>")
        .skip(1)
        .filter_map(|chunk| chunk.split("</version>").next())
        .filter(|v| v.ends_with(&suffix))
        .last()
        .map(str::to_string)
    else {
        return Ok(None);
    };

    let file = format!("{artifact}-{version}.jar");
    let url = format!("{base}/{version}/{file}");
    let sha1 = client
        .get(format!("{url}.sha1"))
        .send()
        .await
        .ok()
        .filter(|r| r.status().is_success());
    let sha1 = match sha1 {
        Some(r) => r.text().await.ok().map(|s| s.trim().to_string()),
        None => None,
    };

    Ok(Some(Resolved {
        file,
        url: Some(url),
        sha1,
        local: None,
        size: 0,
    }))
}

#[derive(Debug, Deserialize)]
struct MrVersion {
    files: Vec<MrFile>,
}

#[derive(Debug, Deserialize)]
struct MrFile {
    url: String,
    filename: String,
    primary: bool,
    hashes: MrHashes,
    #[serde(default)]
    size: u64,
}

#[derive(Debug, Deserialize)]
struct MrHashes {
    sha1: Option<String>,
}

async fn modrinth(client: &reqwest::Client, project: &str, game: &str) -> Result<Option<Resolved>> {
    let url = format!(
        "https://api.modrinth.com/v2/project/{project}/version\
         ?game_versions=%5B%22{game}%22%5D&loaders=%5B%22fabric%22%5D"
    );
    let res = client.get(&url).send().await?;
    if !res.status().is_success() {
        return Err(Error::Invalid(format!(
            "modrinth {project} → HTTP {}",
            res.status()
        )));
    }
    let versions: Vec<MrVersion> = res.json().await?;
    let Some(first) = versions.into_iter().next() else {
        return Ok(None);
    };
    let Some(file) = first
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| first.files.first())
    else {
        return Ok(None);
    };

    Ok(Some(Resolved {
        file: file.filename.clone(),
        url: Some(file.url.clone()),
        sha1: file.hashes.sha1.clone(),
        local: None,
        size: file.size,
    }))
}

/// How much of last time's lookup a sync may lean on.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Policy {
    /// Play and the mod toggles: a jar the lock still vouches for is not looked up again.
    Reuse,
    /// Install: ask every host, whatever the lock says.
    Refresh,
}

/// With a jar on disk to fall back on, a host that does not answer is not
/// worth waiting out the connect timeout for.
const LOOKUP_PATIENCE: std::time::Duration = std::time::Duration::from_secs(8);

/// Bring the instance's `mods/` in line with what is switched on, and record
/// what we put there. The single entry point — install and the mod toggles all
/// go through here, so there is one description of "what should be on disk".
pub async fn apply(
    client: &reqwest::Client,
    resources: &std::path::Path,
    instance_id: &str,
    game: &str,
    loader: &str,
    concurrency: usize,
    policy: Policy,
) -> Result<()> {
    /* The state is read fail-closed here: a corrupt state file must fail the
    sync before any jar is touched, with a named recovery error, instead of
    syncing from defaults and then refusing to save. */
    let st = store::read_bounded::<InstanceModState>(
        &paths::instance_state_file(instance_id),
        MAX_STATE_BYTES,
    )
    .await?
    .unwrap_or_default();
    let enabled: Vec<String> = catalog::get()
        .mods
        .iter()
        .map(|m| m.id.clone())
        .filter(|id| !st.disabled.contains(id))
        .collect();

    let lock = modlock::load(instance_id, game).await;
    let (managed, lock) = sync(
        client,
        SyncInput {
            resources,
            instance_id,
            loader,
            enabled: &enabled,
            managed: &st.managed,
            concurrency,
            policy,
        },
        lock,
    )
    .await?;
    // a lock that fails to save only costs the next launch a lookup
    let _ = modlock::save(instance_id, &lock).await;
    /* The sync above can hold a network request for seconds; the managed map it
    computed must be merged into the freshest state, not written back from this
    snapshot over a pack selection or a toggle that happened meanwhile. */
    update_state_at(&paths::instance_state_file(instance_id), move |fresh| {
        InstanceModState { managed, ..fresh }
    })
    .await
}

/// Everything one sync needs besides the HTTP client and the old lock. Grouped
/// so the entry point stays readable instead of growing a ninth parameter.
struct SyncInput<'a> {
    resources: &'a std::path::Path,
    instance_id: &'a str,
    loader: &'a str,
    enabled: &'a [String],
    managed: &'a BTreeMap<String, String>,
    concurrency: usize,
    policy: Policy,
}

/// Make `<instance>/minecraft/mods` match `enabled`, and return the new
/// managed map for `instance.json` with the lock that describes it.
async fn sync(
    client: &reqwest::Client,
    input: SyncInput<'_>,
    old: ModLock,
) -> Result<(BTreeMap<String, String>, ModLock)> {
    let SyncInput {
        resources,
        instance_id,
        loader,
        enabled,
        managed,
        concurrency,
        policy,
    } = input;
    let dir = paths::instance_mods_dir(instance_id);
    paths::ensure_dir(&dir).await?;

    let game = old.game.clone();
    let game_ref = &game;
    let now = modlock::now_secs();
    let sources = &catalog::get().mod_sources;
    let mut next: BTreeMap<String, String> = BTreeMap::new();
    let mut lock = ModLock {
        game: game.clone(),
        mods: BTreeMap::new(),
    };
    let mut fetches: Vec<Item> = Vec::new();
    let mut fetched_ids: Vec<(String, String)> = Vec::new();
    let mut copies: Vec<(String, PathBuf, PathBuf)> = Vec::new();

    // vanilla instances get no mods at all — there is no loader to run them
    if loader == "fabric" {
        /* What the lock vouches for needs no lookup. The rest is resolved all
        at once: three hosts answering in turn made the launch wait for the
        sum of them. */
        let mut lookups = Vec::new();
        for (id, source) in sources {
            if !enabled.iter().any(|e| e == id) {
                continue;
            }
            let on_disk = managed.get(id).filter(|f| dir.join(f.as_str()).is_file());
            if let (
                Policy::Reuse,
                ModSource::Maven { .. } | ModSource::Modrinth { .. },
                Some(file),
            ) = (policy, source, on_disk)
            {
                if let Some(jar) = Stamp::of(&dir.join(file)).await {
                    if let Some(e) = old.trusted(id, file, &jar, now) {
                        next.insert(id.clone(), file.clone());
                        lock.mods.insert(id.clone(), e.clone());
                        continue;
                    }
                }
            }
            lookups.push(async move {
                /* A host being down is not a reason to ground a launch: if the jar
                this instance already runs is still on disk, keep running it and
                resolve again next time. Only a mod with nothing on disk can
                fail the sync. */
                let res = match on_disk {
                    Some(_) => tokio::time::timeout(
                        LOOKUP_PATIENCE,
                        resolve(client, source, game_ref, resources),
                    )
                    .await
                    .unwrap_or_else(|_| Err(Error::Invalid(format!("{id}: lookup timed out")))),
                    None => resolve(client, source, game_ref, resources).await,
                };
                (id, on_disk, res)
            });
        }

        for (id, on_disk, res) in futures::future::join_all(lookups).await {
            let res = match res {
                Ok(res) => res,
                Err(e) => match on_disk {
                    Some(file) => {
                        next.insert(id.clone(), file.clone());
                        if let Some(e) = old.mods.get(id).filter(|e| &e.file == file) {
                            lock.mods.insert(id.clone(), e.clone());
                        }
                        continue;
                    }
                    None => return Err(e),
                },
            };
            let Some(res) = res else {
                continue; // no build for this game version
            };
            let target = dir.join(&res.file);
            next.insert(id.clone(), res.file.clone());

            match (&res.url, &res.local) {
                (Some(url), _) => {
                    let jar = Stamp::of(&target).await;
                    if old.same_download(id, &res.file, jar.as_ref(), res.sha1.as_deref()) {
                        let mut e = old.mods[id].clone();
                        e.resolved_at = now;
                        lock.mods.insert(id.clone(), e);
                    } else {
                        fetches.push(Item {
                            url: url.clone(),
                            path: target,
                            sha1: res.sha1.clone(),
                            size: res.size,
                        });
                        fetched_ids.push((id.clone(), res.file.clone()));
                    }
                }
                (None, Some(local)) => copies.push((id.clone(), local.clone(), target)),
                _ => {}
            }
        }
    }

    // anything we put there before and no longer want goes away; a jar the user
    // dropped in by hand was never ours and is left alone
    for (id, file) in managed {
        if next.get(id).map(String::as_str) != Some(file.as_str()) {
            let _ = tokio::fs::remove_file(dir.join(file)).await;
        }
    }

    /* Compared by content, not by length: our own mod keeps its file name and
    usually its size across builds, so a length check left every instance
    running whichever jar it happened to install first. Content is only read
    when the lock cannot say that neither copy moved. */
    for (id, from, to) in copies {
        let file = next[&id].clone();
        let origin = Stamp::of(&from).await;
        let jar = Stamp::of(&to).await;
        if let Some(origin) = &origin {
            if old.bundled_current(&id, &file, jar.as_ref(), origin) {
                lock.mods.insert(id.clone(), old.mods[&id].clone());
                continue;
            }
        }
        let want = download::sha1_of(&from).await;
        if jar.is_none() || download::sha1_of(&to).await != want {
            tokio::fs::copy(&from, &to).await?;
        }
        if let (Some(sha1), Some(origin), Some(jar)) = (want, origin, Stamp::of(&to).await) {
            lock.mods.insert(
                id,
                Locked {
                    version: version_from_file(&file),
                    file,
                    sha1,
                    jar,
                    origin: Some(origin),
                    resolved_at: 0,
                },
            );
        }
    }

    if !fetches.is_empty() {
        let wanted: BTreeMap<_, _> = fetches
            .iter()
            .map(|i| (i.path.clone(), i.sha1.clone()))
            .collect();
        download::fetch_all(client, fetches, concurrency, Arc::new(Progress::default())).await?;
        for (id, file) in fetched_ids {
            let path = dir.join(&file);
            let sha1 = match wanted.get(&path).cloned().flatten() {
                Some(s) => Some(s),
                None => download::sha1_of(&path).await,
            };
            if let (Some(sha1), Some(jar)) = (sha1, Stamp::of(&path).await) {
                lock.mods.insert(
                    id,
                    Locked {
                        version: version_from_file(&file),
                        file,
                        sha1,
                        jar,
                        origin: None,
                        resolved_at: now,
                    },
                );
            }
        }
    }

    Ok((next, lock))
}

#[cfg(test)]
mod tests;
