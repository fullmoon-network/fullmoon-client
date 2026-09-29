package dev.fullmoon.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

/** Active player potion status effects with countdown timers and amplifier levels. */
public final class StatusEffectsHud extends BaseHudElement {

    public StatusEffectsHud() {
        super("effects", "상태 효과", "플레이어", false, Anchor.TOP_RIGHT, 16, 134);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return measureWidth(client, false);
    }

    @Override
    public int measureWidth(Minecraft client, boolean isEditor) {
        String text = formatText(client, isEditor);
        return chipWidth(0, java.util.List.of(new Part(text, "FX")));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        String text = formatText(client, isEditor);
        drawChip(painter, bounds, "FX", text, 0);
    }

    private String formatText(Minecraft client, boolean isEditor) {
        if (isEditor || client.player == null) {
            return "신속 II · 02:45";
        }

        Collection<MobEffectInstance> effects = client.player.getActiveEffects();
        if (effects.isEmpty()) {
            return "효과 없음";
        }

        MobEffectInstance first = effects.iterator().next();
        String name = first.getEffect().value().getDisplayName().getString();
        int amp = first.getAmplifier() + 1;
        String ampStr = amp > 1 ? (" " + toRoman(amp)) : "";
        int durationSec = first.getDuration() / 20;
        int min = durationSec / 60;
        int sec = durationSec % 60;

        return String.format("%s%s · %02d:%02d", name, ampStr, min, sec);
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}
