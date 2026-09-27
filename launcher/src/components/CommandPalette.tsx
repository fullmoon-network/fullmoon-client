import { useEffect, useMemo, useRef, useState } from "react";
import { Icon, type IconName } from "./Icon";
import { Key, Marker } from "./Palace";
import { useStore, type Screen } from "../state/store";
import { useT } from "../i18n";

type Action = {
  id: string;
  icon: IconName;
  title: string;
  sub: string;
  group: "quick" | "screens" | "servers";
  run: () => void;
};

const NAV: Array<{ id: Screen; icon: IconName }> = [
  { id: "play", icon: "play" },
  { id: "dashboard", icon: "home" },
  { id: "mods", icon: "puzzle" },
  { id: "cosmetics", icon: "feather" },
  { id: "accounts", icon: "users" },
  { id: "settings", icon: "gear" },
];

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { setScreen, servers, instances, selectedInstance, launch, toast } = useStore();
  const { t } = useT();
  const [q, setQ] = useState("");
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const actions = useMemo<Action[]>(() => {
    const inst =
      (selectedInstance?.installed ? selectedInstance : null) ??
      instances.find((i) => i.installed) ??
      null;
    const out: Action[] = [];
    if (inst) {
      out.push({
        id: "launch",
        icon: "play",
        title: t("palette.launch"),
        sub: `${inst.name} · ${inst.versionId}`,
        group: "quick",
        run: () => void launch(inst.id),
      });
    }
    for (const n of NAV) {
      out.push({
        id: `nav-${n.id}`,
        icon: n.icon,
        title: t("palette.open", { name: t(`nav.${n.id}`) }),
        sub: t(`topbar.sub.${n.id}`),
        group: "screens",
        run: () => setScreen(n.id),
      });
    }
    for (const s of servers) {
      out.push({
        id: `srv-${s.id}`,
        icon: "server",
        title: t("palette.join", { name: s.name }),
        sub: s.address,
        group: "servers",
        run: () => {
          if (!inst) {
            toast("error", t("toast.launchFail", { reason: "no installed instance" }));
            return;
          }
          void launch(inst.id, s.address);
        },
      });
    }
    return out;
  }, [selectedInstance, instances, servers, launch, setScreen, toast, t]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return actions;
    return actions.filter(
      (a) => a.title.toLowerCase().includes(needle) || a.sub.toLowerCase().includes(needle),
    );
  }, [actions, q]);

  useEffect(() => {
    if (open) {
      setQ("");
      setCursor(0);
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  useEffect(() => setCursor(0), [q]);

  useEffect(() => {
    listRef.current
      ?.querySelector(".cmdk-item.cursor")
      ?.scrollIntoView({ block: "nearest" });
  }, [cursor]);

  if (!open) return null;

  const pick = (a: Action) => {
    onClose();
    a.run();
  };

  const groups: Array<{ key: Action["group"]; label: string }> = [
    { key: "quick", label: t("palette.quick") },
    { key: "screens", label: t("palette.screens") },
    { key: "servers", label: t("palette.servers") },
  ];

  return (
    <div
      className="modal-backdrop cmdk-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="cmdk pf-frame" role="dialog" aria-modal aria-label={t("palette.placeholder")}>
        <div className="cmdk-search">
          <Icon name="search" size={16} />
          <input
            ref={inputRef}
            value={q}
            placeholder={t("palette.placeholder")}
            aria-label={t("palette.placeholder")}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") onClose();
              if (e.key === "ArrowDown") {
                e.preventDefault();
                setCursor((c) => Math.min(c + 1, shown.length - 1));
              }
              if (e.key === "ArrowUp") {
                e.preventDefault();
                setCursor((c) => Math.max(c - 1, 0));
              }
              if (e.key === "Enter" && shown[cursor]) pick(shown[cursor]);
            }}
          />
          <Key>Esc</Key>
        </div>

        <div className="cmdk-list" ref={listRef}>
          {shown.length === 0 && <div className="cmdk-empty">{t("palette.empty")}</div>}
          {groups.map(({ key, label }) => {
            const items = shown.filter((a) => a.group === key);
            if (items.length === 0) return null;
            return (
              <div key={key} className="cmdk-section">
                <div className="cmdk-group pf-section-head">{label}</div>
                {items.map((a) => {
                  const idx = shown.indexOf(a);
                  return (
                    <button
                      key={a.id}
                      className={`cmdk-item ${idx === cursor ? "cursor" : ""}`}
                      onMouseEnter={() => setCursor(idx)}
                      onClick={() => pick(a)}
                    >
                      <Marker on={idx === cursor} tone="cinnabar" />
                      <span className="cmdk-ic">
                        <Icon name={a.icon} size={15} />
                      </span>
                      <span className="cmdk-text">
                        <strong>{a.title}</strong>
                        <em>{a.sub}</em>
                      </span>
                      {idx === cursor && <Key>↵</Key>}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </div>

        <footer className="cmdk-foot">
          <span>
            <Key>↑</Key>
            <Key>↓</Key> {t("palette.navigate")}
          </span>
          <span>
            <Key>↵</Key> {t("palette.select")}
          </span>
          <span>
            <Key>Esc</Key> {t("palette.close")}
          </span>
        </footer>
      </div>
    </div>
  );
}
