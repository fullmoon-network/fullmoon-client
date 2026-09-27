package dev.pinion.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pinion.hud.PinionClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/** The client half of the `fullmoon:v1` channel — spec in the repo's
 *  docs/BRIDGE.md.
 *
 *  Three rules this class exists to keep:
 *
 *  1. Registration is a candidacy, the handshake is the membership. A mod that
 *     is installed but never gets a `welcome` stays quiet for the whole
 *     session instead of half-working.
 *  2. Being detected is a rendering convenience, never a trust grant. Every
 *     payload we send is a question; the server's validation is the answer.
 *  3. A server speaking a newer proto than us disables us — an old client must
 *     degrade to the fallback surfaces, not mis-render a protocol it cannot
 *     read. */
public final class FullmoonBridge {
    public static final int PROTO = 1;
    static final String CHANNEL_ID = "fullmoon:v1";
    private static final String VERSION = "0.1.0";

    private static final CustomPacketPayload.Type<Envelope> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fullmoon", "v1"));

    private static final StreamCodec<FriendlyByteBuf, Envelope> CODEC = StreamCodec.of(
            (buf, env) -> buf.writeByteArray(env.data()),
            buf -> new Envelope(buf.readByteArray()));

    /** The one packet shape on the wire: a UTF-8 JSON object, `type` on top. */
    public record Envelope(byte[] data) implements CustomPacketPayload {
        @Override
        public Type<Envelope> type() {
            return TYPE;
        }
    }

    private static volatile boolean supported;
    /** Server data only — replaced wholesale by `welcome` / `waypoint_sync`. */
    private static volatile List<Waypoint> waypoints = List.of();
    /** Set the moment a `tp_request` goes out, cleared by its `tp_result`. */
    private static volatile String pendingId;
    /** Outcome of the last `tp_result`, or null when there is nothing recent
     *  enough to show. The screen owns the wording; this owns only the truth. */
    private static volatile Boolean statusOk;
    private static volatile long statusAt;
    /** True when the waypoint list came from `-Dfullmoon.devWaypoints`, so the
     *  screen can label it honestly instead of pretending the server spoke. */
    private static volatile boolean devData;

