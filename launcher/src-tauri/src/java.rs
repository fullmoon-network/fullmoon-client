/* Real JDK discovery. Candidates come from JAVA_HOME, PATH and the vendor
   install roots people actually end up with (Windows: Adoptium, Microsoft,
   Zulu, Graal; Linux: /usr/lib/jvm, SDKMAN, IntelliJ's ~/.jdks; plus Mojang's
   own bundled runtimes on both); each is then asked what it is with
   `java -version`, because a path proves nothing. */
use std::{
    collections::BTreeSet,
    path::{Path, PathBuf},
};

use crate::{error::Result, model::JavaRuntime};

/// Minecraft 26.1 ships `java-runtime-epsilon` (major 25) in its version json and its classes
/// are compiled for it, so a Java 21 dies with UnsupportedClassVersionError. Anything older is
/// listed but never recommended, so the picker can still show a user their old 8. The frontend
/// mirrors this as `JAVA_MIN_MAJOR` in Settings.tsx; the two move together.
const MIN_MAJOR: u32 = 25;

pub async fn detect() -> Result<Vec<JavaRuntime>> {
    let mut seen = BTreeSet::new();
    let mut out = Vec::new();

    for exe in candidates().await {
        let Ok(canon) = dunce_canonicalize(&exe) else { continue };
        if !seen.insert(canon.clone()) {
            continue;
        }
        if let Some(rt) = probe(&canon).await {
            out.push(rt);
        }
    }

    out.sort_by(|a, b| {
        major_of(&b.version)
            .cmp(&major_of(&a.version))
            .then_with(|| a.path.cmp(&b.path))
    });
    if let Some(first) = out.iter_mut().find(|r| major_of(&r.version) >= MIN_MAJOR) {
        first.recommended = true;
    }
    Ok(out)
}

/// Every `java` executable worth asking about, before deduplication.
/// How far below a vendor root a JDK home may sit. Mojang nests three deep:
/// `<root>/<component>/<platform>/<component>/bin/java`.
const MAX_DEPTH: usize = 3;

/// Every `<dir>/bin/<exe>` in the tree under `root`, `depth` levels down. macOS bundles keep
/// their home under `Contents/Home`, so that one is tried on every directory as well.
async fn collect_java_homes(root: &Path, depth: usize, exe: &str, found: &mut Vec<PathBuf>) {
    let mut level = vec![root.to_path_buf()];
    for _ in 0..depth {
        let mut next = Vec::new();
        for dir in &level {
            let Ok(mut entries) = tokio::fs::read_dir(dir).await else { continue };
            while let Ok(Some(entry)) = entries.next_entry().await {
                let sub = entry.path();
                found.push(sub.join("bin").join(exe));
                found.push(sub.join("Contents").join("Home").join("bin").join(exe));
                next.push(sub);
            }
        }
        level = next;
    }
}

async fn candidates() -> Vec<PathBuf> {
    let exe_name = if cfg!(windows) { "java.exe" } else { "java" };
    let mut found = Vec::new();

    if let Ok(home) = std::env::var("JAVA_HOME") {
        found.push(PathBuf::from(home).join("bin").join(exe_name));
    }

    if let Ok(path) = std::env::var("PATH") {
        for dir in std::env::split_paths(&path) {
            found.push(dir.join(exe_name));
        }
    }

    for root in vendor_roots() {
        collect_java_homes(&root, MAX_DEPTH, exe_name, &mut found).await;
    }

    found
}

fn vendor_roots() -> Vec<PathBuf> {
    let mut roots = Vec::new();
    if cfg!(windows) {
        for base in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] {
            let Ok(base) = std::env::var(base) else { continue };
            let base = PathBuf::from(base);
            roots.push(base.join("Eclipse Adoptium"));
            roots.push(base.join("Java"));
            roots.push(base.join("Microsoft"));
            roots.push(base.join("Zulu"));
            roots.push(base.join("Amazon Corretto"));
            roots.push(base.join("BellSoft"));
            roots.push(base.join("GraalVM"));
            // the official launcher's runtimes, which most players already have
            roots.push(base.join("Packages").join("Microsoft.4297127D64EC6_8wekyb3d8bbwe").join("LocalCache").join("Local").join("runtime"));
        }
        if let Ok(appdata) = std::env::var("APPDATA") {
            roots.push(PathBuf::from(appdata).join(".minecraft").join("runtime"));
        }
    } else {
        roots.extend(unix_roots(dirs::home_dir().as_deref()));
    }
    roots
}

/// Where Linux (and macOS) keep JDKs: distro packages, the official launcher's runtimes,
/// IntelliJ's downloads, SDKMAN and the other version managers, Prism/MultiMC's own copies.
fn unix_roots(home: Option<&Path>) -> Vec<PathBuf> {
    let mut roots: Vec<PathBuf> = [
        "/usr/lib/jvm",   // Debian, Ubuntu, Arch, Fedora (`/usr/lib/jvm/java-25-openjdk-amd64`)
        "/usr/lib64/jvm", // openSUSE
        "/usr/java",      // Oracle's rpm
        "/opt/java",
        "/opt/jdk",
        "/opt/openjdk",
        "/Library/Java/JavaVirtualMachines",
    ]
    .into_iter()
    .map(PathBuf::from)
    .collect();
    if let Some(h) = home {
        for rel in [
            ".minecraft/runtime", // the official launcher: <component>/<platform>/<component>/bin
            ".var/app/com.mojang.Minecraft/.minecraft/runtime", // its Flatpak
            ".jdks",              // IntelliJ
            ".sdkman/candidates/java",
            ".asdf/installs/java",
            ".local/share/mise/installs/java",
            ".local/share/JetBrains/Toolbox/apps",
            ".local/share/PrismLauncher/java",
            ".local/share/multimc/java",
            ".local/share/jdks",
            "Library/Java/JavaVirtualMachines",
        ] {
            roots.push(h.join(rel));
        }
    }
    roots
}

