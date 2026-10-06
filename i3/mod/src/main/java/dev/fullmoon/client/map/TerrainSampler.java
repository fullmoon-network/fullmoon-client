package dev.fullmoon.client.map;

import dev.fullmoon.client.design.Tokens;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import net.minecraft.client.multiplayer.ClientLevel;

/** Samples only chunks already present in the client cache. */
public final class TerrainSampler {
    private TerrainSampler() {}

    public static TerrainSample sample(
            ClientLevel level, MapViewport viewport, int width, int height) {
        if (level == null || viewport == null) {
            throw new IllegalArgumentException("Level and map viewport are required");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Terrain sample dimensions must be positive");
        }

        int count = Math.multiplyExact(width, height);
        boolean[] mapped = new boolean[count];
        int[] elevations = new int[count];
        MapColor[] mapColors = new MapColor[count];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        // A chunk is sixteen blocks and a cell is one to sixteen of them, so neighbours in a row
        // almost always share one; the level is not touched while this runs, so asking once is the
        // same as asking per cell.
        int chunkX = 0;
        int chunkZ = 0;
        LevelChunk chunk = null;
        boolean looked = false;
        for (int row = 0; row < height; row++) {
            int z = viewport.blockZ(row, height);
            for (int column = 0; column < width; column++) {
                int index = row * width + column;
                int x = viewport.blockX(column, width);
                int cellChunkX = Math.floorDiv(x, 16);
                int cellChunkZ = Math.floorDiv(z, 16);
                if (!looked || cellChunkX != chunkX || cellChunkZ != chunkZ) {
                    chunkX = cellChunkX;
                    chunkZ = cellChunkZ;
                    chunk = loaded(level, chunkX, chunkZ);
                    looked = true;
                }
                mapColors[index] = MapColor.NONE;
                if (chunk == null) {
                    continue;
                }
                int elevation = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (!level.isInsideBuildHeight(elevation)) {
                    continue;
                }
                position.set(x, elevation, z);
                BlockState state = chunk.getBlockState(position);
                mapped[index] = true;
                elevations[index] = elevation;
                mapColors[index] = state.getMapColor(chunk, position);
            }
        }

        int[] colors = new int[count];
        for (int index = 0; index < count; index++) {
            colors[index] = mapped[index] ? color(mapped, elevations, mapColors, index, width)
                : Tokens.Color.SURFACE_SUNKEN;
        }
        return TerrainSample.of(TerrainSnapshot.ofRasters(width, height, colors, elevations, mapped));
    }

    private static LevelChunk loaded(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk == null || chunk.isEmpty() ? null : chunk;
    }

    private static int color(boolean[] mapped, int[] elevations, MapColor[] mapColors, int index, int width) {
        MapColor mapColor = mapColors[index];
        if (mapColor == MapColor.NONE) {
            return Tokens.Color.SURFACE_RAISED;
        }
        return mapColor.calculateARGBColor(brightness(mapped, elevations, index, width));
    }

    private static MapColor.Brightness brightness(boolean[] mapped, int[] elevations, int index, int width) {
        if (index < width || !mapped[index - width]) {
            return MapColor.Brightness.NORMAL;
        }
        int slope = elevations[index] - elevations[index - width];
        if (slope > 1) {
            return MapColor.Brightness.HIGH;
        }
        if (slope < -1) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }
}
