package dev.fullmoon.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Rgb;
import org.junit.jupiter.api.Test;

class VoiceTest {
    /** The glass the controls sit on, composited over the void: what a translucent ground is measured against. */
    private static final int GLASS = Rgb.over(Tokens.Color.SURFACE_GLASS, Tokens.Color.SURFACE_VOID);

    @Test
    void inkNeverLandsOnItsOwnGround() {
        for (Voice voice : Voice.values()) {
            for (State state : State.values()) {
                Chrome chrome = voice.chrome(state);
                assertNotEquals(Rgb.over(chrome.fill(), GLASS), chrome.ink(), voice + " " + state);
            }
        }
    }

    @Test
    void everyInkIsOpaqueAndEveryGroundIsATintOfTheGlassOrTheGold() {
        for (Voice voice : Voice.values()) {
            for (State state : State.values()) {
                Chrome chrome = voice.chrome(state);
                assertEquals(0xFF, chrome.ink() >>> 24, voice + " " + state + " ink alpha");
                assertTrue(chrome.fill() >>> 24 > 0, voice + " " + state + " has a ground");
                assertTrue(chrome.line() >>> 24 > 0, voice + " " + state + " has an edge");
            }
        }
    }

    @Test
    void hoverIsVisibleWithoutTheMouseMoving() {
        assertNotEquals(Voice.QUIET.chrome(State.REST).fill(), Voice.QUIET.chrome(State.HOVER).fill());
        assertNotEquals(Voice.LOUD.chrome(State.REST).line(), Voice.LOUD.chrome(State.HOVER).line(),
            "a loud control cannot repaint its fill on hover, so its edge has to carry it");
    }

    @Test
    void aPressSinksRatherThanLifts() {
        for (Voice voice : Voice.values()) {
            assertTrue(luminance(Rgb.over(voice.chrome(State.ACTIVE).fill(), GLASS))
                < luminance(Rgb.over(voice.chrome(State.REST).fill(), GLASS)), voice.name());
        }
        assertTrue(luminance(Rgb.over(Voice.QUIET.chrome(State.HOVER).fill(), GLASS))
            > luminance(Rgb.over(Voice.QUIET.chrome(State.REST).fill(), GLASS)), "and hover lifts");
    }

    @Test
    void focusVisibleLeavesTheGroundAloneBecauseTheRingCarriesIt() {
        for (Voice voice : Voice.values()) {
            assertEquals(voice.chrome(State.REST), voice.chrome(State.FOCUS_VISIBLE), voice.name());
        }
    }

    @Test
    void aRingIsNeverTheFillItSurrounds() {
        for (Voice voice : Voice.values()) {
            assertNotEquals(voice.ring(), voice.chrome(State.FOCUS_VISIBLE).fill(), voice.name());
        }
    }

    @Test
    void bothVoicesGoGreyTogether() {
        for (Voice voice : Voice.values()) {
            assertEquals(Tokens.Color.INK_DISABLED, voice.chrome(State.DISABLED).ink());
            assertEquals(Tokens.Color.SURFACE_CONTROL_DISABLED, voice.chrome(State.DISABLED).fill());
        }
    }

    @Test
    void bothVoicesRaiseTheSameAlarm() {
        for (Voice voice : Voice.values()) {
            assertEquals(Tokens.Color.STATUS_DANGER, voice.chrome(State.ERROR).line());
        }
    }

    @Test
    void loadingDropsTheInkWithoutGoingAsQuietAsDisabled() {
        for (Voice voice : Voice.values()) {
            assertNotEquals(voice.chrome(State.REST).ink(), voice.chrome(State.LOADING).ink());
            assertNotEquals(Tokens.Color.INK_DISABLED, voice.chrome(State.LOADING).ink());
        }
    }

    /** Relative luminance, so "darker" is a measurement and not an opinion about a hex code. */
    private static double luminance(int argb) {
        return 0.2126 * channel(argb >>> 16) + 0.7152 * channel(argb >>> 8) + 0.0722 * channel(argb);
    }

    private static double channel(int shifted) {
        double v = (shifted & 0xFF) / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
