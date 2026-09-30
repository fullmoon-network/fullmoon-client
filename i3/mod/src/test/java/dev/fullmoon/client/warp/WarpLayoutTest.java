package dev.fullmoon.client.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeProtocol;

import org.junit.jupiter.api.Test;

/** The route screen's geometry at the design size is the mockup's, pixel for pixel. */
final class WarpLayoutTest {
    private static final Box DESIGN = new Box(0, 0, 640, 360);

    @Test
    void theMockupsFieldAtTheDesignSize() {
        WarpLayout layout = WarpLayout.fit(DESIGN, Tokens.Size.ROUTE_ROW_MOCK);
        assertEquals(new Box(64, 24, 512, 312), layout.wrap());
        assertEquals(28, layout.header().h());
        assertEquals(new Box(64, 64, 236, 196), layout.list(), "the list from 40 down to the status line");
        assertEquals(64 + 248, layout.divider());
        assertEquals(64 + 260, layout.detail().x());
        assertEquals(64 + 512, layout.detail().right());
        assertEquals(new Box(64 + 512 - 44, 64 + 34, 44, 44), layout.compass());
        assertEquals(24 + 312 - 76, layout.status().y());
        assertEquals(new Box(64 + 512 - 96, 24 + 312 - 32, 96, 20), layout.go());
        assertEquals(340, layout.hintY());
        assertEquals(64 + 260 + 56, layout.factValueX());
    }

    @Test
    void theShippedRowsAreTallerAndStillFitTheLobbysFiveDestinations() {
        WarpLayout layout = WarpLayout.fit(DESIGN, WarpLayout.rowHeight());
        assertEquals(Tokens.Size.ROUTE_ROW, layout.row());
        int captions = 3 * WarpLayout.captionHeight();
        int rows = 5 * layout.row();
        assertTrue(captions + rows <= layout.list().h(), captions + rows + " > " + layout.list().h());
    }

    @Test
    void aNarrowViewportScalesTheColumnsWithTheField() {
        WarpLayout layout = WarpLayout.fit(new Box(0, 0, 427, 240), Tokens.Size.ROUTE_ROW);
        assertEquals(427 - 48, layout.wrap().w());
        assertTrue(layout.list().right() < layout.divider());
        assertTrue(layout.divider() < layout.detail().x());
        assertEquals(layout.wrap().right(), layout.detail().right());
        assertTrue(layout.compass().right() <= layout.wrap().right());
    }

    @Test
    void groupsBecomeCaptionsBeforeTheirFirstDestination() {
        List<BridgeProtocol.Waypoint> routes = WarpRoutes.ordered(List.of(
            waypoint("casino", "달빛 카지노", "궁궐"), waypoint("plaza", "달빛 광장", "광장"),
            waypoint("portico", "로톤다 정문", "궁궐"), waypoint("gate", "달빛 문", "문")));
        List<RouteBoard.Slot> slots = RouteBoard.slots(routes);
        assertEquals(7, slots.size());
        assertEquals("광장", slots.get(0).caption());
        assertEquals(0, slots.get(1).route());
        assertEquals("궁궐", slots.get(2).caption());
        assertEquals(1, slots.get(3).route());
        assertEquals(2, slots.get(4).route());
        assertEquals("문", slots.get(5).caption());
        assertEquals(3, slots.get(6).route());
    }

    private static BridgeProtocol.Waypoint waypoint(String id, String name, String group) {
        return new BridgeProtocol.Waypoint(id, name, "moon", 0, 64, 0, "world", group, "");
    }
}
