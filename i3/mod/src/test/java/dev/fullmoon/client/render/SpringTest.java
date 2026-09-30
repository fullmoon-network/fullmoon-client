package dev.fullmoon.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.design.Tokens;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The glide's arithmetic. A spring that lands somewhere different depending on how the frames
 * fell is a spring that looks different on every machine, so the closed form is checked against
 * itself at two frame rates; a critically damped one that overshoots would let the highlight bounce
 * past its row, which is the one motion the concept forbids.
 */
final class SpringTest {
    @AfterEach
    void restoreMotion() {
        Motion.reduce(false);
    }

    @Test
    void oneLongFrameLandsWhereSixteenShortOnesDo() {
        Spring coarse = new Spring(Tokens.Spring.GLIDE);
        Spring fine = new Spring(Tokens.Spring.GLIDE);
        coarse.snap(0);
        fine.snap(0);
        coarse.to(100);
        fine.to(100);
        coarse.step(0.160f);
        for (int i = 0; i < 16; i++) {
            fine.step(0.010f);
        }
        assertEquals(coarse.value(), fine.value(), 0.05f, "position is frame-rate independent");
        assertEquals(coarse.velocity(), fine.velocity(), 0.5f, "so is velocity");
    }

    @Test
    void aCriticallyDampedSpringNeverOvershoots() {
        Spring spring = new Spring(Tokens.Spring.GLIDE);
        spring.snap(0);
        spring.to(100);
        float last = 0;
        for (int i = 0; i < 200; i++) {
            spring.step(0.008f);
            assertTrue(spring.value() <= 100.0001f, "never past the target");
            assertTrue(spring.value() >= last - 0.0001f, "never backwards");
            last = spring.value();
        }
        assertTrue(spring.settled());
        assertEquals(100, spring.value(), 0.001f);
    }

    @Test
    void theGlideIsMostlyThereWithinItsResponse() {
        Spring spring = new Spring(Tokens.Spring.GLIDE);
        spring.snap(0);
        spring.to(100);
        spring.step(Tokens.Spring.GLIDE.response());
        assertTrue(spring.value() > 90, "over ninety percent of the way after one response, was " + spring.value());
    }

    @Test
    void retargetingMidFlightKeepsTheVelocity() {
        Spring spring = new Spring(Tokens.Spring.GLIDE);
        spring.snap(0);
        spring.to(100);
        spring.step(0.03f);
        float velocity = spring.velocity();
        float value = spring.value();
        spring.to(-50);
        assertEquals(value, spring.value(), 0f, "the value does not jump");
        assertEquals(velocity, spring.velocity(), 0f, "nor does the velocity");
        spring.step(0.5f);
        assertEquals(-50, spring.value(), 0.01f, "and the new target is still reached");
    }

    @Test
    void aFastKeyRepeatLandsItsLastTargetInState() {
        Spring spring = new Spring(Tokens.Spring.GLIDE);
        spring.snap(0);
        for (int row = 1; row <= 10; row++) {
            spring.to(row * 40);
            spring.step(0.016f);
        }
        assertEquals(400, spring.target(), 0f, "the target is always the last key");
        assertTrue(spring.value() < 400, "the drawn position lags by less than a response");
        spring.step(0.5f);
        assertEquals(400, spring.value(), 0.01f);
    }

    @Test
    void reducedMotionSnaps() {
        Motion.reduce(true);
        Spring spring = new Spring(Tokens.Spring.GLIDE);
        spring.snap(0);
        spring.to(100);
        assertEquals(100, spring.value(), 0f);
        assertTrue(spring.settled());
    }

    @Test
    void anUnderdampedSpringStillComesToRest() {
        Spring spring = new Spring(0.2f, 0.6f);
        spring.snap(0);
        spring.to(10);
        boolean overshot = false;
        for (int i = 0; i < 500; i++) {
            spring.step(0.005f);
            overshot |= spring.value() > 10.0f;
        }
        assertTrue(overshot, "under critical damping it overshoots, which is what damping fraction means");
        assertTrue(spring.settled());
        assertFalse(Float.isNaN(spring.value()));
    }

    @Test
    void aGlideSnapsToItsFirstBoxAndGlidesToTheNext() {
        Glide glide = new Glide(Tokens.Spring.GLIDE);
        assertFalse(glide.placed());
        glide.to(new dev.fullmoon.client.layout.Box(0, 40, 312, 40));
        assertTrue(glide.placed());
        assertEquals(40, glide.y(), 0f, "the first box is not glided to");
        glide.to(new dev.fullmoon.client.layout.Box(0, 120, 312, 40));
        assertEquals(40, glide.y(), 0f, "the second is, so nothing has moved before a frame");
        assertEquals(120, glide.target().y());
    }
}