async fn probe(exe: &Path) -> Option<JavaRuntime> {
    if !tokio::fs::try_exists(exe).await.unwrap_or(false) {
        return None;
    }
    let mut cmd = tokio::process::Command::new(exe);
    cmd.arg("-XshowSettings:properties").arg("-version");
    #[cfg(windows)]
    {
        // tokio's Command carries creation_flags itself; no CommandExt needed
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let out = cmd.output().await.ok()?;
    // `-XshowSettings` and `-version` both report on stderr
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&out.stderr),
        String::from_utf8_lossy(&out.stdout)
    );

    let version = property(&text, "java.version").or_else(|| quoted_version(&text))?;
    let vendor = property(&text, "java.vendor").unwrap_or_else(|| "unknown".into());
    let arch = property(&text, "os.arch").unwrap_or_else(|| "unknown".into());

    Some(JavaRuntime {
        path: display_path(exe),
        version,
        vendor,
        arch,
        recommended: false,
    })
}

/// The UI compares this string with the saved `javaPath`, so it must be exactly what gets
/// spawned. Windows wants backslashes; everywhere else the path is already right and a
/// backslash would turn a filename into a different one.
fn display_path(exe: &Path) -> String {
    let s = exe.to_string_lossy();
    if cfg!(windows) { s.replace('/', "\\") } else { s.into_owned() }
}

fn property(text: &str, key: &str) -> Option<String> {
    text.lines()
        .map(str::trim)
        .find_map(|l| l.strip_prefix(key)?.strip_prefix(" = ").map(str::to_owned))
}

/// Fallback for JVMs that don't answer -XshowSettings: `openjdk version "21.0.5"`.
fn quoted_version(text: &str) -> Option<String> {
    let line = text.lines().find(|l| l.contains(" version \""))?;
    let start = line.find('"')? + 1;
    let end = line[start..].find('"')? + start;
    Some(line[start..end].to_owned())
}

pub fn major_of(version: &str) -> u32 {
    let head = version.split(['.', '-', '+']).next().unwrap_or("0");
    // Java 8 reports as 1.8.0_x
    if head == "1" {
        return version
            .split('.')
            .nth(1)
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);
    }
    head.parse().unwrap_or(0)
}

/// `std::fs::canonicalize` on Windows yields `\\?\` paths that then leak into
/// the UI and into process arguments; keep the plain form.
fn dunce_canonicalize(p: &Path) -> std::io::Result<PathBuf> {
    let c = std::fs::canonicalize(p)?;
    let s = c.to_string_lossy();
    Ok(PathBuf::from(
        s.strip_prefix(r"\\?\").map(str::to_owned).unwrap_or_else(|| s.to_string()),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("fullmoon-java-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn java_25_is_the_floor_and_java_8_reads_as_8() {
        assert_eq!(MIN_MAJOR, 25);
        assert_eq!(major_of("25.0.3"), 25);
        assert_eq!(major_of("21.0.5"), 21);
        assert_eq!(major_of("1.8.0_402"), 8);
        assert_eq!(major_of("26-ea"), 26);
    }

    #[test]
    fn linux_roots_cover_distro_and_home_installs() {
        let roots = unix_roots(Some(Path::new("/home/p")));
        for want in [
            "/usr/lib/jvm",
            "/home/p/.minecraft/runtime",
            "/home/p/.jdks",
            "/home/p/.sdkman/candidates/java",
        ] {
            assert!(roots.contains(&PathBuf::from(want)), "missing {want}");
        }
        assert!(!unix_roots(None).is_empty());
    }

    #[test]
    fn finds_flat_and_mojang_nested_homes() {
        let root = scratch("nested");
        // Debian: <root>/java-25-openjdk-amd64/bin/java
        std::fs::create_dir_all(root.join("java-25-openjdk-amd64/bin")).unwrap();
        // Mojang: <root>/java-runtime-epsilon/linux/java-runtime-epsilon/bin/java
        std::fs::create_dir_all(root.join("java-runtime-epsilon/linux/java-runtime-epsilon/bin")).unwrap();
        let mut found = Vec::new();
        let rt = tokio::runtime::Builder::new_current_thread().build().unwrap();
        rt.block_on(collect_java_homes(&root, MAX_DEPTH, "java", &mut found));
        assert!(found.contains(&root.join("java-25-openjdk-amd64/bin/java")));
        assert!(found.contains(&root.join("java-runtime-epsilon/linux/java-runtime-epsilon/bin/java")));
        let _ = std::fs::remove_dir_all(&root);
    }

    #[cfg(unix)]
    #[test]
    fn a_unix_path_keeps_its_slashes() {
        assert_eq!(display_path(Path::new("/usr/lib/jvm/x/bin/java")), "/usr/lib/jvm/x/bin/java");
    }
}
