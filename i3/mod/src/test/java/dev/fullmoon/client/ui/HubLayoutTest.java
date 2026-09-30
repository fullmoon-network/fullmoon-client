package dev.fullmoon.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

import org.junit.jupiter.api.Test;

/** The hub is the server menus' pane, divided into rail, search, list and detail. */
final class HubLayoutTest {
    private static final Box DESIGN = new Box(0, 0, 640, 360);

    @Test
    void theMenusPaneAtTheDesignSize() {
        HubLayout layout = HubLayout.fit(DESIGN);
        assertEquals(new Box(64, 22, 512, 316), layout.panel(), "the 512×316 pane, centred");
        assertEquals(new Box(64, 22, 512, 28), layout.header());
        assertEquals(new Box(64, 50, 512, 28), layout.tabs(), "the rail sits under the header");
        assertEquals(new Box(76, 86, 488, 24), layout.search(), "the field is inset twelve, eight under the rail");
        assertEquals(new Box(64, 118, 224, 220), layout.list(), "the list starts under the search strip");
        assertEquals(288, layout.divider());
        assertEquals(Box.between(300, 126, 564, 326), layout.detail(), "detail is inset twelve from the divider and the pane");
        assertEquals(338 + 8, layout.hintY(), "the hint bar sits in the margin under the pane");
        assertEquals(7, layout.rows(), "seven one-line rows, as the settings page wants");
    }

    @Test
    void aPageWithoutSearchStartsItsListUnderTheRail() {
        HubLayout layout = HubLayout.fit(DESIGN, false);
        assertEquals(Box.EMPTY, layout.search());
        assertEquals(50 + 28 + 8, layout.list().y());
        assertEquals(Box.between(76, 94, 564, 326), layout.body(), "the body spans the pane when there is no list");
    }

    @Test
    void theSmallViewportKeepsTheHintMarginAndLosesRows() {
        HubLayout layout = HubLayout.fit(new Box(0, 0, 426, 240));
        assertEquals(378, layout.panel().w(), "twenty-four clear on either side");
        assertEquals(240 - (Tokens.Size.HINT + 8) * 2, layout.panel().h(), "as tall as leaves the hint margin");
        assertEquals(layout.panel().bottom() + 8, layout.hintY());
        assertTrue(layout.hintY() + Tokens.Size.HINT <= 240, "the hint bar stays on the screen");
        assertEquals(224 * 378 / 512, layout.list().w(), "the list scales with the pane");
        assertEquals(3, layout.rows());
    }

    @Test
    void theRailAndTheHeaderNeverOverlapTheList() {
        for (Box viewport : new Box[] {DESIGN, new Box(0, 0, 426, 240), new Box(0, 0, 960, 540)}) {
            HubLayout layout = HubLayout.fit(viewport);
            assertTrue(layout.tabs().bottom() <= layout.search().y(), viewport.toString());
            assertTrue(layout.search().bottom() <= layout.list().y(), viewport.toString());
            assertTrue(layout.list().bottom() <= layout.panel().bottom(), viewport.toString());
            assertTrue(layout.detail().x() > layout.divider(), viewport.toString());
        }
    }

    @Test
    void refusesAViewportItCannotDivide() {
        assertThrows(IllegalArgumentException.class, () -> HubLayout.fit(new Box(0, 0, 0, 360)));
    }
}
