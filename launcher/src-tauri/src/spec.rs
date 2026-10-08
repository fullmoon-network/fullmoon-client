/* What the player's machine can carry, and the defaults that follow from it.

   The launcher used to give every machine the same 4 GiB heap and leave the
   game's options at its own defaults. In world the client keeps about 250-300
   MiB live (client bench, 2026-10-08), so on a 4 GB PC a 4 GiB ceiling only
   lets the heap crowd out the OS and page; and the game's default options are
   tuned for a GPU a low-spec PC does not have. */
use std::path::Path;

use crate::{error::Result, model::{Instance, Settings}};

/// What every install got before the heap followed the machine's RAM.
pub const OLD_DEFAULT_HEAP_MB: u32 = 4096;

/// The game version whose option names and data version `FAST_OPTIONS` uses.
pub const FAST_OPTIONS_VERSION: &str = "26.1.2";

/// MC 26.1.2's own Fast graphics preset (`GraphicsPreset.apply`), as the game
/// writes it. `version` has to be present: without it the data fixer treats the
/// file as older than presets and rewrites the preset to custom.
pub const FAST_OPTIONS: &str = "version:4790
graphicsPreset:\"fast\"
renderDistance:8
simulationDistance:6
biomeBlendRadius:1
prioritizeChunkUpdates:0
ao:false
renderClouds:\"fast\"
particles:1
mipmapLevels:2
entityShadows:false
entityDistanceScaling:0.75
menuBackgroundBlurriness:2
cloudRange:32
cutoutLeaves:false
improvedTransparency:false
weatherRadius:5
maxAnisotropyBit:1
textureFiltering:0
";

/// Physical RAM in MB, 0 when the platform will not say.
pub async fn total_memory_mb() -> u64 {
    #[cfg(windows)]
    {
        use windows_sys::Win32::System::SystemInformation::{GlobalMemoryStatusEx, MEMORYSTATUSEX};
        let mut st = MEMORYSTATUSEX {
            dwLength: std::mem::size_of::<MEMORYSTATUSEX>() as u32,
            ..unsafe { std::mem::zeroed() }
        };
        // SAFETY: dwLength is set to the struct's own size, as the API requires
        if unsafe { GlobalMemoryStatusEx(&mut st) } != 0 {
            return st.ullTotalPhys / (1024 * 1024);
        }
    }
    #[cfg(target_os = "linux")]
    {
        if let Ok(text) = tokio::fs::read_to_string("/proc/meminfo").await {
            return meminfo_total_mb(&text);
        }
    }
    0
}

/// `MemTotal:       32768000 kB` in `/proc/meminfo`; 0 when the line is missing.
#[cfg(any(target_os = "linux", test))]
fn meminfo_total_mb(text: &str) -> u64 {
    text.lines()
        .find_map(|l| l.strip_prefix("MemTotal:"))
        .and_then(|rest| rest.split_whitespace().next()?.parse::<u64>().ok())
        .map_or(0, |kb| kb / 1024)
}

pub fn logical_cpus() -> usize {
    std::thread::available_parallelism().map_or(4, |n| n.get())
}

/// The heap ceiling for a machine with `total_mb` of RAM. A 4 GB PC reports a
/// little under 4096, so each tier reaches half a gigabyte past its size.
pub fn heap_mb(total_mb: u64) -> u32 {
    match total_mb {
        0 => OLD_DEFAULT_HEAP_MB,
        ..=4608 => 1536,
        ..=6656 => 2048,
        ..=8704 => 3072,
        _ => OLD_DEFAULT_HEAP_MB,
    }
}

/// A machine that gets the Fast preset on its first launch: 6 GB of RAM or
/// less, or two threads or fewer.
pub fn low_spec(total_mb: u64, cpus: usize) -> bool {
    (total_mb != 0 && total_mb <= 6656) || cpus <= 2
}

/// Lowers the heap that installs from before this change still carry, once.
/// Only the old default is touched — a value the player picked is theirs —
/// and only downwards. Returns whether anything needs saving.
pub fn tune_heap(settings: &mut Settings, instances: &mut [Instance], total_mb: u64) -> bool {
    if settings.heap_tuned || total_mb == 0 {
        return false;
    }
    let target = heap_mb(total_mb);
    if target < OLD_DEFAULT_HEAP_MB {
        if settings.memory_mb == OLD_DEFAULT_HEAP_MB {
            settings.memory_mb = target;
        }
        for inst in instances.iter_mut().filter(|i| i.memory_mb == OLD_DEFAULT_HEAP_MB) {
            inst.memory_mb = target;
        }
    }
    settings.heap_tuned = true;
    true
}

