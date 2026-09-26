/* HudEditor — the launcher's half of the in-game HUD layout.
 *
 * The stage is the game's own frame at the size the client measures in — 640×360 GUI px — scaled
 * to fit the panel. So a drag here is the same arithmetic the mod's editor runs (core/hud.ts is
 * mirrored from its Java), and the file both write is one contract rather than two dialects.
 *
 * Two limits, stated because they are visible: element widths come from the browser measuring
 * Pretendard, while the client measures its own baked provider, so a chip out here can sit a pixel
 * or two off the width the game gives it — the anchor and the offset are exact, the box is close.
 * And there is no scale control: `scale` round-trips through the file and the mod's
 * `BaseHudElement`, but no renderer reads it yet, so a slider here would move nothing in game. */

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { core } from "../core/client";
import type { HudConfig, HudElementState } from "../core/bindings";
import { Icon } from "../components/Icon";
import { Button, Segmented, Toggle } from "../components/ui";
import { MoonDisc } from "../components/Palace";
import { moonAt } from "../core/moonPhase";
import { useT } from "../i18n";
import { useStore } from "../state/store";
import {
  ANCHORS,
  DEFAULT_GRID_SNAP,
  STAGE_H,
  STAGE_W,
  col,
  drag,
  orderIds,
  place,
  reanchor,
  row,
  sanitizeSnap,
  type Anchor,
  type Placement,
} from "../core/hud";

/* What each chip reads on the plane, verbatim from the element's own `formatText(client, true)` —
   the editor branch, which is what the mod itself draws while a layout is being arranged. */
const CHIPS: Record<string, { key?: string; value?: string; dot?: boolean; moon?: boolean; reserve?: string }> = {
  coords: { key: "XYZ", value: "124.5  64.0  -320.8 · N (180°)" },
  fps: { key: "FPS", value: "144 fps · 6.9 ms" },
  ping: { dot: true, value: "18 ms · 0% loss" },
  // ClockHud draws tonight's real moon before its key, 4 px in radius
  clock: { key: "TIME", moon: true },
  tps: { key: "TPS", value: "20.0 · 14.2 ms", reserve: "20.0 · 1000.0 ms" },
  effects: { key: "FX", value: "신속 II · 02:45" },
};

/** The two elements whose size the client fixes rather than measures. */
const FIXED: Record<string, { w: number; h: number }> = {
  keystrokes: { w: 76, h: 92 },
  armor: { w: 142, h: 20 },
};

const NUDGE: Record<string, { dx: number; dy: number }> = {
  ArrowLeft: { dx: -1, dy: 0 },
  ArrowRight: { dx: 1, dy: 0 },
  ArrowUp: { dx: 0, dy: -1 },
  ArrowDown: { dx: 0, dy: 1 },
};

const SNAP_STEPS = ["1", "2", "4", "8"] as const;

const clock = () =>
  new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false });

/** `ArmorHud`'s four demo readings, with the durability bar it draws under each. */
function Armor() {
  return (
    <span className="hud-armor">
      {[98, 84, 100, 72].map((pct, i) => (
        <span key={i} className="hud-armor-slot">
          <span className="hud-armor-pct">{pct}%</span>
          <span className="hud-armor-bar" style={{ width: Math.max(2, Math.trunc((34 - 4) * (pct / 100))) }} />
        </span>
      ))}
    </span>
  );
}

/* `KeystrokesHud`'s own grid, in its own px. Every cap is at rest: the mod's `*Down` flags are all
   `!isEditor && …`, so a pressed cap is something only the live HUD ever shows. */
function Keystrokes() {
  return (
    <span className="hud-keys">
      <span className="hud-cap" style={{ left: 26, top: 0 }}>W</span>
      <span className="hud-cap" style={{ left: 0, top: 26 }}>A</span>
      <span className="hud-cap" style={{ left: 26, top: 26 }}>S</span>
      <span className="hud-cap" style={{ left: 52, top: 26 }}>D</span>
      <span className="hud-mouse" style={{ left: 0, top: 52 }}>
        <span className="lbl">LMB</span>
        <span className="cps">10 CPS</span>
      </span>
      <span className="hud-mouse" style={{ left: 39, top: 52 }}>
        <span className="lbl">RMB</span>
        <span className="cps">0 CPS</span>
      </span>
      <span className="hud-space">
        <i />
      </span>
    </span>
  );
}

