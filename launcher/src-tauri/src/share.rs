#![allow(dead_code)]

use std::{
    ffi::{OsStr, OsString},
    path::{Path, PathBuf},
    process::Stdio,
    time::Duration,
};

use tokio::{io::AsyncReadExt, process::Command, sync::watch};

use crate::{
    error::{Error, Result},
    paths,
};

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

const ENCODE_TIMEOUT: Duration = Duration::from_secs(15 * 60);
const STDERR_LIMIT: usize = 4096;
const STDERR_CHARS: usize = 240;

pub struct EncodeCancel {
    timeout: Duration,
    signal: Option<watch::Receiver<bool>>,
}

impl Default for EncodeCancel {
    fn default() -> Self {
        Self {
            timeout: ENCODE_TIMEOUT,
            signal: None,
        }
    }
}

impl EncodeCancel {
    pub fn new(timeout: Duration) -> Self {
        Self {
            timeout,
            signal: None,
        }
    }

    pub fn with_signal(timeout: Duration, signal: watch::Receiver<bool>) -> Self {
        Self {
            timeout,
            signal: Some(signal),
        }
    }
}

pub fn cancel_channel() -> (watch::Sender<bool>, watch::Receiver<bool>) {
    watch::channel(false)
}

enum EncodeOutcome {
    Exited(std::process::ExitStatus),
    TimedOut,
    Cancelled,
    WaitFailed(std::io::Error),
}

pub async fn run_share_encode(
    ffmpeg: &Path,
    input: &Path,
    output: &Path,
    target: ShareTarget,
    duration_s: f64,
    cancel: Option<EncodeCancel>,
) -> Result<PathBuf> {
    let clips_dir = paths::clips_dir();
    run_share_encode_in(
        ffmpeg, input, output, &clips_dir, target, duration_s, cancel,
    )
    .await
}

async fn run_share_encode_in(
    ffmpeg: &Path,
    input: &Path,
    output: &Path,
    clips_dir: &Path,
    target: ShareTarget,
    duration_s: f64,
    cancel: Option<EncodeCancel>,
) -> Result<PathBuf> {
    if !contained_file(clips_dir, input).await {
        return Err(Error::Invalid(
            "the replay master must be inside the launcher's clip folder".into(),
        ));
    }
    let share_file_name = output.file_name().ok_or_else(|| {
        Error::Invalid("the share output must name a file inside the clip folder".into())
    })?;
    let parent = output.parent().ok_or_else(|| {
        Error::Invalid("the share output must name a file inside the clip folder".into())
    })?;
    let (canonical_dir, canonical_parent) = tokio::join!(
        tokio::fs::canonicalize(clips_dir),
        tokio::fs::canonicalize(parent),
    );
    let (Ok(canonical_dir), Ok(canonical_parent)) = (canonical_dir, canonical_parent) else {
        return Err(Error::Invalid(
            "the share output must be inside the launcher's clip folder".into(),
        ));
    };
    if !canonical_parent.starts_with(&canonical_dir) {
        return Err(Error::Invalid(
            "the share output must be inside the launcher's clip folder".into(),
        ));
    }

    let master = tokio::fs::canonicalize(input).await?;
    if share_file_name == master.file_name().unwrap_or(OsStr::new("")) {
        return Err(Error::Invalid(
            "a share output cannot replace the replay master".into(),
        ));
    }

    let resolved = resolve_executable(ffmpeg).ok_or_else(|| {
        Error::Invalid(format!(
            "ffmpeg was not found at {} — set a working ffmpeg path in clip settings",
            ffmpeg.display()
        ))
    })?;

    let mut temp_name = share_file_name.to_os_string();
    temp_name.push(".part-");
    temp_name.push(std::process::id().to_string());
    temp_name.push("-");
    temp_name.push(uuid::Uuid::new_v4().simple().to_string());
    let temp = output.with_file_name(temp_name);

    let mut command = Command::new(&resolved);
    command
        .args(encode_args(&master, &temp, target, duration_s))
        .kill_on_drop(true)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::piped());
    let mut child = command.spawn().map_err(|e| {
        Error::Invalid(format!(
            "could not start ffmpeg at {}: {e}",
            resolved.display()
        ))
    })?;

    let mut stderr_handle = child.stderr.take();
    let mut stderr_task = tokio::spawn(async move {
        let mut collected = Vec::new();
        let Some(stderr) = stderr_handle.as_mut() else {
            return collected;
        };
        let mut buf = [0_u8; 2048];
        loop {
            match stderr.read(&mut buf).await {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    if collected.len() < STDERR_LIMIT {
                        let take = n.min(STDERR_LIMIT - collected.len());
                        collected.extend_from_slice(&buf[..take]);
                    }
                }
            }
        }
        collected
    });

    let EncodeCancel { timeout, signal } = cancel.unwrap_or_default();
    let outcome = tokio::select! {
        status = child.wait() => match status {
            Ok(status) => EncodeOutcome::Exited(status),
            Err(e) => EncodeOutcome::WaitFailed(e),
        },
        _ = tokio::time::sleep(timeout) => EncodeOutcome::TimedOut,
        _ = wait_cancel(signal) => EncodeOutcome::Cancelled,
    };

    let problem = match &outcome {
        EncodeOutcome::Exited(status) if status.success() => None,
        EncodeOutcome::Exited(status) => Some(format!(
            "ffmpeg exited with code {}",
            status
                .code()
                .map(|code| code.to_string())
                .unwrap_or_else(|| "signal".into())
        )),
        EncodeOutcome::TimedOut => Some(format!(
            "ffmpeg encode timed out after {}s",
            timeout.as_secs()
        )),
        EncodeOutcome::Cancelled => Some("ffmpeg encode was cancelled".into()),
        EncodeOutcome::WaitFailed(e) => Some(format!("could not wait for ffmpeg: {e}")),
    };

    if problem.is_some() {
        let _ = child.kill().await;
        let _ = child.wait().await;
    }

    let stderr = match tokio::time::timeout(Duration::from_secs(2), &mut stderr_task).await {
        Ok(Ok(bytes)) => bytes,
        Ok(Err(_)) => Vec::new(),
        Err(_) => {
            stderr_task.abort();
            Vec::new()
        }
    };

    if let Some(problem) = problem {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(Error::Invalid(format!(
            "{problem}{}",
            stderr_detail(&stderr)
        )));
    }

    let state = match tokio::fs::metadata(&temp).await {
        Ok(meta) if meta.is_file() && meta.len() > 0 => Ok(()),
        Ok(_) => Err("ffmpeg finished but the share output was empty"),
        Err(_) => Err("ffmpeg finished but wrote no share output"),
    };
    if let Err(message) = state {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(Error::Invalid(message.into()));
    }

    let mut renamed = tokio::fs::rename(&temp, output).await;
    if renamed.is_err() && cfg!(windows) && tokio::fs::try_exists(output).await.unwrap_or(false) {
        let _ = tokio::fs::remove_file(output).await;
        renamed = tokio::fs::rename(&temp, output).await;
    }
    if let Err(e) = renamed {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(Error::Invalid(format!(
            "could not finish the share file: {e}"
        )));
    }
    Ok(output.to_path_buf())
}

