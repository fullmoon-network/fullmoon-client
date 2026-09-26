import { useMemo } from "react";
import { Icon } from "../components/Icon";
import { Dancheong, Marker, MoonDial, MoonDisc, Wordmark } from "../components/Palace";
import { SkinFace } from "../components/ui";
import { PlayLabel } from "../components/PlayDock";
import { useStore } from "../state/store";
import { usePlayAction } from "../state/playAction";
import { isRealCore } from "../core/client";
import { daysToFull, daysToNextFull, isFull, moonAt, moonName } from "../core/moonPhase";
import { useT } from "../i18n";
import BRAND from "../brand";
import panorama from "../../../i3/mod/src/main/resources/assets/minecraft/textures/gui/title/background/panorama_0.png";
import { HomeScreen } from "./Home";

declare const __APP_VERSION__: string;
const APP_VERSION = typeof __APP_VERSION__ !== "undefined" ? __APP_VERSION__ : "dev";

/* The play screen is the game's title screen brought to the launcher: the Fullmoon lobby at night
   (the panorama the title screen turns behind itself), tonight's real moon on its dial, and the
   palace plaque with one loud way into the lobby. The dashboard reads on below it. */
export function PlayScreen() {
  const { servers, serverStatus, versions, activeAccount, news, instances, selectedInstance, launch, toast, setScreen } =
    useStore();
  const { t } = useT();

  const lobby = servers[0] ?? null;
  const { state, act, busy } = usePlayAction(lobby?.address ?? null);
  const launchOnly = usePlayAction(null);
  const moon = useMemo(() => moonAt(Date.now()), []);
  const full = isFull(moon);
  const headline = t(`moon.${moonName(moon)}`);
  const detail = full ? t("moon.next", { n: daysToNextFull(moon) }) : t("moon.until", { n: daysToFull(moon) });
  const target = versions.find((v) => v.isTarget)?.id ?? selectedInstance?.versionId ?? "26.1.2";
  const featured = useMemo(() => news.find((n) => n.featured) ?? news[0] ?? null, [news]);

  const lobbyStatus = lobby ? serverStatus[lobby.address] : undefined;
  const reachable = lobbyStatus?.online === true;
  const status = !lobby
    ? null
    : !isRealCore && lobbyStatus && !lobbyStatus.online
      ? t("home.lobbyBrowser")
      : !lobbyStatus
        ? t("home.lobbyChecking")
        : lobbyStatus.online
          ? lobbyStatus.maxPlayers > 0 || lobbyStatus.players > 0
            ? t("home.lobbyLive", { n: lobbyStatus.players, ms: lobbyStatus.pingMs })
            : t("home.lobbyOpen", { ms: lobbyStatus.pingMs })
          : t("home.lobbyDown");

  const quickPlay = (address: string) => {
    const inst =
      (selectedInstance?.installed ? selectedInstance : null) ?? instances.find((i) => i.installed);
    if (!inst) {
      toast("error", t("toast.launchFail", { reason: "no installed instance" }));
      return;
    }
    void launch(inst.id, address);
  };

  const others = servers.slice(1, 3);

  return (
    <div className="home">
      <section className="hero" data-theme="dark" aria-label={t("nav.play")}>
        <div className="hero-sky" style={{ backgroundImage: `url(${panorama})` }} aria-hidden />
        <div className="hero-veil" aria-hidden />

        <MoonDial className="hero-moon" r={46} lit={moon.lit} waxing={moon.waxing} complete={full} label={`${headline} · ${detail}`} />

        <div className="hero-plaque pf-frame">
          <div className="plaque-band pf-band">
            <span className="plaque-tagline">{BRAND.tagline}</span>
            <Wordmark size="lg" name={BRAND.name} />
          </div>
          <Dancheong />
          <div className="plaque-body">
            <div className="plaque-today">
              <svg width="26" height="26" viewBox="-13 -13 26 26" aria-hidden className={full ? "is-full" : ""}>
                <MoonDisc r={8.5} lit={moon.lit} waxing={moon.waxing} />
                <circle className="plaque-today-ring" r={11.5} fill="none" />
              </svg>
              <div>
                <strong>{headline}</strong>
                <span className="num">{detail}</span>
              </div>
            </div>
            <div className="pf-rule-dashed" />

            <button
              className={`lobby-btn lobby-btn-${state.kind}`}
              onClick={act}
              aria-busy={busy}
              disabled={state.kind === "preparing"}
            >
              {state.kind === "ready" ? (
                <>
                  <span className="lobby-btn-label">{t("home.joinLobby")}</span>
                  {status && (
                    <span className="lobby-btn-status num">
                      <i className={reachable ? "is-live" : ""} aria-hidden />
                      {status}
                    </span>
                  )}
                </>
              ) : (
                <PlayLabel state={state} idleLabel={t("home.joinLobby")} />
              )}
            </button>

            <ul className="plaque-menu">
              {others.map((s) => (
                <li key={s.id}>
                  <button className="title-row" onClick={() => quickPlay(s.address)}>
                    <Marker />
                    <span>{t("home.joinServer", { name: s.name })}</span>
                    <em className="mono">{s.address}</em>
                  </button>
                </li>
              ))}
              <li>
                <button
                  className="title-row"
                  onClick={launchOnly.act}
                  disabled={launchOnly.state.kind !== "ready"}
                >
                  <Marker />
                  <span>{t("home.launchOnly")}</span>
                </button>
              </li>
              <li>
                <button
                  className="title-row"
                  onClick={() =>
                    document.getElementById("dash")?.scrollIntoView({
                      behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
                      block: "start",
                    })
                  }
                >
                  <Marker />
                  <span>{t("home.serverList")}</span>
                  <em className="num">{servers.length}</em>
                </button>
              </li>
              <li>
                <button className="title-row" onClick={() => setScreen("settings", "hud")}>
                  <Marker />
                  <span>{t("home.hudLayout")}</span>
                </button>
              </li>
              <li>
                <button className="title-row" onClick={() => setScreen("cosmetics")}>
                  <Marker />
                  <span>{t("home.cosmetics")}</span>
                </button>
              </li>
            </ul>
          </div>
        </div>

        {featured && (
          <aside className="hero-news">
            <div className="hero-news-kicker pf-band">{t("home.newsKicker", { date: featured.date })}</div>
            <h2>{featured.title}</h2>
            <p>{featured.summary}</p>
            <button className="hero-news-more" onClick={() => setScreen("dashboard")}>
              {t("home.newsMore")}
              <Icon name="arrowRight" size={13} />
            </button>
          </aside>
        )}

        <footer className="hero-bar">
          <button className="hero-account" onClick={() => setScreen("accounts")}>
            {activeAccount ? (
              <>
                <SkinFace hue={activeAccount.skinHue} skin={activeAccount.skinUrl} size={22} />
                <span>
                  <strong>{activeAccount.username}</strong>
                  <em>{t(`accounts.source.${activeAccount.source}`)}</em>
                </span>
              </>
            ) : (
              <span>
                <strong>{t("dock.needsAccount")}</strong>
              </span>
            )}
          </button>
          <span className="hero-meta num">
            {t("home.footMeta", { version: APP_VERSION, mc: target })}
          </span>
        </footer>
      </section>

      <div className="screen-pad" id="dash">
        <HomeScreen />
      </div>
    </div>
  );
}
