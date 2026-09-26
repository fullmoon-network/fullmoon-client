package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.PixelArt;
import dev.fullmoon.client.text.Typeset;

/**
 * The palace vocabulary every Fullmoon surface is framed in: a gilt double frame with corner
 * brackets, a window-lattice header band, the dancheong band under it, the moon seal, and the
 * diamond that marks a chosen row.
 *
 * <p>Ornament belongs to the frame and never to the content. A header, a corner or a band may
 * carry it; the ground a list or a value sits on stays plain, because a pattern under text is
 * a pattern the text has to fight. The dancheong colours are decoration only and never say
 * anything about state.
 */
public final class Palace {
    /** The second frame line sits this far outside the first. */
    public static final int FRAME_OUTSET = 4;
    /** Brackets stand proud of the second line, so a frame's corner reads at a glance. */
    public static final int BRACKET_OUTSET = 6;
    public static final int BRACKET_ARM = 12;
    public static final int BRACKET_STROKE = 2;
    public static final int LATTICE_PITCH = 11;
    public static final int DANCHEONG_HEIGHT = 5;

    /**
     * One repeat of the band, as width and colour pairs: green, gilt, red, gilt, blue, gilt —
     * the order the brackets of a palace eave are painted in, simplified to flat panels.
     */
    private static final int[][] DANCHEONG = {
        {14, Tokens.Color.ORNAMENT_JADE},
        {2, Tokens.Color.ACCENT},
        {11, Tokens.Color.ORNAMENT_CINNABAR},
        {2, Tokens.Color.ACCENT},
        {14, Tokens.Color.ORNAMENT_LAPIS},
        {2, Tokens.Color.ACCENT},
    };

    /** 月, cut the way a seal carver cuts it: square strokes, the left leg sweeping out. */
    private static final String[] MOON_GLYPH = {
        "............",
        "...#######..",
        "...#.....#..",
        "...#.....#..",
        "...#######..",
        "...#.....#..",
        "...#.....#..",
        "...#######..",
        "...#.....#..",
        "..#......#..",
        ".#.....#.#..",
        "#.......##..",
    };

    private Palace() {}

    /**
     * The wordmark with the moon seal before it, sized to the role's capitals. Returns the x the
     * wordmark starts at, so a subtitle can align under it.
     */
    public static int brand(Painter painter, Tokens.Type.Role role, int x, int y) {
        int size = Typeset.capHeight(role) + Tokens.Space.SNUG;
        seal(painter, x, Typeset.capTop(role, y) - Tokens.Space.TIGHT, size);
        int textX = x + size + Tokens.Space.COZY;
        Typeset.draw(painter, role, "Fullmoon", textX, y, Tokens.Color.INK_PRIMARY);
        return textX;
    }

    /** A panel: plain ground inside the gilt frame. */
    public static void panel(Painter painter, int x, int y, int w, int h) {
        painter.fill(x, y, w, h, Tokens.Color.SURFACE_BASE);
        frame(painter, x, y, w, h);
    }

    /** The double gilt frame around a rect, with the accent brackets on its corners. */
    public static void frame(Painter painter, int x, int y, int w, int h) {
        painter.border(x, y, w, h, Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT);
        painter.border(x - FRAME_OUTSET, y - FRAME_OUTSET, w + FRAME_OUTSET * 2, h + FRAME_OUTSET * 2,
            Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT_FAINT);
        brackets(painter, x - BRACKET_OUTSET, y - BRACKET_OUTSET,
            w + BRACKET_OUTSET * 2, h + BRACKET_OUTSET * 2, BRACKET_ARM, Tokens.Color.ACCENT);
    }

    /** Four L-shaped corner brackets on the rect's own corners. */
    public static void brackets(Painter painter, float x, float y, float w, float h, float arm, int color) {
        float s = BRACKET_STROKE;
        painter.fill(x, y, arm, s, color);
        painter.fill(x, y, s, arm, color);
        painter.fill(x + w - arm, y, arm, s, color);
        painter.fill(x + w - s, y, s, arm, color);
        painter.fill(x, y + h - s, arm, s, color);
        painter.fill(x, y + h - arm, s, arm, color);
        painter.fill(x + w - arm, y + h - s, arm, s, color);
        painter.fill(x + w - s, y + h - arm, s, arm, color);
    }

