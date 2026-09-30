package dev.fullmoon.client.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

import org.junit.jupiter.api.Test;

/** The generated values the concept fixes: the glass, the gold, the eight roles, the rows. */
final class TokensTest {
    @Test
    void theGlassIsEightySixPercentOfTheVoid() {
        assertEquals(0xFF, Tokens.Color.SURFACE_VOID >>> 24);
        assertEquals(Tokens.Color.SURFACE_VOID & 0xFFFFFF, Tokens.Color.SURFACE_GLASS & 0xFFFFFF);
        assertEquals(0.86f, Rgb.alphaOf(Tokens.Color.SURFACE_GLASS), 0.003f);
        assertEquals(0.56f, Rgb.alphaOf(Tokens.Color.SURFACE_GLASS_HUD), 0.003f);
        assertEquals(0.05f, Rgb.alphaOf(Tokens.Color.SURFACE_RAISED), 0.003f, "hover is a five percent lift");
        assertEquals(0.10f, Rgb.alphaOf(Tokens.Color.ACCENT_WASH), 0.003f, "selection is a ten percent wash");
        assertEquals(Tokens.Color.ACCENT & 0xFFFFFF, Tokens.Color.ACCENT_WASH & 0xFFFFFF, "of the same gold");
    }

    @Test
    void theInksAreOpaqueAndTheGoldIsTheConceptsGold() {
        for (int ink : new int[] {Tokens.Color.INK_PRIMARY, Tokens.Color.INK_SECONDARY, Tokens.Color.INK_TERTIARY,
                Tokens.Color.INK_DISABLED, Tokens.Color.INK_ON_ACCENT, Tokens.Color.ACCENT, Tokens.Color.STATUS_DANGER,
                Tokens.Color.STATUS_LIVE, Tokens.Color.STATUS_WARN, Tokens.Color.STATUS_WIN, Tokens.Color.STATUS_ASH}) {
            assertEquals(0xFF, ink >>> 24);
        }
        assertEquals(0xE8C56C, Tokens.Color.ACCENT & 0xFFFFFF);
        assertEquals(0xF2EEE6, Tokens.Color.INK_PRIMARY & 0xFFFFFF);
        assertEquals(0xE0625C, Tokens.Color.STATUS_DANGER & 0xFFFFFF);
        assertEquals(0x6FCFA8, Tokens.Color.STATUS_LIVE & 0xFFFFFF);
    }

    @Test
    void eightRolesAndHangulNeverUnderNine() {
        assertEquals(8, Tokens.Type.ROLL.size());
        for (Map.Entry<String, Tokens.Type.Role> entry : Tokens.Type.ROLL) {
            Tokens.Type.Role role = entry.getValue();
            assertTrue(role.px() >= 9 || role.latinOnly(), entry.getKey() + " sets Hangul under 9 px");
            assertTrue(role.leading() > role.px(), entry.getKey() + " has leading");
        }
        assertEquals(28, Tokens.Type.MARK.px());
        assertEquals(20, Tokens.Type.DISPLAY.px());
        assertEquals(14, Tokens.Type.TITLE.px());
        assertEquals(14, Tokens.Type.FIGURE.px());
        assertEquals(11, Tokens.Type.ROW.px());
        assertEquals(9, Tokens.Type.BODY.px());
        assertEquals(9, Tokens.Type.STRONG.px());
        assertEquals(8, Tokens.Type.MICRO.px());
        assertTrue(Tokens.Type.MICRO.latinOnly());
        assertEquals(Tokens.Type.STRONG, Typeset.roleFor(Tokens.Type.MICRO, "이동"));
        assertEquals(Tokens.Type.MICRO, Typeset.roleFor(Tokens.Type.MICRO, "Esc"));
        assertEquals(Tokens.Type.BODY, Typeset.roleFor(Tokens.Type.BODY, "이동"));
    }

    @Test
    void aRoleIsBakedOncePerGuiScale() {
        assertEquals("fullmoon:body_x2", Typeset.fontId(Tokens.Type.BODY, 2));
        assertEquals("fullmoon:body_x3", Typeset.fontId(Tokens.Type.BODY, 3));
        assertEquals("fullmoon:body_x4", Typeset.fontId(Tokens.Type.BODY, 4));
        assertEquals("fullmoon:body_x2", Typeset.fontId(Tokens.Type.BODY, 1), "scale one shares the ×2 atlas");
        assertEquals("fullmoon:body_x4", Typeset.fontId(Tokens.Type.BODY, 6));
    }

    @Test
    void rowsAreTallerThanTheMockupsAndTheGlideIsCriticallyDamped() {
        assertTrue(Tokens.Size.ROW > Tokens.Size.ROW_MOCK);
        assertTrue(Tokens.Size.ROW_ONE > Tokens.Size.ROW_ONE_MOCK);
        assertEquals(1.0f, Tokens.Spring.GLIDE.dampingFraction(), 0f);
        assertTrue(Tokens.Spring.GLIDE.response() >= 0.12f && Tokens.Spring.GLIDE.response() <= 0.18f);
        assertTrue(Tokens.Duration.OPEN >= 140 && Tokens.Duration.OPEN <= 200);
        assertTrue(Tokens.Sound.VOLUME > 0 && Tokens.Sound.VOLUME <= 0.6f, "on by default at a modest level");
    }
}
