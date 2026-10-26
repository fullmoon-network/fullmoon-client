/* The launcher half of the HUD contract.
 *
 * There is one HUD file per instance and the launcher does not own it: the mod writes the same
 * `config/fullmoon/hud.json` from its in-game editor, and polls the file's mtime so a layout
 * chosen out here lands in a running game. Which means every read has to survive a file the
 * launcher did not author — one from a newer mod with an element this build has never heard of,
 * or an older one missing an element the catalogue now ships.
 *
 * So a read merges onto the catalogue default instead of replacing it, and an id the launcher
 * cannot name is carried through untouched rather than dropped on the next write. */
use std::collections::BTreeMap;

use crate::{
    catalog,
    error::{Error, Result},
    model::{HudConfig, HudElementState},
    paths, store,
};

/// The mod's `Anchor` enum, which is the only vocabulary its `applyConfig` accepts — anything
/// else it silently reads as `TOP_LEFT`, so the launcher refuses to write one instead.
pub const ANCHORS: [&str; 9] = [
    "TOP_LEFT",
    "TOP_CENTER",
    "TOP_RIGHT",
    "CENTER_LEFT",
    "CENTER",
    "CENTER_RIGHT",
    "BOTTOM_LEFT",
    "BOTTOM_CENTER",
    "BOTTOM_RIGHT",
];

pub fn defaults() -> HudConfig {
    catalog::get().default_hud.clone()
}

/// What the editor opens: the catalogue default with whatever the file on disk says over it.
pub async fn read(instance_id: &str) -> HudConfig {
    let bytes = tokio::fs::read(paths::instance_hud_file(instance_id))
        .await
        .unwrap_or_default();
    merge(defaults(), &bytes)
}

pub async fn write(instance_id: &str, cfg: &HudConfig) -> Result<()> {
    validate(cfg)?;
    store::write(&paths::instance_hud_file(instance_id), cfg).await
}

/// Put the catalogue default back and hand it to the caller. The defaults live in the core, so an
/// editor that wants them back asks for them rather than restating them.
pub async fn reset(instance_id: &str) -> Result<HudConfig> {
    let cfg = defaults();
    write(instance_id, &cfg).await?;
    Ok(cfg)
}

/// The mod reads this on its first frame; ship the default layout with the install rather than
/// making the player open an editor once to create it. An existing file is the player's.
pub async fn seed(instance_id: &str) -> Result<()> {
    let file = paths::instance_hud_file(instance_id);
    if file.is_file() {
        return Ok(());
    }
    store::write(&file, &defaults()).await
}

fn validate(cfg: &HudConfig) -> Result<()> {
    // both editors snap a drag to this step, and the mod's own HudGrid::sanitize would quietly
    // read a zero as four — writing one would make the two surfaces round differently
    if cfg.grid_snap == 0 {
        return Err(Error::Invalid("hud gridSnap must be at least one pixel".into()));
    }
    for (id, state) in &cfg.elements {
        if !ANCHORS.contains(&state.anchor.as_str()) {
            return Err(Error::Invalid(format!(
                "hud element {id}: {} is not an anchor the client knows",
                state.anchor
            )));
        }
        if !state.scale.is_finite() || state.scale <= 0.0 {
            return Err(Error::Invalid(format!("hud element {id}: scale must be above zero")));
        }
    }
    Ok(())
}

/// Bytes that do not parse are a file we cannot reason about, and the default is the only honest
/// thing to show for one. Bytes that do parse win per element, not per file.
fn merge(mut base: HudConfig, bytes: &[u8]) -> HudConfig {
    let Ok(disk) = serde_json::from_slice::<HudFile>(bytes) else {
        return base;
    };
    for (id, state) in disk.elements {
        base.elements.insert(id, state);
    }
    if let Some(snap) = disk.grid_snap.filter(|s| *s > 0) {
        base.grid_snap = snap;
    }
    base
}

