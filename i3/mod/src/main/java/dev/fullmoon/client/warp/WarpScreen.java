package dev.fullmoon.client.warp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.map.MapScreen;
import dev.fullmoon.client.menu.ServerMenuSample;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Fade;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.IconButton;
import dev.fullmoon.client.ui.SurfaceScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * 항로: the server's destinations on the scrim, grouped, with the chosen one's bearing and
 * distance beside them and a compass that points the way. The only thing the client ever sends
 * is the id of the one the player asked for; the server checks permission and cooldown and
 * moves them, and the status line says what it answered.
 */
public final class WarpScreen extends SurfaceScreen {
    private static final int RISE = Tokens.Space.COZY;
    private static final float SCRIM = 0.62f;
    private static final float DOT = 2.5f;
    private static final int FACT_PITCH = Tokens.Type.BODY.leading() + Tokens.Space.TIGHT + 1;
    private static final float COMPASS_RING = Tokens.Size.COMPASS / 2.0f - Tokens.Space.SNUG;
    private static final float NEEDLE = Tokens.Size.COMPASS / 2.0f - Tokens.Space.COZY;

    /** The destination the server last took the player to, so the screen can say so on return. */
    private static String lastAccepted = "";

    private final Screen parent;
    private final List<BridgeProtocol.Waypoint> routes;
    private final RouteBoard board;
    private final GoButton go;
    private final IconButton closeButton;
    private final Fade fade;
    private WarpLayout layout;
    private boolean keyboard;

    public WarpScreen(Screen parent) {
        this(parent, "", true);
    }

    private WarpScreen(Screen parent, String selectedId, boolean opening) {
        super(Component.translatable("fullmoon.warp.title"));
        this.parent = parent;
        List<BridgeProtocol.Waypoint> live = FullmoonChannel.waypoints();
        this.routes = WarpRoutes.ordered(live.isEmpty() ? ServerMenuSample.waypoints() : live);
        this.board = surface.add(new RouteBoard(routes, selectedIndex(routes, selectedId),
            this::rowMeta, this::isHere, new RouteBoard.Listener() {
                @Override
                public void moved(BridgeProtocol.Waypoint route) {
                    UiSounds.play(UiSounds.Cue.FOCUS);
                }

                @Override
                public void go() {
                    requested();
                }
            }));
        this.go = surface.add(new GoButton(tr("action.go"), this::requested));
        this.closeButton = surface.add(new IconButton(IconButton.Glyph.CLOSE, "", this::onClose));
        this.fade = opening ? Fade.opening() : Fade.settled();
        if (opening) {
            UiSounds.play(UiSounds.Cue.OPEN);
        }
        surface.focus().point(board);
    }

