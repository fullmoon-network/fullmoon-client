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

    /** Closer than this to a whole screen pixel counts as on it; a shape's edge cannot resolve finer. */
    private static final float PIXEL_TOLERANCE = 1.0e-3f;

    /** Centres {@code art} on {@code cx},{@code cy} inside a {@code size} box. */
    public static void draw(Painter painter, String[] art, float cx, float cy, float size, IntUnaryOperator palette) {
        int cols = art[0].length();
        float cell = size * CELL_INSET / Math.max(art.length, cols);
        float left = cx - cols * cell / 2;
        float top = cy - art.length * cell / 2;
        // Each cell overdraws its neighbour by a hair so no seam shows between two cells of a
        // fractional size.
        float pad = cell + Math.max(0.02f, cell * 0.02f);
        // Two cells of one colour share an antialiased seam under the overdraw, and a seam that falls
        // inside a screen pixel blends twice, so only a grid on whole pixels draws a run as one shape.
        boolean merge = painter.opacity() >= 1.0f && painter.onWholePixels(left, cell);
        for (int row = 0; row < art.length; row++) {
            int col = 0;
            while (col < art[row].length()) {
                int color = palette.applyAsInt(art[row].charAt(col));
                if (color == 0) {
                    col++;
                    continue;
                }
                int end = runEnd(art[row], col, palette, merge);
                painter.fill(left + col * cell, top + row * cell, (end - col - 1) * cell + pad, pad, color);
                col = end;
            }
        }
    }

    /** One past the last cell of the run of {@code palette}'s colour that starts at {@code from}. */
    static int runEnd(String row, int from, IntUnaryOperator palette, boolean merge) {
        int end = from + 1;
        if (merge) {
            int color = palette.applyAsInt(row.charAt(from));
            while (end < row.length() && palette.applyAsInt(row.charAt(end)) == color) {
                end++;
            }
        }
        return end;
    }

    /** Whether a grid that starts at {@code start} and steps by {@code step}, in screen pixels, stays on whole pixels. */
    static boolean onWholePixels(float start, float step) {
        return step > 0.0f && whole(start) && whole(step);
    }

    private static boolean whole(float value) {
        return Math.abs(value - Math.round(value)) <= PIXEL_TOLERANCE;
    }
}