/// The same file, read as "whatever is in there" — every part optional, because the writer on
/// the other side ships on its own schedule.
#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct HudFile {
    #[serde(default)]
    elements: BTreeMap<String, HudElementState>,
    #[serde(default)]
    grid_snap: Option<u32>,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn state(anchor: &str, x: i32, y: i32) -> HudElementState {
        HudElementState { enabled: true, anchor: anchor.to_owned(), offset_x: x, offset_y: y, scale: 1.0 }
    }

    #[test]
    fn the_catalogue_default_is_the_clients_own_registry_defaults() {
        // HudElementRegistry's constructor is the source of truth for a fresh instance. If the
        // two drift, an install ships a layout the client did not choose — so fail here.
        let cfg = defaults();
        let ids: Vec<&str> = cfg.elements.keys().map(String::as_str).collect();
        assert_eq!(
            ids,
            ["armor", "clock", "coords", "effects", "fps", "keystrokes", "ping", "season", "tps"]
        );
        assert_eq!(cfg.grid_snap, 4);

        let coords = &cfg.elements["coords"];
        assert_eq!((coords.enabled, coords.anchor.as_str(), coords.offset_x, coords.offset_y), (true, "TOP_LEFT", 8, 8));
        let effects = &cfg.elements["effects"];
        assert_eq!((effects.enabled, effects.anchor.as_str(), effects.offset_x, effects.offset_y), (false, "TOP_RIGHT", 16, 134));
        let season = &cfg.elements["season"];
        assert_eq!((season.enabled, season.anchor.as_str(), season.offset_x, season.offset_y), (true, "TOP_LEFT", 8, 48));
        for (id, state) in &cfg.elements {
            assert!(ANCHORS.contains(&state.anchor.as_str()), "{id}");
            assert_eq!(state.scale, 1.0, "{id}");
        }
    }

    #[tokio::test]
    async fn what_the_launcher_writes_is_the_file_the_mod_opens() {
        let root = std::env::temp_dir().join(format!("fullmoon-hud-{}", std::process::id()));
        std::env::set_var("FULLMOON_DATA_ROOT", &root);

        write("wiring", &defaults()).await.expect("write");

        let file = paths::instance_hud_file("wiring");
        let tail: Vec<String> = file
            .components()
            .rev()
            .take(4)
            .map(|c| c.as_os_str().to_string_lossy().into_owned())
            .collect();
        assert_eq!(tail, ["hud.json", "fullmoon", "config", "minecraft"]);

        // The mod parses this with Gson into HudConfig/ElementState, so the spellings are the
        // contract — camelCase from serde has to line up with the Java field names.
        let raw = std::fs::read_to_string(&file).expect("read back");
        let json: serde_json::Value = serde_json::from_str(&raw).expect("valid json");
        assert_eq!(json["gridSnap"], 4);
        let fps = &json["elements"]["fps"];
        assert_eq!(fps["anchor"], "TOP_LEFT");
        assert_eq!(fps["offsetX"], 8);
        assert_eq!(fps["offsetY"], 28);
        assert_eq!(fps["enabled"], true);
        assert_eq!(fps["scale"], 1.0);
        assert!(json["elements"].as_object().unwrap().len() == 9);
        assert!(json["modules"].is_null(), "the percent contract is gone, not renamed");

        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn a_file_the_launcher_did_not_author_survives_being_read() {
        let disk = br#"{"elements":{"fps":{"enabled":false,"anchor":"BOTTOM_LEFT","offsetX":8,"offsetY":9,"scale":1.5},
            "lantern":{"enabled":true,"anchor":"CENTER","offsetX":0,"offsetY":0,"scale":1}},"gridSnap":8}"#;

        let cfg = merge(defaults(), disk);

        let fps = &cfg.elements["fps"];
        assert_eq!((fps.enabled, fps.anchor.as_str(), fps.offset_x, fps.offset_y, fps.scale), (false, "BOTTOM_LEFT", 8, 9, 1.5));
        // an element from a client newer than this launcher is not ours to delete
        assert_eq!(cfg.elements["lantern"].anchor, "CENTER");
        // and one the file never mentioned keeps what the catalogue says
        assert_eq!(cfg.elements["coords"].offset_y, 8);
        assert_eq!(cfg.grid_snap, 8);
    }

    #[test]
    fn a_missing_scale_is_full_size_rather_than_nothing() {
        let cfg = merge(defaults(), br#"{"elements":{"clock":{"enabled":true,"anchor":"CENTER","offsetX":1,"offsetY":2}}}"#);

        assert_eq!(cfg.elements["clock"].scale, 1.0);
        assert_eq!(cfg.grid_snap, 4);
    }

    #[test]
    fn bytes_we_cannot_read_show_the_default_rather_than_half_a_layout() {
        for bytes in [&b""[..], b"not json at all", br#"{"elements":{"fps":{"enabled":true}}}"#] {
            let cfg = merge(defaults(), bytes);
            assert_eq!(cfg.elements.len(), 9);
            assert_eq!(cfg.elements["fps"].offset_y, 28);
        }
        // a snap of zero would divide the editor's grid by nothing
        assert_eq!(merge(defaults(), br#"{"gridSnap":0}"#).grid_snap, 4);
    }

    #[test]
    fn an_anchor_the_client_cannot_name_is_refused_rather_than_silently_moved() {
        // applyConfig falls back to TOP_LEFT on an unknown name, which would look like the
        // launcher lost the layout. Refuse the write instead.
        let mut cfg = defaults();
        cfg.elements.insert("fps".into(), state("MIDDLE_ISH", 4, 4));
        assert!(validate(&cfg).is_err());

        cfg.elements.insert("fps".into(), state("TOP_LEFT", 4, 4));
        assert!(validate(&cfg).is_ok());

        for bad in [0.0, -1.0, f32::NAN] {
            let mut cfg = defaults();
            cfg.elements.get_mut("fps").unwrap().scale = bad;
            assert!(validate(&cfg).is_err(), "scale {bad}");
        }

        // read() forgives a zero because the file may not be ours; write() does not, because
        // this one would be.
        let mut cfg = defaults();
        cfg.grid_snap = 0;
        assert!(validate(&cfg).is_err());
        cfg.grid_snap = 1;
        assert!(validate(&cfg).is_ok());
    }
}