/// Gives a low-spec machine the Fast preset the first time an instance of the
/// version it was written for starts. An `options.txt` that exists is never
/// touched: from then on the options are the player's.
pub async fn seed_options(game_dir: &Path, version_id: &str, low: bool) -> Result<bool> {
    if !low || version_id != FAST_OPTIONS_VERSION {
        return Ok(false);
    }
    let file = game_dir.join("options.txt");
    if tokio::fs::try_exists(&file).await.unwrap_or(true) {
        return Ok(false);
    }
    tokio::fs::write(&file, FAST_OPTIONS).await?;
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn inst(id: &str, memory_mb: u32) -> Instance {
        Instance {
            id: id.into(),
            name: id.into(),
            version_id: "26.1.2".into(),
            loader: "fabric".into(),
            installed: true,
            installing: None,
            memory_mb,
            icon_hue: 0,
            created_at: "earlier".into(),
            last_played_at: None,
            quick_play_server: None,
        }
    }

    #[test]
    fn meminfo_total_is_read_in_mb() {
        assert_eq!(meminfo_total_mb("MemTotal:       16384000 kB\nMemFree: 1 kB\n"), 16000);
        assert_eq!(meminfo_total_mb("nothing"), 0);
    }

    #[test]
    fn the_heap_follows_the_ram_tier() {
        assert_eq!(heap_mb(3900), 1536, "a 4 GB PC");
        assert_eq!(heap_mb(5900), 2048, "6 GB");
        assert_eq!(heap_mb(7900), 3072, "8 GB");
        assert_eq!(heap_mb(16_000), 4096, "16 GB");
        assert_eq!(heap_mb(0), 4096, "unknown keeps the old default");
    }

    #[test]
    fn low_spec_is_little_ram_or_few_threads() {
        assert!(low_spec(3900, 4));
        assert!(low_spec(5900, 8));
        assert!(!low_spec(7900, 4));
        assert!(low_spec(16_000, 2));
        assert!(!low_spec(0, 4), "unknown RAM alone is not low");
    }

    #[test]
    fn an_old_default_heap_is_lowered_once_and_a_chosen_one_is_kept() {
        let mut settings = Settings::default();
        let mut instances = vec![inst("default", 4096), inst("chosen", 6144)];
        assert!(tune_heap(&mut settings, &mut instances, 3900));
        assert_eq!(settings.memory_mb, 1536);
        assert_eq!(instances[0].memory_mb, 1536);
        assert_eq!(instances[1].memory_mb, 6144);
        assert!(settings.heap_tuned);

        settings.memory_mb = 4096;
        assert!(!tune_heap(&mut settings, &mut instances, 3900), "runs once");
        assert_eq!(settings.memory_mb, 4096);
    }

    #[test]
    fn a_big_or_unknown_machine_keeps_its_heap() {
        let mut settings = Settings::default();
        let mut instances = vec![inst("default", 4096)];
        assert!(!tune_heap(&mut settings, &mut instances, 0));
        assert!(!settings.heap_tuned, "asks again once the RAM is known");
        assert!(tune_heap(&mut settings, &mut instances, 32_000));
        assert_eq!((settings.memory_mb, instances[0].memory_mb), (4096, 4096));
    }

    #[test]
    fn the_fast_preset_is_the_one_the_game_writes() {
        assert!(FAST_OPTIONS.starts_with("version:4790\n"));
        assert!(FAST_OPTIONS.contains("graphicsPreset:\"fast\"\n"));
        assert!(FAST_OPTIONS.contains("renderDistance:8\n"));
        assert!(FAST_OPTIONS.lines().all(|l| l.split_once(':').is_some()));
    }

    #[tokio::test]
    async fn options_are_seeded_only_on_a_low_spec_first_start_of_the_target_version() {
        let dir = std::env::temp_dir().join("fullmoon_test_seed_options");
        let _ = tokio::fs::remove_dir_all(&dir).await;
        tokio::fs::create_dir_all(&dir).await.unwrap();
        let file = dir.join("options.txt");

        assert!(!seed_options(&dir, "26.1.2", false).await.unwrap(), "standard machine");
        assert!(!seed_options(&dir, "1.21.4", true).await.unwrap(), "another version");
        assert!(!file.exists());

        assert!(seed_options(&dir, "26.1.2", true).await.unwrap());
        assert_eq!(tokio::fs::read_to_string(&file).await.unwrap(), FAST_OPTIONS);

        tokio::fs::write(&file, "renderDistance:12\n").await.unwrap();
        assert!(!seed_options(&dir, "26.1.2", true).await.unwrap(), "the player's file stays");
        assert_eq!(tokio::fs::read_to_string(&file).await.unwrap(), "renderDistance:12\n");
        let _ = tokio::fs::remove_dir_all(&dir).await;
    }
}
