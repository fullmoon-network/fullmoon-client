package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.menu.ServerMenuLayout.Density;
import dev.fullmoon.client.menu.ServerMenuLayout.Mode;

import org.junit.jupiter.api.Test;

/**
 * The menu's geometry at the design size and away from it. The numbers at 640×360 are the
 * concept mockup's, which is what the in-game capture is compared against pixel for pixel.
 */
final class ServerMenuLayoutTest {
    private static final Box DESIGN = new Box(0, 0, 640, 360);

    @Test
    void theModeFollowsHowManyThingsThereAreToChoose() {
        assertEquals(Mode.LIST, ServerMenuLayout.modeFor(0));
        assertEquals(Mode.LIST, ServerMenuLayout.modeFor(6));
        assertEquals(Mode.LIST, ServerMenuLayout.modeFor(8));
        assertEquals(Mode.COLUMNS, ServerMenuLayout.modeFor(9));
        assertEquals(Mode.COLUMNS, ServerMenuLayout.modeFor(14));
        assertEquals(Mode.COLUMNS, ServerMenuLayout.modeFor(18));
        assertEquals(Mode.GRID, ServerMenuLayout.modeFor(19));
    }

    @Test
    void theMockupsPanelAtTheDesignSize() {
        ServerMenuLayout layout = ServerMenuLayout.fit(DESIGN, 6, true, Density.MOCK);
        assertEquals(new Box(64, 30, 512, 300), layout.panel());
        assertEquals(28, layout.header().h());
        assertEquals(312, layout.list().w(), "the list column");
        assertEquals(316, layout.divider(), "the hairline between list and detail");
        assertEquals(64 + 324, layout.detail().x(), "the detail column starts at 324 in the panel");
        assertEquals(176, layout.detail().w());
        assertEquals(30 + 36, layout.detail().y());
        assertEquals(300 - 40, layout.facts().y() - 30, "the facts strip is the last 40");
        assertEquals(338, layout.hintY(), "the hint bar sits 8 under the panel");
        assertEquals(36, layout.pitch());
    }

    @Test
    void theShippedDensityIsTallerAndStillFitsSixGamesWithoutScrolling() {
        ServerMenuLayout layout = ServerMenuLayout.fit(DESIGN, 6, true, Density.SHIPPED);
        assertEquals(512, layout.panel().w());
        assertEquals(Tokens.Size.PANEL_H, layout.panel().h());
        assertTrue(layout.pitch() > Density.MOCK.row(), "rows are taller than the mockup's");
        List<GridCursor.Cell> cells = layout.cells(List.of(19, 20, 21, 22, 23, 24));
        assertEquals(0, layout.maxScroll(cells), "the casino's six games are all in view");
        assertTrue(layout.hintY() + Tokens.Size.HINT <= DESIGN.bottom(), "the hint bar stays on screen");
        assertTrue(layout.panel().y() >= Tokens.Size.EDGE / 4);
    }

    @Test
    void columnsSplitThePanelInTwoWithAStripUnderThem() {
        ServerMenuLayout layout = ServerMenuLayout.fit(DESIGN, 14, true, Density.MOCK);
        assertEquals(Mode.COLUMNS, layout.mode());
        assertEquals(2, layout.columns());
        assertEquals(-1, layout.divider());
        assertEquals(layout.panel().w(), layout.list().w());
        assertEquals(30 + 208, layout.detail().y(), "the strip starts at 208 in the panel");
        assertEquals(92, layout.detail().h());
        List<Integer> slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
        List<GridCursor.Cell> cells = layout.cells(slots);
        assertEquals(new GridCursor.Cell(1, 0), cells.get(1));
        assertEquals(new GridCursor.Cell(0, 6), cells.get(12));
        Box seventh = layout.item(cells.get(12));
        assertEquals(layout.list().x(), seventh.x());
        assertEquals(256, seventh.w());
        assertEquals(24, seventh.h());
        assertEquals(0, layout.maxScroll(cells), "fourteen one-line rows fit the mockup strip");
    }

    @Test
    void theGridKeepsTheServersNineColumns() {
        ServerMenuLayout layout = ServerMenuLayout.fit(DESIGN, 20, false, Density.SHIPPED);
        assertEquals(Mode.GRID, layout.mode());
        assertEquals(9, layout.columns());
        List<GridCursor.Cell> cells = layout.cells(List.of(10, 11, 19));
        assertEquals(new GridCursor.Cell(1, 1), cells.get(0));
        assertEquals(new GridCursor.Cell(1, 2), cells.get(2));
        Box a = layout.item(cells.get(0));
        Box b = layout.item(cells.get(1));
        assertEquals(Tokens.Size.CELL, a.w());
        assertEquals(a.x() + Tokens.Size.CELL + Tokens.Size.CELL_GAP, b.x());
        assertTrue(a.x() >= layout.list().x());
        assertTrue(layout.gridLeft() + 9 * layout.pitch() - Tokens.Size.CELL_GAP <= layout.list().right());
        assertTrue(layout.detail().x() > layout.list().right());
    }

    @Test
    void aSmallViewportShrinksThePanelAndScrollsTheList() {
        Box small = new Box(0, 0, 426, 240);
        ServerMenuLayout layout = ServerMenuLayout.fit(small, 6, true, Density.SHIPPED);
        assertTrue(layout.panel().w() <= small.w() - Tokens.Size.EDGE * 2);
        assertTrue(layout.hintY() + Tokens.Size.HINT <= small.bottom());
        assertTrue(layout.detail().right() <= layout.panel().right());
        assertTrue(layout.facts().bottom() <= layout.panel().bottom());
        List<GridCursor.Cell> cells = layout.cells(List.of(19, 20, 21, 22, 23, 24));
        assertTrue(layout.maxScroll(cells) > 0, "six tall rows no longer fit, so the list scrolls");
    }

    @Test
    void everyRegionStaysInsideThePanelAtAnySize() {
        for (Box viewport : List.of(new Box(0, 0, 320, 240), new Box(0, 0, 960, 540), new Box(0, 0, 1280, 720))) {
            for (int choices : List.of(0, 2, 8, 14, 30)) {
                ServerMenuLayout layout = ServerMenuLayout.fit(viewport, choices, choices % 2 == 0, Density.SHIPPED);
                Box panel = layout.panel();
                assertTrue(layout.list().bottom() <= panel.bottom(), viewport + " " + choices);
                assertTrue(layout.detail().bottom() <= panel.bottom(), viewport + " " + choices);
                assertTrue(layout.detail().right() <= panel.right(), viewport + " " + choices);
                assertEquals(viewport.x() + (viewport.w() - panel.w()) / 2, panel.x());
            }
        }
    }
}
