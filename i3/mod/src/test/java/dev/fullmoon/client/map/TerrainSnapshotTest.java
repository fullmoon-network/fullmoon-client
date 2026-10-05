package dev.fullmoon.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TerrainSnapshotTest {
    private static final TerrainSnapshot.Cell UNKNOWN = TerrainSnapshot.Cell.unmapped(7);
    private static final TerrainSnapshot.Cell GRASS = TerrainSnapshot.Cell.mapped(11, 64);
    private static final TerrainSnapshot.Cell WATER = TerrainSnapshot.Cell.mapped(13, 62);
    private static final TerrainSnapshot.Cell SHOAL = TerrainSnapshot.Cell.mapped(7, 63);

    @Test
    void copiesCellsAndReportsHonestCoverage() {
        List<TerrainSnapshot.Cell> source = new java.util.ArrayList<>(
            List.of(GRASS, GRASS, UNKNOWN, WATER));
        TerrainSnapshot snapshot = new TerrainSnapshot(2, 2, source);
        source.set(0, UNKNOWN);

        assertEquals(GRASS, snapshot.cell(0, 0));
        assertEquals(75, snapshot.mappedPercent());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.cells().set(0, UNKNOWN));
    }

    @Test
    void compressesOnlyAdjacentCellsWithTheSameTruthStateAndColour() {
        TerrainSnapshot snapshot = new TerrainSnapshot(5, 2, List.of(
            GRASS, GRASS, UNKNOWN, UNKNOWN, WATER,
            WATER, GRASS, GRASS, WATER, WATER));

        assertEquals(List.of(
            new TerrainSnapshot.Run(0, 0, 2, 11, true),
            new TerrainSnapshot.Run(0, 2, 2, 7, false),
            new TerrainSnapshot.Run(0, 4, 1, 13, true),
            new TerrainSnapshot.Run(1, 0, 1, 13, true),
            new TerrainSnapshot.Run(1, 1, 2, 11, true),
            new TerrainSnapshot.Run(1, 3, 2, 13, true)), snapshot.runs());
    }

    @Test
    void keepsRealTerrainOutOfARunItOnlySharesAColourWith() {
        TerrainSnapshot snapshot = new TerrainSnapshot(3, 1, List.of(SHOAL, UNKNOWN, SHOAL));

        assertEquals(List.of(
            new TerrainSnapshot.Run(0, 0, 1, 7, true),
            new TerrainSnapshot.Run(0, 1, 1, 7, false),
            new TerrainSnapshot.Run(0, 2, 1, 7, true)), snapshot.runs());
    }

    @Test
    void rejectsMalformedRastersAndCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot(0, 1, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot(1, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot(2, 2, List.of(GRASS)));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot(1, 1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot.Run(-1, 0, 1, 11, true));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot.Run(0, -1, 1, 11, true));
        assertThrows(IllegalArgumentException.class,
            () -> new TerrainSnapshot.Run(0, 0, 0, 11, true));

        TerrainSnapshot snapshot = new TerrainSnapshot(1, 1, List.of(GRASS));
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.cell(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.cell(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.cell(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.cell(0, -1));
        assertTrue(snapshot.runs().getFirst().mapped());
    }

    @Test
    void aRasterBuiltFromPrimitivesIsTheSnapshotItsCellsDescribe() {
        TerrainSnapshot fromCells = new TerrainSnapshot(3, 2, List.of(
            GRASS, GRASS, UNKNOWN,
            WATER, SHOAL, UNKNOWN));
        TerrainSnapshot fromRasters = TerrainSnapshot.ofRasters(3, 2,
            new int[] {11, 11, 7, 13, 7, 7},
            new int[] {64, 64, 0, 62, 63, 0},
            new boolean[] {true, true, false, true, true, false});

        assertEquals(fromCells, fromRasters);
        assertEquals(fromCells.runs(), fromRasters.runs());
        assertEquals(fromCells.cells(), fromRasters.cells());
        assertEquals(fromCells.mappedPercent(), fromRasters.mappedPercent());
        assertEquals(67, fromRasters.mappedPercent());
        assertEquals(WATER, fromRasters.cell(0, 1));
    }

    @Test
    void rastersOfTheWrongSizeAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TerrainSnapshot.ofRasters(2, 2,
            new int[4], new int[3], new boolean[4]));
        assertThrows(IllegalArgumentException.class, () -> TerrainSnapshot.ofRasters(0, 2,
            new int[0], new int[0], new boolean[0]));
    }
}
