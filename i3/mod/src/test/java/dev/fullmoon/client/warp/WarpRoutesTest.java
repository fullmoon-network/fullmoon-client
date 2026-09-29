package dev.fullmoon.client.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.network.BridgeProtocol;

import org.junit.jupiter.api.Test;

final class WarpRoutesTest {
    @Test
    void routesAreOrderedByGroupNameAndIdWithoutMutatingTheSnapshot() {
        BridgeProtocol.Waypoint west = waypoint("west", "West Gate", "gate", 3, 4, 12);
        BridgeProtocol.Waypoint keep = waypoint("keep", "Main Keep", "palace", 0, 64, 0);
        BridgeProtocol.Waypoint gate = waypoint("gate", "Palace Gate", "palace", 0, 64, 0);
        List<BridgeProtocol.Waypoint> snapshot = new ArrayList<>(List.of(gate, west, keep));

        List<BridgeProtocol.Waypoint> ordered = WarpRoutes.ordered(snapshot);

        assertEquals(List.of(gate, west, keep), snapshot);
        assertEquals(List.of(west, keep, gate), ordered);
        assertThrows(UnsupportedOperationException.class, () -> ordered.clear());
    }

    @Test
    void distanceUsesTheSameThreeDimensionalFloorAsTheServerFallback() {
        BridgeProtocol.Waypoint route = waypoint("gate", "Gate", "palace", 3, 4, 12);

        assertEquals(13, WarpRoutes.distanceMeters(route, 0, 0, 0));
        assertEquals(0, WarpRoutes.distanceMeters(route, 3.5, 4, 12.5));
    }

    @Test
    void namesOnlyTheRefusalsTheProtocolDefinesAndCallsTheRestTheServersOwn() {
        assertEquals("cooldown", WarpRoutes.reasonKey("cooldown"));
        assertEquals("permission", WarpRoutes.reasonKey("permission"));
        assertEquals("world", WarpRoutes.reasonKey("world"));
        assertEquals("unknown", WarpRoutes.reasonKey("unknown"));
        assertEquals("unloaded", WarpRoutes.reasonKey("unloaded"));
        assertEquals("timeout", WarpRoutes.reasonKey("timeout"));
        assertEquals("client_send", WarpRoutes.reasonKey("client_send"));
        assertEquals("server", WarpRoutes.reasonKey("Cooldown"));
        assertEquals("server", WarpRoutes.reasonKey(""));
        assertEquals("server", WarpRoutes.reasonKey(null));
    }

    @Test
    void bearingsRunClockwiseFromNorthAndTheCompassNamesThem() {
        BridgeProtocol.Waypoint casino = waypoint("casino", "달빛 카지노", "궁궐", 39, 77, -31);
        double bearing = WarpRoutes.bearing(casino, 0.5, 80.5);
        assertEquals(19.4, bearing, 0.1, "north-north-east of the plaza");
        assertEquals("북북동", WarpRoutes.compassPoint(bearing));
        assertEquals("북", WarpRoutes.compassPoint(359.0));
        assertEquals("동", WarpRoutes.compassPoint(90.0));
        assertEquals("남", WarpRoutes.compassPoint(180.0));
        assertEquals("서", WarpRoutes.compassPoint(270.0));
        assertEquals(0.0, WarpRoutes.bearing(waypoint("n", "n", "g", 0, 64, -10), 0.5, 0.5), 1e-9);
        assertEquals(90.0, WarpRoutes.bearing(waypoint("e", "e", "g", 10, 64, 0), 0.5, 0.5), 1e-9);
    }

    @Test
    void theTurnIsFromWhereThePlayerFacesAndReadsLeftOrRight() {
        assertEquals(0.0, WarpRoutes.heading(180.0f), 1e-9, "yaw 180 faces north");
        assertEquals(90.0, WarpRoutes.heading(270.0f), 1e-9, "yaw 270 faces east");
        assertEquals(20.0, WarpRoutes.turn(0.0, 20.0), 1e-9);
        assertEquals(-20.0, WarpRoutes.turn(0.0, 340.0), 1e-9);
        assertEquals(180.0, WarpRoutes.turn(90.0, 270.0), 1e-9);
        assertEquals("오른쪽 20°", WarpRoutes.turnLabel(19.6));
        assertEquals("왼쪽 20°", WarpRoutes.turnLabel(-20.0));
        assertEquals("정면", WarpRoutes.turnLabel(0.4));
        assertEquals("뒤", WarpRoutes.turnLabel(-179.6));
    }

    private static BridgeProtocol.Waypoint waypoint(
            String id, String name, String group, int x, int y, int z) {
        return new BridgeProtocol.Waypoint(id, name, "moon", x, y, z, "world", group, "");
    }
}
