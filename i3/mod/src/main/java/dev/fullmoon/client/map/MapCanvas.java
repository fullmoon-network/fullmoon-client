package dev.fullmoon.client.map;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.gui.navigation.ScreenRectangle;

import org.joml.Matrix3x2f;

/** Shared north-up terrain plane for the full map and HUD minimap. */
public final class MapCanvas {
    private static final int REGION_GRID_BLOCKS = 64;

    private MapCanvas() {}

    public static void draw(Painter painter, Box bounds, int cellSize,
            TerrainSample terrain, MapViewport viewport, Marks marks,
            MapViewport.GridPoint player) {
        Box raster = MapLayout.raster(bounds, cellSize, terrain.snapshot());

        painter.fill(raster.x(), raster.y(), raster.w(), raster.h(),
            Tokens.Color.SURFACE_SUNKEN);
        painter.gfx().nextStratum();
        ScreenRectangle clip = new ScreenRectangle(
            raster.x(), raster.y(), raster.w(), raster.h());
        painter.gfx().guiRenderState.addGuiElement(new TerrainRenderState(
            new Matrix3x2f(painter.gfx().pose()), raster.x(), raster.y(), cellSize,
            terrain.runs(), terrain.snapshot().width(), terrain.snapshot().height(), clip));
        painter.gfx().nextStratum();

        painter.pushClip(raster.x(), raster.y(), raster.w(), raster.h());
        drawRegionGrid(painter, raster, cellSize, terrain.snapshot(), viewport);
        drawMarkers(painter, raster, cellSize, marks);
        drawPlayer(painter, raster, cellSize, terrain.snapshot(), player);
        painter.popClip();
        painter.border(raster.x(), raster.y(), raster.w(), raster.h(), Tokens.Radius.NONE,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
        Typeset.draw(painter, Tokens.Type.MICRO, "N", raster.midX() - Tokens.Space.TIGHT,
            raster.y() + Tokens.Space.SNUG, Tokens.Color.INK_PRIMARY);
    }

    private static void drawRegionGrid(Painter painter, Box raster, int cellSize,
            TerrainSnapshot snapshot, MapViewport viewport) {
        int gridColor = Rgb.alpha(Tokens.Color.LINE_HAIRLINE, 0.52f);
        for (int column = 0; column < snapshot.width(); column++) {
            MapViewport.WorldPoint point = viewport.worldAt(
                column, 0, snapshot.width(), snapshot.height());
            if (Math.floorMod(point.x(), REGION_GRID_BLOCKS) < viewport.blocksPerCell()) {
                painter.vRule(raster.x() + column * cellSize, raster.y(), raster.h(), gridColor);
            }
        }
        for (int row = 0; row < snapshot.height(); row++) {
            MapViewport.WorldPoint point = viewport.worldAt(
                0, row, snapshot.width(), snapshot.height());
            if (Math.floorMod(point.z(), REGION_GRID_BLOCKS) < viewport.blocksPerCell()) {
                painter.hRule(raster.x(), raster.y() + row * cellSize, raster.w(), gridColor);
            }
        }
    }

    private static void drawMarkers(Painter painter, Box raster, int cellSize, Marks marks) {
        List<MapMarkers.Placed> named = marks.labels()
            ? MapMarkers.declutter(marks.markers(), marker -> labelBox(raster, cellSize, marker))
            : marks.markers();
        for (MapMarkers.Placed marker : named) {
            int x = MapLayout.plot(raster.x(), marker.column(), cellSize);
            int y = MapLayout.plot(raster.y(), marker.row(), cellSize);
            boolean chosen = marker.id().equals(marks.chosenId());
            int ink = chosen ? Tokens.Color.ACCENT : Tokens.Color.INK_PRIMARY;
            if (chosen || marker.id().equals(marks.underPointerId())) {
                // The chosen route has to stay findable in a frame with no cursor in it, and the
                // one under the pointer has to answer before the hint does.
                painter.ring(x, y, Tokens.Space.COZY, Tokens.Stroke.HAIR, ink);
            }
            painter.ring(x, y, Tokens.Space.SNUG, Tokens.Stroke.FOCUS, ink);
            painter.dot(x, y, Tokens.Space.HAIR, ink);
            if (marks.labels() && !marker.label().isBlank()) {
                // Terrain is the subject and it can be any colour, so the name carries its own
                // ground rather than trusting whatever block it lands on.
                Box plate = labelBox(raster, cellSize, marker);
                painter.fill(plate.x(), plate.y(), plate.w(), plate.h(),
                    Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.78f));
                Typeset.draw(painter, Tokens.Type.MICRO, marker.label(),
                    x + Tokens.Space.COZY, y - Tokens.Space.SNUG, ink);
            }
        }
    }

    /** The plate a label sits on, which is also the area another label may not take. */
    private static Box labelBox(Box raster, int cellSize, MapMarkers.Placed marker) {
        return new Box(MapLayout.plot(raster.x(), marker.column(), cellSize) + Tokens.Space.COZY,
            MapLayout.plot(raster.y(), marker.row(), cellSize) - Tokens.Space.SNUG,
            Typeset.width(Tokens.Type.MICRO, marker.label()),
            Tokens.Type.MICRO.px()).inset(-Tokens.Space.TIGHT);
    }

    /**
     * What the plane marks besides terrain: the routes, the one a surface has chosen, the one the
     * pointer is on, and whether the names are drawn at all — a minimap has room for the rings and
     * not for the words.
     */
    public record Marks(List<MapMarkers.Placed> markers, String chosenId, String underPointerId,
            boolean labels) {
        public Marks {
            markers = List.copyOf(markers);
            chosenId = chosenId == null ? "" : chosenId;
            underPointerId = underPointerId == null ? "" : underPointerId;
        }

        /** Rings and names with nothing chosen, which is every plane the pointer cannot reach. */
        public static Marks of(List<MapMarkers.Placed> markers, boolean labels) {
            return new Marks(markers, "", "", labels);
        }
    }

    private static void drawPlayer(Painter painter, Box raster, int cellSize,
            TerrainSnapshot snapshot, MapViewport.GridPoint player) {
        if (player.column() < 0.0 || player.column() > snapshot.width() - 1
                || player.row() < 0.0 || player.row() > snapshot.height() - 1) {
            return;
        }
        int x = MapLayout.plot(raster.x(), player.column(), cellSize);
        int y = MapLayout.plot(raster.y(), player.row(), cellSize);
        painter.ring(x, y, Tokens.Space.SNUG,
            Tokens.Stroke.FOCUS, Tokens.Color.ACCENT);
        painter.hRule(x - Tokens.Space.COZY, y,
            Tokens.Space.GUTTER, Tokens.Color.ACCENT);
        painter.vRule(x, y - Tokens.Space.COZY,
            Tokens.Space.GUTTER, Tokens.Color.ACCENT);
    }
}