function Chip({ id, now }: { id: string; now: string }) {
  const spec = CHIPS[id];
  if (!spec) {
    // out of the file, back into the file — but its size is the client's own secret
    return (
      <span className="hud-chip unknown">
        <span className="hud-v">{id}</span>
      </span>
    );
  }
  const value = spec.value ?? now;
  const moon = spec.moon ? moonAt(Date.now()) : null;
  return (
    <span className="hud-chip">
      {spec.dot && <span className="hud-dot" />}
      {moon && (
        <svg className="hud-moon" width="8" height="8" viewBox="-4 -4 8 8" aria-hidden>
          <MoonDisc r={4} lit={moon.lit} waxing={moon.waxing} />
        </svg>
      )}
      {spec.key && <span className="hud-k">{spec.key}</span>}
      {spec.reserve ? (
        <span className="hud-v hud-v-reserve" data-wide={spec.reserve}>
          <span>{value}</span>
        </span>
      ) : (
        <span className="hud-v">{value}</span>
      )}
    </span>
  );
}

/** Which corner of its cell the picker's dot sits in, so the 3×3 reads as a picture of the screen. */
const EDGE = ["start", "center", "end"] as const;

export function HudEditor({ instanceId }: { instanceId: string }) {
  const { t } = useT();
  const { toast } = useStore();
  const [cfg, setCfg] = useState<HudConfig | null>(null);
  const [sel, setSel] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [sizes, setSizes] = useState<Record<string, { w: number; h: number }>>({});
  const [now, setNow] = useState(clock);
  const [holding, setHolding] = useState(false);

  const stageRef = useRef<HTMLDivElement>(null);
  const nodes = useRef(new Map<string, HTMLDivElement>());
  const held = useRef<{
    id: string;
    x: number;
    y: number;
    from: Placement;
    moved: boolean;
    latest: HudConfig | null;
  } | null>(null);

  useEffect(() => {
    let active = true;
    setCfg(null);
    setSel(null);
    void core.hud_get(instanceId)
      .then((next) => {
        if (active) setCfg(next);
      })
      .catch(() => {
        if (active) toast("error", t("settings.hudReadFailed"));
      });
    return () => {
      active = false;
    };
  }, [instanceId, t, toast]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const ro = new ResizeObserver(([entry]) => setZoom(entry.contentRect.width / STAGE_W));
    ro.observe(stage);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const id = window.setInterval(() => setNow(clock()), 20_000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    // a width measured before Pretendard arrives is the fallback face's
    void document.fonts.ready.then(() => setSizes({}));
  }, []);

  /* The client sizes an element by measuring its own text, so the stage has to measure too — and
     before paint, or the first frame places every right-anchored chip from a width of zero.
     `offsetWidth` ignores the plane's transform, so these are already GUI px. */
  useLayoutEffect(() => {
    const next: Record<string, { w: number; h: number }> = {};
    let changed = Object.keys(sizes).length !== nodes.current.size;
    for (const [id, node] of nodes.current) {
      next[id] = { w: node.offsetWidth, h: node.offsetHeight };
      if (sizes[id]?.w !== next[id].w || sizes[id]?.h !== next[id].h) changed = true;
    }
    if (changed) setSizes(next);
  });

  const write = (next: HudConfig) => {
    setCfg(next);
    void core.hud_set(instanceId, next).catch(() => toast("error", t("settings.hudWriteFailed")));
  };

  const patch = (id: string, p: Partial<HudElementState>, persist: boolean): HudConfig | null => {
    if (!cfg) return null;
    const next: HudConfig = {
      ...cfg,
      elements: { ...cfg.elements, [id]: { ...cfg.elements[id], ...p } },
    };
    if (persist) write(next);
    else setCfg(next);
    return next;
  };

  const sizeOf = (id: string) => FIXED[id] ?? sizes[id] ?? { w: 0, h: 20 };

  const onDown = (e: React.PointerEvent<HTMLDivElement>, id: string) => {
    if (!cfg) return;
    e.preventDefault();
    e.currentTarget.focus();
    const el = cfg.elements[id];
    held.current = {
      id,
      x: e.clientX,
      y: e.clientY,
      from: { anchor: el.anchor, offsetX: el.offsetX, offsetY: el.offsetY },
      moved: false,
      latest: null,
    };
    setSel(id);
    setHolding(true);
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onMove = (e: React.PointerEvent) => {
    const h = held.current;
    if (!h || !cfg) return;
    const size = sizeOf(h.id);
    // the pointer moves in launcher px; the client's arithmetic is in GUI px, hence the divide
    const latest = patch(
      h.id,
      drag({
        from: h.from,
        screenW: STAGE_W,
        screenH: STAGE_H,
        elementW: size.w,
        elementH: size.h,
        dx: (e.clientX - h.x) / zoom,
        dy: (e.clientY - h.y) / zoom,
        step: cfg.gridSnap,
      }),
      false,
    );
    held.current = { ...h, moved: true, latest };
  };

  const onUp = () => {
    const h = held.current;
    held.current = null;
    setHolding(false);
    if (h?.moved && h.latest) write(h.latest);
  };

  const onKey = (e: React.KeyboardEvent, id: string) => {
    const d = NUDGE[e.key];
    if (!d || !cfg) return;
    e.preventDefault();
    const el = cfg.elements[id];
    const step = sanitizeSnap(cfg.gridSnap);
    const size = sizeOf(id);
    patch(
      id,
      drag({
        from: el,
        screenW: STAGE_W,
        screenH: STAGE_H,
        elementW: size.w,
        elementH: size.h,
        dx: d.dx * step,
        dy: d.dy * step,
        step: cfg.gridSnap,
      }),
      true,
    );
  };

  const reset = async () => {
    try {
      setCfg(await core.hud_reset(instanceId));
      setSel(null);
    } catch {
      toast("error", t("settings.hudResetFailed"));
    }
  };

  /** An id from a newer client has no string of ours; show the id rather than a missing key. */
  const label = (id: string) => {
    const key = `settings.elements.${id}`;
    const s = t(key);
    return s === key ? id : s;
  };
  const anchorLabel = (a: Anchor) => t(`settings.anchors.${a}`);

  const ids = cfg ? orderIds(Object.keys(cfg.elements)) : [];
  const shown = cfg ? ids.filter((id) => cfg.elements[id].enabled) : [];
  const selected = sel && cfg?.elements[sel] ? { id: sel, ...cfg.elements[sel] } : null;
  const step = sanitizeSnap(cfg?.gridSnap ?? DEFAULT_GRID_SNAP);

  const bump = (key: "offsetX" | "offsetY", by: number) => {
    if (!selected) return;
    const v = Math.max(0, selected[key] + by);
    patch(selected.id, key === "offsetX" ? { offsetX: v } : { offsetY: v }, true);
  };

  return (
    <div className="hud-editor">
      <div>
        <div className="hud-stage" ref={stageRef}>
          <div
            className={`hud-plane ${holding ? "dragging" : ""}`}
            style={{ transform: `scale(${zoom})` }}
          >
            <span className="hud-zone tl" />
            <span className="hud-zone tr" />
            <span className="hud-zone bl" />
            <span className="hud-zone br" />

            {/* what an element has to share the frame with, at the vanilla HUD's own coordinates */}
            <div className="hud-vanilla">
              <span className="hud-hotbar">
                {Array.from({ length: 9 }, (_, i) => (
                  <i key={i} />
                ))}
              </span>
              <span className="hud-hearts">
                {Array.from({ length: 10 }, (_, i) => (
                  <i key={i} />
                ))}
              </span>
              <span className="hud-food">
                {Array.from({ length: 10 }, (_, i) => (
                  <i key={i} />
                ))}
              </span>
              <span className="hud-cross" />
            </div>

            {shown.map((id) => {
              const el = cfg!.elements[id];
              const size = sizeOf(id);
              const box = place(el.anchor, STAGE_W, STAGE_H, size.w, size.h, el.offsetX, el.offsetY);
              return (
                <div
                  key={id}
                  ref={(node) => {
                    if (node) nodes.current.set(id, node);
                    else nodes.current.delete(id);
                  }}
                  className="hud-node"
                  style={{ left: box.x, top: box.y }}
                  tabIndex={0}
                  aria-label={label(id)}
                  onPointerDown={(e) => onDown(e, id)}
                  onPointerMove={onMove}
                  onPointerUp={onUp}
                  onPointerCancel={onUp}
                  onKeyDown={(e) => onKey(e, id)}
                  onFocus={() => setSel(id)}
                >
                  {id === "keystrokes" ? <Keystrokes /> : id === "armor" ? <Armor /> : <Chip id={id} now={now} />}
                  {sel === id && (
                    <>
                      <i className="hud-tick tl" />
                      <i className="hud-tick tr" />
                      <i className="hud-tick bl" />
                      <i className="hud-tick br" />
                      {/* the mod hides its badge rather than draw it off the top of the screen */}
                      {box.y - 17 >= 0 && (
                        <span className="hud-badge">
                          {anchorLabel(el.anchor)} · ({el.offsetX}, {el.offsetY})
                        </span>
                      )}
                    </>
                  )}
                </div>
              );
            })}

            {cfg && shown.length === 0 && <span className="hud-plane-empty">{t("settings.hudEmpty")}</span>}
          </div>
        </div>

        <div className="hud-foot">
          <span className="hud-dims">
            {STAGE_W} × {STAGE_H} GUI px · {zoom.toFixed(2)}×
          </span>
          <span className="hud-snap">
            {t("settings.hudSnap")}
            <Segmented<string>
              options={SNAP_STEPS.map((s) => ({ value: s as string, label: `${s}px` }))}
              value={String(step)}
              onChange={(v) => cfg && write({ ...cfg, gridSnap: Number(v) })}
            />
          </span>
        </div>

        <p className="hud-note">
          <Icon name="info" size={12} />
          {t("settings.hudNote")}
        </p>
      </div>

      <div className="hud-rail">
        <ul className="hud-list">
          {ids.map((id) => {
            const el = cfg!.elements[id];
            return (
              <li key={id} className={`hud-row ${sel === id ? "active" : ""} ${el.enabled ? "on" : ""}`}>
                <button
                  className="hud-row-pick"
                  aria-pressed={sel === id}
                  onClick={() => setSel(id)}
                  onKeyDown={(e) => onKey(e, id)}
                >
                  <span className="hud-row-dot" />
                  <span className="hud-row-name">{label(id)}</span>
                  <span className="hud-row-at">
                    {el.offsetX},{el.offsetY}
                  </span>
                </button>
                <Toggle checked={el.enabled} label={label(id)} onChange={(v) => patch(id, { enabled: v }, true)} />
              </li>
            );
          })}
        </ul>

        {selected ? (
          <div className="hud-inspector">
            <div className="hud-inspector-head">
              <strong>{label(selected.id)}</strong>
              <span>{anchorLabel(selected.anchor)}</span>
            </div>
            <div className="hud-anchor">
              {ANCHORS.map((a) => (
                <button
                  key={a}
                  className={a === selected.anchor ? "active" : ""}
                  title={anchorLabel(a)}
                  aria-label={anchorLabel(a)}
                  onClick={() => patch(selected.id, reanchor(selected, a), true)}
                >
                  <i style={{ justifySelf: EDGE[col(a)], alignSelf: EDGE[row(a)] }} />
                </button>
              ))}
            </div>
            {([["X", "offsetX"], ["Y", "offsetY"]] as const).map(([axis, key]) => (
              <div className="hud-nudge" key={key}>
                <span className="hud-nudge-axis">{axis}</span>
                <button
                  className="hud-step"
                  disabled={selected[key] <= 0}
                  aria-label={`${axis} −${step}`}
                  onClick={() => bump(key, -step)}
                >
                  <Icon name="minus" size={12} />
                </button>
                <span className="hud-nudge-val">{selected[key]}</span>
                <button className="hud-step" aria-label={`${axis} +${step}`} onClick={() => bump(key, step)}>
                  <Icon name="plus" size={12} />
                </button>
              </div>
            ))}
            <p className="hud-hint">{t("settings.hudHint")}</p>
          </div>
        ) : (
          <p className="hud-hint">{t("settings.hudPick")}</p>
        )}

        <Button variant="outline" size="sm" icon="refresh" onClick={() => void reset()}>
          {t("settings.resetLayout")}
        </Button>
      </div>
    </div>
  );
}
