package dev.fullmoon.client.warp;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where the route screen's parts go, from the viewport. The design size is the 640×360 GUI,
 * where the screen is a 512×312 field on the scrim — no panel — with the destinations in a
 * 236-wide list on the left, the chosen one's detail from 260 on the right, the status line 76
 * up from the foot and the way to go 32 up from it. A narrower viewport scales the two columns
 * with the field; nothing is ever laid out off the screen.
 */
public record WarpLayout(
        Box wrap,
        Box header,
        Box list,
        int divider,
        Box detail,
        Box compass,
        Box status,
        Box cta,
        int hintY,
        int row) {

    static final int LIST_W = 236;
    static final int DIVIDER_X = 248;
    static final int DETAIL_X = 260;
    static final int BODY_Y = 40;
    static final int COMPASS_Y = 34;
    static final int STATUS_UP = 76;
    static final int CTA_UP = 32;
    static final int CTA_W = 96;
    static final int FACT_KEY_W = 56;
    private static final int HINT_GAP = Tokens.Space.BASE;

    /** The height of a destination row: the client's own, or the mockup's under the capture rig. */
    public static int rowHeight() {
        return "mock".equals(System.getProperty("fullmoon.density", ""))
            ? Tokens.Size.ROUTE_ROW_MOCK : Tokens.Size.ROUTE_ROW;
    }

    /** A group's caption band: the group name in strong ink, eight above it and two under it. */
    public static int captionHeight() {
        return Tokens.Space.COZY + Tokens.Type.STRONG.leading() + Tokens.Space.TIGHT;
    }

    public static WarpLayout fit(Box viewport, int row) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int edge = Tokens.Size.EDGE;
        int w = Math.min(Tokens.Size.PANEL_W, viewport.w() - edge * 2);
        int h = viewport.h() - edge * 2;
        Box wrap = new Box(viewport.x() + (viewport.w() - w) / 2, viewport.y() + edge, w, h);
        Box header = new Box(wrap.x(), wrap.y(), w, Tokens.Size.HEADER);
        int listW = scaled(LIST_W, w);
        int divider = wrap.x() + scaled(DIVIDER_X, w);
        int detailX = wrap.x() + scaled(DETAIL_X, w);
        int bodyY = wrap.y() + BODY_Y;
        Box status = new Box(detailX, wrap.bottom() - STATUS_UP, wrap.right() - detailX, Tokens.Type.BODY.leading());
        Box list = Box.between(wrap.x(), bodyY, wrap.x() + listW, status.y());
        Box detail = Box.between(detailX, bodyY, wrap.right(), status.y());
        Box compass = new Box(detail.right() - Tokens.Size.COMPASS, detail.y() + COMPASS_Y,
            Tokens.Size.COMPASS, Tokens.Size.COMPASS);
        Box cta = new Box(detailX, wrap.bottom() - CTA_UP, wrap.right() - detailX, Tokens.Size.BUTTON);
        int hintY = viewport.bottom() - HINT_GAP - Tokens.Size.HINT;
        return new WarpLayout(wrap, header, list, divider, detail, compass, status, cta, hintY, row);
    }

    private static int scaled(int design, int w) {
        return w >= Tokens.Size.PANEL_W ? design : design * w / Tokens.Size.PANEL_W;
    }

    /** The box the go button takes: the right end of the call-to-action band. */
    public Box go() {
        int w = Math.min(CTA_W, cta.w());
        return new Box(cta.right() - w, cta.y(), w, cta.h());
    }

    /** The x the fact values start at, after the key column. */
    public int factValueX() {
        return detail.x() + FACT_KEY_W;
    }
}
