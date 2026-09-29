package dev.fullmoon.client.ui;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * The chrome this client's development surfaces share, in the glass vocabulary: a masthead with
 * the page's name in the title face and a line under it, section captions in strong tertiary ink
 * as the route list's groups, and a foot with the keys out of the page as keycaps.
 *
 * <p>It exists so the pages read as one document rather than three screens that happen to draw
 * from the same tokens. The heights are published separately from the drawing because a screen
 * has to lay its widgets out in {@code init}, before there is a painter to ask.
 */
public final class DevChrome {
    private DevChrome() {}

    public static int headerHeight() {
        return Tokens.Type.TITLE.leading() + Tokens.Type.BODY.leading() + Tokens.Space.GUTTER;
    }

    public static int sectionHeadHeight() {
        return Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
    }

    /** The masthead: the page's name, what it is under it, and the window's size on the right. */
    public static int header(Painter painter, int x, int y, int w, String title, String subtitle) {
        Typeset.draw(painter, Tokens.Type.TITLE, title, x, y, Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.BODY, subtitle, x, y + Tokens.Type.TITLE.leading(), Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.BODY, painter.width() + " × " + painter.height() + " gui px",
            x + w, y + Tokens.Type.TITLE.leading() - Tokens.Type.BODY.leading(), Tokens.Color.INK_TERTIARY);
        Glass.hair(painter, x, y + Tokens.Type.TITLE.leading() + Tokens.Type.BODY.leading() + Tokens.Space.SNUG, w);
        return y + headerHeight();
    }

    /** A section caption: strong tertiary ink, nothing beside it. */
    public static int sectionHead(Painter painter, String name, int x, int y) {
        Typeset.draw(painter, Tokens.Type.STRONG, name, x, y, Tokens.Color.INK_TERTIARY);
        return y + sectionHeadHeight();
    }

    /** The foot: a rule, the keys out of the page as keycaps, and the page's status on the right. */
    public static void footer(Painter painter, Box content, int y, List<Glass.Hint> hints, boolean keyboard,
            String status) {
        painter.hRule(content.x(), y, content.w(), Tokens.Color.LINE_HAIRLINE);
        int statusW = Typeset.width(Tokens.Type.BODY, status);
        Typeset.draw(painter, Tokens.Type.BODY, status, content.right() - statusW,
            Typeset.centred(Tokens.Type.BODY, y + Tokens.Space.COZY, Tokens.Size.HINT), Tokens.Color.INK_TERTIARY);
        Glass.hints(painter, content.x() + (content.w() - statusW - Tokens.Space.GUTTER) / 2, y + Tokens.Space.COZY,
            hints, keyboard);
    }

    /** The height the foot takes under its rule. */
    public static int footerHeight() {
        return Tokens.Space.COZY + Tokens.Size.HINT + Tokens.Space.COZY;
    }

    /** Where the footer rule goes on a screen {@code height} tall. */
    public static int footerY(int height) {
        return height - Tokens.Space.SECTION - footerHeight();
    }
}
