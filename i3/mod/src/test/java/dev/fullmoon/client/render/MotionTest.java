package dev.fullmoon.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.design.Tokens;

import org.junit.jupiter.api.Test;

final class MotionTest {
    private static final float TOLERANCE = 1e-3f;

    @Test
    void progressIsClampedAndAZeroDurationIsDone() {
        assertEquals(0f, Motion.progress(-50, 140));
        assertEquals(0.5f, Motion.progress(70, 140));
        assertEquals(1f, Motion.progress(900, 140));
        assertEquals(1f, Motion.progress(0, 0));
        assertEquals(0f, Motion.progress(-1, 0));
    }

    @Test
    void curvesPinTheirEndpointsAndNeverLeaveTheUnitRange() {
        for (Tokens.Easing.Curve curve : new Tokens.Easing.Curve[] {
                Tokens.Easing.OUT, Tokens.Easing.IN, Tokens.Easing.IN_OUT}) {
            assertEquals(0f, Motion.ease(curve, 0f));
            assertEquals(1f, Motion.ease(curve, 1f));
            float previous = 0;
            for (int i = 1; i <= 100; i++) {
                float value = Motion.ease(curve, i / 100f);
                assertTrue(value >= previous - TOLERANCE, curve + " at " + i);
                assertTrue(value <= 1 + TOLERANCE, curve + " overshoots at " + i);
                previous = value;
            }
        }
    }

    @Test
    void outFrontLoadsAndInBackLoads() {
        assertTrue(Motion.ease(Tokens.Easing.OUT, 0.25f) > 0.6f);
        assertTrue(Motion.ease(Tokens.Easing.IN, 0.25f) < 0.05f);
        assertEquals(0.5f, Motion.ease(Tokens.Easing.IN_OUT, 0.5f), TOLERANCE);
    }

    @Test
    void aLinearCurveIsTheIdentity() {
        Tokens.Easing.Curve linear = new Tokens.Easing.Curve(0.25f, 0.25f, 0.75f, 0.75f);
        for (int i = 0; i <= 10; i++) {
            assertEquals(i / 10f, Motion.ease(linear, i / 10f), TOLERANCE);
        }
    }
}
