import { useEffect, useState, type ReactNode } from "react";
import { Icon } from "../components/Icon";
import { Badge, Button, Segmented, Slider, Toggle } from "../components/ui";
import { Dancheong, Marker, Seal } from "../components/Palace";
import { HudEditor } from "../widgets/HudEditor";
import { isRealCore } from "../core/client";
import { ACCENT_METALS, accentMetal, useStore, type SettingsTab } from "../state/store";
import { useT } from "../i18n";
import brand from "../brand";

/* injected by vite from package.json — the About tab used to carry a literal
   that nobody remembers to bump */
declare const __APP_VERSION__: string;
const APP_VERSION = typeof __APP_VERSION__ !== "undefined" ? __APP_VERSION__ : "dev";

/* Mirrors java.rs `major_of` — Java 8 reports itself as 1.8.0_x. The floor is
   MIN_MAJOR there; the two have to move together. */
const JAVA_MIN_MAJOR = 21;
function javaMajor(version: string): number {
  const head = version.split(/[.\-+]/)[0] ?? "0";
  if (head === "1") return Number(version.split(".")[1] ?? 0) || 0;
  return Number(head) || 0;
}

/** A settings section: the framed panel with its name in the lattice band. */
function Section({ title, action, children, className = "" }: {
  title: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`set-section pf-frame ${className}`}>
      <div className="set-section-head pf-band">
        <h3>{title}</h3>
        {action}
      </div>
      <Dancheong />
      <div className="set-section-body">{children}</div>
    </section>
  );
}

function JavaSection() {
  const {
    settings,
    patchSettings,
    javaRuntimes: runtimes,
    scanningJava: scanning,
    rescanJava,
    versions,
  } = useStore();
  const { t } = useT();

  const target = versions.find((v) => v.isTarget)?.id ?? "";
  const picked = runtimes.find((j) => j.path === settings?.javaPath) ?? null;
  const meets = picked !== null && javaMajor(picked.version) >= JAVA_MIN_MAJOR;

  return (
    <Section
      title={t("settings.java")}
      action={
        <Button size="sm" variant="outline" icon="refresh" loading={scanning} onClick={() => void rescanJava()}>
          {t("settings.rescan")}
        </Button>
      }
    >
      <div className="java-list" role="radiogroup" aria-label={t("settings.javaRuntime")}>
        {runtimes.map((j) => {
          const chosen = settings?.javaPath === j.path;
          return (
            <button
              key={j.path}
              role="radio"
              aria-checked={chosen}
              className={`java-card pf-tile ${chosen ? "is-chosen" : ""}`}
              onClick={() => void patchSettings({ javaPath: j.path })}
            >
              <span className="java-icon">
                <Icon name="cpu" size={17} />
              </span>
              <span className="java-meta">
                <strong className="num">
                  {j.version} <em className="java-arch">{j.arch}</em>
                </strong>
                <span className="java-path mono">{j.path}</span>
                <span className="java-vendor">{j.vendor}</span>
              </span>
              {j.recommended && <Badge tone="ok">{t("settings.recommended")}</Badge>}
            </button>
          );
        })}
        {runtimes.length === 0 && !scanning && <p className="set-hint">{t("settings.javaNone")}</p>}
      </div>

      {/* the picker lists whatever is on the box; this line is the only thing
          that says whether the pick can actually run the target version */}
      <div className={`java-req ${meets ? "is-ok" : "is-bad"}`}>
        <Icon name={meets ? "check" : "info"} size={14} />
        <span>{t("settings.javaReq", { mc: target || "26.x", major: String(JAVA_MIN_MAJOR) })}</span>
        <b className="num">
          {picked ? t(meets ? "settings.javaMeets" : "settings.javaShort", { v: picked.version }) : t("settings.javaUnset")}
        </b>
      </div>

      <div className="field" style={{ marginTop: 20, marginBottom: 0 }}>
        <label className="field-label" htmlFor="java-args">{t("settings.javaArgs")}</label>
        <input
          id="java-args"
          className="input mono"
          value={settings?.javaArgs ?? ""}
          onChange={(e) => void patchSettings({ javaArgs: e.target.value })}
          spellCheck={false}
        />
      </div>
    </Section>
  );
}

