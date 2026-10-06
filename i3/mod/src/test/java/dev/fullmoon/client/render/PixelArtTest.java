package dev.fullmoon.client.render;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PixelArtTest {
    private static final IntUnaryOperator PALETTE = pixel -> switch (pixel) {
        case 'A' -> 0xFF112233;
        case 'B' -> 0xFF445566;
        default -> 0;
    };

    @Test
    void aRunEndsWhereTheColourChangesOrTheCellIsClear() {
        String row = "AAB.AA";
        assertEquals(2, PixelArt.runEnd(row, 0, PALETTE, true));
        assertEquals(3, PixelArt.runEnd(row, 2, PALETTE, true));
        assertEquals(6, PixelArt.runEnd(row, 4, PALETTE, true));
    }

    @Test
    void sameColourCellsAcrossAGapAreTwoRuns() {
        assertEquals(1, PixelArt.runEnd("A.A", 0, PALETTE, true));
    }

    @Test
    void withoutMergingEveryCellIsItsOwnRun() {
        assertEquals(1, PixelArt.runEnd("AAAA", 0, PALETTE, false));
        assertEquals(3, PixelArt.runEnd("AAAA", 2, PALETTE, false));
    }

    @Test
    void onlyAGridOnWholePixelsMerges() {
        assertTrue(PixelArt.onWholePixels(30.0f, 4.0f));
        assertTrue(PixelArt.onWholePixels(30.0004f, 3.9996f));
        assertFalse(PixelArt.onWholePixels(30.0f, 3.68f), "a seam inside a pixel blends twice");
        assertFalse(PixelArt.onWholePixels(30.5f, 4.0f));
        assertFalse(PixelArt.onWholePixels(30.0f, 0.0f));
    }
}
