package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * The frame this client's development surfaces share: a masthead with the moon seal and the
 * dancheong band, a section head, and a footer rule that names the keys out of the screen.
 *
 * <p>It exists so the pages read as one document rather than three screens that happen to draw
 * from the same tokens. The heights are published separately from the drawing because a screen
 * has to lay its widgets out in {@code init}, before there is a painter to ask.
 *
 * <p>The accent rules are measured off {@link Typeset#capTop} rather than off the y they were
 * handed. A bar sized against the nominal line box lands under the wordmark instead of beside
 * it, because the baseline is 7 px below the origin for a 22 px face and an 8 px one alike.
 */
public final class DevChrome {
    private DevChrome() {}

    public static int headerHeight() {
        return Tokens.Type.DISPLAY.leading() + Tokens.Type.MICRO.leading() + Tokens.Space.GUTTER;
    }

    public static int sectionHeadHeight() {
        return Tokens.Type.MICRO.leading() + Tokens.Space.COZY;
    }

    /** The masthead. Returns the y its content starts at. */
    public static int header(Painter painter, int x, int y, int w, String subtitle) {
        int brandY = y + Glass.rise(Tokens.Type.DISPLAY);
        int textX = Glass.wordmark(painter, Tokens.Type.DISPLAY, x, brandY);
        Typeset.draw(painter, Tokens.Type.MICRO, subtitle, textX,
            brandY + Tokens.Type.MICRO.leading() + Tokens.Space.TIGHT, Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.MICRO,
            painter.width() + " × " + painter.height() + " gui px",
            x + w, brandY, Tokens.Color.INK_TERTIARY);

        Glass.hair(painter, x, y + Tokens.Type.DISPLAY.leading() + Tokens.Type.MICRO.leading(), w);
        return y + headerHeight();
    }

    /** A section head is a label with an accent tick, never a tag left and a value right. */
    public static int sectionHead(Painter painter, String name, int x, int y) {
        painter.diamond(x + Tokens.Space.TIGHT, Typeset.capTop(Tokens.Type.MICRO, y)
            + Typeset.capHeight(Tokens.Type.MICRO) / 2.0f, Tokens.Space.TIGHT + 0.5f, 0.0f, Tokens.Color.ACCENT);
        Typeset.draw(painter, Tokens.Type.MICRO, name, x + Tokens.Space.BASE + Tokens.Space.TIGHT, y,
            Tokens.Color.INK_SECONDARY);
        return y + sectionHeadHeight();
    }

    public static void footer(Painter painter, int x, int y, int w, String keys, String status) {
        painter.hRule(x, y, w, Tokens.Color.LINE_HAIRLINE);
        int textY = y + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.MICRO, keys, x, textY, Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.MICRO, status, x + w, textY,
            Tokens.Color.INK_TERTIARY);
    }

    /** Where the footer rule goes on a screen {@code height} tall. */
    public static int footerY(int height) {
        return height - Tokens.Space.SECTION - Tokens.Type.MICRO.leading();
    }
}
