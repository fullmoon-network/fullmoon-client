package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;

/**
 * The three dots a control puts where its answer would be while a request is out.
 *
 * <p>Dimmed ink is what disabled already looks like, so a still frame has to have some other way
 * to tell a request in flight from a control that is off. Every control here says it the same way,
 * which is the whole reason this is one place and not three.
 *
 * <p>They do not move. Motion arrives in P2, with the reduced-motion setting that has to be able
 * to collapse it.
 */
public final class Dots {
    private static final int RADIUS = Tokens.Space.TIGHT;
    private static final int PITCH = Tokens.Space.BASE;

    private Dots() {}

    /** Centred on {@code (cx, cy)}. */
    public static void draw(Painter painter, float cx, float cy, int color) {
        for (int i = -1; i <= 1; i++) {
            painter.dot(cx + i * PITCH, cy, RADIUS, color);
        }
    }

    /** How much room a layout has to keep for a run of them. */
    public static int width() {
        return 2 * PITCH + 2 * RADIUS;
    }
}