async fn contained_file(dir: &Path, candidate: &Path) -> bool {
    let Ok(dir) = tokio::fs::canonicalize(dir).await else {
        return false;
    };
    let Ok(candidate) = tokio::fs::canonicalize(candidate).await else {
        return false;
    };
    candidate.starts_with(&dir) && candidate != dir
}

fn resolve_executable(exe: &Path) -> Option<PathBuf> {
    if exe.is_file() {
        return Some(exe.to_path_buf());
    }
    if exe.components().count() != 1 {
        return None;
    }
    let path = std::env::var_os("PATH")?;
    for dir in std::env::split_paths(&path) {
        let candidate = dir.join(exe);
        if candidate.is_file() {
            return Some(candidate);
        }
        #[cfg(windows)]
        if candidate.extension().is_none() && candidate.with_extension("exe").is_file() {
            return Some(candidate.with_extension("exe"));
        }
    }
    None
}

fn encode_args(input: &Path, output: &Path, target: ShareTarget, duration_s: f64) -> Vec<OsString> {
    let mut args: Vec<OsString> = build_share_args(input, output, target, duration_s)
        .into_iter()
        .map(OsString::from)
        .collect();
    if let Some(index) = args
        .iter()
        .position(|arg| arg.as_os_str() == OsStr::new("-i"))
    {
        if let Some(slot) = args.get_mut(index + 1) {
            *slot = input.as_os_str().to_os_string();
        }
    }
    if let Some(slot) = args.last_mut() {
        *slot = output.as_os_str().to_os_string();
    }
    args
}

async fn wait_cancel(signal: Option<watch::Receiver<bool>>) {
    let Some(mut receiver) = signal else {
        std::future::pending::<()>().await;
        return;
    };
    loop {
        if *receiver.borrow_and_update() {
            return;
        }
        if receiver.changed().await.is_err() {
            std::future::pending::<()>().await;
        }
    }
}

