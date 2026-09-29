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
        assertEquals(new Box(132, 0, 640 - 264, 28), layout.header(), "the corners stay clear for the HUD's own chips");
        assertEquals(360 - 8 - Tokens.Size.HINT, layout.hintY(), "the hint bar sits eight up from the foot");
        assertEquals(layout.hintY() - 8 - 28, layout.dockY(), "the dock sits eight above the hints");
        assertEquals(Box.between(0, 28, 640, layout.dockY()), layout.canvas(640));
    }

    @Test
    void theDockIsCentredOnItsChips() {
        HudEditorLayout layout = HudEditorLayout.fit(640, 360);
        int[] widths = {40, 52, 36};
        assertEquals(40 + 6 + 52 + 6 + 36, HudEditorLayout.chipsWidth(widths));
        HudEditorLayout.Dock dock = layout.dock(widths, 0, 0, 640);
        assertEquals(140 + 24, dock.box().w(), "twelve of padding either side");
        assertEquals(640 / 2, dock.box().midX());
        assertEquals(new Box(dock.box().x(), layout.dockY(), 164, 28), dock.box(), "one row is the strip itself");
        assertEquals(new Box(dock.box().x() + 12, layout.dockY() + 4, 40, 20), dock.chips().get(0));
        assertEquals(dock.box().x() + 12 + 40 + 6, dock.chips().get(1).x());
        assertEquals(dock.box().right() - 12, dock.chips().get(2).right(), "the last chip ends at the padding");
    }

    @Test
    void theResetRidesAtTheDocksRightEnd() {
        HudEditorLayout layout = HudEditorLayout.fit(640, 360);
        int[] widths = {40, 52, 36};
        HudEditorLayout.Dock dock = layout.dock(widths, 70, 24, 640);
        assertEquals(140 + 24 + 70 + 12, dock.box().w(), "chips, the paddings, the tail and its own gap");
        assertEquals(dock.box().right() - 12, dock.tail().right());
        assertEquals(layout.dockY() + 2, dock.tail().y());
        assertTrue(dock.chips().get(2).right() < dock.tail().x());
    }

    @Test
    void eightChipsFoldIntoTwoRowsClearOfTheBottomCorners() {
        // The real dock at 640 x 360: eight elements and the reset are ~560 wide in one row, which
        // reached over the keystrokes in the bottom-right corner (M7 capture, both GUI scales).
        HudEditorLayout layout = HudEditorLayout.fit(640, 360);
        int[] widths = {70, 30, 58, 35, 55, 45, 60, 55};
        HudEditorLayout.Dock dock = layout.dock(widths, 78, 24, 640);
        Box box = dock.box();
        assertEquals(8 + 20 + 4 + 20, box.h(), "two rows of chips with the strip's margins");
        assertEquals(layout.dockY() + 28, box.bottom(), "the dock grows upwards; its foot stays on the hint gap");
        assertTrue(box.x() >= 132 && box.right() <= 640 - 132, "clear of both corners: " + box);
        assertTrue(Math.abs(box.midX() - 640 / 2) <= 1, "centred: " + box);
        assertEquals(box.x() + 12, dock.chips().get(4).x(), "the second row starts at the padding");
        assertEquals(dock.chips().get(0).y() + 24, dock.chips().get(4).y());
        assertEquals(dock.chips().get(3).y(), dock.chips().get(0).y(), "four to a row");
        assertEquals(box.right() - 12, dock.tail().right());
        assertEquals(dock.chips().get(7).y() - 2, dock.tail().y(), "the reset rides on the last row");
        assertTrue(dock.chips().get(7).right() < dock.tail().x());
        for (int i = 0; i < widths.length; i++) {
            assertTrue(box.x() <= dock.chips().get(i).x() && dock.chips().get(i).right() <= box.right(), "chip " + i);
        }
    }

    @Test
    void aWiderWindowKeepsTheDockOnOneRow() {
        HudEditorLayout layout = HudEditorLayout.fit(960, 540);
        int[] widths = {70, 30, 58, 35, 55, 45, 60, 55};
        HudEditorLayout.Dock dock = layout.dock(widths, 78, 24, 960);
        assertEquals(28, dock.box().h());
        assertEquals(dock.chips().get(0).y(), dock.chips().get(7).y());
    }

    @Test
    void aNarrowWindowGivesTheHeaderTheWholeWidthAndClampsTheDock() {
        HudEditorLayout layout = HudEditorLayout.fit(426, 240);
        assertEquals(new Box(0, 0, 426, 28), layout.header());
        HudEditorLayout.Dock dock = layout.dock(new int[] {900}, 0, 0, 426);
        assertEquals(0, dock.box().x());
        assertEquals(426, dock.box().w());
        assertTrue(layout.canvas(426).h() > 100, "the small viewport still has a canvas");
    }

    @Test
    void refusesAViewportItCannotDivide() {
        assertThrows(IllegalArgumentException.class, () -> HudEditorLayout.fit(640, 0));
    }
}
