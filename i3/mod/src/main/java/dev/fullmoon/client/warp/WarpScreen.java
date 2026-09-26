package dev.fullmoon.client.warp;

import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.DevChrome;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.Palace;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** A server-owned destination ledger whose only client action is an id-based request. */
public final class WarpScreen extends SurfaceScreen {
    private static final int MAX_CONTENT = 640;
    private static final int MASTER_MAX = 248;
    private static final int MAX_ROWS = 7;
    private static final int COMPACT_HEIGHT = 320;
    private static final int FACT_BAND_HEIGHT = 72;
    private static final Runnable INERT = () -> {};

    private final Screen parent;
    private final List<BridgeProtocol.Waypoint> routes;
    private final int selected;
    private final ListPanel destinations;
    private final Button request;

    private Box content = Box.EMPTY;
    private Box body = Box.EMPTY;
    private Box detail = Box.EMPTY;
    private int footerY;

    public WarpScreen(Screen parent) {
        this(parent, "");
    }

    private WarpScreen(Screen parent, String selectedId) {
        super(Component.translatable("fullmoon.warp.title"));
        this.parent = parent;
        this.routes = WarpRoutes.ordered(FullmoonChannel.waypoints());
        this.selected = selectedIndex(routes, selectedId);
        this.destinations = surface.add(new ListPanel(
            tr("destinations.label"), rows(routes), tr("destinations.empty"), selected,
            this::picked));
        this.request = surface.add(new Button(Voice.LOUD, tr("action.request"), this::requested));
        request.enabled(current() != null);
    }

    public WarpScreen refreshed() {
        BridgeProtocol.Waypoint current = current();
        return new WarpScreen(parent, current == null ? "" : current.id());
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
        boolean compact = height < COMPACT_HEIGHT;
        int edge = compact ? Tokens.Space.LOOSE : Tokens.Space.SECTION;
        int frame = Math.min(MAX_CONTENT, width - edge * 2);
        content = new Box((width - frame) / 2, edge, frame, height - edge * 2);
        footerY = content.bottom() - Tokens.Type.LABEL.leading() - Tokens.Space.COZY;

        int bodyY = content.y() + headerHeight() + Tokens.Space.GUTTER;
        body = Box.between(content.x(), bodyY, content.right(), footerY - Tokens.Space.GUTTER);
        int masterWidth = Math.min(MASTER_MAX, body.w() * 2 / 5);
        Box.Split columns = body.splitLeft(masterWidth, Tokens.Space.SECTION);
        int listY = body.y() + DevChrome.sectionHeadHeight();
        int rows = Math.min(MAX_ROWS, Math.max(1, routes.size()));
        int listBottom = Math.min(body.bottom(), listY + ListPanel.heightFor(rows));
        destinations.place(Box.between(
            columns.head().x(), listY, columns.head().right(), listBottom));
        detail = columns.rest();

        int buttonWidth = Math.min(detail.w(), Math.max(request.measure(), detail.w() / 2));
        request.place(new Box(detail.right() - buttonWidth, body.bottom() - Button.HEIGHT,
            buttonWidth, Button.HEIGHT));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.84f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        updateActionState();
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        header(painter);
        DevChrome.sectionHead(painter, tr("destinations.count", routes.size()), body.x(), body.y());
        details(painter);
        surface.draw(painter);
        footer(painter);
    }

    private void header(Painter painter) {
        int brandY = content.y();
        int textX = Palace.brand(painter, Tokens.Type.DISPLAY, content.x(), brandY);
        Typeset.draw(painter, Tokens.Type.LABEL, tr("subtitle"), textX,
            brandY + Tokens.Type.DISPLAY.leading(), Tokens.Color.INK_TERTIARY);
        Typeset.drawRight(painter, Tokens.Type.LABEL, tr("authority"), content.right(),
            brandY + Tokens.Space.TIGHT, Tokens.Color.INK_TERTIARY);
        Palace.dancheong(painter, content.x(), content.y() + headerHeight() - Tokens.Space.SNUG,
            content.w());
    }

    private void details(Painter painter) {
        painter.vRule(detail.x() - Tokens.Space.LOOSE, body.y(), body.h(),
            Tokens.Color.LINE_HAIRLINE);
        BridgeProtocol.Waypoint route = current();
        if (route == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("destinations.empty.detail"),
                detail.x(), body.y() + DevChrome.sectionHeadHeight(), detail.w(), 3,
                Tokens.Color.INK_TERTIARY);
            return;
        }

