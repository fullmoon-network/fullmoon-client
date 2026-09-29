package dev.fullmoon.client.hud;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;

/** Frames a second, and the server's ticks a second beside it once a Fullmoon server reports them. */
public final class FpsHud extends BaseHudElement {
    public FpsHud() {
        super("fps", "FPS", "성능", true, Anchor.TOP_LEFT, Tokens.Space.COZY,
            Tokens.Space.COZY + Tokens.Size.HUD_CHIP + Tokens.Space.SNUG);
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
        List<Part> parts = new ArrayList<>();
        if (isEditor) {
            parts.add(new Part("144", "fps"));
            parts.add(new Part("20", "tps"));
            return parts;
        }
        parts.add(new Part(Integer.toString(client.getFps()), "fps"));
        FullmoonChannel.metrics(System.currentTimeMillis()).ifPresent(metrics ->
            parts.add(new Part(Long.toString(Math.round(metrics.ticksPerSecond())), "tps")));
        return parts;
    }
}