    /** The same screen over a newer snapshot: the cursor stays on the destination it was on. */
    public WarpScreen refreshed() {
        BridgeProtocol.Waypoint current = board.current();
        return new WarpScreen(parent, current == null ? "" : current.id(), false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (fade.closing()) {
            return;
        }
        UiSounds.play(UiSounds.Cue.CLOSE);
        fade.close();
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    protected void init() {
        layout = WarpLayout.fit(new Box(0, 0, width, height), WarpLayout.rowHeight());
        board.layout(layout);
        go.place(layout.go());
        Box header = layout.header();
        closeButton.place(new Box(header.right() - IconButton.SIZE + Tokens.Space.BASE,
            header.y() + (header.h() - IconButton.SIZE) / 2, IconButton.SIZE, IconButton.SIZE));
    }

    @Override
    public void tick() {
        FullmoonChannel.warpOutcome(System.currentTimeMillis()).filter(BridgeState.WarpOutcome::ok)
            .ifPresent(outcome -> routes.stream().filter(route -> route.id().equals(outcome.id())).findFirst()
                .ifPresent(route -> lastAccepted = route.name()));
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (fade.closing()) {
            return true;
        }
        keyboard = true;
        if (FullmoonClient.opensMap(event)) {
            Minecraft.getInstance().setScreen(new MapScreen(this));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboard = false;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, SCRIM * fade.appearance()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean active = FullmoonChannel.state().mode() == BridgeState.Mode.ACTIVE;
        boolean pending = FullmoonChannel.pendingWarp().isPresent();
        go.enabled(active && board.current() != null);
        go.busy(pending);
        if (!fade.closing()) {
            surface.hover(mouseX, mouseY);
        }
        Painter painter = new Painter(gfx);
        float t = fade.appearance();
        painter.opacity(t);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (1.0f - t) * RISE);
        header(painter);
        Glass.vhair(painter, layout.divider(), layout.list().y(), layout.list().h());
        detail(painter);
        status(painter);
        cta(painter);
        surface.draw(painter);
        gfx.pose().popMatrix();
        hints(painter);
    }

    private void header(Painter painter) {
        Box header = layout.header();
        int x = header.x();
        int textY = Typeset.centred(Tokens.Type.TITLE, header.y(), header.h());
        x += Typeset.draw(painter, Tokens.Type.TITLE, tr("title"), x, textY, Tokens.Color.INK_PRIMARY) + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, tr("count", worldLabel(), routes.size()), x,
            Typeset.centred(Tokens.Type.BODY, header.y(), header.h()), Tokens.Color.INK_TERTIARY);
        int right = closeButton.bounds().x() - Tokens.Space.COZY;
        Glass.keycap(painter, right - Glass.keycapWidth("Esc"), header.y() + (header.h() - Tokens.Size.KEYCAP) / 2, "Esc");
        Glass.hair(painter, header.x(), header.bottom() - 1, header.w());
    }

    private void detail(Painter painter) {
        Box d = layout.detail();
        BridgeProtocol.Waypoint route = board.current();
        if (route == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("destinations.empty.detail"), d.x(), d.y(), d.w(), 3,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        // Every line here sits in its role's line box, as the mockup's flex rows place theirs.
        int y = d.y();
        int titleW = Typeset.draw(painter, Tokens.Type.TITLE, route.name(), d.x(),
            Typeset.centred(Tokens.Type.TITLE, y, Tokens.Type.TITLE.leading()), Tokens.Color.INK_PRIMARY);
        int groupTop = y + Tokens.Type.TITLE.leading() - Tokens.Type.BODY.leading();
        Typeset.draw(painter, Tokens.Type.BODY, route.group(), d.x() + titleW + Tokens.Space.COZY,
            Typeset.centred(Tokens.Type.BODY, groupTop, Tokens.Type.BODY.leading()), Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.TITLE.leading() + Tokens.Space.LOOSE;

        Entity eye = eye();
        double bearing = eye == null ? 0 : WarpRoutes.bearing(route, eye.getX(), eye.getZ());
        String direction = eye == null ? tr("distance.unknown")
            : WarpRoutes.compassPoint(bearing) + " · "
                + WarpRoutes.turnLabel(WarpRoutes.turn(WarpRoutes.heading(eye.getYRot()), bearing));
        String coordinates = route.x() + ", " + route.y() + ", " + route.z();
        String[][] facts = {
            {tr("detail.distance"), distance(route)},
            {tr("detail.bearing"), direction},
            {tr("detail.coordinates"), coordinates.replace("-", "−")},
            {tr("detail.world"), worldLabel(route.world())},
        };
        int valueRoom = layout.compass().x() - Tokens.Space.COZY - layout.factValueX();
        for (String[] fact : facts) {
            Typeset.draw(painter, Tokens.Type.BODY, fact[0], d.x(),
                Typeset.centred(Tokens.Type.BODY, y, Tokens.Type.BODY.leading()), Tokens.Color.INK_TERTIARY);
            Typeset.tabular(painter, Tokens.Type.STRONG, Typeset.ellipsized(Tokens.Type.STRONG, fact[1], valueRoom),
                layout.factValueX(), Typeset.centred(Tokens.Type.STRONG, y, Tokens.Type.STRONG.leading()),
                Tokens.Color.INK_PRIMARY);
            y += FACT_PITCH;
        }
        compass(painter, layout.compass(), bearing, eye != null);
        y += Tokens.Space.LOOSE + Tokens.Space.TIGHT - FACT_PITCH + Tokens.Type.BODY.leading();
        Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("detail.note"), d.x(),
            Typeset.centred(Tokens.Type.BODY, y, Tokens.Type.BODY.leading()), d.w(), 3, Tokens.Color.INK_SECONDARY);
    }

    /** A ring with north marked, and a gold needle on the destination's bearing. */
    private static void compass(Painter painter, Box c, double bearing, boolean known) {
        float cx = c.midX();
        float cy = c.midY();
        painter.ring(cx, cy, COMPASS_RING, Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
        Typeset.drawCentered(painter, Tokens.Type.MICRO, "N", Math.round(cx), c.y() - Tokens.Space.TIGHT,
            Tokens.Color.INK_TERTIARY);
        if (known) {
            double rad = Math.toRadians(bearing);
            float tipX = cx + (float) Math.sin(rad) * NEEDLE;
            float tipY = cy - (float) Math.cos(rad) * NEEDLE;
            painter.line(cx, cy, tipX, tipY, Tokens.Stroke.FOCUS, Tokens.Color.ACCENT);
            painter.diamond(tipX, tipY, Tokens.Space.SNUG - 1, 0.0f, Tokens.Color.ACCENT);
        }
        painter.dot(cx, cy, DOT / 2 + 0.25f, Tokens.Color.INK_PRIMARY);
    }

    private void status(Painter painter) {
        Status status = status();
        if (status == null) {
            return;
        }
        Box s = layout.status();
        painter.dot(s.x() + DOT, s.midY(), DOT, status.color());
        Typeset.draw(painter, Tokens.Type.BODY,
            Typeset.ellipsized(Tokens.Type.BODY, status.copy(), s.w() - Tokens.Space.LOOSE - Tokens.Space.TIGHT),
            s.x() + Tokens.Space.LOOSE + Tokens.Space.TIGHT, Typeset.centred(Tokens.Type.BODY, s.y(), s.h()), status.color());
    }

    private void cta(Painter painter) {
        if (lastAccepted.isEmpty()) {
            return;
        }
        Box c = layout.cta();
        int room = layout.go().x() - Tokens.Space.COZY - c.x();
        Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, tr("last", lastAccepted), room),
            c.x(), Typeset.centred(Tokens.Type.BODY, c.y(), c.h()), Tokens.Color.INK_TERTIARY);
    }

    private void hints(Painter painter) {
        List<Glass.Hint> hints = new ArrayList<>();
        hints.add(new Glass.Hint("↑↓", tr("hint.move")));
        hints.add(new Glass.Hint("Enter", tr("hint.go")));
        hints.add(new Glass.Hint("M", tr("hint.map")));
        hints.add(new Glass.Hint("Esc", tr("hint.close")));
        Glass.hints(painter, layout.wrap().midX(), layout.hintY(), hints, keyboard);
    }

    private Status status() {
        Optional<BridgeState.PendingWarp> pending = FullmoonChannel.pendingWarp();
        if (pending.isPresent()) {
            return new Status(tr("status.pending"), Tokens.Color.STATUS_WARN);
        }
        Optional<BridgeState.WarpOutcome> outcome = FullmoonChannel.warpOutcome(System.currentTimeMillis());
        if (outcome.isEmpty()) {
            return null;
        }
        BridgeState.WarpOutcome value = outcome.orElseThrow();
        if (value.ok()) {
            return new Status(tr("status.accepted", nameOf(value.id())), Tokens.Color.STATUS_LIVE);
        }
        String key = WarpRoutes.reasonKey(value.reason());
        return new Status(tr("reason." + key), key.equals("cooldown") ? Tokens.Color.STATUS_WARN : Tokens.Color.STATUS_DANGER);
    }

    private String nameOf(String id) {
        return routes.stream().filter(route -> route.id().equals(id)).map(BridgeProtocol.Waypoint::name)
            .findFirst().orElse(id);
    }

    private void requested() {
        BridgeProtocol.Waypoint route = board.current();
        boolean active = FullmoonChannel.state().mode() == BridgeState.Mode.ACTIVE;
        if (route == null || !active || FullmoonChannel.pendingWarp().isPresent() || fade.closing()) {
            board.refuse();
            UiSounds.play(UiSounds.Cue.ERROR);
            return;
        }
        UiSounds.play(UiSounds.Cue.CONFIRM);
        FullmoonChannel.requestWarp(route);
    }

    private String rowMeta(int index) {
        BridgeProtocol.Waypoint route = routes.get(index);
        return isHere(index) ? tr("here") : distance(route);
    }

    private boolean isHere(int index) {
        Entity eye = eye();
        return eye != null && WarpRoutes.distanceMeters(routes.get(index), eye.getX(), eye.getY(), eye.getZ())
            <= WarpRoutes.HERE_METERS;
    }

    private static Entity eye() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        return client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
    }

    private static String distance(BridgeProtocol.Waypoint route) {
        Entity eye = eye();
        if (eye == null) {
            return tr("distance.unknown");
        }
        return tr("distance.meters", WarpRoutes.distanceMeters(route, eye.getX(), eye.getY(), eye.getZ()));
    }

    private String worldLabel() {
        return worldLabel(routes.isEmpty() ? "world" : routes.getFirst().world());
    }

    /** The Bukkit world names as a player says them. */
    static String worldLabel(String world) {
        return switch (world) {
            case "world" -> I18n.get("fullmoon.warp.world.lobby");
            case "world_nether" -> I18n.get("fullmoon.warp.world.nether");
            case "world_the_end" -> I18n.get("fullmoon.warp.world.end");
            default -> world;
        };
    }

    private static int selectedIndex(List<BridgeProtocol.Waypoint> routes, String selectedId) {
        for (int index = 0; index < routes.size(); index++) {
            if (routes.get(index).id().equals(selectedId)) {
                return index;
            }
        }
        return 0;
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.warp." + key, args);
    }

    private record Status(String copy, int color) {}
}
