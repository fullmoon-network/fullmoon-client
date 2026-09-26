import { Icon } from "./Icon";
import { useStore } from "../state/store";

export function Toasts() {
  const { toasts, dismissToast } = useStore();
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.kind}`} onClick={() => dismissToast(t.id)}>
          <span className="toast-icon">
            <Icon
              name={t.kind === "success" ? "check" : t.kind === "error" ? "x" : "info"}
              size={13}
              strokeWidth={2.2}
            />
          </span>
          <span className="toast-text">{t.text}</span>
        </div>
      ))}
    </div>
  );
}

function fmtSpeed(bytesPerSec: number): string {
  const mb = bytesPerSec / 1_048_576;
  return mb >= 1 ? `${mb.toFixed(1)} MB/s` : `${(bytesPerSec / 1024).toFixed(0)} KB/s`;
}

export function ProgressDock() {
  const { downloads } = useStore();
  if (downloads.length === 0) return null;
  return (
    <div className="pdock">
      {downloads.slice(-3).map((d) => (
        <div key={d.taskId} className="pdock-card pf-tile">
          <div className="pdock-row">
            <Icon name="download" size={13} />
            <span className="pdock-file mono" title={d.file}>
              {d.file}
            </span>
            <span className="pdock-speed num">{fmtSpeed(d.bytesPerSec)}</span>
          </div>
          <div className="pbar pbar-sm" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(d.pct)}>
            <span className="pbar-fill" style={{ width: `${d.pct}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}
