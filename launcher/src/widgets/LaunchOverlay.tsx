import { useEffect, useId, useMemo, useRef, useState } from "react";
import { Icon } from "../components/Icon";
import { Button } from "../components/ui";
import { Moon } from "../components/Moon";
import { useStore } from "../state/store";
import { launchProgress } from "../core/launchSteps";
import { useT } from "../i18n";
import type { LogLevel } from "../core/bindings";

const LEVEL_CLASS: Record<LogLevel, string> = {
  OURS: "lov-ours",
  ERROR: "lov-err",
  WARN: "lov-warn",
  INFO: "lov-info",
  DEBUG: "lov-debug",
};

/* The launch surface over the live game://log stream. Progress is the steps the log has proved,
   drawn as a moon that fills one step at a time — never a timer and never an estimate.
   The raw log stays one press away, folded, for the player who wants to watch it scroll. */
export function LaunchOverlay({ onHide }: { onHide: () => void }) {
  const { game, logs, instances, killGame } = useStore();
  const { t } = useT();
  const [showLog, setShowLog] = useState(false);
  const tailRef = useRef<HTMLDivElement>(null);
  const hideRef = useRef<HTMLButtonElement>(null);
  const logId = useId();

  const inst = instances.find((i) => i.id === game.instanceId) ?? null;
  const session = useMemo(() => logs.filter((l) => l.session === game.sessionId), [logs, game.sessionId]);
  const toServer = game.server !== null;
  const { steps, done } = useMemo(
    () => launchProgress(session.map((l) => l.line), toServer),
    [session, toServer],
  );
  const complete = done === steps.length;
  const headline = complete
    ? toServer
      ? t("launchov.joined", { server: game.server ?? "" })
      : t("launchov.running")
    : t("launchov.starting");

  useEffect(() => {
    hideRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onHide();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onHide]);

  useEffect(() => {
    if (showLog) tailRef.current?.scrollTo({ top: tailRef.current.scrollHeight });
  }, [session.length, showLog]);

  return (
    <div className="lov-backdrop">
      <div className="lov glass" role="dialog" aria-modal aria-label={headline}>
        <header className="lov-head">
          <div className="lov-title">
            <strong>{headline}</strong>
            <span className="num">
              {inst ? `${inst.name} · ${inst.versionId}` : "…"}
              {game.server ? ` → ${game.server}` : ""}
            </span>
          </div>
          <button className="iconbtn" title={t("launchov.hide")} aria-label={t("launchov.hide")} onClick={onHide}>
            <Icon name="x" size={16} />
          </button>
        </header>

        <div className="lov-body">
          <div
            className="lov-instrument"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={steps.length}
            aria-valuenow={done}
            aria-valuetext={t("launchov.progressLabel", { done, total: steps.length })}
          >
            <Moon r={44} lit={done / steps.length} waxing />
            <span className="lov-count num">{t("launchov.progress", { done, total: steps.length })}</span>
          </div>

          <ol className="lov-steps">
            {steps.map((s, i) => {
              const state = i < done ? "done" : i === done ? "now" : "todo";
              return (
                <li key={s} className={`lov-step is-${state}`}>
                  <span>{t(`launchov.steps.${s}`)}</span>
                  {state === "now" && <span className="lov-step-dots" aria-hidden>···</span>}
                </li>
              );
            })}
          </ol>
        </div>

        {session.length === 0 && <p className="lov-note">{t("launchov.waiting")}</p>}
        {complete && <p className="lov-note lov-note-live">{t("launchov.handoff")}</p>}

        <div className="lov-rule rule" />

        <footer className="lov-actions">
          <button
            className="lov-logtoggle"
            aria-expanded={showLog}
            aria-controls={logId}
            onClick={() => setShowLog((v) => !v)}
          >
            <Icon name={showLog ? "chevronDown" : "chevronRight"} size={14} />
            <span>{showLog ? t("launchov.logHide") : t("launchov.logShow")}</span>
            <em className="num">{session.length}</em>
          </button>
          <div className="lov-actions-right">
            <Button ref={hideRef} variant="soft" size="sm" onClick={onHide}>
              {t("launchov.hide")}
            </Button>
            <Button variant="danger" size="sm" icon="stop" onClick={() => void killGame()}>
              {t("launchov.kill")}
            </Button>
          </div>
        </footer>

        <div id={logId} className="lov-console mono" ref={tailRef} hidden={!showLog}>
          {session.length === 0 && <p className="lov-debug">{t("launchov.logEmpty")}</p>}
          {session.slice(-200).map((l) => (
            <p key={l.id} className={LEVEL_CLASS[l.level] ?? "lov-info"}>
              <em className="num">{l.ts}</em> {l.line}
            </p>
          ))}
        </div>
      </div>
    </div>
  );
}
