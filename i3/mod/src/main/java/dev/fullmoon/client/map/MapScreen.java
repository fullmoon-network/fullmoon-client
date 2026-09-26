package dev.fullmoon.client.map;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Palace;
import dev.fullmoon.client.ui.Surface;
import dev.fullmoon.client.ui.Tooltip;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.warp.WarpRoutes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.platform.InputConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * North-up map of client-loaded terrain, and the way a player asks to be moved along one of the
 * routes it marks.
 *
 * <p>It is not a {@link dev.fullmoon.client.ui.SurfaceScreen}: cursor-anchored zoom needs
 * {@code mouseScrolled}, and every pointer entry point there is final. So the surface is a field
 * and the events are wired by hand — the map gets the wheel, the surface gets the controls.
 */
public final class MapScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Map");
    private static final int PAN_CELLS = 12;

    private final Screen parent;
    private final Surface surface = new Surface();
    private final Button request;
    private MapViewport viewport;
    private TerrainSample terrain;
    private MapLayout layout = MapLayout.NONE;
    private List<RouteHit> routeHits = List.of();
    private List<MapMarkers.Placed> placed = List.of();
    private String selectedId = "";
    private int ticksUntilRefresh;
    private boolean sampleFailed;

    public MapScreen(Screen parent) {
        super(Component.translatable("fullmoon.map.title"));
        this.parent = parent;
        Entity player = player();
        double x = player == null ? 0.0 : player.getX();
        double z = player == null ? 0.0 : player.getZ();
        viewport = new MapViewport(x, z, 2);
        terrain = blank(1, 1);
        request = surface.add(new Button(Voice.LOUD, warp("action.request"), this::requested));
        request.enabled(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    protected void init() {
        layout = MapLayout.of(width, height, request.measure(), Button.HEIGHT);
        request.place(layout.action());
        refreshTerrain();
        // The map sends one packet and only when a player asks for it, so this line is what pairs a
        // capture with the server that published the routes the frame is marking.
        LOG.info("Map open: {}x{} cells at {} blocks per cell, {} published route(s) in {}",
            terrain.snapshot().width(), terrain.snapshot().height(), viewport.blocksPerCell(),
            currentRoutes().size(), dimensionName());
    }

    @Override
    public void tick() {
        if (ticksUntilRefresh <= 0) {
            refreshTerrain();
            ticksUntilRefresh = 20;
        } else {
            ticksUntilRefresh--;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.91f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        placed = placedRoutes();
        Optional<MapMarkers.Placed> under = markerAt(mouseX, mouseY);
        updateActionState();
        surface.hover(mouseX, mouseY);

        Painter painter = new Painter(gfx);
        header(painter);
        MapCanvas.draw(painter, layout.map(), MapLayout.CELL_SIZE, terrain, viewport,
            new MapCanvas.Marks(placed, selectedId,
                under.map(MapMarkers.Placed::id).orElse(""), true), playerPoint());
        rail(painter);
        actionBand(painter);
        surface.draw(painter);
        footer(painter);
        under.ifPresent(marker -> hint(painter, marker));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (FullmoonClient.opensMap(event)) {
            onClose();
            return true;
        }
        if (surface.key(Chord.from(event))) {
            return true;
        }
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            pan(-PAN_CELLS, 0);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            pan(PAN_CELLS, 0);
            return true;
        }
        if (key == InputConstants.KEY_UP) {
            pan(0, -PAN_CELLS);
            return true;
        }
        if (key == InputConstants.KEY_DOWN) {
            pan(0, PAN_CELLS);
            return true;
        }
        if (key == InputConstants.KEY_R || key == InputConstants.KEY_HOME) {
            recenter();
            return true;
        }
        if (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD) {
            zoom(true, layout.map().midX(), layout.map().midY());
            return true;
        }
        if (key == InputConstants.KEY_MINUS) {
            zoom(false, layout.map().midX(), layout.map().midY());
            return true;
        }
        // Enter without the button focused still asks, because a player who has just clicked a
        // marker has their hand on the mouse and the keyboard nowhere. Space is left alone: the
        // surface owns it when the button holds the keyboard, and it means nothing when it does not.
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            requested();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        surface.pointer(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
            double scrollY) {
        if (!layout.map().holds(mouseX, mouseY) || scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        zoom(scrollY > 0.0, (int) mouseX, (int) mouseY);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (surface.press(event.x(), event.y())) {
            return true;
        }
        Optional<MapMarkers.Placed> marker = markerAt(event.x(), event.y());
        if (marker.isPresent()) {
            // Chosen where it stands. Centring here would move the plane out from under the
            // pointer and take the marker that was just aimed at with it.
            choose(marker.orElseThrow().id(), false);
            return true;
        }
        for (RouteHit hit : routeHits) {
            if (hit.bounds().holds(event.x(), event.y())) {
                choose(hit.route().id(), true);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return surface.release(event.x(), event.y()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (surface.captured() == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        surface.pointer(event.x(), event.y());
        return true;
    }

    private void header(Painter painter) {
        Box content = layout.content();
        int brandY = content.y();
        int textX = Palace.brand(painter, Tokens.Type.DISPLAY, content.x(), brandY);
        Typeset.draw(painter, Tokens.Type.LABEL, tr("subtitle"), textX,
            brandY + Tokens.Type.DISPLAY.leading(), Tokens.Color.INK_TERTIARY);
        Typeset.drawRight(painter, Tokens.Type.LABEL, tr("authority"), content.right(),
            brandY + Tokens.Space.TIGHT, Tokens.Color.INK_TERTIARY);
        Palace.dancheong(painter, content.x(), layout.headerBottom() - Tokens.Space.COZY, content.w());
    }

    private void rail(Painter painter) {
        Box rail = layout.rail();
        painter.vRule(rail.x() - Tokens.Space.LOOSE, rail.y(), rail.h(),
            Tokens.Color.LINE_HAIRLINE);
        section(painter, tr("position"), layout.positionHeading());
        drawFact(painter, tr("centre"), coordinate(viewport.centerX(), viewport.centerZ()),
            layout.centreFact());
        drawFact(painter, tr("scale"), tr("scale.value", viewport.blocksPerCell()),
            layout.scaleFact());

        List<BridgeProtocol.Waypoint> routes = currentRoutes();
        section(painter, tr("routes", routes.size()), layout.routesHeading());
        int shown = layout.visibleRoutes(routes.size());
        List<RouteHit> hits = new java.util.ArrayList<>();
        for (int index = 0; index < shown; index++) {
            Box row = layout.routeRow(index);
            drawRoute(painter, row, routes.get(index));
            hits.add(new RouteHit(row, routes.get(index)));
        }
        routeHits = List.copyOf(hits);
        int below = layout.routeRow(shown).y();
        if (routes.isEmpty()) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("routes.empty"), rail.x(), below,
                rail.w(), 3, Tokens.Color.INK_TERTIARY);
        } else if (layout.beyond(routes.size()) > 0) {
            // A rail too short to list every route still has to admit it. The map keeps marking
            // the ones the list dropped, so the count is the only place the loss is visible.
            Typeset.draw(painter, Tokens.Type.LABEL,
                tr("routes.beyond", layout.beyond(routes.size())), rail.x(),
                below + Tokens.Space.SNUG, Tokens.Color.INK_TERTIARY);
        }
    }

    private void drawRoute(Painter painter, Box row, BridgeProtocol.Waypoint route) {
        boolean chosen = route.id().equals(selectedId);
        if (chosen) {
            painter.fill(row.x(), row.y(), row.w(), row.h(), Tokens.Color.ACCENT_WASH);
            painter.fill(row.x(), row.y(), Tokens.Stroke.FOCUS, row.h(), Tokens.Color.ACCENT);
        }
        int left = row.x() + (chosen ? Tokens.Space.COZY : 0);
        String name = Typeset.fittingPrefix(Tokens.Type.BODY_STRONG, route.name(), row.w());
        Typeset.draw(painter, Tokens.Type.BODY_STRONG, name, left,
            row.y() + Tokens.Space.SNUG, Tokens.Color.INK_PRIMARY);
        Typeset.tabular(painter, Tokens.Type.LABEL, route.x() + "  " + route.z(), left,
            row.y() + Tokens.Space.SNUG + Tokens.Type.BODY.leading(),
            Tokens.Color.INK_TERTIARY);
        painter.hRule(row.x(), row.bottom() - Tokens.Stroke.HAIR, row.w(),
            Tokens.Color.LINE_HAIRLINE);
    }

    /**
     * The one action the map has, and the server's answer to it. Kept out of the footer because the
     * footer is about terrain: the fraction of the frame that is real has nothing to do with whether
     * a warp was allowed, and reading one where the other was expected is worse than two lines.
     */
    private void actionBand(Painter painter) {
        Box band = layout.band();
        painter.hRule(band.x(), band.y(), band.w(), Tokens.Color.LINE_HAIRLINE);
        int labelY = band.y() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("chosen"), band.x(), labelY,
            Tokens.Color.INK_TERTIARY);
        Status status = status();
        Typeset.drawRight(painter, Tokens.Type.LABEL, status.copy(), band.right(), labelY,
            status.color());

        int nameY = labelY + Tokens.Type.LABEL.leading() + Tokens.Space.SNUG;
        BridgeProtocol.Waypoint route = selected();
        if (route == null) {
            Typeset.draw(painter, Tokens.Type.BODY, tr("chosen.none"), band.x(), nameY,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        Typeset.draw(painter, Tokens.Type.BODY_STRONG,
            Typeset.fittingPrefix(Tokens.Type.BODY_STRONG, route.name(), band.w()),
            band.x(), nameY, Tokens.Color.INK_PRIMARY);
    }

    /** The name and the coordinates of the marker the pointer is on, inside the plane it is on. */
    private void hint(Painter painter, MapMarkers.Placed marker) {
        Box raster = MapLayout.raster(layout.map(), MapLayout.CELL_SIZE, terrain.snapshot());
        int x = MapLayout.plot(raster.x(), marker.column(), MapLayout.CELL_SIZE);
        int y = MapLayout.plot(raster.y(), marker.row(), MapLayout.CELL_SIZE);
        BridgeProtocol.Waypoint route = route(marker.id());
        if (route == null) {
            return;
        }
        Box near = new Box(x - Tokens.Space.COZY, y - Tokens.Space.COZY,
            Tokens.Space.GUTTER, Tokens.Space.GUTTER);
        Tooltip.draw(painter, tr("marker.hint", route.name(), route.x(), route.z()), near,
            layout.map());
    }

    private void footer(Painter painter) {
        Box content = layout.content();
        int y = layout.footerTop() + Tokens.Space.COZY;
        painter.hRule(content.x(), layout.footerTop(), content.w(),
            Tokens.Color.LINE_HAIRLINE);
        Typeset.draw(painter, Tokens.Type.LABEL, tr("footer.keys"), content.x(), y,
            Tokens.Color.INK_TERTIARY);
        int color = sampleFailed ? Tokens.Color.STATUS_DANGER : Tokens.Color.INK_TERTIARY;
        String status = sampleFailed
            ? tr("status.failed")
            : tr("status.coverage", terrain.snapshot().mappedPercent());
        Typeset.drawRight(painter, Tokens.Type.LABEL, status, content.right(), y, color);
    }

    private void section(Painter painter, String label, int y) {
        Box rail = layout.rail();
        painter.fill(rail.x(), y + Tokens.Space.TIGHT, Tokens.Stroke.FOCUS,
            Tokens.Type.LABEL.px(), Tokens.Color.ACCENT);
        Typeset.draw(painter, Tokens.Type.LABEL, label,
            rail.x() + Tokens.Space.COZY, y, Tokens.Color.INK_TERTIARY);
    }

    private void drawFact(Painter painter, String label, String value, int y) {
        Box rail = layout.rail();
        Typeset.draw(painter, Tokens.Type.LABEL, label, rail.x(), y,
            Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.BODY_STRONG, value, rail.right(), y,
            Tokens.Color.INK_PRIMARY);
    }

    private void pan(int columns, int rows) {
        viewport = viewport.panCells(columns, rows);
        refreshTerrain();
    }

    private void zoom(boolean in, int mouseX, int mouseY) {
        Box map = layout.map();
        int column = Math.clamp((mouseX - map.x()) / MapLayout.CELL_SIZE, 0,
            terrain.snapshot().width() - 1);
        int row = Math.clamp((mouseY - map.y()) / MapLayout.CELL_SIZE, 0,
            terrain.snapshot().height() - 1);
        viewport = in
            ? viewport.zoomInAt(column, row, terrain.snapshot().width(), terrain.snapshot().height())
            : viewport.zoomOutAt(column, row, terrain.snapshot().width(), terrain.snapshot().height());
        refreshTerrain();
    }

    private void recenter() {
        Entity player = player();
        if (player != null) {
            viewport = new MapViewport(player.getX(), player.getZ(), viewport.blocksPerCell());
            refreshTerrain();
        }
    }

    /**
     * Chooses a route, and centres on it only when the choice came from the rail.
     *
     * <p>A choice outlives every viewport move, because panning away from a destination is not
     * changing your mind about it — the rail row stays lit and the marker stays ringed however far
     * off frame it goes.
     */
    private void choose(String id, boolean centre) {
        selectedId = id;
        BridgeProtocol.Waypoint route = selected();
        if (route == null) {
            return;
        }
        if (centre) {
            viewport = new MapViewport(route.x(), route.z(), viewport.blocksPerCell());
            refreshTerrain();
        }
        LOG.info("Map route chosen: {} at {} {} in {}", route.id(), route.x(), route.z(),
            route.world());
    }

    private void requested() {
        BridgeProtocol.Waypoint route = selected();
        // The state is checked here and not only by the surface, because Enter reaches this without
        // going through the button: a disabled action has to stay disabled on both roads to it.
        if (route != null && request.state(surface.focus()).live()) {
            FullmoonChannel.requestWarp(route);
        }
    }

    private void updateActionState() {
        boolean active = FullmoonChannel.state().mode() == BridgeState.Mode.ACTIVE;
        request.enabled(active && selected() != null);
        request.busy(FullmoonChannel.pendingWarp().isPresent());
    }

    /**
     * The server's answer to the request, in the warp contract's own words.
     *
     * <p>Its keys and not the map's: the ledger and the map ask for the same thing over the same
     * channel, and two namespaces for one answer is how one of them ends up saying something the
     * server did not mean.
     */
    private Status status() {
        if (FullmoonChannel.pendingWarp().isPresent()) {
            return new Status(warp("status.pending"), Tokens.Color.STATUS_WARN);
        }
        Optional<BridgeState.WarpOutcome> outcome =
            FullmoonChannel.warpOutcome(System.currentTimeMillis());
        if (outcome.isEmpty()) {
            return new Status(warp("status.ready"), Tokens.Color.INK_TERTIARY);
        }
        BridgeState.WarpOutcome value = outcome.orElseThrow();
        return value.ok()
            ? new Status(warp("status.accepted"), Tokens.Color.STATUS_LIVE)
            : new Status(
                warp("status.denied", warp("reason." + WarpRoutes.reasonKey(value.reason()))),
                Tokens.Color.STATUS_DANGER);
    }

    private Optional<MapMarkers.Placed> markerAt(double mouseX, double mouseY) {
        return layout.cellAt(mouseX, mouseY, terrain.snapshot())
            .flatMap(cell -> MapMarkers.at(placed, cell.column(), cell.row(),
                MapLayout.HIT_CELLS));
    }

    private void refreshTerrain() {
        Minecraft client = Minecraft.getInstance();
        int columns = Math.max(1, layout.map().w() / MapLayout.CELL_SIZE);
        int rows = Math.max(1, layout.map().h() / MapLayout.CELL_SIZE);
        if (client.level == null) {
            terrain = blank(columns, rows);
            sampleFailed = false;
            return;
        }
        try {
            terrain = TerrainSampler.sample(client.level, viewport, columns, rows);
            sampleFailed = false;
        } catch (RuntimeException error) {
            terrain = blank(columns, rows);
            sampleFailed = true;
            LOG.error("Failed to sample client-loaded terrain", error);
        }
        ticksUntilRefresh = 20;
    }

    private List<MapMarkers.Placed> placedRoutes() {
        List<MapMarkers.Marker> markers = currentRoutes().stream()
            .map(route -> new MapMarkers.Marker(
                route.id(), route.name(), route.x(), route.z()))
            .toList();
        return MapMarkers.place(markers, viewport,
            terrain.snapshot().width(), terrain.snapshot().height());
    }

    private MapViewport.GridPoint playerPoint() {
        Entity player = player();
        return player == null
            ? new MapViewport.GridPoint(-1.0, -1.0)
            : viewport.project(player.getX(), player.getZ(),
                terrain.snapshot().width(), terrain.snapshot().height());
    }

    private BridgeProtocol.Waypoint selected() {
        return route(selectedId);
    }

    private static BridgeProtocol.Waypoint route(String id) {
        return currentRoutes().stream()
            .filter(route -> route.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    private static List<BridgeProtocol.Waypoint> currentRoutes() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return List.of();
        }
        String dimension = client.level.dimension().identifier().toString();
        return FullmoonChannel.waypoints().stream()
            .filter(route -> WorldNames.matches(route.world(), dimension))
            .toList();
    }

    private static String dimensionName() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? "no world" : client.level.dimension().identifier().toString();
    }

    private static Entity player() {
        Minecraft client = Minecraft.getInstance();
        return client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
    }

    private static TerrainSample blank(int width, int height) {
        TerrainSnapshot.Cell cell = TerrainSnapshot.Cell.unmapped(Tokens.Color.SURFACE_SUNKEN);
        TerrainSnapshot snapshot = new TerrainSnapshot(width, height,
            Collections.nCopies(Math.multiplyExact(width, height), cell));
        return TerrainSample.of(snapshot);
    }

    private static String coordinate(double x, double z) {
        return String.format(Locale.ROOT, "%.0f  %.0f", x, z);
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.map." + key, args);
    }

    private static String warp(String key, Object... args) {
        return I18n.get("fullmoon.warp." + key, args);
    }

    private record RouteHit(Box bounds, BridgeProtocol.Waypoint route) {}

    private record Status(String copy, int color) {}
}
