package dev.fullmoon.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MapViewportTest {
    @Test
    void projectsWorldCoordinatesAroundTheMapCentre() {
        MapViewport viewport = new MapViewport(100.0, -40.0, 2);

        assertEquals(new MapViewport.GridPoint(4.5, 3.5),
            viewport.project(100.0, -40.0, 10, 8));
        assertEquals(new MapViewport.WorldPoint(91, -47), viewport.worldAt(0, 0, 10, 8));
        assertEquals(new MapViewport.WorldPoint(109, -33), viewport.worldAt(9, 7, 10, 8));
    }

    @Test
    void pansByCellsAtTheCurrentScale() {
        MapViewport viewport = new MapViewport(100.0, -40.0, 4);

        assertEquals(new MapViewport(112.0, -48.0, 4), viewport.panCells(3, -2));
    }

    @Test
    void zoomKeepsThePointUnderTheCursorFixed() {
        MapViewport viewport = new MapViewport(100.0, -40.0, 4);
        MapViewport.WorldPoint before = viewport.worldAt(8, 2, 12, 10);

        MapViewport zoomed = viewport.zoomInAt(8, 2, 12, 10);

        assertEquals(2, zoomed.blocksPerCell());
        assertEquals(before, zoomed.worldAt(8, 2, 12, 10));
    }

    @Test
    void zoomStopsAtDeclaredSurveyScales() {
        MapViewport close = new MapViewport(0.0, 0.0, 1);
        MapViewport far = new MapViewport(0.0, 0.0, 16);

        assertEquals(close, close.zoomInAt(0, 0, 1, 1));
        assertEquals(far, far.zoomOutAt(0, 0, 1, 1));
    }

    @Test
    void rejectsInvalidCentresScalesAndRasterDimensions() {
        assertThrows(IllegalArgumentException.class,
            () -> new MapViewport(Double.NaN, 0.0, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new MapViewport(0.0, 0.0, 3));
        assertThrows(IllegalArgumentException.class,
            () -> new MapViewport(0.0, Double.POSITIVE_INFINITY, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new MapViewport(0.0, 0.0, 2).worldAt(0, 0, 0, 8));
        assertThrows(IllegalArgumentException.class,
            () -> new MapViewport(0.0, 0.0, 2).project(0.0, 0.0, 10, 0));
    }

    @Test
    void theBlockAtAColumnAndRowIsWhatWorldAtReports() {
        MapViewport viewport = new MapViewport(100.4, -39.6, 4);
        for (int column = 0; column < 13; column++) {
            for (int row = 0; row < 9; row++) {
                MapViewport.WorldPoint point = viewport.worldAt(column, row, 13, 9);
                assertEquals(point.x(), viewport.blockX(column, 13));
                assertEquals(point.z(), viewport.blockZ(row, 9));
            }
        }
    }
}