    /**
     * Two brackets on opposite corners, top-left and bottom-right: the frame's mark at the size
     * of a HUD chip, where four would crowd the text.
     */
    public static void ticks(Painter painter, float x, float y, float w, float h, float arm) {
        float s = Tokens.Stroke.HAIR;
        painter.fill(x, y, arm, s, Tokens.Color.ACCENT);
        painter.fill(x, y, s, arm, Tokens.Color.ACCENT);
        painter.fill(x + w - arm, y + h - s, arm, s, Tokens.Color.ACCENT);
        painter.fill(x + w - s, y + h - arm, s, arm, Tokens.Color.ACCENT);
    }

    /** Window lattice over a header band. The band's own ground is drawn by the caller. */
    public static void lattice(Painter painter, int x, int y, int w, int h) {
        painter.pushClip(x, y, w, h);
        for (int i = LATTICE_PITCH; i < w; i += LATTICE_PITCH) {
            painter.vRule(x + i, y, h, Tokens.Color.LINE_LATTICE);
        }
        for (int j = LATTICE_PITCH; j < h; j += LATTICE_PITCH) {
            painter.hRule(x, y + j, w, Tokens.Color.LINE_LATTICE);
        }
        painter.popClip();
    }

    /** The dancheong band, {@link #DANCHEONG_HEIGHT} tall, cut off square at {@code w}. */
    public static void dancheong(Painter painter, int x, int y, int w) {
        int cursor = 0;
        for (int i = 0; cursor < w; i = (i + 1) % DANCHEONG.length) {
            int span = Math.min(DANCHEONG[i][0], w - cursor);
            painter.fill(x + cursor, y, span, DANCHEONG_HEIGHT, DANCHEONG[i][1]);
            cursor += span;
        }
    }

    /** The moon seal: a cinnabar square with a cut border and 月 in ivory. */
    public static void seal(Painter painter, float x, float y, float size) {
        painter.fill(x, y, size, size, Tokens.Color.ORNAMENT_CINNABAR);
        float inset = Math.max(1.5f, size / 12.0f);
        painter.border(x + inset, y + inset, size - inset * 2, size - inset * 2,
            Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.INK_PRIMARY);
        PixelArt.draw(painter, MOON_GLYPH, x + size / 2.0f, y + size / 2.0f, size * 0.64f,
            pixel -> pixel == '#' ? Tokens.Color.INK_PRIMARY : 0);
    }

    /** The row marker: a filled cinnabar diamond when chosen, a gilt outline otherwise. */
    public static void marker(Painter painter, float cx, float cy, boolean chosen) {
        if (chosen) {
            painter.diamond(cx, cy, 3.5f, 0.0f, Tokens.Color.ORNAMENT_CINNABAR);
        } else {
            painter.diamond(cx, cy, 3.5f, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT);
        }
    }

    /**
     * A keycap: the key's name in a gilt outline with a deeper bottom edge. Returns the width it
     * took, so a footer can lay a row of them out.
     */
    public static int key(Painter painter, int x, int y, String name) {
        int w = Typeset.width(Tokens.Type.LABEL, name) + Tokens.Space.COZY;
        int h = Tokens.Type.LABEL.leading() + Tokens.Space.TIGHT;
        painter.fill(x, y, w, h, Tokens.Color.SURFACE_SUNKEN);
        painter.border(x, y, w, h, Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT_FAINT);
        painter.hRule(x, y + h - 1, w, Tokens.Color.LINE_GILT);
        Typeset.drawCentered(painter, Tokens.Type.LABEL, name, x + w / 2,
            Typeset.centred(Tokens.Type.LABEL, y, h - 1), Tokens.Color.INK_SECONDARY);
        return w;
    }

    /** A dashed gilt rule, the divider inside a scroll-like detail panel. */
    public static void dashedRule(Painter painter, int x, int y, int w) {
        for (int i = 0; i < w; i += 7) {
            painter.fill(x + i, y, Math.min(4, w - i), 1, Tokens.Color.LINE_GILT);
        }
    }
}
