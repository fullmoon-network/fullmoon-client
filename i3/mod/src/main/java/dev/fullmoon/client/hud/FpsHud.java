package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;

/** Client framerate and frame time chip. */
public final class FpsHud extends BaseHudElement {

    public FpsHud() {
        super("fps", "FPS", "성능", true, Anchor.TOP_LEFT, 16, 82);
    }

    @Override
    public int measureWidth(Minecraft client) {
        String text = formatText(client, false);
        return PADDING_H * 2 + Typeset.width(Tokens.Type.MICRO, "FPS") + Tokens.Space.SNUG
            + Typeset.width(Tokens.Type.STRONG, text);
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        String text = formatText(client, isEditor);
        drawChip(painter, bounds, "FPS", text, 0);
    }

    private String formatText(Minecraft client, boolean isEditor) {
        if (isEditor) {
            return "144 fps · 6.9 ms";
        }
        int fps = client.getFps();
        double ms = fps > 0 ? (1000.0 / fps) : 0.0;
        return String.format("%d fps · %.1f ms", fps, ms);
    }
}
