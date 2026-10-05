package dev.fullmoon.client.hud;

import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;

/** Frames a second, and the server's ticks a second beside it once a Fullmoon server reports them. */
public final class FpsHud extends BaseHudElement {
    private static final List<Part> SAMPLE = List.of(new Part("144", "fps"), new Part("20", "tps"));
    private static final long NO_TPS = Long.MIN_VALUE;

    /** The chip for the last readings, rebuilt only when the frame rate or the tick rate moves. */
    private List<Part> live = List.of();
    private int liveFps = Integer.MIN_VALUE;
    private long liveTps = NO_TPS;
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

    private List<Part> parts(Minecraft client, boolean isEditor) {
        if (isEditor) {
            return SAMPLE;
        }
        int fps = client.getFps();
        Optional<BridgeState.Metrics> metrics = FullmoonChannel.metrics(System.currentTimeMillis());
        long tps = metrics.isPresent() ? Math.round(metrics.get().ticksPerSecond()) : NO_TPS;
        if (fps != liveFps || tps != liveTps) {
            liveFps = fps;
            liveTps = tps;
            live = tps == NO_TPS
                ? List.of(new Part(Integer.toString(fps), "fps"))
                : List.of(new Part(Integer.toString(fps), "fps"), new Part(Long.toString(tps), "tps"));
        }
        return live;
    }
}
