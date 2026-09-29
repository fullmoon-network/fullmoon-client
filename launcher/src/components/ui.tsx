/* ui.tsx — the kit. Every control in the shell is built from
   these primitives so the whole skin speaks one language. */

import {
  forwardRef,
  useEffect,
  useRef,
  type ButtonHTMLAttributes,
  type CSSProperties,
  type ReactNode,
} from "react";
import { Icon, type IconName } from "./Icon";
import { useT } from "../i18n";

/* ── Key: a keycap ── */

export function Key({ children }: { children: ReactNode }) {
  return <kbd className="key">{children}</kbd>;
}

/* ── Button ── */

type BtnVariant = "primary" | "soft" | "ghost" | "outline" | "danger";

export const Button = forwardRef<
  HTMLButtonElement,
  {
    variant?: BtnVariant;
    size?: "sm" | "md" | "lg";
    icon?: IconName;
    loading?: boolean;
  } & ButtonHTMLAttributes<HTMLButtonElement>
>(function Button({ variant = "soft", size = "md", icon, loading = false, children, className = "", ...rest }, ref) {
  return (
    <button
      ref={ref}
      {...rest}
      className={`btn btn-${variant} btn-${size} ${className}`}
      disabled={rest.disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading ? <span className="spinner" /> : icon ? <Icon name={icon} size={size === "sm" ? 14 : 16} /> : null}
      {children && <span>{children}</span>}
    </button>
  );
});

/* ── IconButton ── */

export function IconButton({
  icon,
  label,
  danger = false,
  ...rest
}: { icon: IconName; label: string; danger?: boolean } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={`iconbtn ${danger ? "iconbtn-danger" : ""}`} title={label} aria-label={label} {...rest}>
      <Icon name={icon} size={16} />
    </button>
  );
}

/* ── Toggle ── */

export function Toggle({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={`toggle ${checked ? "on" : ""}`}
      disabled={disabled}
      onClick={() => onChange(!checked)}
    >
      <span className="toggle-knob" />
    </button>
  );
}

/* ── Segmented ── */

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: Array<{ value: T; label: ReactNode }>;
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div className="segmented">
      {options.map((o) => (
        <button
          key={o.value}
          className={`segmented-item ${o.value === value ? "active" : ""}`}
          aria-pressed={o.value === value}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/* ── Slider ── */

export function Slider({
  min,
  max,
  step = 1,
  value,
  onChange,
  marks,
  format,
}: {
  min: number;
  max: number;
  step?: number;
  value: number;
  onChange: (v: number) => void;
  marks?: number[];
  format?: (v: number) => string;
}) {
  const pct = ((value - min) / (max - min)) * 100;
  return (
    <div className="slider">
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        style={{ "--pct": `${pct}%` }}
      />
      {marks && (
        <div className="slider-marks">
          {marks.map((m) => {
            const p = ((m - min) / (max - min)) * 100;
            const tx = p <= 2 ? "0%" : p >= 98 ? "-100%" : "-50%";
            return (
              <span
                key={m}
                className={value >= m ? "mark hit" : "mark"}
                style={{ left: `${p}%`, transform: `translateX(${tx})` }}
              >
                {format ? format(m) : m}
              </span>
            );
          })}
        </div>
      )}
    </div>
  );
}

/* ── Badge ── */

export function Badge({
  tone = "dim",
  children,
  style,
}: {
  tone?: "accent" | "ok" | "warn" | "err" | "info" | "dim";
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <span className={`badge badge-${tone}`} style={style}>
      {children}
    </span>
  );
}

/* ── ProgressBar ── */

export function ProgressBar({ pct, indeterminate = false }: { pct: number; indeterminate?: boolean }) {
  return (
    <div className="pbar">
      {indeterminate ? (
        <span className="pbar-ind" />
      ) : (
        <span className="pbar-fill" style={{ width: `${Math.min(100, Math.max(0, pct))}%` }} />
      )}
    </div>
  );
}

/* ── Modal ── */

export function Modal({
  open,
  onClose,
  title,
  children,
  width = 440,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  width?: number;
}) {
  const { t } = useT();
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal glass" style={{ width }} ref={ref} role="dialog" aria-modal aria-label={typeof title === "string" ? title : undefined}>
        <div className="modal-head">
          <h3>{title}</h3>
          <IconButton icon="x" label={t("common.close")} onClick={onClose} />
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

/* ── ConfirmModal ── */

export function ConfirmModal({
  open,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  body: string;
  confirmLabel: string;
}) {
  const { t } = useT();
  return (
    <Modal open={open} onClose={onClose} title={title} width={400}>
      <p className="confirm-body">{body}</p>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>
          {t("common.cancel")}
        </Button>
        <Button
          variant="danger"
          onClick={() => {
            onConfirm();
            onClose();
          }}
        >
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}

/* ── SkinFace: the face off the account's own skin, or a drawn one when it has none ── */

export function SkinFace({ hue, skin = null, size = 32 }: { hue: number; skin?: string | null; size?: number }) {
  if (skin) {
    // a 64×64 skin: the face is the 8×8 at (8,8) and the hat layer the 8×8 at (40,8)
    return (
      <span
        className="skin-face"
        aria-hidden
        style={{
          width: size,
          height: size,
          backgroundImage: `url(${skin}), url(${skin})`,
          backgroundSize: "800% 800%, 800% 800%",
          backgroundPosition: "71.4286% 14.2857%, 14.2857% 14.2857%",
        }}
      />
    );
  }
  // A depiction of a Minecraft skin for an account that sent none; content, not interface colour.
  const hair = `hsl(${hue} 45% 34%)`; // TOKENS-OK
  const face = `hsl(26 42% 60%)`; // TOKENS-OK
  const shade = `hsl(26 40% 50%)`; // TOKENS-OK
  const iris = `hsl(${hue} 60% 45%)`; // TOKENS-OK
  return (
    <svg className="skin-face" width={size} height={size} viewBox="0 0 8 8" shapeRendering="crispEdges" aria-hidden>
      <rect x="0" y="0" width="8" height="8" fill={face} />
      <rect x="0" y="0" width="8" height="2" fill={hair} />
      <rect x="0" y="2" width="1" height="2" fill={hair} />
      <rect x="7" y="2" width="1" height="2" fill={hair} />
      <rect x="1" y="3" width="1" height="1" className="skin-face-eye" />
      <rect x="2" y="3" width="1" height="1" fill={iris} />
      <rect x="5" y="3" width="1" height="1" fill={iris} />
      <rect x="6" y="3" width="1" height="1" className="skin-face-eye" />
      <rect x="3" y="4" width="2" height="1" fill={shade} />
      <rect x="3" y="6" width="2" height="1" fill={shade} />
    </svg>
  );
}

/* ── Empty state ── */

export function Empty({
  icon,
  title,
  hint,
  action,
}: {
  icon: IconName;
  title: string;
  hint?: string;
  /** the one thing that ends the empty state, when there is exactly one */
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Icon name={icon} size={26} strokeWidth={1.4} />
      </div>
      <p className="empty-title">{title}</p>
      {hint && <p className="empty-hint">{hint}</p>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  );
}
