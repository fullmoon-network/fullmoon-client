import { useCallback, useEffect, useState, type CSSProperties } from "react";
import { Icon } from "../components/Icon";
import { Badge, Button, ConfirmModal, Empty, IconButton, Toggle } from "../components/ui";
import { core, errText } from "../core/client";
import type { Clip, ClipSettings, ClipStatus } from "../core/bindings";
import { useStore } from "../state/store";
import { useT } from "../i18n";

function coverStyle(id: string): CSSProperties {
  let h = 0;
  for (const ch of id) h = (Math.imul(31, h) + ch.charCodeAt(0)) | 0;
  /* amber, moonlight, teal and sky only — no purple or neon in this skin */
  const hues = [22, 45, 152, 190];
  const hue = hues[Math.abs(h) % hues.length];
  return {
    backgroundImage: `linear-gradient(135deg, hsl(${hue} 24% 26%), hsl(${hue} 22% 14%))`,
  };
}

function duration(sec: number | null, none: string): string {
  if (sec === null || !Number.isFinite(sec)) return none;
  const whole = Math.max(0, Math.floor(sec));
  return `${String(Math.floor(whole / 60)).padStart(2, "0")}:${String(whole % 60).padStart(2, "0")}`;
}

function localTime(iso: string, lang: string): string {
  const date = new Date(iso);
  const locale = lang.startsWith("ko") ? "ko-KR" : "en-US";
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString(locale);
}

function ClipCard({
  clip,
  onOpen,
  onReveal,
  onDelete,
  t,
  lang,
}: {
  clip: Clip;
  onOpen: (id: string) => void;
  onReveal: (id: string) => void;
  onDelete: (id: string) => void;
  t: (path: string, vars?: Record<string, string | number>) => string;
  lang: string;
}) {
  const [imageFailed, setImageFailed] = useState(false);
  const [cover, setCover] = useState<string | null>(null);
  const showImage = cover !== null && !imageFailed;
  const time = duration(clip.durationSec, t("clips.durationNone"));

  /* the webview cannot read the file, so the core hands the thumbnail over as
     a data URL; a clip without one keeps the generated cover */
  useEffect(() => {
    let live = true;
    setCover(null);
    setImageFailed(false);
    if (clip.thumbnailPath === null) return;
    void core
      .clips_thumbnail(clip.id)
      .then((url) => {
        if (live) setCover(url);
      })
      .catch(() => {
        if (live) setImageFailed(true);
      });
    return () => {
      live = false;
    };
  }, [clip.id, clip.thumbnailPath]);

  return (
    <article className="clip-card card">
      <div className="clip-cover" style={coverStyle(clip.id)}>
        {showImage && (
          <img
            className="clip-cover-img"
            src={cover}
            alt=""
            onError={() => setImageFailed(true)}
          />
        )}
        {!showImage && <span className="clip-cover-glyph"><Icon name="play" size={22} /></span>}
        <span className="clip-cover-time num">{time}</span>
      </div>
      <div className="clip-card-body">
        <div className="clip-card-meta">
          <span>{localTime(clip.createdAt, lang)}</span>
          <span className="num">{t("clips.sizeFmt", { mb: (clip.sizeBytes / 1_048_576).toFixed(1) })}</span>
        </div>
        <div className="clip-card-actions">
          <Button size="sm" variant="ghost" icon="play" onClick={() => onOpen(clip.id)}>
            {t("clips.open")}
          </Button>
          <Button size="sm" variant="ghost" icon="folder" onClick={() => onReveal(clip.id)}>
            {t("clips.reveal")}
          </Button>
          <IconButton icon="trash" danger label={t("clips.delete")} onClick={() => onDelete(clip.id)} />
        </div>
      </div>
    </article>
  );
}

