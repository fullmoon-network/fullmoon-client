package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.menu.GridCursor.Cell;
import dev.fullmoon.client.menu.GridCursor.Direction;

import org.junit.jupiter.api.Test;

/** Where the arrows take the keyboard through a list, two columns, and the server's slot grid. */
final class GridCursorTest {
    private static List<Cell> list(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> new Cell(0, i)).toList();
    }

    @Test
    void aListWalksDownAndUpAndStopsAtItsEnds() {
        GridCursor cursor = new GridCursor(list(3), 0);
        assertTrue(cursor.move(Direction.DOWN));
        assertTrue(cursor.move(Direction.DOWN));
        assertEquals(2, cursor.at());
        assertFalse(cursor.move(Direction.DOWN), "no wrap: a held key must be able to rest at the end");
        assertEquals(2, cursor.at());
        assertFalse(cursor.move(Direction.RIGHT), "a list has no sideways");
        assertTrue(cursor.move(Direction.UP));
        assertEquals(1, cursor.at());
    }

    @Test
    void twoColumnsWalkAcrossAndDownInReadingOrder() {
        // 0 1 / 2 3 / 4 — the shop's fourteen items less nine
        List<Cell> cells = List.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1), new Cell(1, 1), new Cell(0, 2));
        GridCursor cursor = new GridCursor(cells, 0);
        assertTrue(cursor.move(Direction.RIGHT));
        assertEquals(1, cursor.at());
        assertTrue(cursor.move(Direction.DOWN));
        assertEquals(3, cursor.at());
        assertTrue(cursor.move(Direction.DOWN), "the last row has nothing under column one, so the nearest item is taken");
        assertEquals(4, cursor.at());
        assertTrue(cursor.move(Direction.UP));
        assertEquals(2, cursor.at(), "straight up wins over diagonal");
    }

    @Test
    void theChestGridKeepsTheServersSlotsAndSkipsGaps() {
        // slots 10, 12, 16, 19: a row with holes and a second row
        List<Cell> cells = List.of(new Cell(1, 1), new Cell(3, 1), new Cell(7, 1), new Cell(1, 2));
        GridCursor cursor = new GridCursor(cells, 0);
        assertTrue(cursor.move(Direction.RIGHT));
        assertEquals(1, cursor.at(), "the hole at slot 11 is skipped");
        assertTrue(cursor.move(Direction.RIGHT));
        assertEquals(2, cursor.at());
        assertTrue(cursor.move(Direction.DOWN));
        assertEquals(3, cursor.at(), "down from the far right finds the only item on the next row");
        assertFalse(cursor.move(Direction.LEFT));
    }

    @Test
    void anEmptyBoardHasNoCursor() {
        GridCursor cursor = new GridCursor(List.of(), 0);
        assertEquals(-1, cursor.at());
        assertFalse(cursor.move(Direction.DOWN));
        assertFalse(cursor.set(0));
    }

    @Test
    void firstAndLastAndSet() {
        GridCursor cursor = new GridCursor(list(6), 3);
        assertTrue(cursor.first());
        assertEquals(0, cursor.at());
        assertTrue(cursor.last());
        assertEquals(5, cursor.at());
        assertFalse(cursor.set(6));
        assertEquals(5, cursor.at());
    }
}
