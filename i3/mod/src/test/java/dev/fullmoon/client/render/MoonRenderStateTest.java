package dev.fullmoon.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MoonRenderStateTest {
    private static final float EPS = 1e-6f;

    @Test
    void newMoonPutsTheTerminatorOnTheLitLimb() {
        assertEquals(1.0f, MoonRenderState.terminator(0.0f), EPS);
    }

    @Test
    void fullMoonPutsTheTerminatorOnTheDarkLimb() {
        assertEquals(-1.0f, MoonRenderState.terminator(1.0f), EPS);
    }

    @Test
    void halfMoonTerminatorIsTheVerticalDiameter() {
        assertEquals(0.0f, MoonRenderState.terminator(0.5f), EPS);
    }

    /** The lit area past x = k·sqrt(r² − y²) is (1 − k)/2 of the disc; integrate it numerically. */
    @Test
    void litAreaMatchesTheRequestedFraction() {
        for (float lit : new float[] {0.08f, 0.3f, 0.5f, 0.75f}) {
            float k = MoonRenderState.terminator(lit);
            int steps = 20000;
            double area = 0;
            for (int i = 0; i < steps; i++) {
                double y = -1 + (i + 0.5) * 2.0 / steps;
                double h = Math.sqrt(1 - y * y);
                area += (h - k * h) * 2.0 / steps;
            }
            assertEquals(lit, area / Math.PI, 1e-3, "lit " + lit);
        }
    }

    @Test
    void outOfRangeFractionsClamp() {
        assertEquals(1.0f, MoonRenderState.terminator(-0.2f), EPS);
        assertEquals(-1.0f, MoonRenderState.terminator(1.4f), EPS);
    }
}