export function ClipsScreen() {
  const { toast } = useStore();
  const { t, lang } = useT();
  const [clips, setClips] = useState<Clip[]>([]);
  const [status, setStatus] = useState<ClipStatus | null>(null);
  const [settings, setSettings] = useState<ClipSettings | null>(null);
  const [draft, setDraft] = useState<ClipSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [replayBusy, setReplayBusy] = useState(false);
  const [setupOpen, setSetupOpen] = useState(false);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [list, state] = await Promise.all([core.clips_list(), core.clips_status()]);
      setClips(list);
      setStatus(state);
    } catch (e) {
      toast("error", errText(e));
    }
  }, [toast]);

  useEffect(() => {
    let live = true;
    setLoading(true);
    void (async () => {
      await refresh();
      try {
        const cfg = await core.clips_settings_get();
        if (live) {
          setSettings(cfg);
          setDraft(cfg);
        }
      } catch (e) {
        if (live) toast("error", errText(e));
      }
      if (live) setLoading(false);
    })();
    return () => {
      live = false;
    };
  }, [refresh, toast]);

  useEffect(() => {
    const offSaved = core.on("clips://saved", (clip) => {
      setClips((prev) => (prev.some((c) => c.id === clip.id) ? prev : [clip, ...prev]));
      void refresh();
    });
    const offError = core.on("clips://error", () => {
      void core.clips_status().then(setStatus).catch(() => {});
    });
    return () => {
      offSaved();
      offError();
    };
  }, [refresh]);

  const saveNow = async () => {
    setSaving(true);
    try {
      const clip = await core.clips_save();
      setClips((prev) => [clip, ...prev.filter((c) => c.id !== clip.id)]);
      toast("success", t("clips.savedToast"));
      const state = await core.clips_status().catch(() => null);
      if (state) setStatus(state);
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setSaving(false);
    }
  };

  const toggleReplay = async (start: boolean) => {
    setReplayBusy(true);
    try {
      setStatus(start ? await core.clips_replay_start() : await core.clips_replay_stop());
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setReplayBusy(false);
    }
  };

  const saveSettings = async () => {
    if (!draft) return;
    setSaving(true);
    try {
      const next = await core.clips_settings_set({
        enabled: draft.enabled,
        obsUrl: draft.obsUrl.trim(),
        obsPassword: draft.obsPassword,
        hotkey: draft.hotkey.trim() || "F8",
        ffmpegPath: draft.ffmpegPath?.trim() || null,
      });
      setSettings(next);
      setDraft(next);
      setStatus(await core.clips_status());
      toast("success", t("clips.settingsSaved"));
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setSaving(false);
    }
  };

  const patchDraft = (patch: Partial<ClipSettings>) => {
    setDraft((prev) => (prev ? { ...prev, ...patch } : prev));
  };

  const openOrReveal = async (id: string, reveal: boolean) => {
    try {
      if (reveal) await core.clips_reveal(id);
      else await core.clips_open(id);
    } catch (e) {
      toast("error", errText(e));
    }
  };

  const removeClip = async (id: string) => {
    setConfirmId(null);
    setDeleting(true);
    try {
      await core.clips_delete(id);
      setClips((prev) => prev.filter((c) => c.id !== id));
      toast("info", t("clips.deletedToast"));
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setDeleting(false);
    }
  };

  const obs = status?.obs ?? "disconnected";
  const obsTone = obs === "connected" ? "ok" : obs === "error" ? "err" : "dim";

  return (
    <div className="screen-pad clips-screen">
      <div className="clip-head">
        <div>
          <h1 className="clip-title">{t("clips.title")}</h1>
          <p className="clip-sub">{t("clips.subtitle")}</p>
        </div>
        <div className="clip-head-actions">
          <Button
            variant="primary"
            icon="zap"
            loading={saving || deleting}
            onClick={() => void saveNow()}
          >
            {t("clips.saveNow")}
          </Button>
          <Button
            variant="outline"
            icon={status?.replayBufferActive ? "stop" : "play"}
            loading={replayBusy}
            onClick={() => void toggleReplay(!status?.replayBufferActive)}
          >
            {t(status?.replayBufferActive ? "clips.replayStop" : "clips.replayStart")}
          </Button>
        </div>
      </div>

      <div className="clip-status" aria-live="polite">
        <div className="clip-status-item">
          <Icon name="signal" size={15} />
          <span className="clip-status-label">{t("clips.obsLabel")}</span>
          <Badge tone={obsTone}>{t(`clips.obs.${obs}`)}</Badge>
        </div>
        <div className="clip-status-item">
          <Icon name="refresh" size={15} />
          <span className="clip-status-label">{t("clips.replayLabel")}</span>
          <Badge tone={status?.replayBufferActive ? "ok" : "dim"}>
            {t(status?.replayBufferActive ? "clips.replayOn" : "clips.replayOff")}
          </Badge>
        </div>
        <div className="clip-status-item">
          <Icon name="command" size={15} />
          <span className="clip-status-label">{t("clips.hotkeyLabel")}</span>
          <Badge tone={status?.hotkeyRegistered ? "ok" : "dim"}>
            {status?.hotkeyRegistered
              ? t("clips.hotkeyOn", { key: settings?.hotkey ?? "F8" })
              : t("clips.hotkeyOff")}
          </Badge>
        </div>
        <IconButton icon="refresh" label={t("clips.refresh")} onClick={() => void refresh()} />
      </div>
      {status?.message && <p className="clip-status-message">{status.message}</p>}

      <section className={`clip-setup card ${setupOpen ? "open" : ""}`}>
        <button
          className="clip-setup-toggle"
          aria-expanded={setupOpen}
          onClick={() => {
            if (!setupOpen && settings) setDraft(settings);
            setSetupOpen((v) => !v);
          }}
        >
          <Icon name="gear" size={16} />
          <span>{t("clips.setupTitle")}</span>
          <Icon name={setupOpen ? "chevronDown" : "chevronRight"} size={14} />
        </button>
        {setupOpen && draft && (
          <div className="clip-setup-body">
            <div className="clip-setup-row">
              <div>
                <strong>{t("clips.enabled")}</strong>
                <p className="clip-setup-hint">{t("clips.enabledHint")}</p>
              </div>
              <Toggle checked={draft.enabled} onChange={(v) => patchDraft({ enabled: v })} />
            </div>
            <div className="clip-setup-grid">
              <label className="field">
                <span className="field-label">{t("clips.obsUrl")}</span>
                <input
                  className="input"
                  value={draft.obsUrl}
                  spellCheck={false}
                  onChange={(e) => patchDraft({ obsUrl: e.target.value })}
                />
              </label>
              <label className="field">
                <span className="field-label">{t("clips.obsPassword")}</span>
                <input
                  className="input"
                  type="password"
                  value={draft.obsPassword}
                  autoComplete="new-password"
                  onChange={(e) => patchDraft({ obsPassword: e.target.value })}
                />
              </label>
              <label className="field">
                <span className="field-label">{t("clips.hotkey")}</span>
                <input
                  className="input"
                  value={draft.hotkey}
                  placeholder={t("clips.hotkeyPlaceholder")}
                  spellCheck={false}
                  onChange={(e) => patchDraft({ hotkey: e.target.value })}
                />
              </label>
              <label className="field">
                <span className="field-label">{t("clips.ffmpegPath")}</span>
                <input
                  className="input"
                  value={draft.ffmpegPath ?? ""}
                  placeholder={t("clips.ffmpegPlaceholder")}
                  spellCheck={false}
                  onChange={(e) => patchDraft({ ffmpegPath: e.target.value })}
                />
              </label>
            </div>
            <p className="clip-setup-help">
              <Icon name="info" size={13} /> {t("clips.setupHelp")}
            </p>
            <p className="clip-setup-note">{t("clips.passwordNote")}</p>
            <div className="clip-setup-actions">
              <Button variant="soft" icon="check" loading={saving} onClick={() => void saveSettings()}>
                {t("clips.saveSettings")}
              </Button>
            </div>
          </div>
        )}
      </section>

      <div className="clip-gallery-head">
        <h2>{t("clips.galleryTitle")}</h2>
        <span className="clip-count num">{t("clips.countFmt", { n: clips.length })}</span>
      </div>

      {loading ? (
        <div className="clip-grid">
          {[0, 1, 2].map((i) => (
            <div key={i} className="clip-card card clip-skeleton" />
          ))}
        </div>
      ) : clips.length === 0 ? (
        <Empty
          icon="clips"
          title={t("clips.emptyTitle")}
          hint={t("clips.emptyHint")}
          action={
            <Button variant="primary" icon="zap" loading={saving} onClick={() => void saveNow()}>
              {t("clips.saveNow")}
            </Button>
          }
        />
      ) : (
        <div className="clip-grid stagger">
          {clips.map((clip) => (
            <ClipCard
              key={clip.id}
              clip={clip}
              t={t}
              lang={lang}
              onOpen={(id) => void openOrReveal(id, false)}
              onReveal={(id) => void openOrReveal(id, true)}
              onDelete={setConfirmId}
            />
          ))}
        </div>
      )}

      <ConfirmModal
        open={confirmId !== null}
        onClose={() => setConfirmId(null)}
        onConfirm={() => {
          if (confirmId) void removeClip(confirmId);
        }}
        title={t("clips.deleteTitle")}
        body={t("clips.deleteConfirm")}
        confirmLabel={t("clips.delete")}
      />
    </div>
  );
}
