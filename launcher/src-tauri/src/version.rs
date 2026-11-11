/* The version JSON as Mojang actually ships it, plus the two things every
launcher has to get right: rule evaluation (a library that is not for this
OS must never reach the classpath) and inheritance (a Fabric profile is a
thin overlay whose `inheritsFrom` points at the vanilla version).

Unknown fields are kept out of the way rather than rejected — the format
grows a key every few releases and a launcher that refuses to parse the
game it targets is useless. */
use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::paths;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Artifact {
    pub path: Option<String>,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct LibDownloads {
    pub artifact: Option<Artifact>,
    #[serde(default)]
    pub classifiers: BTreeMap<String, Artifact>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
    /// `{"is_demo": false, "has_custom_resolution": true, …}` on game args
    pub features: Option<BTreeMap<String, bool>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: LibDownloads,
    /// Fabric libraries carry a maven root plus loose hash/size instead of a
    /// downloads block
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub rules: Option<Vec<Rule>>,
    /// pre-1.19 shape, still emitted by some third-party profiles
    pub natives: Option<BTreeMap<String, String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
    #[serde(rename = "totalSize")]
    pub total_size: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaVersion {
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Arg {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Arg>,
    #[serde(default)]
    pub jvm: Vec<Arg>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: Option<String>,
    #[serde(rename = "mainClass")]
    pub main_class: Option<String>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndexRef>,
    pub assets: Option<String>,
    #[serde(default)]
    pub downloads: BTreeMap<String, Artifact>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    pub arguments: Option<Arguments>,
    /// the 1.12-era flat string; still the only arg source on old profiles
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    #[serde(rename = "javaVersion")]
    pub java_version: Option<JavaVersion>,
}

/* ── rules ─────────────────────────────────────────────────────── */

pub fn os_name() -> &'static str {
    if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn os_arch() -> &'static str {
    match std::env::consts::ARCH {
        "x86_64" => "x86_64",
        "aarch64" => "arm64",
        "x86" => "x86",
        other => other,
    }
}

/// Mojang's rules are last-match-wins over an implicit `disallow`.
/// Feature-gated args (demo mode, custom resolution) are declined — the
/// launcher does not offer those flags, so claiming the feature would put
/// arguments on the command line that nothing fills in.
pub fn allowed(rules: &Option<Vec<Rule>>) -> bool {
    let Some(rules) = rules else { return true };
    let mut ok = false;
    for r in rules {
        let matches = match (&r.os, &r.features) {
            (Some(os), _) => {
                os.name.as_deref().is_none_or(|n| n == os_name())
                    && os.arch.as_deref().is_none_or(|a| a == os_arch())
            }
            (None, Some(_)) => false,
            (None, None) => true,
        };
        if matches {
            ok = r.action == "allow";
        }
    }
    ok
}

impl Library {
    /// `group:artifact:version[:classifier]` → `group/artifact/version/artifact-version[-classifier].jar`
    pub fn maven_path(&self) -> Option<String> {
        let mut parts = self.name.split(':');
        let group = parts.next()?.replace('.', "/");
        let artifact = parts.next()?;
        let version = parts.next()?;
        let classifier = parts.next();
        let file = match classifier {
            Some(c) => format!("{artifact}-{version}-{c}.jar"),
            None => format!("{artifact}-{version}.jar"),
        };
        Some(format!("{group}/{artifact}/{version}/{file}"))
    }

    /// Where this library lands under `shared/libraries`.
    pub fn rel_path(&self) -> Option<String> {
        self.downloads
            .artifact
            .as_ref()
            .and_then(|a| a.path.clone())
            .or_else(|| self.maven_path())
    }

    /// Fabric ships `url` (a maven root) instead of a downloads block.
    pub fn download_url(&self) -> Option<String> {
        if let Some(a) = &self.downloads.artifact {
            return Some(a.url.clone());
        }
        let root = self.url.as_deref()?.trim_end_matches('/').to_string();
        Some(format!("{root}/{}", self.maven_path()?))
    }

    pub fn checksum(&self) -> Option<String> {
        self.downloads
            .artifact
            .as_ref()
            .map(|a| a.sha1.clone())
            .or_else(|| self.sha1.clone())
    }

    pub fn byte_size(&self) -> u64 {
        self.downloads
            .artifact
            .as_ref()
            .map(|a| a.size)
            .or(self.size)
            .unwrap_or(0)
    }

    /// Native jars are extracted next to the instance, never put on the
    /// classpath — LWJGL loads the DLLs from `java.library.path`.
    pub fn is_native(&self) -> bool {
        self.classifier().is_some_and(|c| c.starts_with("natives")) || self.natives.is_some()
    }

    pub fn classifier(&self) -> Option<&str> {
        self.name.split(':').nth(3)
    }

    /// The rules on a native library only name the OS: `natives-windows`,
    /// `natives-windows-arm64` and `natives-windows-x86` all pass on Windows.
    /// The architecture lives in the classifier, so it has to be read there —
    /// extraction flattens by filename, and letting the arm64 jar through
    /// overwrites lwjgl.dll with one this machine cannot load.
    pub fn matches_arch(&self) -> bool {
        let Some(classifier) = self.classifier() else {
            return true;
        };
        let Some(rest) = classifier.strip_prefix("natives-") else {
            return true;
        };
        // "windows" | "windows-arm64" | "macos-arm64" | "linux"
        let suffix = rest.split_once('-').map(|(_, s)| s);
        match suffix {
            None => os_arch() == "x86_64",
            Some("arm64") | Some("aarch64") => os_arch() == "arm64",
            Some("x86") => os_arch() == "x86",
            // an arch this build has never heard of is not this machine's
            Some(_) => false,
        }
    }
}

impl VersionJson {
    /// Merge an overlay (Fabric) onto its parent. The child wins on scalars;
    /// its libraries go *first* so the loader shadows the vanilla classes it
    /// replaces, which is the whole point of a mod loader.
    pub fn inherit(child: VersionJson, parent: VersionJson) -> VersionJson {
        let mut libraries = child.libraries;
        libraries.extend(parent.libraries);

        let arguments = match (child.arguments, parent.arguments) {
            (Some(c), Some(p)) => Some(Arguments {
                game: [p.game, c.game].concat(),
                jvm: [p.jvm, c.jvm].concat(),
            }),
            (some, None) | (None, some) => some,
        };

        VersionJson {
            id: child.id,
            inherits_from: None,
            main_class: child.main_class.or(parent.main_class),
            asset_index: child.asset_index.or(parent.asset_index),
            assets: child.assets.or(parent.assets),
            downloads: if child.downloads.is_empty() {
                parent.downloads
            } else {
                child.downloads
            },
            libraries,
            arguments,
            minecraft_arguments: child.minecraft_arguments.or(parent.minecraft_arguments),
            java_version: child.java_version.or(parent.java_version),
        }
    }

    pub fn usable_libraries(&self) -> impl Iterator<Item = &Library> {
        self.libraries
            .iter()
            .filter(|l| allowed(&l.rules) && l.matches_arch())
    }

    pub fn client_jar(&self) -> Option<&Artifact> {
        self.downloads.get("client")
    }
}

/* ── on-disk cache ─────────────────────────────────────────────── */

pub fn json_file(id: &str) -> std::path::PathBuf {
    paths::versions_dir().join(id).join(format!("{id}.json"))
}

pub fn client_jar_file(id: &str) -> std::path::PathBuf {
    paths::versions_dir().join(id).join(format!("{id}.jar"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rule(action: &str, os: Option<&str>) -> Rule {
        Rule {
            action: action.into(),
            os: os.map(|n| OsRule {
                name: Some(n.into()),
                arch: None,
            }),
            features: None,
        }
    }

    #[test]
    fn no_rules_means_allowed() {
        assert!(allowed(&None));
    }

    #[test]
    fn other_os_is_rejected() {
        let other = if os_name() == "windows" {
            "linux"
        } else {
            "windows"
        };
        assert!(!allowed(&Some(vec![rule("allow", Some(other))])));
        assert!(allowed(&Some(vec![rule("allow", Some(os_name()))])));
    }

    #[test]
    fn disallow_wins_when_it_comes_last() {
        assert!(!allowed(&Some(vec![
            rule("allow", None),
            rule("disallow", Some(os_name()))
        ])));
    }

    #[test]
    fn feature_gated_args_are_declined() {
        let r = Rule {
            action: "allow".into(),
            os: None,
            features: Some(BTreeMap::from([("is_demo".to_string(), true)])),
        };
        assert!(!allowed(&Some(vec![r])));
    }

    fn native(classifier: &str) -> Library {
        Library {
            name: format!("org.lwjgl:lwjgl:3.4.1:{classifier}"),
            downloads: LibDownloads::default(),
            url: None,
            sha1: None,
            size: None,
            // exactly what Mojang ships: os only, no arch
            rules: Some(vec![rule("allow", Some(os_name()))]),
            natives: None,
        }
    }

    #[test]
    fn only_this_machines_native_arch_survives() {
        let plain = native(&format!(
            "natives-{}",
            if os_name() == "osx" {
                "macos"
            } else {
                os_name()
            }
        ));
        let arm = native("natives-windows-arm64");
        let x86 = native("natives-windows-x86");
        assert_eq!(plain.matches_arch(), os_arch() == "x86_64");
        assert_eq!(arm.matches_arch(), os_arch() == "arm64");
        assert_eq!(x86.matches_arch(), os_arch() == "x86");
        // the rules alone would have let all three through
        assert!(allowed(&arm.rules) || os_name() != "windows");
    }

    #[test]
    fn maven_path_handles_classifiers() {
        let lib = Library {
            name: "com.mojang:jtracy:1.0.37:natives-windows".into(),
            downloads: LibDownloads::default(),
            url: None,
            sha1: None,
            size: None,
            rules: None,
            natives: None,
        };
        assert_eq!(
            lib.maven_path().unwrap(),
            "com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-windows.jar"
        );
        assert!(lib.is_native());
    }
}
