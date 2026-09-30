package dev.fullmoon.client.menu;

import java.util.List;

/**
 * Where the keyboard is among items laid out on a grid, and where an arrow takes it.
 *
 * <p>Every mode of the menu is a grid: a list is one column, the shop is two, a chest-sized menu
 * is the nine the server laid its slots on. An arrow moves to the nearest item in that direction
 * — nearest along the arrow first, then across it — so a column with a gap in it is still walked,
 * and a row that ends early hands the cursor to the item that is closest, not to nothing. The
 * cursor never wraps: a list that jumps from its last item to its first under a held key is a
 * list that cannot be scrolled to its end.
 */
public final class GridCursor {
    /** An item's cell. */
    public record Cell(int column, int row) {}

    public enum Direction { UP, DOWN, LEFT, RIGHT }

    private final List<Cell> cells;
    private int at;

    public GridCursor(List<Cell> cells, int at) {
        this.cells = List.copyOf(cells);
        this.at = this.cells.isEmpty() ? -1 : Math.clamp(at, 0, this.cells.size() - 1);
    }

    /** The index of the item the cursor is on, or -1 with no items. */
    public int at() {
        return at;
    }

    public Cell cell() {
        return at < 0 ? null : cells.get(at);
    }

    public int size() {
        return cells.size();
    }

    /** Puts the cursor on {@code index} if there is such an item. */
    public boolean set(int index) {
        if (index < 0 || index >= cells.size()) {
            return false;
        }
        at = index;
        return true;
    }

    /** Moves one step; false when nothing lies that way, so the caller can leave the cursor alone. */
    public boolean move(Direction direction) {
        if (at < 0) {
            return false;
        }
        Cell from = cells.get(at);
        int best = -1;
        long bestScore = Long.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            if (i == at) {
                continue;
            }
            Cell to = cells.get(i);
            int along = switch (direction) {
                case UP -> from.row() - to.row();
                case DOWN -> to.row() - from.row();
                case LEFT -> from.column() - to.column();
                case RIGHT -> to.column() - from.column();
            };
            if (along <= 0) {
                continue;
            }
            int across = switch (direction) {
                case UP, DOWN -> Math.abs(to.column() - from.column());
                case LEFT, RIGHT -> Math.abs(to.row() - from.row());
            };
            // Along the arrow counts more than across it, and across it a stray of one is still
            // the same line; only a far-off item loses to a nearer one on the wrong line.
            long score = (long) along * 1_000 + across;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0) {
            return false;
        }
        at = best;
        return true;
    }

    public boolean first() {
        return set(0);
    }

    public boolean last() {
        return set(cells.size() - 1);
    }
}