fn stderr_detail(stderr: &[u8]) -> String {
    let text = String::from_utf8_lossy(stderr);
    let text = text.trim();
    if text.is_empty() {
        return String::new();
    }
    let mut short: String = text.chars().take(STDERR_CHARS).collect();
    if text.chars().count() > STDERR_CHARS {
        short.push_str("...");
    }
    format!(": {short}")
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
        let index = args
            .iter()
            .position(|arg| arg == "-vf")
            .expect("scale filter");
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
        assert_eq!(
            args.iter()
                .filter(|arg| arg.as_str() == "+faststart")
                .count(),
            1
        );
        assert_eq!(args[args.len() - 2].as_str(), "+faststart");
        assert_eq!(args[args.len() - 1].as_str(), "/clips/master.share.mp4");
    }

    #[test]
    fn codec_quality_and_audio_flags_are_emitted() {
        let args = args_for(target(1280, 720));
        for flag in ["-c:v", "libx264", "-crf", "20", "-b:a", "160k"] {
            assert!(
                args.iter().any(|arg| arg.as_str() == flag),
                "missing {flag}"
            );
        }
        assert!(!args
            .iter()
            .any(|arg| matches!(arg.as_str(), "-r" | "-t" | "-an")));
    }

    #[test]
    fn the_worthwhile_threshold_is_its_own_boundary() {
        assert!(!share_is_worthwhile(
            WORTHWHILE_BYTES - 1,
            target(1920, 1080)
        ));
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
        let a = build_share_args(
            Path::new("in.mp4"),
            Path::new("out.mp4"),
            target(3840, 2160),
            0.0,
        );
        let b = build_share_args(
            Path::new("in.mp4"),
            Path::new("out.mp4"),
            target(3840, 2160),
            999.0,
        );
        assert_eq!(a, b);
    }
}

#[cfg(test)]
mod run_tests {
    use super::*;

    struct Scratch {
        root: PathBuf,
        clips: PathBuf,
        bin: PathBuf,
    }

    impl Scratch {
        fn new(name: &str) -> Self {
            let root = std::env::temp_dir().join(format!(
                "fullmoon-share-{name}-{}-{}",
                std::process::id(),
                uuid::Uuid::new_v4().simple()
            ));
            let clips = root.join("clips");
            let bin = root.join("bin");
            std::fs::create_dir_all(&clips).expect("clips dir");
            std::fs::create_dir_all(&bin).expect("bin dir");
            Self { root, clips, bin }
        }

        fn master(&self) -> PathBuf {
            self.clips.join("master.mkv")
        }

        fn output(&self) -> PathBuf {
            output_name(&self.master())
        }

        fn write_master(&self) -> PathBuf {
            let master = self.master();
            std::fs::write(&master, b"MASTER-BYTES-UNTOUCHED").expect("master");
            master
        }

        fn clip_files(&self) -> Vec<String> {
            let mut names: Vec<String> = std::fs::read_dir(&self.clips)
                .expect("read clips dir")
                .map(|entry| {
                    entry
                        .expect("entry")
                        .file_name()
                        .to_string_lossy()
                        .into_owned()
                })
                .collect();
            names.sort();
            names
        }

        fn cleanup(&self) {
            let _ = std::fs::remove_dir_all(&self.root);
        }
    }

    fn target() -> ShareTarget {
        ShareTarget {
            width: 1920,
            height: 1080,
        }
    }

    #[test]
    fn encode_args_attach_the_master_and_output_as_os_strings() {
        let input = Path::new("/clips/master.mkv");
        let output = Path::new("/clips/master.share.mkv.part-7");
        let args = encode_args(input, output, target(), 12.5);
        let values: Vec<&OsStr> = args.iter().map(OsString::as_os_str).collect();
        let index = values
            .iter()
            .position(|arg| *arg == OsStr::new("-i"))
            .expect("-i");
        assert_eq!(values[index + 1], input.as_os_str());
        assert_eq!(values.last().copied(), Some(output.as_os_str()));
        assert_eq!(values[values.len() - 2], OsStr::new("+faststart"));
        assert!(values.contains(&OsStr::new("-nostdin")));
        assert!(values.contains(&OsStr::new("-y")));
        assert!(!values.contains(&OsStr::new("-t")));
        assert!(!values.contains(&OsStr::new("-r")));
    }

    #[tokio::test]
    async fn a_missing_executable_fails_without_writing_anything() {
        let scratch = Scratch::new("missing");
        let master = scratch.write_master();
        let missing = scratch.bin.join("no-such-ffmpeg");
        let error = run_share_encode_in(
            &missing,
            &master,
            &scratch.output(),
            &scratch.clips,
            target(),
            12.5,
            None,
        )
        .await
        .expect_err("no ffmpeg there");
        assert!(error.to_string().contains("not found"), "{error}");
        assert!(error.to_string().contains("clip settings"), "{error}");
        assert_eq!(scratch.clip_files(), ["master.mkv".to_string()]);
        assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
        scratch.cleanup();
    }

