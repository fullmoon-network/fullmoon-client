package dev.fullmoon.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.layout.Box;

import org.junit.jupiter.api.Test;

/** The title screen's geometry at the design size is the mockup's, pixel for pixel. */
final class TitleLayoutTest {
    private static final Box DESIGN = new Box(0, 0, 640, 360);

    @Test
    void theMockupAtTheDesignSize() {
        TitleLayout layout = TitleLayout.fit(DESIGN);
        assertEquals(48, layout.margin());
        assertEquals(112, layout.markBaseline(), "the wordmark's capitals end on 112");
        assertEquals(120, layout.taglineY());
        assertEquals(new Box(48, 150, 232, 26), layout.primary());
        assertEquals(new Box(48, 182, 232, 20), layout.item(1));
        assertEquals(new Box(48, 262, 232, 20), layout.item(5));
        assertEquals(6, layout.items());
        assertEquals(new Box(48, 150, 232, 132), layout.menu());
        assertEquals(640 - 72, layout.skyRight());
        assertEquals(44, layout.skyTop());
        assertEquals(360 - 16 - 13, layout.footY());
    }

    @Test
    void aTallerViewportCentresTheComposition() {
        TitleLayout layout = TitleLayout.fit(new Box(0, 0, 960, 540));
        assertEquals(112 + 90, layout.markBaseline());
        assertEquals(new Box(48, 240, 232, 26), layout.primary());
        assertEquals(44 + 90, layout.skyTop());
        assertEquals(960 - 72, layout.skyRight());
        assertEquals(540 - 29, layout.footY(), "the foot stays on the foot");
    }

    @Test
    void aShortViewportClosesUpFromTheFoot() {
        TitleLayout layout = TitleLayout.fit(new Box(0, 0, 427, 240));
        assertEquals(240 - 29, layout.footY(), "the foot keeps its edge");
        assertEquals(new Box(48, 73, 232, 26), layout.primary(), "the menu sits as low as the foot allows");
        assertEquals(layout.footY() - 6, layout.item(5).bottom());
        assertEquals(20 + 28, layout.markBaseline(), "the wordmark lifts only the four pixels still short");
        assertEquals(56 + 13 + 4, layout.primary().y(), "the menu is never closer than four to the line");
        assertEquals(8, layout.skyTop(), "the moon stays in the sky");
    }

    @Test
    void aNarrowViewportKeepsTheMarginsAndShortensTheMenu() {
        TitleLayout layout = TitleLayout.fit(new Box(0, 0, 300, 360));
        assertEquals(48, layout.primary().x());
        assertEquals(300 - 96, layout.primary().w());
        assertEquals(layout.primary().w(), layout.item(3).w());
    }
}
