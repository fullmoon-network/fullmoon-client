package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Network latency ping chip with status dot and connection readout. */
public final class PingHud extends BaseHudElement {

    public PingHud() {
        super("ping", "네트워크 핑", "네트워크", true, Anchor.TOP_RIGHT, 16, 56);
    }

    @Override
    public int measureWidth(Minecraft client) {
        String text = formatText(client, false);
        return PADDING_H * 2 + Tokens.Space.COZY + Typeset.width(Tokens.Type.STRONG, text);
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        String text = formatText(client, isEditor);
        int ping = isEditor ? 18 : getPing(client);
        int dotColor = ping <= 60 ? Tokens.Color.STATUS_LIVE
            : (ping <= 150 ? Tokens.Color.ACCENT : Tokens.Color.STATUS_DANGER);
        drawChip(painter, bounds, "", text, dotColor);
    }

    private int getPing(Minecraft client) {
        if (client.getConnection() != null && client.player != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        }
        return 0;
    }

    private String formatText(Minecraft client, boolean isEditor) {
        int ping = isEditor ? 18 : getPing(client);
        return ping + " ms · 0% loss";
    }
}
