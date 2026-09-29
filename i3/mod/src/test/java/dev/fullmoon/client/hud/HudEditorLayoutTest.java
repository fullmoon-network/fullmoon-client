package dev.fullmoon.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

import org.junit.jupiter.api.Test;

/** The editor's two strips and the hint bar leave the canvas between them, at either GUI size. */
final class HudEditorLayoutTest {
    @Test
    void theStripsAtTheDesignSize() {
        HudEditorLayout layout = HudEditorLayout.fit(640, 360);
        assertEquals(new Box(0, 0, 640, 28), layout.header());
        assertEquals(360 - 8 - Tokens.Size.HINT, layout.hintY(), "the hint bar sits eight up from the foot");
        assertEquals(layout.hintY() - 8 - 28, layout.dockY(), "the dock sits eight above the hints");
        assertEquals(Box.between(0, 28, 640, layout.dockY()), layout.canvas(640));
    }

    @Test
    void theDockIsCentredOnItsChips() {
        HudEditorLayout layout = HudEditorLayout.fit(640, 360);
        int[] widths = {40, 52, 36};
        int chips = HudEditorLayout.chipsWidth(widths);
        assertEquals(40 + 6 + 52 + 6 + 36, chips);
        Box dock = layout.dock(chips, 640);
        assertEquals(chips + 24, dock.w(), "twelve of padding either side");
        assertEquals(640 / 2, dock.midX());
        assertEquals(new Box(dock.x() + 12, layout.dockY() + 4, 40, 20), HudEditorLayout.chip(dock, widths, 0));
        assertEquals(dock.x() + 12 + 40 + 6, HudEditorLayout.chip(dock, widths, 1).x());
        assertEquals(dock.right() - 12, HudEditorLayout.chip(dock, widths, 2).right(), "the last chip ends at the padding");
    }

    @Test
    void aDockWiderThanTheWindowIsClampedToIt() {
        HudEditorLayout layout = HudEditorLayout.fit(426, 240);
        Box dock = layout.dock(900, 426);
        assertEquals(0, dock.x());
        assertEquals(426, dock.w());
        assertTrue(layout.canvas(426).h() > 100, "the small viewport still has a canvas");
    }

    @Test
    void refusesAViewportItCannotDivide() {
        assertThrows(IllegalArgumentException.class, () -> HudEditorLayout.fit(640, 0));
    }
}
