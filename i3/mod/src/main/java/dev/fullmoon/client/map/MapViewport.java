package dev.fullmoon.client.map;

import java.util.List;

/** Immutable world centre and survey scale for a north-up raster map. */
public record MapViewport(double centerX, double centerZ, int blocksPerCell) {
    private static final List<Integer> SCALES = List.of(1, 2, 4, 8, 16);

    public MapViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Map centre must be finite");
        }
        if (!SCALES.contains(blocksPerCell)) {
            throw new IllegalArgumentException("Unsupported map scale: " + blocksPerCell);
        }
    }

    public GridPoint project(double worldX, double worldZ, int columns, int rows) {
        requireRaster(columns, rows);
        return new GridPoint(
            (worldX - centerX) / blocksPerCell + midpoint(columns),
            (worldZ - centerZ) / blocksPerCell + midpoint(rows));
    }

    public WorldPoint worldAt(int column, int row, int columns, int rows) {
        return new WorldPoint(blockX(column, columns), blockZ(row, rows));
    }

    /** The block column a raster column sits over: {@link #worldAt}'s x, without the point. */
    public int blockX(int column, int columns) {
        requireRaster(columns, 1);
        return (int) Math.floor(centerX + (column - midpoint(columns)) * blocksPerCell);
    }

    /** The block row a raster row sits over: {@link #worldAt}'s z, without the point. */
    public int blockZ(int row, int rows) {
        requireRaster(1, rows);
        return (int) Math.floor(centerZ + (row - midpoint(rows)) * blocksPerCell);
    }

    public MapViewport panCells(int columns, int rows) {
        return new MapViewport(
            centerX + (double) columns * blocksPerCell,
            centerZ + (double) rows * blocksPerCell,
            blocksPerCell);
    }

    public MapViewport zoomInAt(int column, int row, int columns, int rows) {
        int index = SCALES.indexOf(blocksPerCell);
        return zoomAt(Math.max(0, index - 1), column, row, columns, rows);
    }

    public MapViewport zoomOutAt(int column, int row, int columns, int rows) {
        int index = SCALES.indexOf(blocksPerCell);
        return zoomAt(Math.min(SCALES.size() - 1, index + 1), column, row, columns, rows);
    }

    private MapViewport zoomAt(int scaleIndex, int column, int row, int columns, int rows) {
        requireRaster(columns, rows);
        int nextScale = SCALES.get(scaleIndex);
        if (nextScale == blocksPerCell) {
            return this;
        }
        WorldPoint focus = worldAt(column, row, columns, rows);
        return new MapViewport(
            focus.x() - (column - midpoint(columns)) * nextScale,
            focus.z() - (row - midpoint(rows)) * nextScale,
            nextScale);
    }

    private static double midpoint(int cells) {
        return (cells - 1) * 0.5;
    }

    private static void requireRaster(int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Map raster dimensions must be positive");
        }
    }

    public record GridPoint(double column, double row) {}

    public record WorldPoint(int x, int z) {}
}
