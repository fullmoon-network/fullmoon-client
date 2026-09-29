package dev.fullmoon.client.title;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where the title screen's parts go, from the viewport. The design size is the 640×360 GUI:
 * the wordmark 48 in with its capitals ending on 112 and its line under it from 120, the menu
 * under that from 150 — the way into the lobby 26 tall, then five 20-tall rows after a gap of
 * 6, all 232 wide — tonight's moon 44 down with its column ending 72 short of the right edge,
 * and the foot line 16 up from the bottom between the two margins. A taller viewport centres
 * the composition, a shorter one closes it up from the foot, and a wider one keeps the margins.
 */
public record TitleLayout(
        int margin,
        int markBaseline,
        int taglineY,
        Box primary,
        Box rows,
        int skyRight,
        int skyTop,
        int footY) {

    static final int MARGIN = 48;
    static final int BRAND_TOP = 84;
    /** The wordmark's capitals end this far under the top of its line. */
    static final int MARK_BASELINE = 28;
    static final int MENU_TOP = 150;
    static final int MENU_W = 232;
    static final int PRIMARY_H = 26;
    static final int ROW_H = 20;
    static final int PRIMARY_GAP = 6;
    static final int ROW_COUNT = 5;
    static final int SKY_RIGHT = 72;
    static final int SKY_TOP = 44;
    static final int MOON_R = 22;
    static final int FOOT_UP = 16;
    static final int DESIGN_H = 360;

    public static TitleLayout fit(Box viewport) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int shift = (viewport.h() - DESIGN_H) / 2;
        int brandY = viewport.y() + BRAND_TOP + shift;
        int taglineY = brandY + Tokens.Type.MARK.leading() + Tokens.Space.SNUG;
        int primaryY = viewport.y() + MENU_TOP + shift;
        int footY = viewport.bottom() - FOOT_UP - Tokens.Type.BODY.leading();
        // A viewport shorter than the design (720p at GUI 3 is 240 tall) closes the gap between
        // the line and the menu first, and only then lifts the wordmark, so the foot keeps its edge.
        int latest = footY - Tokens.Space.BASE - ROW_H * ROW_COUNT - PRIMARY_GAP - PRIMARY_H;
        if (primaryY > latest) {
            primaryY = latest;
            int nearest = taglineY + Tokens.Type.BODY.leading() + Tokens.Space.SNUG;
            if (primaryY < nearest) {
                brandY -= nearest - primaryY;
                taglineY -= nearest - primaryY;
            }
        }
        int x = viewport.x() + MARGIN;
        int w = Math.min(MENU_W, viewport.w() - MARGIN * 2);
        Box primary = new Box(x, primaryY, w, PRIMARY_H);
        Box rows = new Box(x, primary.bottom() + PRIMARY_GAP, w, ROW_H * ROW_COUNT);
        int skyTop = Math.max(viewport.y() + Tokens.Space.COZY, viewport.y() + SKY_TOP + shift);
        return new TitleLayout(viewport.x() + MARGIN, brandY + MARK_BASELINE, taglineY, primary, rows,
            viewport.right() - SKY_RIGHT, skyTop, footY);
    }

    /** The box of menu item {@code index}: 0 is the way in, then the rows. */
    public Box item(int index) {
        return index == 0 ? primary : new Box(rows.x(), rows.y() + (index - 1) * ROW_H, rows.w(), ROW_H);
    }

    public int items() {
        return ROW_COUNT + 1;
    }

    /** The one box the whole menu is, for the keyboard. */
    public Box menu() {
        return Box.between(primary.x(), primary.y(), primary.right(), rows.bottom());
    }
}
