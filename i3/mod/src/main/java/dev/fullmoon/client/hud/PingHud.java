package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** The round trip to the server, with a dot that says whether it is one to worry about. */
public final class PingHud extends BaseHudElement {
    private static final int GOOD_MS = 60;
    private static final int FAIR_MS = 150;

    public PingHud() {
        super("ping", "네트워크 핑", "네트워크", true, Anchor.TOP_RIGHT, Tokens.Space.COZY,
            Tokens.Space.COZY + Tokens.Size.HUD_CHIP + Tokens.Space.SNUG);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return chipWidth(dotWidth(), parts(client, false));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        int ping = isEditor ? 18 : ping(client);
        int dot = ping <= GOOD_MS ? Tokens.Color.STATUS_LIVE
            : ping <= FAIR_MS ? Tokens.Color.STATUS_WARN : Tokens.Color.STATUS_DANGER;
        drawChip(painter, bounds, dot, parts(client, isEditor));
    }

    private static List<Part> parts(Minecraft client, boolean isEditor) {
        return List.of(new Part(Integer.toString(isEditor ? 18 : ping(client)), "ms"));
    }

    private static int ping(Minecraft client) {
        if (client.getConnection() != null && client.player != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        }
        return 0;
    }
}