        int y = DevChrome.sectionHead(painter, group(route), detail.x(), body.y());
        Typeset.draw(painter, Tokens.Type.TITLE, route.name(), detail.x(), y,
            Tokens.Color.INK_PRIMARY);
        int idY = y + Tokens.Type.TITLE.leading() + Tokens.Space.SNUG;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("detail.route_id", route.id()),
            detail.x(), idY, Tokens.Color.INK_TERTIARY);

        int bandY = idY + Tokens.Type.LABEL.leading() + Tokens.Space.COZY;
        drawFacts(painter, route, bandY);

        int noteY = body.bottom() - Button.HEIGHT - Tokens.Type.BODY.leading()
            - Tokens.Space.COZY;
        Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("detail.authority"), detail.x(),
            noteY, detail.w(), 2, Tokens.Color.INK_SECONDARY);
    }

    private void drawFacts(Painter painter, BridgeProtocol.Waypoint route, int y) {
        painter.fill(detail.x(), y, detail.w(), FACT_BAND_HEIGHT, Tokens.Radius.SM,
            Tokens.Color.SURFACE_SUNKEN);
        painter.border(detail.x(), y, detail.w(), FACT_BAND_HEIGHT, Tokens.Radius.SM,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);

        int left = detail.x() + Tokens.Space.COZY;
        int right = detail.right() - Tokens.Space.COZY;
        int labelY = y + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("detail.world"), left, labelY,
            Tokens.Color.INK_TERTIARY);
        Typeset.drawRight(painter, Tokens.Type.BODY_STRONG, route.world(), right, labelY,
            Tokens.Color.INK_PRIMARY);

        int ruleY = labelY + Tokens.Type.BODY.leading() + Tokens.Space.SNUG;
        painter.hRule(left, ruleY, right - left, Tokens.Color.LINE_HAIRLINE);
        int coordinatesY = ruleY + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("detail.coordinates"), left, coordinatesY,
            Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.BODY_STRONG, coordinates(route), right,
            coordinatesY, Tokens.Color.INK_PRIMARY);

        int distanceY = coordinatesY + Tokens.Type.BODY.leading() + Tokens.Space.SNUG;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("detail.distance"), left, distanceY,
            Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.BODY_STRONG, distance(route), right,
            distanceY, Tokens.Color.ACCENT);
    }

    private void footer(Painter painter) {
        painter.hRule(content.x(), footerY, content.w(), Tokens.Color.LINE_HAIRLINE);
        int y = footerY + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, tr("footer.keys"), content.x(), y,
            Tokens.Color.INK_TERTIARY);
        Status status = status();
        Typeset.drawRight(painter, Tokens.Type.LABEL, status.copy(), content.right(), y,
            status.color());
    }

    private void updateActionState() {
        boolean active = FullmoonChannel.state().mode() == BridgeState.Mode.ACTIVE;
        boolean pending = FullmoonChannel.pendingWarp().isPresent();
        request.enabled(active && current() != null);
        request.busy(pending);
    }

    private Status status() {
        Optional<BridgeState.PendingWarp> pending = FullmoonChannel.pendingWarp();
        if (pending.isPresent()) {
            return new Status(tr("status.pending"), Tokens.Color.STATUS_WARN);
        }
        Optional<BridgeState.WarpOutcome> outcome =
            FullmoonChannel.warpOutcome(System.currentTimeMillis());
        if (outcome.isEmpty()) {
            return new Status(tr("status.ready"), Tokens.Color.INK_TERTIARY);
        }
        BridgeState.WarpOutcome value = outcome.orElseThrow();
        return value.ok()
            ? new Status(tr("status.accepted"), Tokens.Color.STATUS_LIVE)
            : new Status(tr("status.denied", reason(value.reason())), Tokens.Color.STATUS_DANGER);
    }

    private void picked(int row) {
        showNext(new WarpScreen(parent, routes.get(row).id()));
    }

    private void requested() {
        BridgeProtocol.Waypoint route = current();
        if (route != null) {
            FullmoonChannel.requestWarp(route);
        }
    }

    private BridgeProtocol.Waypoint current() {
        return selected < 0 ? null : routes.get(selected);
    }

    private static int headerHeight() {
        return Tokens.Type.DISPLAY.leading() + Tokens.Type.LABEL.leading()
            + Tokens.Space.GUTTER;
    }

    private static List<ListRow> rows(List<BridgeProtocol.Waypoint> routes) {
        return routes.stream().map(route ->
            new ListRow(route.name(), () -> distance(route), INERT)).toList();
    }

    private static int selectedIndex(List<BridgeProtocol.Waypoint> routes, String selectedId) {
        if (routes.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < routes.size(); index++) {
            if (routes.get(index).id().equals(selectedId)) {
                return index;
            }
        }
        return 0;
    }

    private static String group(BridgeProtocol.Waypoint route) {
        return route.group().isBlank() ? tr("detail.no_group") : route.group();
    }

    private static String coordinates(BridgeProtocol.Waypoint route) {
        return "X " + route.x() + "  Y " + route.y() + "  Z " + route.z();
    }

    private static String distance(BridgeProtocol.Waypoint route) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return tr("distance.unknown");
        }
        Entity player = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        return tr("distance.meters",
            WarpRoutes.distanceMeters(route, player.getX(), player.getY(), player.getZ()));
    }

    private static String reason(String reason) {
        return tr("reason." + WarpRoutes.reasonKey(reason));
    }

    private static void showNext(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(screen));
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.warp." + key, args);
    }

    private record Status(String copy, int color) {}
}