export function SettingsScreen() {
  const { settings, patchSettings, selectedInstance, toast, settingsTab, versions, systemMemoryMb } =
    useStore();
  const target = versions.find((v) => v.isTarget);
  const { t, setLang } = useT();
  const [tab, setTab] = useState<SettingsTab>(settingsTab ?? "java");

  /* a deep link can arrive while this screen is already mounted */
  useEffect(() => {
    if (settingsTab) setTab(settingsTab);
  }, [settingsTab]);

  if (!settings) return null;

  const tabs = [
    { id: "java" as const, icon: "cpu" as const, label: t("settings.java") },
    { id: "perf" as const, icon: "ram" as const, label: t("settings.perf") },
    { id: "look" as const, icon: "palette" as const, label: t("settings.appearance") },
    { id: "hud" as const, icon: "gamepad" as const, label: t("settings.hudSection") },
    { id: "privacy" as const, icon: "shield" as const, label: t("settings.privacy") },
    { id: "about" as const, icon: "info" as const, label: t("settings.about") },
  ];
  const metal = accentMetal(settings.accent);

  return (
    <div className="screen-pad">
      <div className={`set-layout ${tab === "hud" ? "is-wide" : ""}`}>
        <nav className="set-nav" aria-label={t("nav.settings")}>
          {tabs.map((x) => (
            <button
              key={x.id}
              className={tab === x.id ? "is-current" : ""}
              aria-current={tab === x.id ? "page" : undefined}
              onClick={() => setTab(x.id)}
            >
              <Marker on={tab === x.id} />
              <Icon name={x.icon} size={16} />
              {x.label}
            </button>
          ))}
        </nav>

        <div className="set-body screen-enter" key={tab}>
          {tab === "java" && <JavaSection />}

          {tab === "perf" && (
            <Section title={t("settings.perf")}>
              <div className="field">
                <label className="field-label">
                  {t("settings.memory")} — <b className="num">{(settings.memoryMb / 1024).toFixed(1)} GB</b>
                </label>
                <Slider
                  min={2048}
                  max={16384}
                  step={512}
                  value={settings.memoryMb}
                  onChange={(v) => void patchSettings({ memoryMb: v })}
                  marks={[2048, 4096, 6144, 8192, 12288, 16384]}
                  format={(v) => `${v / 1024}G`}
                />
                <p className="set-hint">{t("settings.memoryDesc")}</p>
                {/* a JVM handed most of the machine leaves nothing for the OS or
                    the launcher itself, and the slider used to run to 16G on a
                    16 GB box without saying so */}
                {systemMemoryMb > 0 && (
                  <p className={`mem-note ${settings.memoryMb > systemMemoryMb * 0.6 ? "is-warn" : ""}`}>
                    <Icon name="info" size={13} />
                    {t("settings.memoryTotal", { gb: (systemMemoryMb / 1024).toFixed(1) })}
                    {settings.memoryMb > systemMemoryMb * 0.6 && ` · ${t("settings.memoryOver")}`}
                  </p>
                )}
              </div>
              <div className="field" style={{ marginBottom: 0 }}>
                <label className="field-label">
                  {t("settings.concurrency")} — <b className="num">{settings.concurrency}</b>
                </label>
                <Slider
                  min={1}
                  max={16}
                  value={settings.concurrency}
                  onChange={(v) => void patchSettings({ concurrency: v })}
                  marks={[1, 4, 8, 12, 16]}
                />
                <p className="set-hint">{t("settings.concurrencyDesc")}</p>
              </div>
            </Section>
          )}

          {tab === "look" && (
            <Section title={t("settings.appearance")}>
              <div className="field">
                <span className="field-label">{t("settings.theme")}</span>
                <Segmented
                  options={[
                    { value: "dark", label: t("settings.dark") },
                    { value: "light", label: t("settings.light") },
                  ]}
                  value={settings.theme}
                  onChange={(v) => void patchSettings({ theme: v })}
                />
              </div>
              <div className="field">
                <span className="field-label">{t("settings.accent")}</span>
                <div className="metal-row" role="radiogroup" aria-label={t("settings.accent")}>
                  {ACCENT_METALS.map((m) => (
                    <button
                      key={m}
                      role="radio"
                      aria-checked={metal === m}
                      className={`metal-chip ${metal === m ? "is-current" : ""}`}
                      onClick={() => void patchSettings({ accent: m })}
                    >
                      <span className={`metal-swatch metal-${m}`} aria-hidden />
                      {t(`settings.metal.${m}`)}
                    </button>
                  ))}
                </div>
                <p className="set-hint">{t("settings.accentDesc")}</p>
              </div>
              <div className="field" style={{ marginBottom: 0 }}>
                <span className="field-label">{t("settings.language")}</span>
                <Segmented
                  options={[
                    { value: "ko", label: "한국어" },
                    { value: "en", label: "English" },
                  ]}
                  value={settings.language}
                  onChange={(v) => {
                    void patchSettings({ language: v });
                    setLang(v);
                  }}
                />
              </div>
            </Section>
          )}

          {tab === "hud" && (
            <Section
              title={t("settings.hudSection")}
              action={selectedInstance && <Badge tone="accent">{selectedInstance.name}</Badge>}
            >
              <p className="set-hint" style={{ marginBottom: 16 }}>{t("settings.hudDesc")}</p>
              {selectedInstance ? (
                <HudEditor key={selectedInstance.id} instanceId={selectedInstance.id} />
              ) : (
                <p className="set-hint">{t("dock.selectInstance")}</p>
              )}
            </Section>
          )}

          {tab === "privacy" && (
            <Section title={t("settings.privacy")}>
              {/* the pledge — every competitor broke one of these lines */}
              <ul className="pledge">
                {(["noAds", "noForced", "noSell"] as const).map((k) => (
                  <li key={k}>
                    <Marker on />
                    <div>
                      <strong>{t(`settings.pledge.${k}`)}</strong>
                      <p className="set-hint">{t(`settings.pledge.${k}Desc`)}</p>
                    </div>
                  </li>
                ))}
              </ul>

              <div className="set-row">
                <div>
                  <strong>{t("settings.telemetry")}</strong>
                  <p className="set-hint">{t("settings.telemetryDesc")}</p>
                </div>
                <Toggle
                  checked={settings.telemetry}
                  label={t("settings.telemetry")}
                  onChange={(v) => {
                    void patchSettings({ telemetry: v });
                    toast("info", t("settings.saved"));
                  }}
                />
              </div>

              <p className="pledge-foot">
                <Icon name="shield" size={13} /> {t("settings.pledgeFoot")}
              </p>
            </Section>
          )}

          {tab === "about" && (
            <Section title={t("settings.about")} className="about">
              <div className="about-mark">
                <Seal size={64} />
                <div>
                  <h3 className="about-name">{brand.name}</h3>
                  <p className="about-tagline">{brand.tagline}</p>
                </div>
              </div>
              <div className="about-rows">
                <div><span>{t("settings.version")}</span><b className="num">{APP_VERSION}</b></div>
                <div>
                  <span>{t("settings.build")}</span>
                  <b className="mono">{isRealCore ? "rust core / tauri" : "ui-standalone / vite"}</b>
                </div>
                <div><span>{t("settings.minecraft")}</span><b className="num">{target?.id ?? "…"}</b></div>
              </div>
              {/* the shell decides which core answers, so this line has to ask
                  rather than assert — it claimed mock while the rust core was live */}
              {!isRealCore && <p className="set-hint">{t("settings.aboutMock")}</p>}
            </Section>
          )}
        </div>
      </div>
    </div>
  );
}
