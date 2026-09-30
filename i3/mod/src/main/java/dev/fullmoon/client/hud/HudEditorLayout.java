package dev.fullmoon.client.hud;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where the HUD editor's chrome goes: a 28-tall glass strip along the top with the editor's name,
 * the chosen element's anchor and switch and the way out — pulled in from both corners on a wide
 * window, because the HUD's own chips live in the corners and the editor exists to show them where
 * they are; a strip of the same height near the foot holding one chip per element and the reset,
 * centred and folded into rows when one row would reach into the bottom corners; the hint bar
 * under that; and the canvas — the HUD itself — between the two strips.
 */
public record HudEditorLayout(Box header, int dockY, int hintY) {
    static final int STRIP = Tokens.Size.HEADER;
    static final int CHIP = Tokens.Size.ACTION_ROW;
    static final int CHIP_GAP = Tokens.Space.BASE;
    static final int DOCK_PAD = Tokens.Space.LOOSE;
    /** Between two folded rows of chips: half the strip's own margin, so a row's chip keeps its 4 px either side. */
    static final int ROW_GAP = (STRIP - CHIP) / 2;
    /** The header keeps this much clear at either corner, on a window wide enough to afford it. */
    static final int CORNER = Tokens.Size.SIDEBAR;
    static final int WIDE = 600;

    public static HudEditorLayout fit(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int hintY = height - Tokens.Space.COZY - Tokens.Size.HINT;
        int dockY = hintY - Tokens.Space.COZY - STRIP;
        int corner = width >= WIDE ? CORNER : 0;
        return new HudEditorLayout(new Box(corner, 0, width - corner * 2, STRIP), dockY, hintY);
    }

    /** The dock: its glass, one box per chip in element order, and the tail's box. */
    public record Dock(Box box, List<Box> chips, Box tail) {}

    /**
     * The dock for chips {@code widths} wide and a tail — the reset — {@code tailW} by {@code tailH}
     * after them, centred in a window {@code screenW} wide and standing on the hint bar's gap. On a
     * wide window the dock keeps out of the corners the way the header does, because the HUD's
     * bottom-corner elements (the keystrokes) live there: when one row would reach into them the
     * chips fold into even rows, as few as fit, the tail riding at the end of the last one. The dock
     * grows upwards, so its foot and the hint bar under it never move.
     */
    public Dock dock(int[] widths, int tailW, int tailH, int screenW) {
        int room = screenW >= WIDE ? screenW - CORNER * 2 : screenW;
        int n = widths.length;
        int rows = 1;
        while (rows < n && inner(widths, tailW, rows) + DOCK_PAD * 2 > room) {
            rows++;
        }
        int per = n == 0 ? 1 : (n + rows - 1) / rows;
        int used = n == 0 ? 1 : (n + per - 1) / per;
        int w = Math.min(screenW, inner(widths, tailW, rows) + DOCK_PAD * 2);
        int h = (STRIP - CHIP) + used * CHIP + (used - 1) * ROW_GAP;
        Box box = new Box((screenW - w) / 2, dockY + STRIP - h, w, h);
        List<Box> chips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int row = i / per;
            int x = box.x() + DOCK_PAD;
            for (int j = row * per; j < i; j++) {
                x += widths[j] + CHIP_GAP;
            }
            chips.add(new Box(x, rowY(box, row), widths[i], CHIP));
        }
        Box tail = new Box(box.right() - DOCK_PAD - tailW, rowY(box, used - 1) + (CHIP - tailH) / 2, tailW, tailH);
        return new Dock(box, List.copyOf(chips), tail);
    }

    private static int rowY(Box box, int row) {
        return box.y() + (STRIP - CHIP) / 2 + row * (CHIP + ROW_GAP);
    }

    /** The widest row, padding aside, when the chips go {@code rows} deep with the tail on the last. */
    static int inner(int[] widths, int tailW, int rows) {
        int n = widths.length;
        int tail = tailW > 0 ? DOCK_PAD + tailW : 0;
        if (n == 0) {
            return Math.max(0, tail - DOCK_PAD);
        }
        int per = (n + rows - 1) / rows;
        int widest = 0;
        for (int first = 0; first < n; first += per) {
            int last = Math.min(n, first + per);
            int w = 0;
            for (int j = first; j < last; j++) {
                w += widths[j] + (j > first ? CHIP_GAP : 0);
            }
            widest = Math.max(widest, last == n ? w + tail : w);
        }
        return widest;
    }

    /** The width all the chips take in one row, gaps included. */
    public static int chipsWidth(int[] widths) {
        int total = 0;
        for (int i = 0; i < widths.length; i++) {
            total += widths[i] + (i > 0 ? CHIP_GAP : 0);
        }
        return total;
    }

    /** What the editor is for: everything between the two strips. */
    public Box canvas(int width) {
        return Box.between(0, header.bottom(), width, dockY);
    }
}
