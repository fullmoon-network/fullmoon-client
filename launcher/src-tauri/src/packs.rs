/* GUI resource pack delivery.

The Fullmoon GUI archive ships with the client and seeds or upgrades its local
copy without a network lookup. The mod also loads its assets unconditionally.
Other sources remain publish-driven: the server publishes the zip and a SHA-1
line, and a verified temporary file replaces the old one only after it matches.

A pack never grounds a launch. Every failure - no published line, an offline
host, an oversized or corrupt download, an unreadable options file - leaves the
copy that was already on disk and becomes a warning the launch surface can show.

A pack is switched on in `options.txt` once per instance, the first time a copy
is on disk. From then on the list is the player's: a pack they switched off stays
off, and their other entries keep their order. */
use std::{path::Path, time::Duration};

use futures::StreamExt;
use serde::Deserialize;
use sha1::{Digest, Sha1};
use tokio::io::AsyncReadExt;

use crate::{
    atomic::TempFile,
    catalog,
    error::{Error, Result},
    mods, paths, spec, store,
};

mod bundled;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PackSource {
    pub id: String,
    /// The name in `resourcepacks/`, and so in the game's list as `file/<file>`.
    pub file: String,
    pub url: String,
    /// One line, the zip's SHA-1 in hex.
    pub sha1_url: String,
    /// The game version the pack was made for; an instance of any other goes without it.
    pub game: String,
    #[serde(default)]
    pub bundled: bool,
}

/// What one refresh did, and everything the player may need to know about a pack
/// that could not be refreshed. Warnings are already complete sentences.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct PackReport {
    pub updated: Vec<String>,
    pub warnings: Vec<String>,
}

/// The SHA-1 line is a few bytes; a host that cannot send it this fast is not one to wait on.
const LOOKUP_PATIENCE: Duration = Duration::from_secs(8);
/// A pack download is bounded in time and size so a stalled or hostile host cannot
/// hold a launch open or fill the disk.
const FETCH_PATIENCE: Duration = Duration::from_secs(60);
const MAX_PACK_BYTES: u64 = 64 * 1024 * 1024;
const MAX_SHA1_BYTES: usize = 4 * 1024;
const MAX_OPTIONS_BYTES: usize = 1024 * 1024;

const PACKS_KEY: &str = "resourcePacks:";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Refresh {
    UpToDate,
    Updated,
}

