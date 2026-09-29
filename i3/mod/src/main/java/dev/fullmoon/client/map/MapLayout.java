package dev.fullmoon.client.map;

import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/**
 * Every map measurement that is arithmetic rather than drawing: the chrome a window divides into,
 * the plane the terrain fills inside it, and how many published routes the rail can show.
 *
 * <p>Separate from {@link MapScreen} and {@link MapCanvas} because each of these numbers fails
 * quietly. A plane that is not a whole number of cells leaves a seam no one reads as a bug, a hit
 * test measured against the wrong origin aims at rings it cannot reach, and a rail one row short
 * drops a destination from the list while the map keeps marking it. None of the three announces
 * itself in a frame, so all three are decided here where a test can ask.
 */
public record MapLayout(Box content, Box map, Box rail, Box band, Box action) {
    /** GUI px per terrain cell. The plane is snapped to a multiple of it in both axes. */
    public static final int CELL_SIZE = 3;

    /** Below this window width the rail narrows and the page margin tightens. */
    public static final int COMPACT_WIDTH = 700;

    public static final int ROUTE_ROW_HEIGHT = 34;

    /**
     * How near the pointer has to be, in cells, to be on a marker. Two and a half cells is the
     * outer ring {@link MapCanvas} draws around a chosen one: the target a player aims at is the
     * ring they can see, not the block under it.
     */
    public static final double HIT_CELLS = 2.5;

    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 24;
    private static final int RAIL_WIDTH = 208;
    private static final int COMPACT_RAIL_WIDTH = 148;

    /** The window a screen has before {@code init} divides one: nothing anywhere, nothing hit. */
    public static final MapLayout NONE =
        new MapLayout(Box.EMPTY, Box.EMPTY, Box.EMPTY, Box.EMPTY, Box.EMPTY);

    /**
     * Divides a window. {@code actionWidth} is what the request button asks for and
     * {@code actionHeight} what it stands at — passed in rather than read off the widget so this
     * arithmetic stays free of the render tree.
     */
    public static MapLayout of(int width, int height, int actionWidth, int actionHeight) {
        int edge = width < COMPACT_WIDTH ? Tokens.Space.LOOSE : Tokens.Space.SECTION;
        Box content = new Box(edge, edge, Math.max(0, width - edge * 2),
            Math.max(0, height - edge * 2));
        Box body = Box.between(content.x(), content.y() + HEADER_HEIGHT,
            content.right(), content.bottom() - FOOTER_HEIGHT);
        int railWidth = width < COMPACT_WIDTH ? COMPACT_RAIL_WIDTH : RAIL_WIDTH;
        Box.Split columns = body.splitLeft(body.w() - railWidth - Tokens.Space.SECTION,
            Tokens.Space.SECTION);
        Box slot = columns.head();
        Box map = new Box(slot.x(), slot.y(),
            Math.max(CELL_SIZE, slot.w() / CELL_SIZE * CELL_SIZE),
            Math.max(CELL_SIZE, slot.h() / CELL_SIZE * CELL_SIZE));
        Box rail = columns.rest();
        int bandHeight = bandHeight(actionHeight);
        Box band = new Box(rail.x(), rail.bottom() - bandHeight, rail.w(), bandHeight);
        int buttonWidth = Math.min(band.w(), Math.max(actionWidth, band.w() * 2 / 3));
        Box action = new Box(band.right() - buttonWidth, band.bottom() - actionHeight,
            buttonWidth, actionHeight);
        return new MapLayout(content, map, rail, band, action);
    }

    public int headerBottom() {
        return content.y() + HEADER_HEIGHT;
    }

    public int footerTop() {
        return content.bottom() - FOOTER_HEIGHT;
    }

    /**
     * The rail runs down in fixed steps, and each one is named here rather than accumulated in the
     * drawing: the route rows begin where the facts above them end, so a screen that adds the steps
     * up for itself is a screen that can disagree with {@link #routeCapacity()}.
     */
    public int positionHeading() {
        return rail.y();
    }

    public int centreFact() {
        return positionHeading() + headingHeight();
    }

    public int scaleFact() {
        return centreFact() + Tokens.Type.BODY.leading() + Tokens.Space.SNUG;
    }

    public int routesHeading() {
        return scaleFact() + Tokens.Type.BODY.leading() + Tokens.Space.SECTION;
    }

    /** Where the first route row starts, below the position facts and the routes heading. */
    public int routesTop() {
        return routesHeading() + headingHeight();
    }

    /** How many rows fit whole between the routes heading and the action band. */
    public int routeCapacity() {
        return Math.max(0, (band.y() - Tokens.Space.GUTTER - routesTop()) / ROUTE_ROW_HEIGHT);
    }

    public int visibleRoutes(int published) {
        return Math.min(Math.max(0, published), routeCapacity());
    }

    /** The routes the rail had no room for, which the count under the list has to admit to. */
    public int beyond(int published) {
        return Math.max(0, published) - visibleRoutes(published);
    }

    public Box routeRow(int index) {
        return new Box(rail.x(), routesTop() + index * ROUTE_ROW_HEIGHT, rail.w(),
            ROUTE_ROW_HEIGHT);
    }

    /**
     * Where the pointer is in cell space, or empty when it is not over the map at all.
     *
     * <p>Measured from the plane and bounded by the slot: the terrain is centred in whatever the
     * window left for it, so the origin a marker was drawn from is not the corner of the box a
     * pointer is tested against.
     */
    public Optional<MapViewport.GridPoint> cellAt(double px, double py, TerrainSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("A terrain snapshot is required");
        }
        if (!map.holds(px, py)) {
            return Optional.empty();
        }
        Box raster = raster(map, CELL_SIZE, snapshot);
        return Optional.of(new MapViewport.GridPoint((px - raster.x()) / CELL_SIZE,
            (py - raster.y()) / CELL_SIZE));
    }

    /**
     * The plane the terrain fills inside {@code bounds}. A screen sizes its slot to whole cells and
     * gets the same box back, but the box is what a pointer is measured against, so the screen and
     * the drawing cannot each work it out for themselves.
     */
    public static Box raster(Box bounds, int cellSize, TerrainSnapshot snapshot) {
        if (bounds == null || snapshot == null || cellSize <= 0) {
            throw new IllegalArgumentException(
                "Plane bounds, a snapshot and a positive cell size are required");
        }
        return bounds.centred(snapshot.width() * cellSize, snapshot.height() * cellSize);
    }

    /**
     * Where a cell coordinate lands on screen. Shared because a surface that hit-tests markers or
     * hangs a hint off one has to round the same way the ring it is aiming at was drawn.
     */
    public static int plot(int origin, double cell, int cellSize) {
        return origin + (int) Math.round(cell * cellSize);
    }

    private static int headingHeight() {
        return Tokens.Type.MICRO.leading() + Tokens.Space.COZY;
    }

    private static int bandHeight(int actionHeight) {
        return Tokens.Space.COZY + Tokens.Type.MICRO.leading() + Tokens.Space.SNUG
            + Tokens.Type.STRONG.leading() + Tokens.Space.COZY + actionHeight;
    }
}
