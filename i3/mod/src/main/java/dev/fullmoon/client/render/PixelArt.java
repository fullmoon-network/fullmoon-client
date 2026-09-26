package dev.fullmoon.client.render;

import java.util.function.IntUnaryOperator;

/**
 * Draws a hand-drawn pixel mark: one character per cell, each mapped to a token colour, zero for
 * a clear cell. The marks are the game's own idiom — its item art is pixels — so the UI's small
 * pictures read as the game's craft rather than as vector chrome laid over it.
 */
public final class PixelArt {
    private static final float CELL_INSET = 0.92f;

    private PixelArt() {}

    /** Centres {@code art} on {@code cx},{@code cy} inside a {@code size} box. */
    public static void draw(Painter painter, String[] art, float cx, float cy, float size, IntUnaryOperator palette) {
        int cols = art[0].length();
        float cell = size * CELL_INSET / Math.max(art.length, cols);
        float left = cx - cols * cell / 2;
        float top = cy - art.length * cell / 2;
        // Each cell overdraws its neighbour by a hair so no seam shows between two cells of a
        // fractional size.
        float pad = cell + Math.max(0.02f, cell * 0.02f);
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length(); col++) {
                int color = palette.applyAsInt(art[row].charAt(col));
                if (color != 0) {
                    painter.fill(left + col * cell, top + row * cell, pad, pad, color);
                }
            }
        }
    }
}