    #[tokio::test]
    async fn paths_outside_the_clip_folder_are_refused() {
        let scratch = Scratch::new("paths");
        let master = scratch.write_master();
        let outside = scratch.bin.join("outside.mkv");
        std::fs::write(&outside, b"x").expect("outside file");
        let error = run_share_encode_in(
            &outside,
            &outside,
            &scratch.output(),
            &scratch.clips,
            target(),
            12.5,
            None,
        )
        .await
        .expect_err("outside master");
        assert!(error.to_string().contains("clip folder"), "{error}");

        let error = run_share_encode_in(
            &outside,
            &master,
            &outside,
            &scratch.clips,
            target(),
            12.5,
            None,
        )
        .await
        .expect_err("outside output");
        assert!(error.to_string().contains("clip folder"), "{error}");

        let error = run_share_encode_in(
            &outside,
            &master,
            &master,
            &scratch.clips,
            target(),
            12.5,
            None,
        )
        .await
        .expect_err("the master itself");
        assert!(error.to_string().contains("cannot replace"), "{error}");

        assert_eq!(scratch.clip_files(), ["master.mkv".to_string()]);
        assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
        scratch.cleanup();
    }

    #[cfg(unix)]
    mod fake_process {
        use super::*;
        use std::os::unix::fs::PermissionsExt;

        fn script(dir: &Path, args_file: &Path, body: &str) -> PathBuf {
            let exe = dir.join("ffmpeg");
            let source = format!(
                "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"{}\"\nlast=\nfor arg do last=$arg; done\n{}\n",
                args_file.display(),
                body
            );
            std::fs::write(&exe, source).expect("fake ffmpeg script");
            let mut permissions = std::fs::metadata(&exe).expect("metadata").permissions();
            permissions.set_mode(0o755);
            std::fs::set_permissions(&exe, permissions).expect("permissions");
            exe
        }

        fn sleeping_body(pid_file: &Path) -> String {
            format!(
                "printf 'PARTIAL' > \"$last\"\nprintf '%s' \"$$\" > \"{}\"\nexec sleep 30",
                pid_file.display()
            )
        }

        fn args_written(file: &Path) -> Vec<String> {
            std::fs::read_to_string(file)
                .expect("argv file")
                .lines()
                .map(str::to_owned)
                .collect()
        }

        fn pid_of(file: &Path) -> u32 {
            for _ in 0..50 {
                if let Ok(text) = std::fs::read_to_string(file) {
                    if let Ok(pid) = text.trim().parse() {
                        return pid;
                    }
                }
                std::thread::sleep(Duration::from_millis(10));
            }
            panic!("fake ffmpeg never wrote its pid");
        }

        fn process_is_gone(pid: u32) -> bool {
            !std::process::Command::new("kill")
                .arg("-0")
                .arg(pid.to_string())
                .status()
                .expect("kill -0")
                .success()
        }

        async fn encode_with(
            scratch: &Scratch,
            exe: &Path,
            cancel: Option<EncodeCancel>,
        ) -> Result<PathBuf> {
            run_share_encode_in(
                exe,
                &scratch.master(),
                &scratch.output(),
                &scratch.clips,
                target(),
                12.5,
                cancel,
            )
            .await
        }

        #[tokio::test]
        async fn fake_ffmpeg_receives_the_shared_arguments() {
            let scratch = Scratch::new("args");
            let master = scratch.write_master();
            let args_file = scratch.bin.join("argv.txt");
            let exe = script(
                &scratch.bin,
                &args_file,
                "printf 'ENCODED-BYTES' > \"$last\"\nexit 0",
            );
            let output = encode_with(&scratch, &exe, None)
                .await
                .expect("encode succeeds");
            assert_eq!(output, scratch.output());

            let args = args_written(&args_file);
            assert!(args.iter().any(|arg| arg == "-nostdin"), "{args:?}");
            assert!(args.iter().any(|arg| arg == "-y"), "{args:?}");
            assert!(args.iter().any(|arg| arg == "libx264"), "{args:?}");
            let index = args.iter().position(|arg| arg == "-i").expect("-i");
            assert_eq!(args[index + 1], master.to_string_lossy());
            assert!(!args.iter().any(|arg| arg == "-t" || arg == "-r"));
            let expected = scratch.output().to_string_lossy().into_owned();
            let last = args.last().expect("last argument");
            assert!(last.starts_with(&expected), "{last}");
            assert!(last.contains(".part-"), "{last}");
            scratch.cleanup();
        }

