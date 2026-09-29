package dev.fullmoon.client.hud;

import java.util.Locale;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;

/** Server tick rate (TPS) and frame millisecond time chip. */
public final class ServerTickHud extends BaseHudElement {
    private static final String MAXIMUM_VALUE = "20.0 · 1000.0 ms";

    public ServerTickHud() {
        super("tps", "서버 틱", "성능", false, Anchor.TOP_RIGHT, 16, 108);
    }

    @Override
    public int measureWidth(Minecraft client) {
        String text = formatText(client, false);
        return PADDING_H * 2 + Typeset.width(Tokens.Type.MICRO, "TPS") + Tokens.Space.SNUG
            + Math.max(
                Typeset.width(Tokens.Type.STRONG, text),
                Typeset.width(Tokens.Type.STRONG, MAXIMUM_VALUE));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        String text = formatText(client, isEditor);
        drawChip(painter, bounds, "TPS", text, 0);
    }

    private String formatText(Minecraft client, boolean isEditor) {
        if (isEditor) {
            return "20.0 · 14.2 ms";
        }
        return FullmoonChannel.metrics(System.currentTimeMillis())
            .map(metrics -> String.format(Locale.ROOT, "%.1f · %.1f ms",
                metrics.ticksPerSecond(), metrics.tickMilliseconds()))
            .orElse("—");
    }
}
