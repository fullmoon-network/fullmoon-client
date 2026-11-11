/* Fabric, straight from meta.fabricmc.net. The profile it returns is already
in launcher format with `inheritsFrom` pointing at the vanilla version, so
nothing here has to know what a loader does — it downloads the overlay and
hands it to version::inherit.

A game version Fabric has not shipped for is a hard error with the version
in the message, not a silent fallback to vanilla: the instance said Fabric,
and quietly launching something else would be a lie about what is running. */
use serde::Deserialize;

use crate::{
    error::{Error, Result},
    version::VersionJson,
};

const META: &str = "https://meta.fabricmc.net/v2";

#[derive(Debug, Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}

#[derive(Debug, Deserialize)]
struct LoaderInfo {
    version: String,
    stable: bool,
}

/// Newest stable loader for this game version, falling back to the newest
/// unstable one when that is all Fabric has.
pub async fn latest_loader(client: &reqwest::Client, game: &str) -> Result<String> {
    let url = format!("{META}/versions/loader/{game}");
    let res = client.get(&url).send().await?;
    if !res.status().is_success() {
        return Err(Error::Invalid(format!(
            "Fabric has no loader list for {game} (HTTP {})",
            res.status()
        )));
    }
    let list: Vec<LoaderEntry> = res.json().await?;
    list.iter()
        .find(|e| e.loader.stable)
        .or_else(|| list.first())
        .map(|e| e.loader.version.clone())
        .ok_or_else(|| Error::NotFound(format!("Fabric loader for {game}")))
}

/// The launcher-format profile for game+loader, ready to inherit from vanilla.
pub async fn profile(client: &reqwest::Client, game: &str, loader: &str) -> Result<VersionJson> {
    let url = format!("{META}/versions/loader/{game}/{loader}/profile/json");
    let res = client.get(&url).send().await?;
    if !res.status().is_success() {
        return Err(Error::Invalid(format!(
            "Fabric profile {game}/{loader} → HTTP {}",
            res.status()
        )));
    }
    Ok(res.json().await?)
}
