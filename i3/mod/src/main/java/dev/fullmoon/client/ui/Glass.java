package dev.fullmoon.client.ui;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

/**
 * The glass vocabulary every Fullmoon surface is drawn in: one translucent pane over the game's
 * own blur, and on it only ink, icons, figures and the few small marks a player needs to read a
 * menu — a keycap, a mouse, a chevron, a cross, a check.
 *
 * <p>There is no frame. A panel's edges are a one-pixel light along its top and a one-pixel
 * shadow outside it, and nothing inside a panel is boxed unless the box is a control.
 */
public final class Glass {
    /** The ✖ and ✔ marks are drawn, not typed: no face here carries them at the weight the ink needs. */
    public static final int MARK = 7;

    /** A hint under a panel: a key name and the word for what it does. */
    public record Hint(String key, String does) {}

    private Glass() {}

    /** A panel: the glass ground, its top light and its outer shadow. */
    public static void panel(Painter painter, Box box) {
        painter.border(box.x() - 1, box.y() - 1, box.w() + 2, box.h() + 2, Tokens.Radius.NONE,
            Tokens.Stroke.HAIR, Tokens.Color.SURFACE_EDGE);
        painter.fill(box.x(), box.y(), box.w(), box.h(), Tokens.Color.SURFACE_GLASS);
        painter.hRule(box.x(), box.y(), box.w(), Tokens.Color.SURFACE_HIGHLIGHT);
    }

    /** A hairline rule, the divider between regions of one pane. */
    public static void hair(Painter painter, int x, int y, int w) {
        painter.hRule(x, y, w, Tokens.Color.LINE_HAIRLINE);
    }

    public static void vhair(Painter painter, int x, int y, int h) {
        painter.vRule(x, y, h, Tokens.Color.LINE_HAIRLINE);
    }

    /** A keycap's width: the key name in micro plus three pixels a side. */
    public static int keycapWidth(String name) {
        return Typeset.width(Tokens.Type.MICRO, name) + 3 * 2;
    }

