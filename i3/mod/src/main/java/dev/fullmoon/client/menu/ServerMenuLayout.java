package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where a server menu's parts go, from the viewport and what the menu holds.
 *
 * <p>The design size is the 640×360 GUI, where the panel is 512 wide and sits centred with a hint
 * bar under it. A smaller viewport shrinks the panel to its margins and the lists inside it
 * scroll; nothing is ever laid out off the pane.
 *
 * <p>Three modes, chosen by how many things there are to choose: up to eight choices are a list
 * of two-line rows with a detail column beside it; up to eighteen are two columns of one-line
 * rows with a detail strip under them; more than that keep the server's own nine-column slot
 * geometry as a grid of cells, with the detail column beside it.
 */
public record ServerMenuLayout(
        Mode mode,
        Density density,
        Box panel,
        Box header,
        Box list,
        Box detail,
        Box facts,
        int hintY,
        int columns) {

    public enum Mode { LIST, COLUMNS, GRID }

    /**
     * The row spacing. {@link #SHIPPED} is the client's own; {@link #MOCK} is the concept mockup's,
     * kept so the two can be photographed side by side and chosen by the operator.
     */
    public record Density(int row, int rowOne, int panelH, int facts) {
        public static final Density SHIPPED = new Density(
            Tokens.Size.ROW, Tokens.Size.ROW_ONE, Tokens.Size.PANEL_H, Tokens.Size.FACTS);
        public static final Density MOCK = new Density(
            Tokens.Size.ROW_MOCK, Tokens.Size.ROW_ONE_MOCK, Tokens.Size.MOCK_PANEL_H, Tokens.Size.FACTS_MOCK);

        /** The density the process was started with; the capture rig asks for the mockup's. */
        public static Density current() {
            return "mock".equals(System.getProperty("fullmoon.density", "")) ? MOCK : SHIPPED;
        }
    }

    public static final int LIST_CHOICES_MAX = 8;
    public static final int COLUMN_CHOICES_MAX = 18;
    /** The detail column starts this far inside the list's right edge plus the hairline gap. */
    private static final int DETAIL_GAP = Tokens.Space.LOOSE;
    private static final int STRIP_H = 92;
    private static final int HINT_GAP = 8;

    public static Mode modeFor(int choices) {
        if (choices <= LIST_CHOICES_MAX) {
            return Mode.LIST;
        }
        return choices <= COLUMN_CHOICES_MAX ? Mode.COLUMNS : Mode.GRID;
    }

    public static ServerMenuLayout fit(Box viewport, int choices, boolean hasFacts) {
        return fit(viewport, choices, hasFacts, Density.current());
    }

    public static ServerMenuLayout fit(Box viewport, int choices, boolean hasFacts, Density density) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        Mode mode = modeFor(choices);
        int edge = Tokens.Size.EDGE;
        int panelW = Math.min(Tokens.Size.PANEL_W, viewport.w() - edge * 2);
        // Centred alone, as the mockup centres it; the hint bar lives in the margin under it, so
        // the panel may only be as tall as leaves that margin on both sides.
        int panelH = Math.min(density.panelH(), viewport.h() - (Tokens.Size.HINT + HINT_GAP) * 2);
        int panelY = viewport.y() + (viewport.h() - panelH) / 2;
        Box panel = new Box(viewport.x() + (viewport.w() - panelW) / 2, panelY, panelW, panelH);
        Box header = new Box(panel.x(), panel.y(), panel.w(), Tokens.Size.HEADER);
        int hintY = panel.bottom() + HINT_GAP;

        Box list;
        Box detail;
        Box facts;
        int columns;
        switch (mode) {
            case COLUMNS -> {
                int stripTop = panel.bottom() - STRIP_H;
                list = Box.between(panel.x(), header.bottom(), panel.right(), stripTop);
                detail = Box.between(panel.x(), stripTop, panel.right(), panel.bottom());
                facts = Box.EMPTY;
                columns = 2;
            }
            case GRID -> {
                int listW = panel.w() - Tokens.Size.DETAIL - DETAIL_GAP * 2;
                list = Box.between(panel.x(), header.bottom(), panel.x() + listW, panel.bottom());
                detail = Box.between(list.right() + DETAIL_GAP * 2, header.bottom() + Tokens.Space.COZY,
                    panel.right(), panel.bottom());
                facts = Box.EMPTY;
                columns = 9;
            }
            default -> {
                int factsH = hasFacts ? density.facts() : 0;
                int listW = panel.w() - Tokens.Size.DETAIL - DETAIL_GAP * 2;
                list = Box.between(panel.x(), header.bottom(), panel.x() + listW, panel.bottom() - factsH);
                detail = Box.between(list.right() + DETAIL_GAP * 2, header.bottom() + Tokens.Space.COZY,
                    panel.right(), panel.bottom() - factsH);
                facts = hasFacts
                    ? Box.between(panel.x(), panel.bottom() - factsH, panel.right(), panel.bottom())
                    : Box.EMPTY;
                columns = 1;
            }
        }
        return new ServerMenuLayout(mode, density, panel, header, list, detail, facts, hintY, columns);
    }

    /** The x of the hairline between the list and the detail column, or -1 in the columns mode. */
    public int divider() {
        return mode == Mode.COLUMNS ? -1 : list.right() + DETAIL_GAP / 3;
    }

    /** The pitch of one item: a two-line row, a one-line row, or a grid cell with its gap. */
    public int pitch() {
        return switch (mode) {
            case LIST -> density.row();
            case COLUMNS -> density.rowOne();
            case GRID -> Tokens.Size.CELL + Tokens.Size.CELL_GAP;
        };
    }

    /** The top padding inside the list area before the first item. */
    public int listInset() {
        return mode == Mode.GRID ? Tokens.Space.LOOSE : Tokens.Space.SNUG;
    }

    /** The left x of the grid, centred in the list area. */
    public int gridLeft() {
        int width = columns * Tokens.Size.CELL + (columns - 1) * Tokens.Size.CELL_GAP;
        return list.x() + (list.w() - width) / 2;
    }

    /**
     * The box of the {@code index}-th choice (in cursor order) before scrolling, where
     * {@code cell} is its grid cell. A list row spans the list's width; a column row half of it;
     * a grid cell is the server's slot.
     */
    public Box item(GridCursor.Cell cell) {
        int top = list.y() + listInset();
        return switch (mode) {
            case LIST -> new Box(list.x(), top + cell.row() * pitch(), list.w(), density.row());
            case COLUMNS -> {
                int colW = list.w() / 2;
                yield new Box(list.x() + cell.column() * colW, top + cell.row() * pitch(), colW, density.rowOne());
            }
            case GRID -> new Box(gridLeft() + cell.column() * pitch(), top + cell.row() * pitch(),
                Tokens.Size.CELL, Tokens.Size.CELL);
        };
    }

    /** The cells the choices occupy, in the order they were given, from their slots. */
    public List<GridCursor.Cell> cells(List<Integer> slots) {
        List<GridCursor.Cell> cells = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            cells.add(switch (mode) {
                case LIST -> new GridCursor.Cell(0, i);
                case COLUMNS -> new GridCursor.Cell(i % 2, i / 2);
                case GRID -> new GridCursor.Cell(slots.get(i) % 9, slots.get(i) / 9);
            });
        }
        return cells;
    }

    /** The total height the items take, so the list knows how far it can scroll. */
    public int contentHeight(List<GridCursor.Cell> cells) {
        int rows = 0;
        for (GridCursor.Cell cell : cells) {
            rows = Math.max(rows, cell.row() + 1);
        }
        if (mode == Mode.GRID) {
            // Grid rows are the server's, from the top of the chest, so empty leading rows count.
            return listInset() * 2 + rows * pitch() - Tokens.Size.CELL_GAP;
        }
        return listInset() * 2 + rows * pitch();
    }

    /** How far the list can scroll: content past the list's height, never negative. */
    public int maxScroll(List<GridCursor.Cell> cells) {
        return Math.max(0, contentHeight(cells) - list.h());
    }
}
