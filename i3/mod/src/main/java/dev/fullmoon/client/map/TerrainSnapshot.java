package dev.fullmoon.client.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable sampled terrain raster with explicit unmapped cells. It is held as three primitive
 * rasters, because a map is thousands of cells and is re-sampled every second; {@link Cell}
 * objects are made only for a caller that asks for one.
 */
public final class TerrainSnapshot {
    private final int width;
    private final int height;
    private final int[] colors;
    private final int[] elevations;
    private final boolean[] mapped;
    private final int mappedPercent;
    private List<Cell> cells;

    public TerrainSnapshot(int width, int height, List<Cell> cells) {
        this(width, height, colorsOf(width, height, cells), elevationsOf(cells), mappedOf(cells));
    }

    /** Takes ownership of the arrays, which are {@code width * height} long in row order. */
    static TerrainSnapshot ofRasters(int width, int height, int[] colors, int[] elevations, boolean[] mapped) {
        return new TerrainSnapshot(width, height, colors, elevations, mapped);
    }

    private TerrainSnapshot(int width, int height, int[] colors, int[] elevations, boolean[] mapped) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Terrain raster dimensions must be positive");
        }
        int count = Math.multiplyExact(width, height);
        if (colors.length != count || elevations.length != count || mapped.length != count) {
            throw new IllegalArgumentException("Terrain cell count does not match the raster");
        }
        this.width = width;
        this.height = height;
        this.colors = colors;
        this.elevations = elevations;
        this.mapped = mapped;
        long real = 0;
        for (boolean cell : mapped) {
            if (cell) {
                real++;
            }
        }
        this.mappedPercent = (int) Math.round(real * 100.0 / count);
    }

    private static int[] colorsOf(int width, int height, List<Cell> cells) {
        check(width, height, cells);
        int[] colors = new int[cells.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = cells.get(i).color();
        }
        return colors;
    }

    private static int[] elevationsOf(List<Cell> cells) {
        int[] elevations = new int[cells.size()];
        for (int i = 0; i < elevations.length; i++) {
            elevations[i] = cells.get(i).elevation();
        }
        return elevations;
    }

    private static boolean[] mappedOf(List<Cell> cells) {
        boolean[] mapped = new boolean[cells.size()];
        for (int i = 0; i < mapped.length; i++) {
            mapped[i] = cells.get(i).mapped();
        }
        return mapped;
    }

    private static void check(int width, int height, List<Cell> cells) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Terrain raster dimensions must be positive");
        }
        if (cells == null || cells.size() != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("Terrain cell count does not match the raster");
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Cell cell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Terrain cell outside raster: " + x + "," + y);
        }
        int index = y * width + x;
        return new Cell(mapped[index], colors[index], elevations[index]);
    }

    public List<Cell> cells() {
        if (cells == null) {
            List<Cell> all = new ArrayList<>(colors.length);
            for (int i = 0; i < colors.length; i++) {
                all.add(new Cell(mapped[i], colors[i], elevations[i]));
            }
            cells = List.copyOf(all);
        }
        return cells;
    }

    /** Share of the raster that is real terrain, counted once when the snapshot is made. */
    public int mappedPercent() {
        return mappedPercent;
    }

    public List<Run> runs() {
        List<Run> result = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            appendRow(result, row);
        }
        return List.copyOf(result);
    }

    private void appendRow(List<Run> result, int row) {
        int base = row * width;
        int start = 0;
        while (start < width) {
            int color = colors[base + start];
            boolean real = mapped[base + start];
            int end = start + 1;
            while (end < width && colors[base + end] == color && mapped[base + end] == real) {
                end++;
            }
            result.add(new Run(row, start, end - start, color, real));
            start = end;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerrainSnapshot that && width == that.width && height == that.height
            && Arrays.equals(colors, that.colors) && Arrays.equals(elevations, that.elevations)
            && Arrays.equals(mapped, that.mapped);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * width + height) + Arrays.hashCode(colors)) + Arrays.hashCode(mapped);
    }

    public record Cell(boolean mapped, int color, int elevation) {
        public static Cell mapped(int color, int elevation) {
            return new Cell(true, color, elevation);
        }

        public static Cell unmapped(int color) {
            return new Cell(false, color, 0);
        }
    }

    public record Run(int row, int column, int length, int color, boolean mapped) {
        public Run {
            if (row < 0 || column < 0 || length <= 0) {
                throw new IllegalArgumentException("Invalid terrain run");
            }
        }
    }
}
