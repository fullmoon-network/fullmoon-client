package dev.fullmoon.client.render;

import dev.fullmoon.client.design.Tokens;

/** Time-to-progress arithmetic for token-driven motion. Pure, so it is tested without a frame. */
public final class Motion {
    private static final int NEWTON_STEPS = 8;
    private static final int BISECTION_STEPS = 24;
    private static final float EPSILON = 1e-5f;

    /** The player's reduce-motion choice, mirrored here so nothing under render/ reads a setting. */
    private static volatile boolean reduced;

    private Motion() {}

    public static boolean reduced() {
        return reduced;
    }

    public static void reduce(boolean value) {
        reduced = value;
    }

    /** Linear progress through {@code duration} ms, clamped to 0..1; a zero duration is done. */
    public static float progress(long elapsed, int duration) {
        if (duration <= 0) {
            return elapsed < 0 ? 0 : 1;
        }
        return Math.clamp((float) elapsed / duration, 0f, 1f);
    }

    /**
     * Eased progress through a token duration, or through the reduced crossfade when the player
     * asked for less motion: the one call a transition needs.
     */
    public static float eased(long elapsed, int duration, Tokens.Easing.Curve curve) {
        int span = reduced ? Math.min(duration, Tokens.Duration.REDUCED) : duration;
        return ease(curve, progress(elapsed, span));
    }

    /** {@code curve} evaluated at linear progress {@code t}: solve x for the curve parameter, return y. */
    public static float ease(Tokens.Easing.Curve curve, float t) {
        float x = Math.clamp(t, 0f, 1f);
        if (x == 0 || x == 1) {
            return x;
        }
        float s = x;
        for (int i = 0; i < NEWTON_STEPS; i++) {
            float error = bezier(curve.x1(), curve.x2(), s) - x;
            if (Math.abs(error) < EPSILON) {
                return bezier(curve.y1(), curve.y2(), s);
            }
            float slope = slope(curve.x1(), curve.x2(), s);
            if (Math.abs(slope) < EPSILON) {
                break;
            }
            s -= error / slope;
        }
        float low = 0;
        float high = 1;
        s = x;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            float value = bezier(curve.x1(), curve.x2(), s);
            if (Math.abs(value - x) < EPSILON) {
                break;
            }
            if (value < x) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) / 2;
        }
        return bezier(curve.y1(), curve.y2(), s);
    }

    /**
     * A disabled control's answer to a press: a horizontal nudge that rings out over
     * {@link Tokens.Duration#NUDGE} ms, two pixels at most, gone under reduced motion.
     */
    public static float nudge(long elapsed) {
        if (reduced || elapsed < 0 || elapsed >= Tokens.Duration.NUDGE) {
            return 0f;
        }
        float t = (float) elapsed / Tokens.Duration.NUDGE;
        return (float) (2.0 * Math.sin(t * Math.PI * 3.0) * (1.0 - t));
    }

    private static float bezier(float p1, float p2, float s) {
        float inverse = 1 - s;
        return 3 * inverse * inverse * s * p1 + 3 * inverse * s * s * p2 + s * s * s;
    }

    private static float slope(float p1, float p2, float s) {
        float inverse = 1 - s;
        return 3 * inverse * inverse * p1 + 6 * inverse * s * (p2 - p1) + 3 * s * s * (1 - p2);
    }
}
