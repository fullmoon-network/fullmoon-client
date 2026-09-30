package dev.fullmoon.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FadeTest {
    @AfterEach
    void fullMotionAgain() {
        Motion.reduce(false);
    }

    @Test
    void aSettledPaneIsSimplyThere() {
        Fade fade = Fade.settled();
        assertEquals(1.0f, fade.appearance());
        assertFalse(fade.closing());
        assertFalse(fade.gone());
    }

    @Test
    void anOpeningPaneStartsFromNothing() {
        Fade fade = Fade.opening();
        assertTrue(fade.appearance() < 1.0f, "the rise takes its time");
        assertFalse(fade.closing());
        assertFalse(fade.gone());
    }

    @Test
    void closingIsNotYetGoneUnderFullMotion() {
        Fade fade = Fade.settled();
        fade.close();
        assertTrue(fade.closing());
        assertFalse(fade.gone(), "the fall takes CLOSE ms");
        assertTrue(fade.appearance() <= 1.0f);
    }

    @Test
    void closeIsIdempotent() {
        Fade fade = Fade.settled();
        fade.close();
        fade.close();
        assertTrue(fade.closing());
    }

    @Test
    void reducedMotionSkipsBothWays() {
        Motion.reduce(true);
        Fade fade = Fade.opening();
        assertEquals(1.0f, fade.appearance(), "no rise");
        fade.close();
        assertTrue(fade.gone(), "no fall");
        assertEquals(0.0f, fade.appearance());
    }
}
