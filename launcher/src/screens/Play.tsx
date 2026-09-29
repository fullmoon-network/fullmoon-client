import { useMemo } from "react";
import { Moon } from "../components/Moon";
import { PlayLabel } from "../components/Dock";
import { useStore } from "../state/store";
import { usePlayAction } from "../state/playAction";
import { isRealCore } from "../core/client";
import { play as cue } from "../core/uiSounds";
import { daysToFull, daysToNextFull, isFull, moonAt, moonName } from "../core/moonPhase";
import { useT } from "../i18n";
import BRAND from "../brand";
import panorama from "../../../i3/mod/src/main/resources/assets/minecraft/textures/gui/title/background/panorama_0.png";

/** The direction particle a Korean name takes: 로 after a vowel or ㄹ, 으로 after any other final. */
function particleKey(name: string): "home.particleRo" | "home.particleEuro" {
  const code = name.charCodeAt(name.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return "home.particleRo";
  const final = code % 28;
  return final === 0 || final === 8 ? "home.particleRo" : "home.particleEuro";
}

/** The month and day of an ISO date, in the dictionary's form; anything else is shown as it came. */
function monthDay(iso: string, t: (key: string, vars: Record<string, string | number>) => string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  return t("home.monthDay", { m: Number(m[2]), d: Number(m[3]) });
}

/* The play screen is the game's title screen brought to the launcher, laid as mock/g-launcher.html
   lays it: the lobby's panorama under a veil, the wordmark and its line, tonight's real moon, the
   one gold block that is the way into the lobby with the lobby's live answer beside it, two quiet
   ways beside that, the latest news, and the three servers as cards under the hero. */
export function PlayScreen() {
  const { servers, serverStatus, news, instances, selectedInstance, launch, toast } = useStore();
  const { t } = useT();

  const lobby = servers[0] ?? null;
  const second = servers[1] ?? null;
  const { state, act, busy } = usePlayAction(lobby?.address ?? null);
  const launchOnly = usePlayAction(null);
  const moon = useMemo(() => moonAt(Date.now()), []);
  const full = isFull(moon);
  const headline = t(`moon.short.${moonName(moon)}`);
  const detail = full ? t("moon.next", { n: daysToNextFull(moon) }) : t("moon.until", { n: daysToFull(moon) });
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
    cue("confirm");
    void launch(inst.id, address);
  };

  const canQuickPlay = launchOnly.state.kind === "ready";

  return (
    <div className="play">
      <section className="hero" aria-label={t("nav.play")}>
        <div className="hero-sky" style={{ backgroundImage: `url(${panorama})` }} aria-hidden />
        <div className="hero-veil" aria-hidden />
        <Moon className="hero-moon" r={72} lit={moon.lit} waxing={moon.waxing} />

        <div className="hero-col">
          <h1 className="hero-mark">{BRAND.name}</h1>
          <p className="hero-tagline">{t("home.tagline")}</p>
          <div className="hero-tonight" role="img" aria-label={`${headline} · ${detail}`}>
            <Moon r={14} lit={moon.lit} waxing={moon.waxing} />
            <span>{headline} · {detail}</span>
          </div>

          <button
            className={`hero-play hero-play-${state.kind}`}
            onClick={() => {
              cue("confirm");
              act();
            }}
            aria-busy={busy}
            disabled={state.kind === "preparing"}
          >
            {state.kind === "ready" ? (
              <>
                <span className="hero-play-word">{t("home.joinLobby")}</span>
                {status && (
                  <span className="hero-play-status num" title={status}>
                    <i className={reachable ? "is-live" : ""} aria-hidden />
                    <span>{status}</span>
                  </span>
                )}
              </>
            ) : (
              <PlayLabel state={state} idleLabel={t("home.joinLobby")} />
            )}
          </button>

          <div className="hero-sub">
            {second && (
              <button className="hero-sub-btn" onClick={() => quickPlay(second.address)} disabled={!canQuickPlay}>
                {t("home.quickTo", { name: second.name, ro: t(particleKey(second.name)) })}
                <span>{second.motd.split(/\s[—·-]\s/)[0]}</span>
              </button>
            )}
            <button
              className="hero-sub-btn"
              onClick={() => {
                cue("confirm");
                launchOnly.act();
              }}
              disabled={!canQuickPlay}
            >
              {t("home.launchOnly")}
            </button>
          </div>
        </div>

        {featured && (
          <aside className="hero-news">
            <div className="hero-news-kicker">{t("home.newsKicker", { date: monthDay(featured.date, t) })}</div>
            <h2>{featured.title}</h2>
            <p>{featured.summary}</p>
          </aside>
        )}
      </section>

      <div className="play-grid">
        {servers.slice(0, 3).map((s) => {
          const st = serverStatus[s.address];
          const online = st?.online === true;
          const [meta] = s.motd.split(/\s[—·-]\s/);
          return (
            <article key={s.id} className="srv-card">
              <div className="srv-card-head">
                <i className={online ? "is-live" : ""} aria-hidden />
                {s.name}
              </div>
              <div className="srv-card-meta">{s.address} · {meta || s.motd}</div>
              <div className="srv-card-nums">
                <span>
                  <b className="num">{online && st ? st.players : "—"}</b>
                  <span>{t("home.onlineLabel")}</span>
                </span>
                {online && st && (
                  <span>
                    <b className="num">{st.pingMs}</b>
                    <span>ms</span>
                  </span>
                )}
              </div>
              <button className="srv-card-go" onClick={() => quickPlay(s.address)} disabled={!canQuickPlay || (st !== undefined && !online)}>
                {t("home.join")}
              </button>
            </article>
          );
        })}
      </div>
    </div>
  );
}
