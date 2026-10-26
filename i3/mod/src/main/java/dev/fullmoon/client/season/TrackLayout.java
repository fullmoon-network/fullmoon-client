package dev.fullmoon.client.season;

import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.SeasonProtocol;

/**
 * Where the reward track's parts go on the page: the standing along the top, then one column per
 * level — the free cell above, the level's node on the rail, the Plus cell below — and the chosen
 * cell's line under them.
 *
 * <p>The columns scroll sideways inside {@link #window()}; the row names stay put in the gutter on
 * its left. Scroll is a distance in GUI pixels from the first column, never negative, never past
 * the last column's right edge.
 */
public record TrackLayout(
        Box summary,
        Box window,
        Box caption,
        Box free,
        Box rail,
        Box plus,
        int scrollbarY,
        Box detail) {
    public static final int TILE_W = 48;
    public static final int TILE_H = 56;
    public static final int PITCH = TILE_W + Tokens.Space.SNUG;
    public static final int RAIL_H = Tokens.Space.SECTION;
    public static final int GUTTER = 40;
    static final int SUMMARY_H = 36;
    static final int CAPTION_H = 14;
    /** Where a column is put when the track opens on it: a third of the way in, with what is next in view. */
    static final float OPENING = 1.0f / 3.0f;

    static TrackLayout of(Box body) {
        int left = body.x() + Tokens.Space.LOOSE;
        int right = body.right() - Tokens.Space.LOOSE;
        Box summary = Box.between(left, body.y() + Tokens.Space.COZY, right, body.y() + Tokens.Space.COZY + SUMMARY_H);
        Box caption = Box.between(left + GUTTER, summary.bottom() + Tokens.Space.TIGHT, right,
            summary.bottom() + Tokens.Space.TIGHT + CAPTION_H);
        Box free = new Box(left, caption.bottom(), right - left, TILE_H);
        Box rail = new Box(left, free.bottom() + Tokens.Space.TIGHT, right - left, RAIL_H);
        Box plus = new Box(left, rail.bottom() + Tokens.Space.TIGHT, right - left, TILE_H);
        Box window = Box.between(left + GUTTER, caption.y(), right, plus.bottom());
        int scrollbarY = plus.bottom() + Tokens.Space.SNUG;
        Box detail = Box.between(left, scrollbarY + Tokens.Space.TIGHT + Tokens.Space.SNUG, right,
            Math.max(scrollbarY + Tokens.Space.TIGHT + Tokens.Space.SNUG, body.bottom()));
        return new TrackLayout(summary, window, caption, free, rail, plus, scrollbarY, detail);
    }

    /** A cell of the track: the level's column and which row of it. */
    public record Cell(int column, SeasonProtocol.Track track) {}

    public int contentWidth(int columns) {
        return Math.max(0, columns) * PITCH;
    }

    public int maxScroll(int columns) {
        return Math.max(0, contentWidth(columns) - window.w());
    }

    /** The left edge of column {@code index}'s cells at {@code scroll}. */
    public int tileX(int index, float scroll) {
        return window.x() + index * PITCH + (PITCH - TILE_W) / 2 - Math.round(scroll);
    }

    public float centreX(int index, float scroll) {
        return window.x() + index * PITCH + PITCH / 2.0f - Math.round(scroll);
    }

    public Box tile(int index, SeasonProtocol.Track track, float scroll) {
        Box row = track == SeasonProtocol.Track.FREE ? free : plus;
        return new Box(tileX(index, scroll), row.y(), TILE_W, TILE_H);
    }

    /** The column's whole height, caption to Plus cell: what the current level is lit across. */
    public Box column(int index, float scroll) {
        return new Box(window.x() + index * PITCH - Math.round(scroll), caption.y(), PITCH, plus.bottom() - caption.y());
    }

    /** The scroll that puts {@code index} a third of the way in, or as near as the ends allow. */
    public float opening(int index, int columns) {
        return Math.clamp(index * PITCH + PITCH / 2.0f - window.w() * OPENING, 0, maxScroll(columns));
    }

    /** The least change to {@code scroll} that brings the whole of column {@code index} into view. */
    public float reveal(int index, float scroll, int columns) {
        float left = index * PITCH;
        float right = left + PITCH;
        float next = scroll;
        if (left < scroll) {
            next = left;
        } else if (right > scroll + window.w()) {
            next = right - window.w();
        }
        return Math.clamp(next, 0, maxScroll(columns));
    }

    /** The cell under the pointer, if it is on one. The rail between the two rows picks nothing. */
    public Optional<Cell> hit(double mx, double my, float scroll, int columns) {
        if (!window.holds(mx, my)) {
            return Optional.empty();
        }
        SeasonProtocol.Track track;
        if (my >= free.y() && my < free.bottom()) {
            track = SeasonProtocol.Track.FREE;
        } else if (my >= plus.y() && my < plus.bottom()) {
            track = SeasonProtocol.Track.PLUS;
        } else {
            return Optional.empty();
        }
        double along = mx - window.x() + Math.round(scroll);
        int index = (int) Math.floor(along / PITCH);
        double inside = along - index * PITCH;
        int inset = (PITCH - TILE_W) / 2;
        if (index < 0 || index >= columns || inside < inset || inside >= inset + TILE_W) {
            return Optional.empty();
        }
        return Optional.of(new Cell(index, track));
    }

    /** The horizontal scrollbar's thumb as {@code [x, width]} inside the window, or null with nothing to scroll. */
    public int[] thumb(float scroll, int columns) {
        int max = maxScroll(columns);
        if (max <= 0) {
            return null;
        }
        int track = window.w();
        int width = Math.max(Tokens.Space.SECTION, track * window.w() / contentWidth(columns));
        int x = window.x() + Math.round((track - width) * Math.clamp(scroll / max, 0.0f, 1.0f));
        return new int[] {x, width};
    }
}