#[derive(Debug, PartialEq, Eq)]
enum PackListEdit {
    Unchanged,
    Updated(String),
    Unreadable,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SwitchOutcome {
    Selected,
    AlreadySelected,
    Deferred,
    Unreadable,
}

#[derive(Debug, PartialEq, Eq)]
enum OptionsRead {
    Missing,
    Unreadable,
    Text(String),
}

/// Bring the instance's packs up to the published ones, and switch on any it has
/// not had before. Never returns an error: an optional pack is not a launch gate.
pub async fn apply(client: &reqwest::Client, instance_id: &str, game: &str) -> PackReport {
    apply_with(
        client,
        &paths::instance_minecraft_dir(instance_id),
        &paths::instance_state_file(instance_id),
        game,
        &catalog::get().packs,
    )
    .await
}

/// The refresh works on its own game directory and state file so the merge back
/// into `instance.json` can be tested without the process-wide data root.
async fn apply_with(
    client: &reqwest::Client,
    game_dir: &Path,
    state_file: &Path,
    game: &str,
    packs: &[PackSource],
) -> PackReport {
    /* Fail-open on a state file this launcher cannot read: the pack stays an
    optional warning, the corrupt file is left untouched, and no default is
    written over the user's choices. */
    let before = match store::read_bounded::<mods::InstanceModState>(
        state_file,
        mods::MAX_STATE_BYTES,
    )
    .await
    {
        Ok(Some(state)) => state.packs,
        Ok(None) => Vec::new(),
        Err(e) => {
            return PackReport {
                updated: Vec::new(),
                warnings: vec![format!(
                    "instance state could not be read, so the pack selection was not checked: {e}"
                )],
            }
        }
    };
    let (report, switched) = refresh_all(client, game_dir, game, packs, before.clone()).await;
    if switched == before {
        return report;
    }

    /* The download above must not hold the state lock, and its old snapshot must
    not be written back over a mod toggle or a second refresh. The freshest
    state is read under the instance lock and only the pack field is merged. */
    let remembered = mods::update_state_at(state_file, move |fresh| {
        let mut packs = fresh.packs;
        for id in switched {
            if !packs.contains(&id) {
                packs.push(id);
            }
        }
        mods::InstanceModState { packs, ..fresh }
    })
    .await;
    match remembered {
        Ok(_) => report,
        Err(e) => {
            let PackReport {
                updated,
                mut warnings,
            } = report;
            warnings.push(format!(
                "could not remember the resource pack selection: {e}"
            ));
            PackReport { updated, warnings }
        }
    }
}

async fn refresh_all(
    client: &reqwest::Client,
    game_dir: &Path,
    game: &str,
    packs: &[PackSource],
    mut switched_on: Vec<String>,
) -> (PackReport, Vec<String>) {
    let mut updated = Vec::new();
    let mut warnings = Vec::new();
    for pack in packs.iter().filter(|pack| pack.game == game) {
        match refresh_one(client, pack, game_dir).await {
            Ok(Refresh::Updated) => updated.push(pack.id.clone()),
            Ok(Refresh::UpToDate) => {}
            Err(e) => {
                warnings.push(format!("{}: {e}", pack.id));
                continue;
            }
        }
        if switched_on.iter().any(|id| id == &pack.id) {
            continue;
        }
        let entry = format!("file/{}", pack.file);
        let fresh = (game == spec::FAST_OPTIONS_VERSION).then_some(spec::OPTIONS_DATA_VERSION);
        match switch_on(&game_dir.join("options.txt"), &entry, fresh).await {
            Ok(SwitchOutcome::Selected | SwitchOutcome::AlreadySelected) => {
                switched_on.push(pack.id.clone());
            }
            Ok(SwitchOutcome::Deferred) => warnings.push(format!(
                "{}: there is no options.txt and no known data version to create one",
                pack.id
            )),
            Ok(SwitchOutcome::Unreadable) => warnings.push(format!(
                "{}: the resource pack list in options.txt is unreadable; left unchanged",
                pack.id
            )),
            Err(e) => warnings.push(format!(
                "{}: could not select it in options.txt: {e}",
                pack.id
            )),
        }
    }
    (PackReport { updated, warnings }, switched_on)
}

async fn refresh_one(
    client: &reqwest::Client,
    pack: &PackSource,
    game_dir: &Path,
) -> Result<Refresh> {
    refresh_one_with(client, pack, game_dir, FETCH_PATIENCE).await
}

async fn refresh_one_with(
    client: &reqwest::Client,
    pack: &PackSource,
    game_dir: &Path,
    fetch_patience: Duration,
) -> Result<Refresh> {
    if !safe_file(&pack.file) {
        return Err(Error::Invalid(format!(
            "{} is not a safe resource pack file name",
            pack.file
        )));
    }
    let dir = game_dir.join("resourcepacks");
    let path = dir.join(&pack.file);
    if pack.bundled {
        return bundled::refresh(pack, &path).await;
    }
    let want = published(client, pack).await?;
    if sha1_file(&path).await.as_deref() == Some(want.as_str()) {
        return Ok(Refresh::UpToDate);
    }

    tokio::fs::create_dir_all(&dir).await?;
    let mut temp = TempFile::create(&dir, &pack.file).await?;
    let fetched = tokio::time::timeout(
        fetch_patience,
        fetch_verified(client, &pack.url, &want, &mut temp, MAX_PACK_BYTES),
    )
    .await;
    match fetched {
        Ok(Ok(_)) => {
            temp.commit(&path).await?;
            Ok(Refresh::Updated)
        }
        Ok(Err(e)) => Err(temp.discard(e).await),
        Err(_) => Err(temp
            .discard(Error::Invalid(format!(
                "timed out downloading {}",
                pack.url
            )))
            .await),
    }
}

async fn published(client: &reqwest::Client, pack: &PackSource) -> Result<String> {
    let url = &pack.sha1_url;
    let body = tokio::time::timeout(LOOKUP_PATIENCE, async {
        let res = client.get(url).send().await?;
        if !res.status().is_success() {
            return Err(Error::Invalid(format!("{url} -> HTTP {}", res.status())));
        }
        body_limited(res, MAX_SHA1_BYTES).await
    })
    .await
    .map_err(|_| Error::Invalid(format!("timed out reading {url}")))??;
    let text =
        String::from_utf8(body).map_err(|_| Error::Invalid(format!("{url} is not UTF-8")))?;
    sha1_line(&text)
        .ok_or_else(|| Error::Invalid(format!("{url} did not publish a 40-character SHA-1")))
}

async fn body_limited(res: reqwest::Response, max: usize) -> Result<Vec<u8>> {
    if res.content_length().is_some_and(|len| len > max as u64) {
        return Err(Error::Invalid(format!(
            "response is larger than the {max} byte limit"
        )));
    }
    let mut body = Vec::new();
    let mut stream = res.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        if body.len() + chunk.len() > max {
            return Err(Error::Invalid(format!(
                "response is larger than the {max} byte limit"
            )));
        }
        body.extend_from_slice(&chunk);
    }
    Ok(body)
}

