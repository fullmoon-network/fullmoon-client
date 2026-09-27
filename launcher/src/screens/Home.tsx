import { useMemo, useState } from "react";
import { Icon } from "../components/Icon";
import { Badge, Button, Empty, IconButton } from "../components/ui";
import { Marker } from "../components/Palace";
import { useStore } from "../state/store";
import { isRealCore } from "../core/client";
import { useT } from "../i18n";
import Skin3D from "../widgets/Skin3D";

const TAG_TONE: Record<string, "accent" | "ok" | "warn" | "err" | "info" | "dim"> = {
  update: "accent",
  event: "info",
  dev: "dim",
  cosmetic: "dim",
};

function fmtWhen(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

type Tab = "servers" | "wallet" | "news";

/* The dashboard: the servers with what they last answered, the wallet's ledger and the news, with
   the player, the wallet and the client summarised down the side. The play screen reads it under
   its hero; the dashboard screen shows it alone. */
export function HomeScreen() {
  const {
    news, wallet, walletTxs, servers, serverStatus, pingingServers, refreshServers, addServer,
    versions, modCatalog, cosmetics, loadout,
    activeAccount, instances, selectedInstance, launch, removeServer, toast, setScreen,
  } = useStore();
  const { t, lang } = useT();
  const [draft, setDraft] = useState({ name: "", address: "" });
  const [tab, setTab] = useState<Tab>("servers");

  const target = useMemo(() => versions.find((v) => v.isTarget), [versions]);
  const installedAny = instances.some((i) => i.installed);
  const memGb = selectedInstance ? (selectedInstance.memoryMb / 1024).toFixed(1).replace(/\.0$/, "") : null;
  const locale = lang === "ko" ? "ko-KR" : "en-US";
  const unit = wallet?.currency ?? t("home.walletUnit");

  const cape = useMemo(() => {
    const id = loadout?.cape;
    return id ? cosmetics.find((c) => c.id === id) ?? null : null;
  }, [loadout, cosmetics]);

  const flow = useMemo(() => {
    let income = 0;
    let spent = 0;
    for (const tx of walletTxs) {
      if (tx.delta >= 0) income += tx.delta;
      else spent += Math.abs(tx.delta);
    }
    return { income, spent };
  }, [walletTxs]);

  const quickPlay = (address: string) => {
    const inst =
      (selectedInstance?.installed ? selectedInstance : null) ?? instances.find((i) => i.installed);
    if (!inst) {
      toast("error", t("toast.launchFail", { reason: "no installed instance" }));
      return;
    }
    void launch(inst.id, address);
  };

  const tabs: Array<{ id: Tab; label: string; meta: string }> = [
    { id: "servers", label: t("home.serversTitle"), meta: String(servers.length) },
    {
      id: "wallet",
      label: t("home.walletTitle"),
      meta: wallet ? `${wallet.balance.toLocaleString(locale)}${unit}` : "—",
    },
    { id: "news", label: t("home.newsTitle"), meta: String(news.length) },
  ];

  return (
    <div className="dash stagger">
      <div className="dash-bar">
        <div className="tabrail" role="tablist" aria-label={t("nav.dashboard")}>
          {tabs.map((x) => (
            <button
              key={x.id}
              role="tab"
              id={`dash-tab-${x.id}`}
              aria-selected={tab === x.id}
              aria-controls={`dash-panel-${x.id}`}
              className={`tabrail-tab ${tab === x.id ? "is-current" : ""}`}
              onClick={() => setTab(x.id)}
            >
              {x.label}
              <em className="num">{x.meta}</em>
            </button>
          ))}
        </div>
        {tab === "servers" && (
          <button className="section-act" onClick={() => void refreshServers()} disabled={pingingServers}>
            <Icon name="refresh" size={14} className={pingingServers ? "spin" : undefined} />
            {pingingServers ? t("home.probing") : t("home.refreshServers")}
          </button>
        )}
      </div>

      <div className="dash-grid">
        <div className="dash-main" role="tabpanel" id={`dash-panel-${tab}`} aria-labelledby={`dash-tab-${tab}`}>
          {tab === "servers" && (
            <div className="srv-list">
              {servers.length === 0 && (
                <Empty icon="server" title={t("home.emptyServers")} hint={t("home.noServers")} />
              )}
              {servers.map((s) => {
                const st = serverStatus[s.address];
                const online = st?.online === true;
                const state = !st ? "checking" : online ? "live" : "idle";
                return (
                  <article key={s.id} className="srv pf-tile">
                    <div className="srv-main">
                      <div className="srv-head">
                        <h3>{s.name}</h3>
                        <span className={`srv-state is-${state}`}>
                          <i aria-hidden />
                          {state === "checking" ? t("home.srvChecking") : online ? t("home.srvOnline") : t("home.srvOffline")}
                        </span>
                      </div>
                      <span className="srv-addr mono">{s.address}</span>
                      <p className="srv-motd">{st?.motd || s.motd || "—"}</p>
                    </div>
                    <dl className="srv-nums">
                      <div>
                        <dt>{t("home.playersLabel")}</dt>
                        <dd className="num">
                          {online && st ? t("home.players", { n: st.players, max: st.maxPlayers }) : "—"}
                        </dd>
                      </div>
                      <div>
                        <dt>{t("home.pingLabel")}</dt>
                        <dd className="num">{online && st ? `${st.pingMs} ms` : "—"}</dd>
                      </div>
                    </dl>
                    <div className="srv-actions">
                      <Button
                        variant="soft"
                        size="sm"
                        icon="zap"
                        disabled={!installedAny || (st && !online)}
                        onClick={() => quickPlay(s.address)}
                      >
                        {t("home.join")}
                      </Button>
                      <IconButton icon="x" label={t("home.removeServer")} onClick={() => void removeServer(s.id)} />
                    </div>
                  </article>
                );
              })}

              <form
                className="srv-add"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!draft.address.trim()) return;
                  void addServer(draft.name || draft.address, draft.address);
                  setDraft({ name: "", address: "" });
                }}
              >
                <input
                  className="input"
                  value={draft.name}
                  onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                  placeholder={t("home.serverNamePlaceholder")}
                  aria-label={t("home.serverNamePlaceholder")}
                  spellCheck={false}
                />
                <input
                  className="input mono"
                  value={draft.address}
                  onChange={(e) => setDraft({ ...draft, address: e.target.value })}
                  placeholder={t("home.serverAddrPlaceholder")}
                  aria-label={t("home.serverAddrPlaceholder")}
                  spellCheck={false}
                />
                <Button variant="outline" icon="plus" type="submit" disabled={!draft.address.trim()}>
                  {t("home.addServer")}
                </Button>
              </form>
              {!isRealCore && <p className="dash-note">{t("home.srvBrowserNote")}</p>}
            </div>
          )}

          {tab === "wallet" &&
            (wallet ? (
              <section className="ledger">
                <div className="ledger-top">
                  <div className="ledger-balance">
                    <span className="overline">{t("home.balance")}</span>
                    <strong className="num">
                      {wallet.balance.toLocaleString(locale)}
                      <small>{unit}</small>
                    </strong>
                    <em className="num">{t("home.updated", { when: fmtWhen(wallet.updatedAt) })}</em>
                  </div>
                  <dl className="ledger-flow">
                    <div>
                      <dt>{t("home.income")}</dt>
                      <dd className="num">+{flow.income.toLocaleString(locale)}</dd>
                    </div>
                    <div>
                      <dt>{t("home.spent")}</dt>
                      <dd className="num">−{flow.spent.toLocaleString(locale)}</dd>
                    </div>
                  </dl>
                </div>
                <div className="pf-section-head">
                  {t("home.ledger")}
                  <em className="num">{t("home.ledgerCount", { n: walletTxs.length })}</em>
                </div>
                <ul className="ledger-rows">
                  {walletTxs.map((tx, idx) => (
                    <li key={tx.at + tx.reason + idx} className="ledger-row">
                      <span className="ledger-when num">{fmtWhen(tx.at)}</span>
                      <span className="ledger-what">
                        <strong>{tx.label}</strong>
                        <em className="mono">{tx.reason}</em>
                      </span>
                      <span className={`ledger-delta num ${tx.delta >= 0 ? "is-in" : "is-out"}`}>
                        {tx.delta >= 0 ? "+" : "−"}
                        {Math.abs(tx.delta).toLocaleString(locale)}
                      </span>
                      <span className="ledger-after num">
                        {tx.balanceAfter !== null
                          ? t("home.balanceAfter", { n: tx.balanceAfter.toLocaleString(locale) })
                          : ""}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ) : (
              <Empty icon="star" title={t("home.walletTitle")} hint={t("home.walletEmpty")} />
            ))}

          {tab === "news" && (
            <ul className="news-list">
              {news.map((n) => (
                <li key={n.id} className="news-row">
                  <Marker on={n.featured} />
                  <div className="news-meta">
                    <div className="news-top">
                      <Badge tone={TAG_TONE[n.tag] ?? "dim"}>{t(`news.tag.${n.tag}`)}</Badge>
                      {n.featured && <Badge tone="accent">{t("home.featured")}</Badge>}
                      <span className="news-date num">{n.date}</span>
                    </div>
                    <h4>{n.title}</h4>
                    <p>{n.summary}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <aside className="dash-side">
          <section className="side-card side-player pf-tile">
            <div className="side-head">
              <span className="pf-section-head">{t("home.playerTitle")}</span>
              <button className="section-act" onClick={() => setScreen("cosmetics")}>
                <Icon name="feather" size={13} />
                {t("home.changeLook")}
              </button>
            </div>
            <div className="side-stage">
              <Skin3D
                skin={activeAccount?.skinUrl ?? "/skins/blackcow.png"}
                cape={cape?.capeUrl ?? null}
                width={220}
                height={240}
                zoom={0.9}
              />
            </div>
            <div className="side-player-foot">
              <strong>{activeAccount?.username ?? t("dock.needsAccount")}</strong>
              <span>{cape ? t("home.capeOn", { name: cape.name }) : t("home.noCape")}</span>
            </div>
          </section>

          <section className="side-card pf-tile">
            <div className="side-head">
              <span className="pf-section-head">{t("home.balance")}</span>
              <button className="section-act" onClick={() => setTab("wallet")}>
                {t("home.walletStats")}
                <Icon name="arrowRight" size={13} />
              </button>
            </div>
            <p className="side-balance num">
              {wallet ? wallet.balance.toLocaleString(locale) : "—"}
              <small>{unit}</small>
            </p>
            {!wallet && <p className="dash-note">{t("home.walletEmpty")}</p>}
          </section>

          <section className="side-card pf-tile">
            <div className="side-head">
              <span className="pf-section-head">{t("home.instanceTitle")}</span>
              <button className="section-act" onClick={() => setScreen("settings")}>
                <Icon name="gear" size={13} />
                {t("nav.settings")}
              </button>
            </div>
            <dl className="side-facts">
              <div>
                <dt>{t("home.targetMc")}</dt>
                <dd className="num">
                  {target?.id ?? selectedInstance?.versionId ?? "—"} · {selectedInstance?.loader ?? "fabric"}
                </dd>
              </div>
              <div>
                <dt>{t("home.memory")}</dt>
                <dd className="num">{memGb ? `${memGb} GB` : "—"}</dd>
              </div>
              {modCatalog && (
                <div>
                  <dt>{t("home.bundled")}</dt>
                  <dd className="num">{t("home.metaMods", { n: modCatalog.mods.length })}</dd>
                </div>
              )}
              <div>
                <dt>{t("home.installState")}</dt>
                <dd className={selectedInstance?.installed ? "is-live" : "is-warn"}>
                  {selectedInstance?.installed ? t("home.installed") : t("home.needsInstall")}
                </dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
    </div>
  );
}