        #[tokio::test]
        async fn success_returns_the_share_path_and_leaves_no_temp() {
            let scratch = Scratch::new("success");
            let master = scratch.write_master();
            let args_file = scratch.bin.join("argv.txt");
            let exe = script(
                &scratch.bin,
                &args_file,
                "printf 'ENCODED-BYTES' > \"$last\"\nexit 0",
            );
            let output = encode_with(&scratch, &exe, None)
                .await
                .expect("encode succeeds");
            assert_eq!(output, scratch.output());
            assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
            assert_eq!(std::fs::read(&output).unwrap(), b"ENCODED-BYTES");
            assert_eq!(
                scratch.clip_files(),
                ["master.mkv".to_string(), "master.share.mkv".to_string()]
            );
            scratch.cleanup();
        }

        #[tokio::test]
        async fn nonzero_exit_removes_the_partial_and_keeps_a_good_prior_share() {
            let scratch = Scratch::new("nonzero");
            let master = scratch.write_master();
            let prior = scratch.output();
            std::fs::write(&prior, b"GOOD-PRIOR-SHARE").expect("prior share");
            let args_file = scratch.bin.join("argv.txt");
            let exe = script(
                &scratch.bin,
                &args_file,
                "printf 'PARTIAL' > \"$last\"\nprintf 'bad frame' >&2\nexit 3",
            );
            let error = encode_with(&scratch, &exe, None).await.expect_err("exit 3");
            assert!(error.to_string().contains("code 3"), "{error}");
            assert!(error.to_string().contains("bad frame"), "{error}");
            assert_eq!(std::fs::read(&prior).unwrap(), b"GOOD-PRIOR-SHARE");
            assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
            assert_eq!(
                scratch.clip_files(),
                ["master.mkv".to_string(), "master.share.mkv".to_string()]
            );
            scratch.cleanup();
        }

        #[tokio::test]
        async fn a_zero_byte_success_is_a_failure() {
            let scratch = Scratch::new("empty");
            scratch.write_master();
            let args_file = scratch.bin.join("argv.txt");
            let exe = script(&scratch.bin, &args_file, "exit 0");
            let error = encode_with(&scratch, &exe, None)
                .await
                .expect_err("empty output");
            assert!(error.to_string().contains("no share output"), "{error}");
            assert_eq!(scratch.clip_files(), ["master.mkv".to_string()]);
            scratch.cleanup();
        }

        #[tokio::test]
        async fn a_timeout_kills_the_child_and_removes_the_partial() {
            let scratch = Scratch::new("timeout");
            let master = scratch.write_master();
            let args_file = scratch.bin.join("argv.txt");
            let pid_file = scratch.bin.join("pid");
            let exe = script(&scratch.bin, &args_file, &sleeping_body(&pid_file));
            let started = std::time::Instant::now();
            let error = encode_with(
                &scratch,
                &exe,
                Some(EncodeCancel::new(Duration::from_millis(400))),
            )
            .await
            .expect_err("timeout");
            assert!(error.to_string().contains("timed out"), "{error}");
            assert!(started.elapsed() < Duration::from_secs(10));
            assert!(process_is_gone(pid_of(&pid_file)));
            assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
            assert_eq!(scratch.clip_files(), ["master.mkv".to_string()]);
            scratch.cleanup();
        }

        #[tokio::test]
        async fn an_explicit_cancel_signal_stops_the_encode() {
            let scratch = Scratch::new("cancel");
            let master = scratch.write_master();
            let args_file = scratch.bin.join("argv.txt");
            let pid_file = scratch.bin.join("pid");
            let exe = script(&scratch.bin, &args_file, &sleeping_body(&pid_file));
            let (sender, receiver) = cancel_channel();
            let sender_task = tokio::spawn(async move {
                tokio::time::sleep(Duration::from_millis(200)).await;
                let _ = sender.send(true);
            });
            let error = encode_with(
                &scratch,
                &exe,
                Some(EncodeCancel::with_signal(Duration::from_secs(30), receiver)),
            )
            .await
            .expect_err("cancelled");
            sender_task.await.expect("sender task");
            assert!(error.to_string().contains("cancelled"), "{error}");
            assert!(process_is_gone(pid_of(&pid_file)));
            assert_eq!(std::fs::read(&master).unwrap(), b"MASTER-BYTES-UNTOUCHED");
            assert_eq!(scratch.clip_files(), ["master.mkv".to_string()]);
            scratch.cleanup();
        }
    }
}
