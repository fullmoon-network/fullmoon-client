package dev.fullmoon.client.hud;

import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;

/** A single movable in-game HUD module. */
public interface HudElement {
    String id();

    String label();

    String category();

    boolean enabled();

    void setEnabled(boolean enabled);

    Anchor anchor();

    void setAnchor(Anchor anchor);

    int offsetX();

    void setOffsetX(int offsetX);

    int offsetY();

    void setOffsetY(int offsetY);

    float scale();

    void setScale(float scale);

    /** Measures the natural dimensions of this element in GUI pixels. */
    int measureWidth(Minecraft client);

    int measureHeight(Minecraft client);

    /**
     * The width as drawn with {@code isEditor}: the editor draws sample values ("124 · 64 · -320"),
     * which are wider than most live ones, and its outline and hit box have to be the chip it drew.
     */
    default int measureWidth(Minecraft client, boolean isEditor) {
        return measureWidth(client);
    }

    /** Computes the on-screen placement bounding box for the current screen geometry. */
    default Box computeBounds(int screenW, int screenH, Minecraft client) {
        return computeBounds(screenW, screenH, client, false);
    }

    /** The placement as {@link #draw} will fill it with {@code isEditor}. */
    default Box computeBounds(int screenW, int screenH, Minecraft client, boolean isEditor) {
        int w = measureWidth(client, isEditor);
        int h = measureHeight(client);
        return anchor().place(screenW, screenH, w, h, offsetX(), offsetY());
    }

    /** Draws the element solid and text. */
    void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor);
}