/// Hashes while the bytes arrive; the caller owns the temp file and commits or
/// discards it after this returns.
async fn fetch_verified(
    client: &reqwest::Client,
    url: &str,
    want_sha1: &str,
    temp: &mut TempFile,
    max_bytes: u64,
) -> Result<u64> {
    let res = client.get(url).send().await?;
    if !res.status().is_success() {
        return Err(Error::Invalid(format!("{url} -> HTTP {}", res.status())));
    }
    if res.content_length().is_some_and(|len| len > max_bytes) {
        return Err(Error::Invalid(format!(
            "download for {url} is larger than the {max_bytes} byte limit"
        )));
    }

    let mut hasher = Sha1::new();
    let mut total = 0_u64;
    let mut stream = res.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        total += chunk.len() as u64;
        if total > max_bytes {
            return Err(Error::Invalid(format!(
                "download for {url} is larger than the {max_bytes} byte limit"
            )));
        }
        hasher.update(&chunk);
        temp.write_all(&chunk).await?;
    }

    let got = hex::encode(hasher.finalize());
    if got != want_sha1 {
        return Err(Error::Invalid(format!(
            "checksum mismatch for {url}: expected {want_sha1}, got {got}"
        )));
    }
    Ok(total)
}

/// SHA-1 of a file without reading it all into memory.
async fn sha1_file(path: &Path) -> Option<String> {
    let mut file = tokio::fs::File::open(path).await.ok()?;
    let mut hasher = Sha1::new();
    let mut buf = [0_u8; 64 * 1024];
    loop {
        let read = file.read(&mut buf).await.ok()?;
        if read == 0 {
            break;
        }
        hasher.update(&buf[..read]);
    }
    Some(hex::encode(hasher.finalize()))
}

/// A pack file name is one plain `.zip` name under `resourcepacks/`, never a path.
fn safe_file(file: &str) -> bool {
    if !file.ends_with(".zip") || file.len() > 96 || file.starts_with('.') {
        return false;
    }
    let mut parts = Path::new(file).components();
    let one_part =
        matches!(parts.next(), Some(std::path::Component::Normal(_))) && parts.next().is_none();
    one_part
        && file
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
}

/// The SHA-1 file's line: forty hex digits, with anything after them (`sha1sum`
/// writes the file name) ignored. Uppercase is accepted and normalised.
fn sha1_line(text: &str) -> Option<String> {
    let word = text
        .trim_start_matches('\u{feff}')
        .split_whitespace()
        .next()?
        .to_ascii_lowercase();
    (word.len() == 40 && word.bytes().all(|b| b.is_ascii_hexdigit())).then_some(word)
}

async fn switch_on(file: &Path, entry: &str, fresh: Option<u32>) -> Result<SwitchOutcome> {
    let text = match read_options_file(file).await? {
        OptionsRead::Missing => None,
        OptionsRead::Unreadable => return Ok(SwitchOutcome::Unreadable),
        OptionsRead::Text(text) => Some(text),
    };
    match with_pack(text.as_deref(), entry, fresh) {
        PackListEdit::Unchanged if text.is_none() => Ok(SwitchOutcome::Deferred),
        PackListEdit::Unchanged => Ok(SwitchOutcome::AlreadySelected),
        PackListEdit::Unreadable => Ok(SwitchOutcome::Unreadable),
        PackListEdit::Updated(next) => {
            write_atomic(file, next.as_bytes()).await?;
            Ok(SwitchOutcome::Selected)
        }
    }
}

