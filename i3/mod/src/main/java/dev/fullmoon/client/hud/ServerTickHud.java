package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;

/**
 * The server's tick rate and the time one tick takes, for players who want the second figure;
 * the tick rate alone already rides on the FPS chip.
 */
public final class ServerTickHud extends BaseHudElement {
    public ServerTickHud() {
        super("tps", "서버 틱", "성능", false, Anchor.TOP_RIGHT, Tokens.Space.COZY,
            Tokens.Space.COZY + (Tokens.Size.HUD_CHIP + Tokens.Space.SNUG) * 2);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return measureWidth(client, false);
    }

    @Override
    public int measureWidth(Minecraft client, boolean isEditor) {
        return chipWidth(0, parts(client, isEditor));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        drawChip(painter, bounds, 0, parts(client, isEditor));
    }

    private static List<Part> parts(Minecraft client, boolean isEditor) {
        if (isEditor) {
            return List.of(new Part("20", "tps"), new Part("14", "ms"));
        }
        return FullmoonChannel.metrics(System.currentTimeMillis())
            .map(metrics -> List.of(
                new Part(Long.toString(Math.round(metrics.ticksPerSecond())), "tps"),
                new Part(Long.toString(Math.round(metrics.tickMilliseconds())), "ms")))
            .orElse(List.of(new Part("—", "tps")));
    }
}
