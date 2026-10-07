package dev.fullmoon.client.text.fonts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuiScaleVariantsTest {
    @Test
    void aWindowDrawsThroughTheNearestBakedScale() {
        // 1 shares the x2 atlas, 5 and up the x4: the mapping Typeset has always used.
        int[] expected = {2, 2, 3, 4, 4, 4, 4};
        for (int guiScale = 1; guiScale <= 7; guiScale++) {
            assertEquals(expected[guiScale - 1], GuiScaleVariants.scaleOf(guiScale), "gui scale " + guiScale);
        }
    }

    @Test
    void indexAndScaleAgree() {
        for (int guiScale = 1; guiScale <= 8; guiScale++) {
            assertEquals(GuiScaleVariants.SCALES[GuiScaleVariants.indexOf(guiScale)],
                GuiScaleVariants.scaleOf(guiScale));
        }
    }

    @Test
    void onlyTheBakedOversamplesAreVariants() {
        assertTrue(GuiScaleVariants.isVariant(2f));
        assertTrue(GuiScaleVariants.isVariant(3f));
        assertTrue(GuiScaleVariants.isVariant(4f));
        assertFalse(GuiScaleVariants.isVariant(1f));
        assertFalse(GuiScaleVariants.isVariant(2.5f));
    }

    @Test
    void aProviderIsWantedOnlyAtItsOwnScale() {
        assertTrue(GuiScaleVariants.isWanted(3f, 3));
        assertFalse(GuiScaleVariants.isWanted(2f, 3));
        assertFalse(GuiScaleVariants.isWanted(4f, 3));
        // scale 1 draws through x2, scale 6 through x4
        assertTrue(GuiScaleVariants.isWanted(2f, 1));
        assertTrue(GuiScaleVariants.isWanted(4f, 6));
        assertFalse(GuiScaleVariants.isWanted(3f, 6));
    }

    @Test
    void aProviderThatIsNotAVariantIsAlwaysWanted() {
        for (int guiScale = 1; guiScale <= 6; guiScale++) {
            assertTrue(GuiScaleVariants.isWanted(1f, guiScale));
        }
    }
}
