import { useEffect, useMemo, useRef, useState } from "react";
import { Icon } from "../components/Icon";
import { Badge, Button, ConfirmModal, IconButton, Modal, SkinFace } from "../components/ui";
import Skin3D from "../widgets/Skin3D";
import {
  addOfflineAccount,
  core,
  errText,
  LOCAL_TEST_USERNAME,
  openExternal,
} from "../core/client";
import type { Account, AuthStatus, DeviceCodePrompt } from "../core/bindings";
import { useStore } from "../state/store";
import { useT } from "../i18n";

/* ── device-code flow ── */

function DeviceCodeModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { syncAccounts, toast } = useStore();
  const { t } = useT();
  const [prompt, setPrompt] = useState<DeviceCodePrompt | null>(null);
  const [dots, setDots] = useState(1);
  const alive = useRef(false);

  useEffect(() => {
    if (!open) return;
    alive.current = true;
    setPrompt(null);
    let timer: ReturnType<typeof setInterval>;
    let dotTimer: ReturnType<typeof setInterval>;

    const fail = (e: unknown) => {
      clearInterval(timer);
      clearInterval(dotTimer);
      toast("error", errText(e));
      onClose();
    };

    (async () => {
      let p: DeviceCodePrompt;
      try {
        p = await core.auth_begin_device_code();
      } catch (e) {
        if (alive.current) fail(e);
        return;
      }
      if (!alive.current) return;
      setPrompt(p);
      dotTimer = setInterval(() => setDots((d) => (d % 3) + 1), 500);
      timer = setInterval(async () => {
        let status: AuthStatus;
        try {
          status = await core.auth_poll(p.session);
        } catch (e) {
          if (alive.current) fail(e);
          return;
        }
        if (!alive.current) return;
        if (status.state === "error") {
          fail(status.message);
        } else if (status.state === "done") {
          clearInterval(timer);
          clearInterval(dotTimer);
          await syncAccounts();
          toast("success", t("accounts.loggedIn", { name: status.account.username }));
          onClose();
        }
      }, 2000);
    })();

    return () => {
      alive.current = false;
      if (timer) clearInterval(timer);
      if (dotTimer) clearInterval(dotTimer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  return (
    <Modal open={open} onClose={onClose} title={t("accounts.deviceTitle")} width={420}>
      {prompt ? (
        <div className="device-flow">
          <div className="device-code mono">{prompt.userCode}</div>
          <p className="device-step">
            <a
              href={prompt.verificationUri}
              onClick={(e) => {
                e.preventDefault();
                void openExternal(prompt.verificationUri);
              }}
            >
              {prompt.verificationUri}
            </a>{" "}
            {t("accounts.deviceStep")}
          </p>
          <div className="device-waiting">
            <span className="spinner" />
            <span>
              {t("accounts.waiting")}
              {".".repeat(dots)}
            </span>
          </div>
          <Button
            variant="outline"
            icon="copy"
            size="sm"
            onClick={() => {
              void navigator.clipboard?.writeText(prompt.userCode);
              toast("info", t("common.copied"));
            }}
          >
            {t("common.copy")}
          </Button>
        </div>
      ) : (
        <div className="device-flow device-loading">
          <span className="spinner" />
        </div>
      )}
    </Modal>
  );
}

/* ── add-account chooser ── */

function AddAccountModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { importOfficial, syncAccounts, toast } = useStore();
  const { t } = useT();
  const [deviceOpen, setDeviceOpen] = useState(false);
  const [busyBrowser, setBusyBrowser] = useState(false);

  const [offlineName, setOfflineName] = useState("");
  const [busyOffline, setBusyOffline] = useState(false);

  const browserLogin = async () => {
    setBusyBrowser(true);
    try {
      const acc = await core.auth_login_authcode();
      await syncAccounts();
      toast("success", t("accounts.loggedIn", { name: acc.username }));
      onClose();
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusyBrowser(false);
    }
  };

  const addOffline = async () => {
    if (!offlineName.trim()) return;
    setBusyOffline(true);
    try {
      const acc = await addOfflineAccount(offlineName.trim());
      await syncAccounts();
      toast("success", t("accounts.loggedIn", { name: acc.username }));
      setOfflineName("");
      onClose();
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusyOffline(false);
    }
  };

  return (
    <>
      <Modal open={open && !deviceOpen} onClose={onClose} title={t("accounts.add")} width={440}>
        <div className="add-options">
          <button className="add-option pf-tile" onClick={() => setDeviceOpen(true)}>
            <span className="add-option-icon"><Icon name="zap" size={18} /></span>
            <span className="add-option-meta">
              <strong>{t("accounts.viaDevice")}</strong>
              <span>{t("accounts.viaDeviceDesc")}</span>
            </span>
            <Icon name="chevronRight" size={16} />
          </button>
          <button className="add-option pf-tile" onClick={() => void browserLogin()}>
            <span className="add-option-icon"><Icon name="globe" size={18} /></span>
            <span className="add-option-meta">
              <strong>{t("accounts.viaBrowser")}</strong>
              <span>{t("accounts.viaBrowserDesc")}</span>
            </span>
            {busyBrowser ? <span className="spinner" /> : <Icon name="chevronRight" size={16} />}
          </button>
          <button
            className="add-option pf-tile"
            onClick={() => {
              void importOfficial();
              onClose();
            }}
          >
            <span className="add-option-icon"><Icon name="download" size={18} /></span>
            <span className="add-option-meta">
              <strong>{t("accounts.importOfficial")}</strong>
              <span>{t("accounts.importDesc")}</span>
            </span>
            <Icon name="chevronRight" size={16} />
          </button>
        </div>

        {/* offline is a real mode, not a placeholder: singleplayer and LAN
            work, online servers do not, and the card says so */}
        <div className="add-offline">
          <label className="field-label">{t("accounts.offline")}</label>
          <div className="add-offline-row">
            <input
              className="input"
              value={offlineName}
              onChange={(e) => setOfflineName(e.target.value)}
              placeholder={t("accounts.offlinePlaceholder")}
              maxLength={16}
              onKeyDown={(e) => e.key === "Enter" && void addOffline()}
            />
            <Button
              variant="outline"
              icon="plus"
              loading={busyOffline}
              disabled={!offlineName.trim()}
              onClick={() => void addOffline()}
            >
              {t("accounts.add")}
            </Button>
          </div>
          <p className="set-hint">{t("accounts.offlineDesc")}</p>
        </div>
      </Modal>
      <DeviceCodeModal open={deviceOpen} onClose={() => { setDeviceOpen(false); onClose(); }} />
    </>
  );
}

/* ── the account the game will actually launch as ── */

function ActiveHero({ account, onAdd }: { account: Account; onAdd: () => void }) {
  const { cosmetics, loadout, refreshAccount, setScreen, toast } = useStore();
  const { t } = useT();
  const [busy, setBusy] = useState(false);

  const cape = useMemo(() => cosmetics.find((c) => c.id === loadout?.cape) ?? null, [cosmetics, loadout]);

  const refresh = async () => {
    setBusy(true);
    try {
      await refreshAccount(account.uuid);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="acc-hero pf-frame">
      <div className="acc-hero-stage">
        <Skin3D
          skin={account.skinUrl ?? "/skins/blackcow.png"}
          cape={cape?.capeUrl ?? null}
          /* fixed three-quarters, facing the viewer: this panel answers "who am
             I", and a spin that parks the figure edge-on half the time cannot */
          rotate={false}
          angle={-0.42}
          width={214}
          height={306}
          zoom={0.88}
        />
      </div>

      <div className="acc-hero-body">
        <span className="acc-hero-kicker">{t("accounts.heroKicker")}</span>
        <h2 className="acc-hero-name">{account.username}</h2>
        <div className="acc-hero-badges">
          <Badge tone={account.source === "microsoft" ? "accent" : account.source === "imported" ? "ok" : "dim"}>
            {t(`accounts.source.${account.source}`)}
          </Badge>
          {/* Mojang's own capes, which are a different thing from the ones this
              client draws — counting them next to an equipped Pinion cape only
              reads as a contradiction when the number is zero */}
          {account.capes.length > 0 && (
            <Badge tone="dim">{t("accounts.capeCount", { n: String(account.capes.length) })}</Badge>
          )}
        </div>

        <dl className="acc-hero-facts">
          <div>
            <dt>{t("accounts.uuid")}</dt>
            {/* full, not truncated: a server op typing /whitelist needs every
                character of it, and this is the only place the launcher shows it */}
            <dd className="mono acc-hero-uuid">
              {account.uuid}
              <IconButton
                icon="copy"
                label={t("common.copy")}
                onClick={() => {
                  void navigator.clipboard?.writeText(account.uuid);
                  toast("info", t("common.copied"));
                }}
              />
            </dd>
          </div>
          <div>
            <dt>{t("accounts.clientCape")}</dt>
            <dd>
              {cape ? (
                <button className="acc-hero-link" onClick={() => setScreen("cosmetics")}>
                  {cape.name}
                  <Icon name="chevronRight" size={13} />
                </button>
              ) : (
                <button className="acc-hero-link dim" onClick={() => setScreen("cosmetics")}>
                  {t("accounts.noCape")}
                  <Icon name="chevronRight" size={13} />
                </button>
              )}
            </dd>
          </div>
        </dl>

        {account.source === "offline" && (
          <p className="acc-hero-warn">
            <Icon name="info" size={13} />
            {t("accounts.offlineDesc")}
          </p>
        )}

        <div className="acc-hero-actions">
          {account.source !== "offline" && (
            <Button variant="soft" icon="refresh" loading={busy} onClick={() => void refresh()}>
              {t("accounts.refresh")}
            </Button>
          )}
          <Button variant="outline" icon="plus" onClick={onAdd}>
            {t("accounts.add")}
          </Button>
        </div>
      </div>
    </section>
  );
}

/* ── account card ── */

function AccountCard({ account }: { account: Account }) {
  const { activeAccount, selectAccount, removeAccount, refreshAccount } = useStore();
  const { t } = useT();
  const [confirmDel, setConfirmDel] = useState(false);
  const isActive = activeAccount?.uuid === account.uuid;

  return (
    <article className={`acc-card pf-tile ${isActive ? "is-chosen" : ""}`}>
      {isActive && <span className="acc-ribbon">{t("accounts.current")}</span>}
      <div className="acc-top">
        <div className="acc-face">
          <SkinFace hue={account.skinHue} skin={account.skinUrl} size={56} />
        </div>
        <div className="acc-meta">
          <strong>{account.username}</strong>
          <span className="acc-uuid mono">{account.uuid.slice(0, 13)}…</span>
          <Badge tone={account.source === "microsoft" ? "accent" : account.source === "imported" ? "ok" : "dim"}>
            {t(`accounts.source.${account.source}`)}
          </Badge>
        </div>
      </div>
      <div className="acc-actions">
        {!isActive && (
          <Button size="sm" variant="soft" onClick={() => void selectAccount(account.uuid)}>
            {t("accounts.switch")}
          </Button>
        )}
        <span className="inst-actions-spacer" />
        <IconButton icon="refresh" label={t("accounts.refresh")} onClick={() => void refreshAccount(account.uuid)} />
        <IconButton icon="trash" label={t("common.delete")} danger onClick={() => setConfirmDel(true)} />
      </div>
      <ConfirmModal
        open={confirmDel}
        onClose={() => setConfirmDel(false)}
        onConfirm={() => void removeAccount(account.uuid)}
        title={t("accounts.removeTitle")}
        body={t("accounts.removeConfirm")}
        confirmLabel={t("common.delete")}
      />
    </article>
  );
}

function LocalAccountStart({ onMicrosoft }: { onMicrosoft: () => void }) {
  const { syncAccounts, toast } = useStore();
  const { t } = useT();
  const [busy, setBusy] = useState(false);

  const createLocalTestAccount = async () => {
    setBusy(true);
    try {
      const account = await addOfflineAccount(LOCAL_TEST_USERNAME);
      await syncAccounts();
      toast("success", t("accounts.loggedIn", { name: account.username }));
    } catch (error) {
      toast("error", errText(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="acc-local-start pf-frame">
      <div className="acc-local-identity" aria-hidden="true">
        <SkinFace hue={45} size={78} />
        <span className="acc-local-name mono">{LOCAL_TEST_USERNAME}</span>
      </div>
      <div className="acc-local-copy">
        <span className="acc-hero-kicker">{t("accounts.localTestKicker")}</span>
        <h2>{t("accounts.localTestTitle")}</h2>
        <p>{t("accounts.localTestDesc")}</p>
        <div className="acc-local-scope">
          <span><Icon name="play" size={14} />{t("accounts.localTestClient")}</span>
          <span><Icon name="user" size={14} />{t("accounts.localTestSingleplayer")}</span>
          <span className="acc-local-blocked"><Icon name="server" size={14} />{t("accounts.localTestNoServer")}</span>
        </div>
        <div className="acc-local-actions">
          <Button
            variant="primary"
            icon="play"
            loading={busy}
            onClick={() => void createLocalTestAccount()}
          >
            {t("accounts.localTestAction")}
          </Button>
          <Button variant="outline" icon="plus" onClick={onMicrosoft}>
            {t("accounts.microsoftAction")}
          </Button>
        </div>
      </div>
    </section>
  );
}

export function AccountsScreen() {
  const { accounts, activeAccount } = useStore();
  const { t } = useT();
  const [addOpen, setAddOpen] = useState(false);

  /* The active account owns the top of the page; the grid below is the bench.
     Repeating the active one as a card too would make the page say the same
     thing twice. */
  const others = accounts.filter((a) => a.uuid !== activeAccount?.uuid);

  return (
    <div className="screen-pad acc-screen">
      {activeAccount ? (
        <ActiveHero account={activeAccount} onAdd={() => setAddOpen(true)} />
      ) : (
        <LocalAccountStart onMicrosoft={() => setAddOpen(true)} />
      )}

      {(others.length > 0 || activeAccount) && (
        <>
          <h3 className="pf-section-head">{t("accounts.others")}</h3>
          <div className="acc-grid stagger">
            {others.map((a) => (
              <AccountCard key={a.uuid} account={a} />
            ))}
            <button className="acc-new" onClick={() => setAddOpen(true)}>
              <span className="acc-new-icon">
                <Icon name="plus" size={20} />
              </span>
              <span>{t("accounts.add")}</span>
            </button>
          </div>
        </>
      )}

      <AddAccountModal open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  );
}
