package dev.fullmoon.client.season;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * The season pass's frame: one pane of glass with a header, the tab rail under it, the page in the
 * middle and a foot line along the bottom, and the hint bar in the margin below the pane.
 *
 * <p>The design size is the 640×360 GUI, where the pane is 592 wide — wider than a server menu,
 * because a track of fifty levels is read sideways and every column the pane gains is one less
 * scroll. A bigger viewport keeps the same pane centred; a smaller one shrinks it to its margins.
 */
public record SeasonLayout(Box panel, Box header, Box tabs, Box body, Box foot, int hintY) {
    public static final int PANEL_W = 592;
    public static final int FOOT_H = Tokens.Space.SECTION;
    private static final int HINT_GAP = Tokens.Space.COZY;

    public static SeasonLayout fit(Box viewport) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        int panelW = Math.min(PANEL_W, viewport.w() - Tokens.Size.EDGE * 2);
        int panelH = Math.min(Tokens.Size.PANEL_H, viewport.h() - (Tokens.Size.HINT + HINT_GAP) * 2);
        Box panel = new Box(viewport.x() + (viewport.w() - panelW) / 2,
            viewport.y() + (viewport.h() - panelH) / 2, panelW, panelH);
        Box header = new Box(panel.x(), panel.y(), panel.w(), Tokens.Size.HEADER);
        Box tabs = new Box(panel.x(), header.bottom(), panel.w(), Tokens.Size.HEADER);
        Box foot = new Box(panel.x(), panel.bottom() - FOOT_H, panel.w(), FOOT_H);
        Box body = Box.between(panel.x(), tabs.bottom(), panel.right(), foot.y());
        return new SeasonLayout(panel, header, tabs, body, foot, panel.bottom() + HINT_GAP);
    }

    public TrackLayout track() {
        return TrackLayout.of(body);
    }

    public MissionLayout missions(int daily, int weekly, int season) {
        return MissionLayout.of(body, side(), daily, weekly, season);
    }

    /**
     * Where the bulk-claim control goes: right-aligned in the standing row, while that row has a
     * button's height to hold it. On a compact standing it is empty — a button there would sit on
     * the reward tiles — and the keyboard carries the bulk claim instead, advertised in the hint
     * bar.
     */
    public static java.util.Optional<Box> claimAll(Box summary, int width) {
        if (summary.h() < Tokens.Size.BUTTON) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Box(summary.right() - width,
            summary.midY() - Tokens.Size.BUTTON / 2, width, Tokens.Size.BUTTON));
    }

    /**
     * The list or the perks on the left of a page, and the column beside them. The column keeps
     * the design width while the viewport allows it and gives ground on a small one, so the list
     * beside it never falls under a row's worth of pixels.
     */
    public Box side() {
        int w = Math.min(Tokens.Size.DETAIL, Math.max(body.w() * 2 / 5, 96));
        return Box.between(body.right() - w - Tokens.Space.LOOSE, body.y() + Tokens.Space.COZY,
            body.right() - Tokens.Space.LOOSE, body.bottom() - Tokens.Space.COZY);
    }

    /** The x of the hairline between a page's list and its side column. */
    public int divider() {
        return side().x() - Tokens.Space.LOOSE + Tokens.Space.LOOSE / 3;
    }
}
