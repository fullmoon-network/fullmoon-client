package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Production armor durability reader with item slot status and color-coded health meters. */
public final class ArmorHud extends BaseHudElement {
    private static final int SLOT_COUNT = 4;
    private static final int SLOT_W = 34;
    private static final int GAP = Tokens.Space.TIGHT;
    private static final int TOTAL_W = SLOT_COUNT * SLOT_W + (SLOT_COUNT - 1) * GAP;

    private static final String[] LABELS = { "HLM", "CHS", "LEG", "BOT" };
    private static final int[] DEMO_PCT = { 98, 84, 100, 72 };
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorHud() {
        super("armor", "방어구 상태", "플레이어", false, Anchor.BOTTOM_LEFT, 16, 56);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return TOTAL_W;
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        int x = bounds.x();
        int y = bounds.y();

        for (int i = 0; i < SLOT_COUNT; i++) {
            int slotX = x + i * (SLOT_W + GAP);
            Box slotBox = new Box(slotX, y, SLOT_W, bounds.h());

            int pct = 100;
            String text = LABELS[i];

            if (isEditor || client.player == null) {
                pct = DEMO_PCT[i];
                text = pct + "%";
            } else {
                ItemStack stack = client.player.getItemBySlot(SLOTS[i]);
                if (!stack.isEmpty()) {
                    if (stack.isDamageableItem()) {
                        int maxDmg = stack.getMaxDamage();
                        int dmg = stack.getDamageValue();
                        pct = Math.max(0, (int) (((maxDmg - dmg) / (float) maxDmg) * 100));
                        text = pct + "%";
                    } else {
                        text = "100%";
                    }
                } else {
                    text = "—";
                    pct = 0;
                }
            }

            int barColor = pct > 60 ? Tokens.Color.STATUS_LIVE
                : (pct > 25 ? Tokens.Color.ACCENT : Tokens.Color.STATUS_DANGER);

            // Container fill and hairline border
            painter.fill(slotBox.x(), slotBox.y(), slotBox.w(), slotBox.h(),
                Tokens.Radius.SM, Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.78f));
            painter.border(slotBox.x(), slotBox.y(), slotBox.w(), slotBox.h(),
                Tokens.Radius.SM, Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);

            // Micro durability bar at bottom of slot
            if (pct > 0) {
                int barW = Math.max(2, (int) ((slotBox.w() - 4) * (pct / 100.0f)));
                painter.fill(slotBox.x() + 2, slotBox.y() + slotBox.h() - 3, barW, 2, 0, barColor);
            }

            int textW = Typeset.width(Tokens.Type.MICRO, text);
            int textX = slotBox.x() + (slotBox.w() - textW) / 2;
            int textY = slotBox.y() + (slotBox.h() - 3 - 9) / 2;
            int ink = pct > 0 ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY;

            Typeset.draw(painter, Tokens.Type.MICRO, text, textX, textY, ink);
        }
    }
}
