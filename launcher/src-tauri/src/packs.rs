/* Resource packs, kept beside the mods.

   A pack is art that changes when the server's art changes, not when the launcher is released, so
   no checksum for it is built in. The server publishes the zip and, beside it, a one-line file
   with the zip's sha1; a launch reads that line first and fetches the zip only when the copy on
   disk is not that file. A pack never grounds a launch: with no line to compare against, the copy
   on disk is used as it is, and with no copy either the game starts as it would without one.

   A pack is switched on in `options.txt` once per instance, the first time a copy is on disk.
   From then on the list is the player's, and a pack they switched off stays off. */
use std::{path::Path, sync::Arc, time::Duration};

use serde::Deserialize;

use crate::{
    catalog,
    download::{self, Item, Progress},
    error::Result,
    mods, paths, spec,
};

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PackSource {
    pub id: String,
    /// The name in `resourcepacks/`, and so in the game's list as `file/<file>`.
    pub file: String,
    pub url: String,
    /// One line, the zip's sha1 in hex.
    pub sha1_url: String,
    /// The game version the pack was made for; an instance of any other goes without it.
    pub game: String,
}

/// The sha1 line is a few bytes; a host that cannot send it this fast is not one to wait on.
const LOOKUP_PATIENCE: Duration = Duration::from_secs(8);
const FETCH_PATIENCE: Duration = Duration::from_secs(30);
const PACKS_KEY: &str = "resourcePacks:";

/// Bring the instance's packs up to the published ones, and switch on any it has not had before.
pub async fn apply(client: &reqwest::Client, instance_id: &str, game: &str) -> Result<()> {
    let game_dir = paths::instance_minecraft_dir(instance_id);
    let mut st = mods::state(instance_id).await;
    let before = st.packs.len();
    for pack in catalog::get().packs.iter().filter(|p| p.game == game) {
        let path = game_dir.join("resourcepacks").join(&pack.file);
        if !refresh(client, pack, &path).await || st.packs.contains(&pack.id) {
            continue;
        }
        let fresh = (game == spec::FAST_OPTIONS_VERSION).then_some(spec::OPTIONS_DATA_VERSION);
        switch_on(&game_dir.join("options.txt"), &format!("file/{}", pack.file), fresh).await?;
        st.packs.push(pack.id.clone());
    }
    if st.packs.len() != before {
        mods::save_state(instance_id, &st).await?;
    }
    Ok(())
}

/// Fetches the published pack if the copy at `path` is not it, and says whether there is a copy.
async fn refresh(client: &reqwest::Client, pack: &PackSource, path: &Path) -> bool {
    if let Some(sha1) = published(client, pack).await {
        let item = Item { url: pack.url.clone(), path: path.to_path_buf(), sha1: Some(sha1), size: 0 };
        // a fetch that fails or does not match leaves the copy that was there
        let _ = tokio::time::timeout(
            FETCH_PATIENCE,
            download::fetch_all(client, vec![item], 1, Arc::new(Progress::default())),
        )
        .await;
    }
    tokio::fs::metadata(path).await.is_ok_and(|m| m.is_file())
}

async fn published(client: &reqwest::Client, pack: &PackSource) -> Option<String> {
    let text = tokio::time::timeout(LOOKUP_PATIENCE, async {
        let res = client.get(&pack.sha1_url).send().await.ok()?;
        if !res.status().is_success() {
            return None;
        }
        res.text().await.ok()
    })
    .await
    .ok()??;
    sha1_line(&text)
}

/// The sha1 file's line: forty hex digits, with anything after them (`sha1sum` writes the name) ignored.
fn sha1_line(text: &str) -> Option<String> {
    let word = text.split_whitespace().next()?.to_ascii_lowercase();
    (word.len() == 40 && word.bytes().all(|b| b.is_ascii_hexdigit())).then_some(word)
}

async fn switch_on(file: &Path, entry: &str, fresh: Option<u32>) -> Result<()> {
    let text = match tokio::fs::read_to_string(file).await {
        Ok(text) => Some(text),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => None,
        Err(e) => return Err(e.into()),
    };
    if let Some(next) = with_pack(text.as_deref(), entry, fresh) {
        tokio::fs::write(file, next).await?;
    }
    Ok(())
}

