package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.layout.Box;

import org.junit.jupiter.api.Test;

final class ServerMenuLayoutTest {
    @Test
    void casinoActionsFormACompactTwoColumnDeck() {
        Box viewport = new Box(0, 0, 960, 540);
        ServerMenuLayout layout = ServerMenuLayout.fit(viewport, 6);

        assertEquals(2, layout.columns());
        assertEquals(layout.action(0).y(), layout.action(1).y());
        assertEquals(layout.action(0).x(), layout.action(2).x());
        assertTrue(layout.action(0).w() >= 150);
        assertTrue(layout.action(0).h() >= 48);
        assertTrue(layout.frame().w() < viewport.w());
        assertTrue(layout.frame().h() < viewport.h());
        assertEquals(viewport.x() + (viewport.w() - layout.frame().w()) / 2,
            layout.frame().x());
        assertEquals(viewport.y() + (viewport.h() - layout.frame().h()) / 2,
            layout.frame().y());
    }

    @Test
    void denseMenusUseFourColumnsWithoutEscapingThePanel() {
        ServerMenuLayout layout = ServerMenuLayout.fit(new Box(0, 0, 960, 540), 28);

        assertEquals(4, layout.columns());
        for (int index = 0; index < 28; index++) {
            Box card = layout.action(index);
            assertTrue(card.x() >= layout.actions().x());
            assertTrue(card.y() >= layout.actions().y());
            assertTrue(card.right() <= layout.actions().right());
            assertTrue(card.bottom() <= layout.actions().bottom());
        }
    }

    @Test
    void smallViewportsKeepAllRegionsInsideTheFrame() {
        ServerMenuLayout layout = ServerMenuLayout.fit(new Box(0, 0, 640, 360), 12);

        assertTrue(layout.header().bottom() <= layout.actions().y());
        assertTrue(layout.actions().right() < layout.context().x());
        assertTrue(layout.context().right() <= layout.frame().right());
        assertTrue(layout.footer().bottom() <= layout.frame().bottom());
    }

    @Test
    void doubleChestMenusKeepEveryCardClickableAtSmallViewports() {
        ServerMenuLayout layout = ServerMenuLayout.fit(new Box(0, 0, 320, 240), 54);

        for (int index = 0; index < 54; index++) {
            Box card = layout.action(index);
            assertTrue(card.w() >= 1, "card " + index + " must stay clickable");
            assertTrue(card.h() >= 1, "card " + index + " must stay clickable");
            assertTrue(card.x() >= layout.actions().x());
            assertTrue(card.y() >= layout.actions().y());
            assertTrue(card.right() <= layout.actions().right());
            assertTrue(card.bottom() <= layout.actions().bottom());
        }
    }

    @Test
    void narrowViewportsKeepTheRailInsideTheFrame() {
        ServerMenuLayout layout = ServerMenuLayout.fit(new Box(0, 0, 220, 200), 6);

        assertTrue(layout.actions().w() >= 8);
        assertTrue(layout.actions().right() < layout.context().x());
        assertTrue(layout.context().right() <= layout.frame().right());
    }
}
