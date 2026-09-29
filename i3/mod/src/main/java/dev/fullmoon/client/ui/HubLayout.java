package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Where the client's own hub pages go — 풀문 설정 and the pages on its rail. The design size is
 * the 640×360 GUI, where the hub is the server menus' 512×316 pane: the 28-tall header, the
 * rail of pages 28 under it, a search field on a 40-tall strip, then a 224-wide list on the left
 * and the chosen thing's detail from the divider on the right, and the hint bar in the margin
 * under the pane. A narrower pane scales the list with itself; a shorter one loses rows.
 */
public record HubLayout(
        Box panel,
        Box header,
        Box tabs,
        Box search,
        Box list,
        int divider,
        Box detail,
        Box body,
        int hintY) {

    static final int LIST_W = 224;
    static final int SEARCH_STRIP = Tokens.Space.COZY + Tokens.Size.BUTTON + Tokens.Space.SNUG + Tokens.Space.COZY;
    static final int INSET = Tokens.Space.LOOSE;
    private static final int HINT_GAP = Tokens.Space.COZY;

    public static HubLayout fit(Box viewport) {
        return fit(viewport, true);
    }

    /** {@code searching} is whether the page has a search field; without one the list starts under the rail. */
    public static HubLayout fit(Box viewport, boolean searching) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int edge = Tokens.Size.EDGE;
        int panelW = Math.min(Tokens.Size.PANEL_W, viewport.w() - edge * 2);
        int panelH = Math.min(Tokens.Size.PANEL_H, viewport.h() - (Tokens.Size.HINT + HINT_GAP) * 2);
        Box panel = new Box(viewport.x() + (viewport.w() - panelW) / 2, viewport.y() + (viewport.h() - panelH) / 2,
            panelW, panelH);
        Box header = new Box(panel.x(), panel.y(), panel.w(), Tokens.Size.HEADER);
        Box tabs = new Box(panel.x(), header.bottom(), panel.w(), Tokens.Size.HEADER);
        Box search = searching
            ? new Box(panel.x() + INSET, tabs.bottom() + Tokens.Space.COZY, panel.w() - INSET * 2, Tokens.Size.BUTTON + Tokens.Space.SNUG)
            : Box.EMPTY;
        int listTop = searching ? tabs.bottom() + SEARCH_STRIP : tabs.bottom() + Tokens.Space.COZY;
        int listW = panelW >= Tokens.Size.PANEL_W ? LIST_W : LIST_W * panelW / Tokens.Size.PANEL_W;
        Box list = Box.between(panel.x(), listTop, panel.x() + listW, panel.bottom());
        int divider = list.right();
        Box detail = Box.between(divider + INSET, listTop + Tokens.Space.COZY, panel.right() - INSET, panel.bottom() - INSET);
        Box body = Box.between(panel.x() + INSET, listTop + Tokens.Space.COZY, panel.right() - INSET, panel.bottom() - INSET);
        return new HubLayout(panel, header, tabs, search, list, divider, detail, body, panel.bottom() + HINT_GAP);
    }

    /** How many whole one-line rows the list holds. */
    public int rows() {
        return Math.max(1, list.h() / Tokens.Size.ROW_ONE);
    }
}