async fn read_options_file(file: &Path) -> Result<OptionsRead> {
    let file = match tokio::fs::File::open(file).await {
        Ok(file) => file,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(OptionsRead::Missing),
        Err(e) => return Err(e.into()),
    };
    let mut bytes = Vec::new();
    file.take(MAX_OPTIONS_BYTES as u64 + 1)
        .read_to_end(&mut bytes)
        .await?;
    if bytes.len() > MAX_OPTIONS_BYTES {
        return Ok(OptionsRead::Unreadable);
    }
    match String::from_utf8(bytes) {
        Ok(text) => Ok(OptionsRead::Text(text)),
        Err(_) => Ok(OptionsRead::Unreadable),
    }
}

async fn write_atomic(file: &Path, bytes: &[u8]) -> Result<()> {
    let dir = file
        .parent()
        .ok_or_else(|| Error::Invalid(format!("{} has no parent directory", file.display())))?;
    let name = file
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("options.txt");
    let mut temp = TempFile::create(dir, name).await?;
    if let Err(e) = temp.write_all(bytes).await {
        return Err(temp.discard(e).await);
    }
    temp.commit(file).await
}

/// `options.txt` with `entry` last in its pack list, where the game gives it the
/// final say; `Unchanged` when it is already listed, `Unreadable` when the list is
/// malformed or a duplicate key makes the effective choice ambiguous. Other lines
/// and their line endings are kept exactly as they were. A missing file is only
/// created when the data version it has to carry is known (`fresh`): without one
/// the game treats it as older and runs its fixers.
fn with_pack(text: Option<&str>, entry: &str, fresh: Option<u32>) -> PackListEdit {
    let Some(text) = text else {
        return create_options(entry, fresh);
    };

    let newline = if text.contains("\r\n") { "\r\n" } else { "\n" };
    let mut found = false;
    let mut listed = false;
    let mut out = String::with_capacity(text.len() + entry.len() + 24);
    for raw in text.split_inclusive('\n') {
        let (line, ending) = split_ending(raw);
        let Some(rest) = line.strip_prefix(PACKS_KEY) else {
            out.push_str(raw);
            continue;
        };
        if found {
            return PackListEdit::Unreadable;
        }
        found = true;
        let Some(mut packs) = parse_pack_list(rest) else {
            return PackListEdit::Unreadable;
        };
        listed = packs.iter().any(|pack| pack == entry);
        if listed {
            out.push_str(raw);
            continue;
        }
        packs.push(entry.to_string());
        let Some(json) = serde_json::to_string(&packs).ok() else {
            return PackListEdit::Unreadable;
        };
        out.push_str(PACKS_KEY);
        out.push_str(&json);
        out.push_str(ending);
    }

    if !found {
        return append_to_end(out, entry, newline);
    }
    if listed {
        PackListEdit::Unchanged
    } else {
        PackListEdit::Updated(out)
    }
}

fn create_options(entry: &str, fresh: Option<u32>) -> PackListEdit {
    let Some(version) = fresh else {
        return PackListEdit::Unchanged;
    };
    let Some(json) = list(&["vanilla", entry]) else {
        return PackListEdit::Unreadable;
    };
    PackListEdit::Updated(format!("version:{version}\n{PACKS_KEY}{json}\n"))
}

fn append_to_end(mut out: String, entry: &str, newline: &str) -> PackListEdit {
    if !out.is_empty() && !out.ends_with('\n') {
        out.push_str(newline);
    }
    let Some(json) = list(&["vanilla", entry]) else {
        return PackListEdit::Unreadable;
    };
    out.push_str(PACKS_KEY);
    out.push_str(&json);
    out.push_str(newline);
    PackListEdit::Updated(out)
}

fn parse_pack_list(rest: &str) -> Option<Vec<String>> {
    serde_json::from_str(rest).ok()
}

fn split_ending(raw: &str) -> (&str, &str) {
    if let Some(line) = raw.strip_suffix("\r\n") {
        (line, "\r\n")
    } else if let Some(line) = raw.strip_suffix('\n') {
        (line, "\n")
    } else {
        (raw, "")
    }
}

fn list(packs: &[&str]) -> Option<String> {
    serde_json::to_string(packs).ok()
}

#[cfg(test)]
mod tests;
