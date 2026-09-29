package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where the HUD editor's chrome goes: a 28-tall glass strip along the top with the editor's name,
 * the chosen element's anchor and switch and the way out — pulled in from both corners on a wide
 * window, because the HUD's own chips live in the corners and the editor exists to show them where
 * they are; a strip of the same height near the foot holding one chip per element and the reset,
 * centred; the hint bar under that; and the canvas — the HUD itself — between the two strips.
 */
public record HudEditorLayout(Box header, int dockY, int hintY) {
    static final int STRIP = Tokens.Size.HEADER;
    static final int CHIP = Tokens.Size.ACTION_ROW;
    static final int CHIP_GAP = Tokens.Space.BASE;
    static final int DOCK_PAD = Tokens.Space.LOOSE;
    /** The header keeps this much clear at either corner, on a window wide enough to afford it. */
    static final int CORNER = Tokens.Size.SIDEBAR;
    static final int WIDE = 600;

    public static HudEditorLayout fit(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int hintY = height - Tokens.Space.COZY - Tokens.Size.HINT;
        int dockY = hintY - Tokens.Space.COZY - STRIP;
        int corner = width >= WIDE ? CORNER : 0;
        return new HudEditorLayout(new Box(corner, 0, width - corner * 2, STRIP), dockY, hintY);
    }

    /**
     * The dock strip for chips {@code width} wide in all and a {@code tail} — the reset — after
     * them, centred in a window {@code screenW} wide.
     */
    public Box dock(int width, int tail, int screenW) {
        int w = Math.min(screenW, width + DOCK_PAD * 2 + (tail > 0 ? tail + DOCK_PAD : 0));
        return new Box((screenW - w) / 2, dockY, w, STRIP);
    }

    /** Where the tail sits: the dock's right end, its own padding in. */
    public static Box tail(Box dock, int width, int height) {
        return new Box(dock.right() - DOCK_PAD - width, dock.y() + (STRIP - height) / 2, width, height);
    }

    /** Chip {@code index} inside {@code dock}, given every chip's width. */
    public static Box chip(Box dock, int[] widths, int index) {
        int x = dock.x() + DOCK_PAD;
        for (int i = 0; i < index; i++) {
            x += widths[i] + CHIP_GAP;
        }
        return new Box(x, dock.y() + (STRIP - CHIP) / 2, widths[index], CHIP);
    }

    /** The width all the chips take, gaps included. */
    public static int chipsWidth(int[] widths) {
        int total = 0;
        for (int i = 0; i < widths.length; i++) {
            total += widths[i] + (i > 0 ? CHIP_GAP : 0);
        }
        return total;
    }

    /** What the editor is for: everything between the two strips. */
    public Box canvas(int width) {
        return Box.between(0, header.bottom(), width, dockY);
    }
}
