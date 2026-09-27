import { useMemo, useState } from "react";
import { Icon } from "../components/Icon";
import { Empty, IconButton, Segmented } from "../components/ui";
import { Dancheong, Marker } from "../components/Palace";
import Skin3D from "../widgets/Skin3D";
import type { Cosmetic, CosmeticSlot } from "../core/bindings";
import { useStore } from "../state/store";
import { useT } from "../i18n";

/* What the mod can actually draw. A slot that reaches the game is equippable;
   the rest stay browsable and say so, because a loadout the game ignores is a
   promise the client does not keep. */
const RENDERS: Record<CosmeticSlot, boolean> = { cape: true, wings: false, trail: false };

const SLOT_ICON: Record<CosmeticSlot, "layers" | "feather" | "zap"> = {
  cape: "layers",
  wings: "feather",
  trail: "zap",
};

export function CosmeticsScreen() {
  const { activeAccount, cosmetics, loadout, equip, toast } = useStore();
  const { t } = useT();
  const [slotFilter, setSlotFilter] = useState<CosmeticSlot>("cape");
  const [walk, setWalk] = useState(false);

  const filtered = useMemo(() => cosmetics.filter((c) => c.slot === slotFilter), [cosmetics, slotFilter]);
  const equippedCape = useMemo(
    () => cosmetics.find((c) => c.id === loadout?.cape) ?? null,
    [cosmetics, loadout],
  );

  const equipItem = (item: Cosmetic) => {
    if (!RENDERS[item.slot]) {
      toast("info", t("cosmetics.notYetToast", { slot: t(`cosmetics.${item.slot}`) }));
      return;
    }
    const isEquipped = loadout?.[item.slot] === item.id;
    void equip(item.slot, isEquipped ? null : item.id);
    if (isEquipped) toast("info", t("cosmetics.unequippedToast", { name: item.name }));
  };

  if (!activeAccount) {
    return (
      <div className="screen-pad">
        <Empty icon="feather" title={t("accounts.add")} hint={t("cosmetics.perAccount")} />
      </div>
    );
  }

  const slots: CosmeticSlot[] = ["cape", "wings", "trail"];

  return (
    <div className="screen-pad">
      <div className="cos-layout">
        <section className="cos-side">
          {/* preview stage — the real skinview3d viewer, same one the HUD ships */}
          <div className="cos-stage pf-frame">
            <div className="cos-stage-band pf-band">
              <span>{activeAccount.username}</span>
              <Segmented
                options={[
                  { value: "idle", label: t("cosmetics.idle") },
                  { value: "walk", label: t("cosmetics.walk") },
                ]}
                value={walk ? "walk" : "idle"}
                onChange={(v) => setWalk(v === "walk")}
              />
            </div>
            <Dancheong />
            <div className="cos-figure">
              {/* three-quarters from behind: a cape stage that spins the cape
                  out of view half the time is a stage that shows nothing */}
              <Skin3D
                skin={activeAccount.skinUrl ?? "/skins/blackcow.png"}
                cape={equippedCape?.capeUrl ?? null}
                walk={walk}
                rotate={false}
                angle={Math.PI * 0.86}
                width={246}
                height={320}
                zoom={0.95}
              />
            </div>
            <div className="cos-stage-foot">
              <span>{t("cosmetics.dragHint")}</span>
              <span>{t("cosmetics.stageLabel")}</span>
            </div>
          </div>

          <h3 className="pf-section-head cos-col-title">{t("cosmetics.loadout")}</h3>
          <div className="cos-slots">
            {slots.map((slot) => {
              const item = cosmetics.find((c) => c.id === loadout?.[slot]) ?? null;
              return (
                <div key={slot} className={`cos-slot pf-tile ${slotFilter === slot ? "is-current" : ""}`}>
                  <button
                    className="cos-slot-pick"
                    aria-pressed={slotFilter === slot}
                    onClick={() => setSlotFilter(slot)}
                  >
                    <Marker on={slotFilter === slot} />
                    <span className="cos-slot-icon">
                      <Icon name={SLOT_ICON[slot]} size={16} />
                    </span>
                    <span className="cos-slot-meta">
                      <em>{t(`cosmetics.${slot}`)}</em>
                      <strong className={RENDERS[slot] ? "" : "cos-slot-soon"}>
                        {RENDERS[slot] ? (item ? item.name : t("cosmetics.empty")) : t("cosmetics.notYet")}
                      </strong>
                    </span>
                  </button>
                  {item && (
                    <IconButton icon="x" label={t("cosmetics.unequip")} onClick={() => void equip(slot, null)} />
                  )}
                </div>
              );
            })}
          </div>
        </section>

        <section className="cos-catalog">
          <div className="tabrail" role="tablist" aria-label={t("cosmetics.loadout")}>
            {slots.map((s) => (
              <button
                key={s}
                role="tab"
                aria-selected={slotFilter === s}
                className={`tabrail-tab ${slotFilter === s ? "is-current" : ""}`}
                onClick={() => setSlotFilter(s)}
              >
                {t(`cosmetics.${s}`)}
                <em className="num">{cosmetics.filter((c) => c.slot === s).length}</em>
              </button>
            ))}
          </div>
          {!RENDERS[slotFilter] && (
            <div className="notice notice-quiet">
              <Icon name="info" size={15} />
              <div>
                <strong>{t("cosmetics.notYetTitle", { slot: t(`cosmetics.${slotFilter}`) })}</strong>
                <span>{t("cosmetics.notYetHint")}</span>
              </div>
            </div>
          )}
          <div className="cos-grid stagger">
            {filtered.map((item) => {
              const equipped = loadout?.[item.slot] === item.id;
              const renders = RENDERS[item.slot];
              return (
                <button
                  key={item.id}
                  className={`cos-item pf-tile ${equipped ? "is-chosen" : ""} ${renders ? "" : "is-unbuilt"}`}
                  aria-pressed={renders ? equipped : undefined}
                  onClick={() => equipItem(item)}
                >
                  <span className="cos-swatch">
                    {item.capeUrl ? (
                      <span className="cos-cape" style={{ backgroundImage: `url(${item.capeUrl})` }} />
                    ) : (
                      <span className="cos-glyph">
                        <Icon name={item.slot === "wings" ? "feather" : "zap"} size={30} strokeWidth={1.3} />
                      </span>
                    )}
                  </span>
                  <span className="cos-item-meta">
                    <strong>{item.name}</strong>
                    <span className="cos-item-desc">{item.desc}</span>
                  </span>
                  <span className="cos-item-foot">
                    <span className={`cos-rarity cos-r-${item.rarity}`}>{t(`cosmetics.rarity.${item.rarity}`)}</span>
                    {!renders && <span className="cos-soon-label">{t("cosmetics.notYet")}</span>}
                    {equipped && renders && <span className="cos-equipped-label">{t("cosmetics.equipped")}</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      </div>
    </div>
  );
}
