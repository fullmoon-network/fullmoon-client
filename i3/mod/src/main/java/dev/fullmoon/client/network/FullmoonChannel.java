package dev.fullmoon.client.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.menu.ServerMenuScreen;
import dev.fullmoon.client.season.SeasonScreen;
import dev.fullmoon.client.warp.WarpScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FullmoonChannel {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Channel");
    private static final CustomPacketPayload.Type<Envelope> TYPE =
        new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(FullmoonClient.NAMESPACE, "v1"));
    private static final StreamCodec<FriendlyByteBuf, Envelope> CODEC = StreamCodec.of(
        (buffer, envelope) -> buffer.writeByteArray(envelope.data()),
        buffer -> new Envelope(buffer.readByteArray(BridgeProtocol.MAX_PAYLOAD_BYTES)));
    private static final AtomicReference<BridgeState> STATE =
        new AtomicReference<>(BridgeState.disconnected());
    /** The capture rig's switch: it rebuilds the concept mockup from the snapshot the client drew. */
    private static final boolean MENU_DUMP = Boolean.getBoolean("fullmoon.menuDump");
    /** The optional surfaces this client draws, so a server knows it may send them. */
    private static final List<String> FEATURES = List.of("season_v1", "season_hud");
    /** What the server was last told about the season widget; a new login starts from not shown. */
    private static volatile boolean seasonHudSent;

    private FullmoonChannel() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);

        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
            context.client().execute(() -> receive(payload.data())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            client.execute(FullmoonChannel::connect));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            STATE.set(BridgeState.disconnected()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static Optional<BridgeState.Metrics> metrics(long now) {
        return BridgeState.liveMetrics(STATE.get(), now);
    }

    public static Optional<BridgeState.ActiveNotice> notice(long now) {
        return BridgeState.liveNotice(STATE.get(), now);
    }

    public static List<BridgeProtocol.Waypoint> waypoints() {
        return STATE.get().waypoints();
    }

    public static Optional<BridgeState.PendingWarp> pendingWarp() {
        return STATE.get().pendingWarp();
    }

    public static Optional<BridgeState.WarpOutcome> warpOutcome(long now) {
        return BridgeState.liveWarpOutcome(STATE.get(), now);
    }

    public static Optional<BridgeState.CasinoReveal> casino(long now) {
        return BridgeState.liveCasino(STATE.get(), now);
    }

    public static BridgeState state() {
        return STATE.get();
    }

    public static Optional<BridgeState.SeasonFeed> season() {
        return STATE.get().season();
    }

    public static boolean requestWarp(BridgeProtocol.Waypoint waypoint) {
        Objects.requireNonNull(waypoint, "waypoint");
        long now = System.currentTimeMillis();
        while (true) {
            BridgeState before = STATE.get();
            BridgeState requested = BridgeState.requestWarp(before, waypoint.id(), now);
            if (requested == before) {
                return false;
            }
            if (STATE.compareAndSet(before, requested)) {
                return sendWarpRequest(waypoint);
            }
        }
    }

    public static boolean requestMenuAction(
            String menuId, long revision, int slot, MenuProtocol.Click click) {
        if (STATE.get().mode() != BridgeState.Mode.ACTIVE) {
            return false;
        }
        try {
            ClientPlayNetworking.send(
                new Envelope(MenuProtocol.action(menuId, revision, slot, click)));
            LOG.info("Sent fullmoon:v1 menu action {} slot {} ({})", menuId, slot,
                click.wireName());
            return true;
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 menu action {} slot {}", menuId, slot, error);
            return false;
        }
    }

    public static void closeMenu(String menuId, long revision) {
        if (STATE.get().mode() != BridgeState.Mode.ACTIVE) {
            return;
        }
        try {
            ClientPlayNetworking.send(new Envelope(MenuProtocol.close(menuId, revision)));
            LOG.info("Sent fullmoon:v1 menu close {}", menuId);
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 menu close {}", menuId, error);
        }
    }

    public static boolean requestSeasonAction(String sessionId, long revision, SeasonProtocol.Action action) {
        if (STATE.get().mode() != BridgeState.Mode.ACTIVE) {
            return false;
        }
        try {
            ClientPlayNetworking.send(new Envelope(SeasonProtocol.action(sessionId, revision, action)));
            LOG.info("Sent fullmoon:v1 season action {} revision {} ({})", sessionId, revision, action);
            return true;
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 season action {}", sessionId, error);
            return false;
        }
    }

    public static void closeSeason(String sessionId, long revision) {
        if (STATE.get().mode() != BridgeState.Mode.ACTIVE) {
            return;
        }
        try {
            ClientPlayNetworking.send(new Envelope(SeasonProtocol.close(sessionId, revision)));
            LOG.info("Sent fullmoon:v1 season close {}", sessionId);
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 season close {}", sessionId, error);
        }
    }

    /** Tells the server whether the season widget is on the HUD, once per change. */
    public static void seasonHud(boolean visible) {
        if (visible == seasonHudSent || STATE.get().mode() != BridgeState.Mode.ACTIVE) {
            return;
        }
        try {
            ClientPlayNetworking.send(new Envelope(SeasonProtocol.hud(visible)));
            seasonHudSent = visible;
            LOG.info("Sent fullmoon:v1 season hud visible={}", visible);
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 season hud", error);
        }
    }

    private static void connect() {
        STATE.set(BridgeState.connected(System.currentTimeMillis()));
        seasonHudSent = false;
        try {
            List<BridgeProtocol.Pack> packs = ClientPacks.active();
            ClientPlayNetworking.send(new Envelope(BridgeProtocol.hello(clientVersion(), FEATURES, packs)));
            LOG.info("Sent fullmoon:v1 hello (proto {}, features {}, packs {})", BridgeProtocol.VERSION,
                FEATURES, packs);
        } catch (RuntimeException error) {
            LOG.error("Failed to send fullmoon:v1 hello", error);
        }
    }

    private static void receive(byte[] payload) {
        BridgeProtocol.DecodeResult decoded = BridgeProtocol.decode(payload);
        if (decoded.error().isPresent()) {
            LOG.warn("Rejected fullmoon:v1 payload: {}", decoded.error().orElseThrow());
            return;
        }

        BridgeProtocol.Message message = decoded.message().orElseThrow();
        BridgeState before = STATE.get();
        BridgeState after = BridgeState.apply(before, message, System.currentTimeMillis());
        STATE.set(after);

        if (message instanceof BridgeProtocol.Welcome welcome) {
            LOG.info("Received fullmoon:v1 welcome (server proto {}, mode {})",
                welcome.proto(), after.mode());
        } else if (message instanceof BridgeProtocol.HudSync sync && after != before) {
            if (before.metrics().isEmpty()) {
                LOG.info("Received fullmoon:v1 HUD revision {} ({} TPS, {} ms)",
                    sync.revision(), sync.ticksPerSecond(), sync.tickMilliseconds());
            } else {
                LOG.debug("Applied fullmoon:v1 HUD revision {}", sync.revision());
            }
        } else if (message instanceof BridgeProtocol.Notice notice && after != before) {
            LOG.info("Received fullmoon:v1 notice {}", notice.id());
        } else if (message instanceof BridgeProtocol.WaypointSync sync && after != before) {
            LOG.info("Replaced fullmoon:v1 waypoint snapshot ({} routes)", sync.waypoints().size());
            refreshWarpScreen();
        } else if (message instanceof BridgeProtocol.TpResult result && after != before) {
            LOG.info("Received fullmoon:v1 warp result {} ({})", result.id(),
                result.ok() ? "accepted" : result.reason());
        } else if (message instanceof SeasonProtocol.Progress progress) {
            if (before.season().isEmpty() != after.season().isEmpty()) {
                LOG.info("Received fullmoon:v1 season progress (level {}, tracked {})", progress.level(),
                    progress.tracked().map(SeasonProtocol.Goal::id).orElse("none"));
            }
        } else if (message instanceof SeasonProtocol.Open open) {
            openSeason(before, open);
        } else if (message instanceof CasinoProtocol.Result result && after != before) {
            LOG.info("Received fullmoon:v1 casino result {} ({})", result.game().wireName(),
                result.won() ? "won x" + result.payoutMultiplier() : "lost");
        } else if (message instanceof BridgeProtocol.ScreenOpen open) {
            openScreen(before, open);
        } else if (message instanceof MenuProtocol.Open open) {
            openMenu(before, open);
        } else if (message instanceof MenuProtocol.Close close) {
            closeMenu(before, close);
        } else if (message instanceof BridgeProtocol.Unknown unknown) {
            LOG.debug("Ignored fullmoon:v1 payload type {}", unknown.type());
        }
    }

    private static boolean sendWarpRequest(BridgeProtocol.Waypoint waypoint) {
        try {
            ClientPlayNetworking.send(new Envelope(BridgeProtocol.teleportRequest(waypoint.id())));
            LOG.info("Sent fullmoon:v1 warp request {}", waypoint.id());
            return true;
        } catch (RuntimeException error) {
            long now = System.currentTimeMillis();
            STATE.updateAndGet(state ->
                BridgeState.failWarp(state, waypoint.id(), "client_send", now));
            LOG.error("Failed to send fullmoon:v1 warp request {}", waypoint.id(), error);
            return false;
        }
    }

    private static void openScreen(BridgeState state, BridgeProtocol.ScreenOpen open) {
        if (state.mode() != BridgeState.Mode.ACTIVE || open.proto() != state.serverProtocol()) {
            return;
        }
        if (!"warp".equals(open.screen())) {
            LOG.debug("Ignored fullmoon:v1 screen {}", open.screen());
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen instanceof WarpScreen) {
            return;
        }
        client.setScreen(new WarpScreen(client.screen));
        LOG.info("Opened fullmoon:v1 warp screen");
    }

    private static void refreshWarpScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof WarpScreen screen) {
            client.setScreen(screen.refreshed());
        }
    }

    private static void openMenu(BridgeState state, MenuProtocol.Open open) {
        if (state.mode() != BridgeState.Mode.ACTIVE || open.proto() != state.serverProtocol()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (client.screen instanceof ServerMenuScreen current) {
            client.setScreen(current.menuId().equals(open.id())
                ? current.refreshed(open) : current.replacedBy(open));
        } else if (client.screen instanceof SeasonScreen season) {
            client.setScreen(new ServerMenuScreen(season.parentScreen(), open));
        } else {
            client.setScreen(new ServerMenuScreen(client.screen, open));
        }
        LOG.info("Opened fullmoon:v1 server menu {} revision {} ({} items)",
            open.id(), open.revision(), open.items().size());
        if (MENU_DUMP) {
            LOG.info("fullmoon:v1 menu snapshot {}", open);
        }
    }

    private static void openSeason(BridgeState state, SeasonProtocol.Open open) {
        if (state.mode() != BridgeState.Mode.ACTIVE || open.proto() != state.serverProtocol()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (client.screen instanceof SeasonScreen current && current.sessionId().equals(open.id())) {
            current.update(open);
        } else {
            Screen parent = client.screen;
            if (parent instanceof ServerMenuScreen menu) {
                parent = menu.parentScreen();
            } else if (parent instanceof SeasonScreen season) {
                parent = season.parentScreen();
            }
            client.setScreen(new SeasonScreen(parent, open));
        }
        LOG.info("Opened fullmoon:v1 season pass {} revision {} ({} levels, tab {})",
            open.id(), open.revision(), open.levels().size(), SeasonProtocol.wire(open.tab()));
    }

    private static void closeMenu(BridgeState state, MenuProtocol.Close close) {
        if (state.mode() != BridgeState.Mode.ACTIVE || close.proto() != state.serverProtocol()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ServerMenuScreen current
                && current.menuId().equals(close.id())) {
            current.closeFromServer();
            LOG.info("Closed fullmoon:v1 server menu {}", close.id());
        }
    }

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer(FullmoonClient.NAMESPACE)
            .orElseThrow(() -> new IllegalStateException("Fullmoon mod container is missing"))
            .getMetadata()
            .getVersion()
            .getFriendlyString();
    }

    private static void tick() {
        BridgeState before = STATE.get();
        BridgeState after = BridgeState.tick(before, System.currentTimeMillis());
        if (after != before && STATE.compareAndSet(before, after)
                && after.mode() == BridgeState.Mode.FALLBACK) {
            LOG.info("No fullmoon:v1 welcome within 5 seconds; using vanilla fallback");
        }
    }

    public record Envelope(byte[] data) implements CustomPacketPayload {
        public Envelope {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public Type<Envelope> type() {
            return TYPE;
        }
    }
}
