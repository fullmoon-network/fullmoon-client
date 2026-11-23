use std::path::{Path, PathBuf};

const MAX_WIDTH: u32 = 1920;
const MAX_HEIGHT: u32 = 1080;
// Below this the second encode costs more CPU than the bytes it saves,
// unless the source has to be scaled down anyway.
const WORTHWHILE_BYTES: u64 = 50 * 1024 * 1024;

pub struct ShareTarget {
    pub width: u32,
    pub height: u32,
}

fn round_even(value: f64) -> u32 {
    ((value / 2.0).round() * 2.0) as u32
}

pub fn fit_dimensions(target: ShareTarget) -> (u32, u32) {
    let width = target.width.max(1) as f64;
    let height = target.height.max(1) as f64;
    // Capping the factor at 1 keeps small clips native: blowing them up to the
    // box would spend bits without adding any information.
    let scale = (MAX_WIDTH as f64 / width)
        .min(MAX_HEIGHT as f64 / height)
        .min(1.0);
    // H.264 needs even dimensions; rounding outward keeps the source aspect as
    // close as possible while staying inside the (already even) 1080p box.
    (round_even(width * scale), round_even(height * scale))
}

pub fn build_share_args(
    input: &Path,
    output: &Path,
    target: ShareTarget,
    duration_s: f64,
) -> Vec<String> {
    // The master is the untouched replay. Duration is deliberately not emitted
    // as -t: a late replay stop must not silently truncate the uploaded copy.
    let _ = duration_s;
    let (width, height) = fit_dimensions(target);
    let scale = format!("scale={width}:{height}");
    // libx264 + High + yuv420p is the playback baseline; HEVC/AV1 are still not
    // universal. -nostdin keeps ffmpeg off the launcher's console, and -y can
    // only replace the derived share file. No -r: keep the source frame rate.
    // +faststart moves the index to the front so playback can begin before the
    // whole file has downloaded.
    vec![
        "-hide_banner".into(),
        "-nostdin".into(),
        "-y".into(),
        "-i".into(),
        input.to_string_lossy().into_owned(),
        "-c:v".into(),
        "libx264".into(),
        "-preset".into(),
        "medium".into(),
        "-crf".into(),
        "20".into(),
        "-profile:v".into(),
        "high".into(),
        "-pix_fmt".into(),
        "yuv420p".into(),
        "-vf".into(),
        scale,
        "-c:a".into(),
        "aac".into(),
        "-b:a".into(),
        "160k".into(),
        "-ac".into(),
        "2".into(),
        "-movflags".into(),
        "+faststart".into(),
        output.to_string_lossy().into_owned(),
    ]
}

pub fn share_is_worthwhile(master_bytes: u64, target: ShareTarget) -> bool {
    // A re-encode only pays off for a big master or a source that must be
    // downscaled; a small already-1080p clip gains too little for the CPU cost.
    master_bytes >= WORTHWHILE_BYTES || target.width > MAX_WIDTH || target.height > MAX_HEIGHT
}

pub fn output_name(input: &Path) -> PathBuf {
    let Some(stem) = input.file_stem() else {
        return input.to_path_buf();
    };
    let mut name = stem.to_os_string();
    name.push(".share");
    if let Some(extension) = input.extension() {
        name.push(".");
        name.push(extension);
    }
    input.with_file_name(name)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::OsStr;

    fn target(width: u32, height: u32) -> ShareTarget {
        ShareTarget { width, height }
    }

    fn args_for(t: ShareTarget) -> Vec<String> {
        build_share_args(
            Path::new("/clips/master.mp4"),
            Path::new("/clips/master.share.mp4"),
            t,
            12.5,
        )
    }

    fn scale_of(args: &[String]) -> &str {
        let index = args.iter().position(|arg| arg == "-vf").expect("scale filter");
        args[index + 1].as_str()
    }

    fn dims_of(args: &[String]) -> (u32, u32) {
        let value = scale_of(args).strip_prefix("scale=").expect("scale prefix");
        let (w, h) = value.split_once(':').expect("w:h");
        (w.parse().unwrap(), h.parse().unwrap())
    }

    #[test]
    fn a_1080p_source_stays_1080p_without_upscaling() {
        let args = args_for(target(1920, 1080));
        assert_eq!(scale_of(&args), "scale=1920:1080");
        assert_eq!(dims_of(&args), (1920, 1080));
    }

    #[test]
    fn a_4k_source_falls_into_the_1080p_box() {
        assert_eq!(dims_of(&args_for(target(3840, 2160))), (1920, 1080));
    }

    #[test]
    fn a_four_by_three_source_keeps_its_aspect_and_even_dimensions() {
        let (w, h) = dims_of(&args_for(target(1440, 1080)));
        assert_eq!((w, h), (1440, 1080));
        assert_eq!(w * 3, h * 4);
        assert_eq!(w % 2, 0);
        assert_eq!(h % 2, 0);
    }

    #[test]
    fn an_odd_source_is_rounded_to_even_dimensions() {
        let (w, h) = dims_of(&args_for(target(1919, 1079)));
        assert_eq!((w, h), (1920, 1080));
        assert_eq!(w % 2, 0);
        assert_eq!(h % 2, 0);
    }

    #[test]
    fn faststart_appears_once_immediately_before_the_output() {
        let args = args_for(target(1920, 1080));
        assert_eq!(args.iter().filter(|arg| arg.as_str() == "+faststart").count(), 1);
        assert_eq!(args[args.len() - 2].as_str(), "+faststart");
        assert_eq!(args[args.len() - 1].as_str(), "/clips/master.share.mp4");
    }

    #[test]
    fn codec_quality_and_audio_flags_are_emitted() {
        let args = args_for(target(1280, 720));
        for flag in ["-c:v", "libx264", "-crf", "20", "-b:a", "160k"] {
            assert!(args.iter().any(|arg| arg.as_str() == flag), "missing {flag}");
        }
        assert!(!args.iter().any(|arg| matches!(arg.as_str(), "-r" | "-t" | "-an")));
    }

    #[test]
    fn the_worthwhile_threshold_is_its_own_boundary() {
        assert!(!share_is_worthwhile(WORTHWHILE_BYTES - 1, target(1920, 1080)));
        assert!(share_is_worthwhile(WORTHWHILE_BYTES, target(1920, 1080)));
        assert!(share_is_worthwhile(0, target(3840, 2160)));
    }

    #[test]
    fn output_name_inserts_share_without_mutating_the_stem() {
        assert_eq!(
            output_name(Path::new("/clips/a.mp4")),
            PathBuf::from("/clips/a.share.mp4")
        );
        assert_eq!(
            output_name(Path::new(r"C:\clips\a.mp4")),
            PathBuf::from(r"C:\clips\a.share.mp4")
        );
        let out = output_name(Path::new("/clips/save.2026.mp4"));
        assert_eq!(out.file_stem(), Some(OsStr::new("save.2026.share")));
    }

    #[test]
    fn duration_does_not_change_the_arguments() {
        let a = build_share_args(Path::new("in.mp4"), Path::new("out.mp4"), target(3840, 2160), 0.0);
        let b = build_share_args(Path::new("in.mp4"), Path::new("out.mp4"), target(3840, 2160), 999.0);
        assert_eq!(a, b);
    }
}