/// `options.txt` with `entry` last in its pack list, where the game gives it the final say over
/// the packs below it; `None` when there is nothing to write. A file that does not exist yet is
/// only created when the data version it has to carry is known (`fresh`): without one the game
/// reads the file as one from an older game and runs its fixers over it.
fn with_pack(text: Option<&str>, entry: &str, fresh: Option<u32>) -> Option<String> {
    let Some(text) = text else {
        let version = fresh?;
        return Some(format!("version:{version}\n{PACKS_KEY}{}\n", list(&["vanilla", entry])?));
    };
    let mut found = false;
    let mut lines = Vec::new();
    for line in text.lines() {
        match line.strip_prefix(PACKS_KEY) {
            Some(rest) => {
                found = true;
                let mut packs: Vec<String> = serde_json::from_str(rest).ok()?;
                if packs.iter().any(|p| p == entry) {
                    return None;
                }
                packs.push(entry.to_string());
                lines.push(format!("{PACKS_KEY}{}", serde_json::to_string(&packs).ok()?));
            }
            None => lines.push(line.to_string()),
        }
    }
    if !found {
        lines.push(format!("{PACKS_KEY}{}", list(&["vanilla", entry])?));
    }
    Some(lines.join("\n") + "\n")
}

fn list(packs: &[&str]) -> Option<String> {
    serde_json::to_string(packs).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    const ENTRY: &str = "file/fullmoon-gui.zip";

    #[test]
    fn the_pack_goes_last_so_it_has_the_final_say() {
        let text = "version:4790\nresourcePacks:[\"vanilla\",\"fabric\"]\nincompatibleResourcePacks:[]\nlang:ko_kr\n";
        assert_eq!(
            with_pack(Some(text), ENTRY, None).unwrap(),
            "version:4790\nresourcePacks:[\"vanilla\",\"fabric\",\"file/fullmoon-gui.zip\"]\n\
             incompatibleResourcePacks:[]\nlang:ko_kr\n"
        );
    }

    #[test]
    fn a_listed_pack_or_an_unreadable_list_is_left_alone() {
        assert_eq!(with_pack(Some("resourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n"), ENTRY, None), None);
        assert_eq!(with_pack(Some("resourcePacks:vanilla\n"), ENTRY, None), None);
    }

    #[test]
    fn a_file_without_a_list_gets_one_after_vanilla() {
        assert_eq!(
            with_pack(Some("version:4790\r\nlang:ko_kr\r\n"), ENTRY, None).unwrap(),
            "version:4790\nlang:ko_kr\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n"
        );
    }

    #[test]
    fn a_missing_file_is_only_created_with_its_data_version() {
        assert_eq!(
            with_pack(None, ENTRY, Some(4790)).unwrap(),
            "version:4790\nresourcePacks:[\"vanilla\",\"file/fullmoon-gui.zip\"]\n"
        );
        assert_eq!(with_pack(None, ENTRY, None), None);
    }

    #[test]
    fn the_sha1_line_is_forty_hex_digits() {
        let sha1 = "175de05a740e46b47a2db6533c3d217c1c14cb19";
        assert_eq!(sha1_line(&format!("{sha1}\n")).as_deref(), Some(sha1));
        assert_eq!(sha1_line(&format!("{}  fullmoon-gui.zip\n", sha1.to_uppercase())).as_deref(), Some(sha1));
        assert_eq!(sha1_line("<html>not found</html>"), None);
        assert_eq!(sha1_line(&sha1[..39]), None);
        assert_eq!(sha1_line(""), None);
    }

    #[test]
    fn the_catalogue_names_the_gui_pack_for_the_game_it_was_made_for() {
        let pack = catalog::get().packs.iter().find(|p| p.id == "fullmoon-gui").unwrap();
        assert_eq!(pack.file, "fullmoon-gui.zip");
        assert_eq!(pack.game, spec::FAST_OPTIONS_VERSION);
        assert!(pack.sha1_url.ends_with(".sha1"));
    }

    #[tokio::test]
    async fn switching_on_twice_writes_once() {
        let dir = std::env::temp_dir().join("fullmoon_test_switch_on");
        let _ = tokio::fs::remove_dir_all(&dir).await;
        tokio::fs::create_dir_all(&dir).await.unwrap();
        let file = dir.join("options.txt");

        switch_on(&file, ENTRY, None).await.unwrap();
        assert!(!file.exists(), "no data version, no file");
        switch_on(&file, ENTRY, Some(4790)).await.unwrap();
        let first = tokio::fs::read_to_string(&file).await.unwrap();
        switch_on(&file, ENTRY, Some(4790)).await.unwrap();
        assert_eq!(tokio::fs::read_to_string(&file).await.unwrap(), first);
        let _ = tokio::fs::remove_dir_all(&dir).await;
    }
}