    /**
     * A keycap: the key name in micro inside a hairline with a deeper bottom edge, eleven pixels
     * tall. Returns the width it took, so a row of them can be laid out.
     */
    public static int keycap(Painter painter, int x, int y, String name, int ink) {
        int w = keycapWidth(name);
        int h = Tokens.Size.KEYCAP;
        painter.fill(x, y, w, h, Tokens.Radius.KEY, Tokens.Color.SURFACE_CONTROL_DISABLED);
        painter.border(x, y, w, h, Tokens.Radius.KEY, Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
        painter.hRule(x, y + h - 1, w, Tokens.Color.LINE_STRONG);
        Typeset.drawCentered(painter, Tokens.Type.MICRO, name, x + w / 2,
            Typeset.centred(Tokens.Type.MICRO, y, h - 1), ink);
        return w;
    }

    public static int keycap(Painter painter, int x, int y, String name) {
        return keycap(painter, x, y, name, Tokens.Color.INK_TERTIARY);
    }

    /**
     * The hint bar under a panel, centred on {@code cx}: each key as a keycap with the word for
     * what it does beside it. It sits brighter while the keyboard is what the player is using.
     */
    public static void hints(Painter painter, int cx, int y, List<Hint> hints, boolean keyboard) {
        int ink = keyboard ? Tokens.Color.INK_SECONDARY : Tokens.Color.INK_TERTIARY;
        int total = 0;
        for (Hint hint : hints) {
            total += keycapWidth(hint.key()) + Tokens.Space.SNUG
                + Typeset.width(Tokens.Type.BODY, hint.does()) + Tokens.Space.GUTTER - Tokens.Space.TIGHT;
        }
        total -= Tokens.Space.GUTTER - Tokens.Space.TIGHT;
        int x = cx - total / 2;
        int textY = Typeset.centred(Tokens.Type.BODY, y, Tokens.Size.HINT);
        int capY = y + (Tokens.Size.HINT - Tokens.Size.KEYCAP) / 2;
        for (Hint hint : hints) {
            x += keycap(painter, x, capY, hint.key(), ink) + Tokens.Space.SNUG;
            x += Typeset.draw(painter, Tokens.Type.BODY, hint.does(), x, textY, ink)
                + Tokens.Space.GUTTER - Tokens.Space.TIGHT;
        }
    }

    /** ‹, the way back, {@code size} tall about the point given. */
    public static void back(Painter painter, float cx, float cy, float size, int color) {
        painter.chevron(cx, cy, size, Tokens.Stroke.GLYPH, color, true);
    }

    /** ✕, the way out. */
    public static void close(Painter painter, float cx, float cy, float size, int color) {
        painter.cross(cx, cy, size, Tokens.Stroke.GLYPH, color);
    }

    /** ✖ as the reason line wears it: a small cross in danger ink. */
    public static void blockedMark(Painter painter, float cx, float cy, int color) {
        painter.cross(cx, cy, MARK - 2, Tokens.Stroke.GLYPH, color);
    }

    /** ✔ as the status line wears it. */
    public static void doneMark(Painter painter, float cx, float cy, int color) {
        painter.check(cx, cy, MARK, Tokens.Stroke.GLYPH, color);
    }

    /** A mouse, seven by ten, with its left button filled: the glyph before "클릭". */
    public static void mouse(Painter painter, float x, float y, int color) {
        painter.border(x, y, 7, 10, Tokens.Radius.MOUSE, Tokens.Stroke.HAIR, color);
        painter.fill(x + 1, y + 1, 2, 3, 0, color);
    }

    /**
     * A line that starts with a drawn mark and continues in strong ink: {@code ✖ reason} in danger
     * or {@code ✔ state} in live. Returns the width it took.
     */
    public static int markedLine(Painter painter, int x, int y, String text, int color, boolean blocked) {
        float cy = Typeset.capTop(Tokens.Type.STRONG, y) + Typeset.capHeight(Tokens.Type.STRONG) / 2.0f;
        if (blocked) {
            blockedMark(painter, x + MARK / 2.0f, cy, color);
        } else {
            doneMark(painter, x + MARK / 2.0f, cy, color);
        }
        int textX = x + MARK + Tokens.Space.SNUG + 1;
        return textX - x + Typeset.draw(painter, Tokens.Type.STRONG, text, textX, y, color);
    }

    /** A command a player types, as a chip: strong ink on a faint ground. Returns the width. */
    public static int codeChip(Painter painter, int x, int y, String command) {
        int w = Typeset.width(Tokens.Type.STRONG, command) + Tokens.Space.SNUG * 2;
        painter.fill(x, y, w, Tokens.Type.STRONG.leading(), Tokens.Color.SURFACE_CONTROL);
        Typeset.draw(painter, Tokens.Type.STRONG, command, x + Tokens.Space.SNUG, y, Tokens.Color.INK_PRIMARY);
        return w;
    }

    /**
     * An action row: what a click does, as [Shift] mouse 클릭 · text on a control ground. A row
     * that cannot act right now is drawn at {@code alpha}, and never brighter than the panel's own
     * opacity.
     */
    public static void actionRow(Painter painter, Box box, boolean shift, String text, float alpha) {
        float was = painter.opacity();
        painter.opacity(was * alpha);
        painter.fill(box.x(), box.y(), box.w(), box.h(), Tokens.Color.SURFACE_CONTROL);
        painter.border(box.x(), box.y(), box.w(), box.h(), Tokens.Radius.NONE, Tokens.Stroke.HAIR,
            Tokens.Color.LINE_STRONG);
        int x = box.x() + Tokens.Space.COZY;
        int textY = Typeset.centred(Tokens.Type.STRONG, box.y(), box.h());
        if (shift) {
            x += keycap(painter, x, box.midY() - (Tokens.Size.KEYCAP - 1) / 2, "Shift") + Tokens.Space.BASE;
        }
        mouse(painter, x, box.midY() - 5, Rgb.alpha(Tokens.Color.INK_PRIMARY, 0.8f));
        x += 7 + Tokens.Space.BASE;
        x += Typeset.draw(painter, Tokens.Type.STRONG, "클릭", x, textY, Tokens.Color.INK_PRIMARY);
        x += Tokens.Space.BASE;
        x += Typeset.draw(painter, Tokens.Type.BODY, "·", x, textY, Tokens.Color.INK_TERTIARY);
        x += Tokens.Space.BASE;
        int room = box.right() - Tokens.Space.COZY - x;
        Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, text, room), x, textY,
            Tokens.Color.INK_PRIMARY);
        painter.opacity(was);
    }

    /** A progress bar: a four-pixel track with {@code fill} of it lit in {@code color}. */
    public static void bar(Painter painter, int x, int y, int w, float fill, int color) {
        painter.fill(x, y, w, Tokens.Space.SNUG, Tokens.Color.LINE_HAIRLINE);
        int lit = Math.round(w * Math.clamp(fill, 0.0f, 1.0f));
        if (lit > 0) {
            painter.fill(x, y, lit, Tokens.Space.SNUG, color);
        }
    }

    /** The wordmark in a role's face, and the x its text starts at, for a screen that names itself. */
    public static int wordmark(Painter painter, Tokens.Type.Role role, int x, int y) {
        Typeset.draw(painter, role, "Fullmoon", x, y, Tokens.Color.INK_PRIMARY);
        return x;
    }

    /**
     * How far a wordmark's capitals rise above the y it is drawn at. A display face hangs its
     * capitals above its origin, so a masthead drawn at a box's top edge overruns the box: draw it
     * at {@code top + rise(role)} and the capitals start on {@code top}.
     */
    public static int rise(Tokens.Type.Role role) {
        return Tokens.Space.TIGHT - Typeset.capTop(role, 0);
    }
}
