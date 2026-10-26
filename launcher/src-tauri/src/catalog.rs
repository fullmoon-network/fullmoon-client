/* The bundled catalogue: the mods we ship, the cosmetics we drew, the news
   we wrote and the starter server list. Compiled in rather than fetched —
   these are product data, not user data, and the launcher must render on a
   plane with no network. */
use std::{collections::BTreeMap, sync::OnceLock};

use serde::Deserialize;

use crate::{
    model::{Cosmetic, HudConfig, Mod, NewsItem, ServerEntry},
    mods::ModSource,
    packs::PackSource,
    paths,
};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Catalog {
    pub mods: Vec<Mod>,
    /// Where each listed mod actually comes from. Not part of the wire
    /// contract — the UI has no business knowing a maven coordinate.
    pub mod_sources: BTreeMap<String, ModSource>,
    /// Resource packs fetched beside the mods. Only where they live is listed: their checksum is
    /// published with them, so a new pack needs no new launcher.
    #[serde(default)]
    pub packs: Vec<PackSource>,
    pub cosmetics: Vec<Cosmetic>,
    pub news: Vec<NewsItem>,
    pub servers: Vec<ServerEntry>,
    pub default_hud: HudConfig,
}

pub fn get() -> &'static Catalog {
    static CATALOG: OnceLock<Catalog> = OnceLock::new();
    CATALOG.get_or_init(|| {
        // authored copy says `{brand}`, never the name, so a fork's catalogue
        // reads as its own product without a find-and-replace
        let raw = include_str!("../resources/catalog.json").replace("{brand}", paths::BRAND_NAME);
        serde_json::from_str(&raw).expect("bundled catalog.json is malformed")
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_bundled_catalog_starts_with_the_fullmoon_lobby() {
        let servers = &get().servers;

        assert_eq!(servers.len(), 1);
        assert_eq!(servers[0].id, "fullmoon-lobby");
        assert_eq!(servers[0].address, "play.fullmoon.ink");
    }
}
