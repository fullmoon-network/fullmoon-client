package dev.fullmoon.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

final class CoordinatesHudTest {
    @Test
    void thePositionIsWholeBlocksFlooredLikeTheDebugScreen() {
        assertEquals("0 · 64 · 0", CoordinatesHud.position(0.5, 64.0, 0.5));
        assertEquals("-1 · 63 · -321", CoordinatesHud.position(-0.2, 63.9, -320.8));
    }

    @Test
    void theFacingIsALetterAndAHeadingInsideTheCircle() {
        assertEquals("S 0°", CoordinatesHud.facing(Direction.SOUTH, 0.0f));
        assertEquals("N 180°", CoordinatesHud.facing(Direction.NORTH, 180.0f));
        assertEquals("W 90°", CoordinatesHud.facing(Direction.WEST, -270.0f));
        assertEquals("E 270°", CoordinatesHud.facing(Direction.EAST, 630.0f));
    }
}
