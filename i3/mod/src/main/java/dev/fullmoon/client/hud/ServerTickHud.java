package dev.fullmoon.client.hud;

import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;

/**
 * The server's tick rate and the time one tick takes, for players who want the second figure;
 * the tick rate alone already rides on the FPS chip.
 */
public final class ServerTickHud extends BaseHudElement {
    private static final List<Part> SAMPLE = List.of(new Part("20", "tps"), new Part("14", "ms"));
    private static final List<Part> SILENT = List.of(new Part("—", "tps"));

    private List<Part> live = SILENT;
    private long liveTps = Long.MIN_VALUE;
    private long liveMs;
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

    private List<Part> parts(Minecraft client, boolean isEditor) {
        if (isEditor) {
            return SAMPLE;
        }
        Optional<BridgeState.Metrics> metrics = FullmoonChannel.metrics(System.currentTimeMillis());
        if (metrics.isEmpty()) {
            return SILENT;
        }
        long tps = Math.round(metrics.get().ticksPerSecond());
        long ms = Math.round(metrics.get().tickMilliseconds());
        if (tps != liveTps || ms != liveMs) {
            liveTps = tps;
            liveMs = ms;
            live = List.of(new Part(Long.toString(tps), "tps"), new Part(Long.toString(ms), "ms"));
        }
        return live;
    }
}
