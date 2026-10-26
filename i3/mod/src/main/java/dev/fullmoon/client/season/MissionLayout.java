package dev.fullmoon.client.season;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * The missions page's list: today's missions, this week's and the season's, each under a caption,
 * one row per mission, scrolled as one. A list with no missions in it has no section for them.
 *
 * <p>Rows are counted in reading order across the sections, so the keyboard's cursor is one index
 * whatever section it is in. Positions are in content coordinates from the list's top; the screen
 * subtracts the scroll when it draws.
 */
public record MissionLayout(Box list, List<Line> lines, int rows) {
    public static final int CAPTION_H = 22;
    public static final int ROW_H = 34;
    static final int INSET = Tokens.Space.SNUG;

    public enum Section { DAILY, WEEKLY, SEASON }

    /** One line of the list: a section's caption, or the {@code row}-th mission in reading order. */
    public record Line(Section section, int row, int index, int y, int h) {
        public boolean caption() {
            return row < 0;
        }
    }

    public MissionLayout {
        lines = List.copyOf(lines);
    }

    static MissionLayout of(Box body, int daily, int weekly, int season) {
        Box list = Box.between(body.x(), body.y(), body.right() - Tokens.Size.DETAIL - Tokens.Space.LOOSE * 2,
            body.bottom());
        List<Line> lines = new ArrayList<>();
        int y = list.y() + INSET;
        int row = 0;
        int[] counts = {daily, weekly, season};
        for (Section section : Section.values()) {
            int count = counts[section.ordinal()];
            if (count <= 0) {
                continue;
            }
            lines.add(new Line(section, -1, -1, y, CAPTION_H));
            y += CAPTION_H;
            for (int i = 0; i < count; i++) {
                lines.add(new Line(section, row++, i, y, ROW_H));
                y += ROW_H;
            }
        }
        return new MissionLayout(list, lines, row);
    }

    public int contentHeight() {
        return lines.isEmpty() ? 0 : lines.getLast().y() + lines.getLast().h() - list.y() + INSET;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight() - list.h());
    }

    /** The line of the {@code row}-th mission, or null when there is no such row. */
    public Line row(int row) {
        for (Line line : lines) {
            if (line.row() == row) {
                return line;
            }
        }
        return null;
    }

    /** The row a pointer is on, or -1 for a caption, the gaps and anything outside the list. */
    public int hit(double mx, double my, float scroll) {
        if (!list.holds(mx, my)) {
            return -1;
        }
        double y = my + Math.round(scroll);
        for (Line line : lines) {
            if (!line.caption() && y >= line.y() && y < line.y() + line.h()) {
                return line.row();
            }
        }
        return -1;
    }

    /**
     * The least change to {@code scroll} that shows the row whole. The first row of a section also
     * brings its caption in, so the list never shows a row cut off from what it is a row of.
     */
    public float reveal(int row, float scroll) {
        Line line = row(row);
        if (line == null) {
            return Math.clamp(scroll, 0, maxScroll());
        }
        int top = line.index() == 0 ? line.y() - CAPTION_H : line.y();
        float next = scroll;
        if (top - scroll < list.y() + INSET) {
            next = top - list.y() - INSET;
        } else if (line.y() + line.h() - scroll > list.bottom() - INSET) {
            next = line.y() + line.h() - list.bottom() + INSET;
        }
        return Math.clamp(next, 0, maxScroll());
    }
}
