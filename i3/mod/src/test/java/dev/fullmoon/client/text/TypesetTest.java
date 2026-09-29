package dev.fullmoon.client.text;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.fullmoon.client.design.Tokens;

/**
 * The vertical metrics, which are the ones the game gets wrong for us: every font provider hangs
 * off one 9 px line box, so a role's size says nothing about where its ink lands relative to the
 * y it was drawn at. Nothing here touches a glyph — {@code capTop}, {@code capHeight} and
 * {@code centred} are arithmetic over a role, and a rule that has to stand beside a wordmark is
 * placed from them before there is a font to measure.
 */
final class TypesetTest {
    /** An arbitrary draw origin. Every claim below is relative to it, none depends on it. */
    private static final int ORIGIN = 24;

    @Test
    void everyRoleSitsOnOneBaseline() {
        int baseline = Typeset.capTop(Tokens.Type.DISPLAY, ORIGIN)
            + Typeset.capHeight(Tokens.Type.DISPLAY);
        for (Map.Entry<String, Tokens.Type.Role> role : Tokens.Type.ROLL) {
            assertEquals(baseline,
                Typeset.capTop(role.getValue(), ORIGIN) + Typeset.capHeight(role.getValue()),
                role.getKey() + " sits on the same baseline as every other provider");
        }
    }

    @Test
    void theBaselineDoesNotMoveWithTheFace() {
        // 7 px below the origin, for a 22 px face and an 8 px one alike. A tick sized against the
        // face's own box instead of this is the bug this file exists to keep fixed.
        assertEquals(ORIGIN + 7,
            Typeset.capTop(Tokens.Type.MICRO, ORIGIN) + Typeset.capHeight(Tokens.Type.MICRO),
            "the line box is 9 px whatever the provider is");
    }

    @Test
    void aLargeFaceDrawsItsCapsAboveTheOriginItWasHanded() {
        assertTrue(Typeset.capTop(Tokens.Type.DISPLAY, ORIGIN) < ORIGIN,
            "a 22 px face on a 9 px line box has nowhere else to put them");
        assertTrue(Typeset.capTop(Tokens.Type.MICRO, ORIGIN) >= ORIGIN,
            "an 8 px face fits, so it does not");
    }

    @Test
    void aTallerRoleHasATallerCapBand() {
        assertTrue(Typeset.capHeight(Tokens.Type.DISPLAY) > Typeset.capHeight(Tokens.Type.ROW),
            "display over title");
        assertTrue(Typeset.capHeight(Tokens.Type.ROW) > Typeset.capHeight(Tokens.Type.MICRO),
            "title over label");
    }

    @Test
    void aCapBandLeavesRoomBelowTheBaselineForTheRest() {
        for (Map.Entry<String, Tokens.Type.Role> role : Tokens.Type.ROLL) {
            Tokens.Type.Role value = role.getValue();
            assertTrue(Typeset.capHeight(value) < value.px(),
                role.getKey() + " keeps a descender");
            assertTrue(Typeset.capHeight(value) > value.px() / 2,
                role.getKey() + " keeps most of the face above the baseline");
        }
    }

    @Test
    void centringPutsTheInkInTheMiddleAndNotTheLineBox() {
        // One model read two ways: centred() centres the whole ink band, cap band plus the quarter
        // below the baseline, so the two agree to within the rounding they each do.
        int h = 20;
        int origin = Typeset.centred(Tokens.Type.BODY, 0, h);
        int capTop = Typeset.capTop(Tokens.Type.BODY, origin);
        int inkBottom = capTop + Typeset.capHeight(Tokens.Type.BODY) + Tokens.Type.BODY.px() / 4;
        assertTrue(Math.abs((capTop + inkBottom) / 2 - h / 2) <= 1,
            "ink " + capTop + ".." + inkBottom + " is not centred in a band " + h + " tall");
    }
}