    private FullmoonBridge() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);

        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            JsonObject json = parse(payload.data());
            if (json == null) {
                return;
            }
            context.client().execute(() -> handle(json));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            reset();
            if (Boolean.getBoolean("fullmoon.devWaypoints")) {
                devData = true;
                supported = true;
                waypoints = devFixtures();
                return;
            }
            /* No `welcome` back within the timeout simply means the session
               stays quiet — the screen shows the unsupported state and the
               server keeps serving its fallback surfaces. */
            JsonObject hello = new JsonObject();
            hello.addProperty("type", "hello");
            hello.addProperty("proto", PROTO);
            hello.addProperty("client", "fullmoon");
            hello.addProperty("version", VERSION);
            send(hello);
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        PinionClient.LOG.info("bridge ready (proto {})", PROTO);
    }

    // ── inbound ───────────────────────────────────────────────────

    private static void handle(JsonObject json) {
        switch (str(json, "type")) {
            case "welcome" -> {
                int proto = json.has("proto") ? json.get("proto").getAsInt() : 0;
                if (proto > PROTO) {
                    PinionClient.LOG.info("bridge: server proto {} newer than ours {}, disabled", proto, PROTO);
                    return;
                }
                supported = true;
                applyWaypoints(json);
                PinionClient.LOG.info("bridge: welcome (server proto {}), {} waypoints", proto, waypoints.size());
            }
            case "waypoint_sync" -> applyWaypoints(json);
            case "tp_result" -> {
                String id = str(json, "id");
                if (pendingId != null && pendingId.equals(id)) {
                    pendingId = null;
                }
                boolean ok = json.has("ok") && json.get("ok").getAsBoolean();
                statusOk = ok;
                statusAt = System.currentTimeMillis();
            }
            case "casino_result" -> CasinoResultCard.handle(json);
            case "link_result" -> DiscordLink.handleResult(json);
            case "link_prompt" -> {
                // the player ran /link — if the launcher gave us a Discord id,
                // complete the link without them touching Discord
                String code = str(json, "code");
                if (!code.isEmpty() && DiscordLink.available()) {
                    DiscordLink.sendLink(code);
                }
            }
            case "screen_open" -> {
                if ("warp".equals(str(json, "screen"))) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new WarpScreen(null));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void applyWaypoints(JsonObject json) {
        if (!json.has("waypoints") || !json.get("waypoints").isJsonArray()) {
            return;
        }
        JsonArray arr = json.getAsJsonArray("waypoints");
        List<Waypoint> next = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String id = str(o, "id");
            if (id.isEmpty()) {
                continue;
            }
            next.add(new Waypoint(id, str(o, "name"), str(o, "icon"),
                    o.has("x") ? o.get("x").getAsInt() : 0,
                    o.has("y") ? o.get("y").getAsInt() : 0,
                    o.has("z") ? o.get("z").getAsInt() : 0,
                    str(o, "world"), str(o, "group")));
        }
        waypoints = List.copyOf(next);
    }

    // ── outbound ──────────────────────────────────────────────────

    public static void requestTp(Waypoint wp) {
        if (!supported || pendingId != null) {
            return;
        }
        pendingId = wp.id();
        JsonObject req = new JsonObject();
        req.addProperty("type", "tp_request");
        req.addProperty("id", wp.id());
        send(req);
    }

    /** Package-visible send for sibling handlers (DiscordLink). */
    static void sendRaw(JsonObject json) {
        send(json);
    }

    private static void send(JsonObject json) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new Envelope(json.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // ── state for the screen ──────────────────────────────────────

    public static boolean supported() {
        return supported;
    }

    public static boolean devData() {
        return devData;
    }

    public static List<Waypoint> waypoints() {
        return waypoints;
    }

    public static String pendingId() {
        return pendingId;
    }

    /** The last `tp_result`, or null once its few seconds have passed. */
    public static Boolean statusOk() {
        long now = System.currentTimeMillis();
        return now - statusAt < 3500 ? statusOk : null;
    }

    private static void reset() {
        supported = false;
        devData = false;
        waypoints = List.of();
        pendingId = null;
        statusOk = null;
        statusAt = 0;
    }

    // ── json helpers ──────────────────────────────────────────────

    private static JsonObject parse(byte[] data) {
        try {
            JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            PinionClient.LOG.warn("bridge: bad payload", e);
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }


    /** Only when `-Dfullmoon.devWaypoints` is set — a GUI reviewable without a
     *  bridge server, labelled as fixtures by {@link #devData()}. */
    private static List<Waypoint> devFixtures() {
        List<Waypoint> list = new ArrayList<>();
        list.add(new Waypoint("palace_gate", "만월궁 정문", "moon", 500, 72, -140, "lobby", "palace"));
        list.add(new Waypoint("palace_keep", "만월궁 대천수", "moon", 500, 86, -140, "lobby", "palace"));
        list.add(new Waypoint("spawn_fountain", "스폰 분수", "drop", 0, 65, 0, "lobby", "plaza"));
        list.add(new Waypoint("moon_pond", "달샘 연못", "drop", 0, 64, 60, "lobby", "plaza"));
        list.add(new Waypoint("west_gate", "서쪽 달문 (야생)", "gate", 431, 65, 0, "lobby", "gates"));
        list.add(new Waypoint("east_gate", "동쪽 달문", "gate", 569, 65, 0, "lobby", "gates"));
        list.add(new Waypoint("garden_teal", "청자 정원", "leaf", -80, 65, 40, "lobby", "gardens"));
        list.add(new Waypoint("rear_garden", "후원", "leaf", 500, 66, -40, "lobby", "gardens"));
        return list;
    }
}
