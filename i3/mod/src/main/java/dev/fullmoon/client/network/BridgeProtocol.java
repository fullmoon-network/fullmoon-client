package dev.fullmoon.client.network;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public final class BridgeProtocol {
    public static final int VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 32_767;

    private static final int MAX_CLIENT_VERSION_LENGTH = 32;
    private static final int MAX_NOTICE_ID_LENGTH = 64;
    private static final int MAX_NOTICE_TITLE_LENGTH = 64;
    private static final int MAX_NOTICE_BODY_LENGTH = 160;
    private static final int MIN_NOTICE_DURATION_MILLIS = 1_000;
    private static final int MAX_NOTICE_DURATION_MILLIS = 10_000;
    private static final int MAX_WAYPOINTS = 128;
    private static final int MAX_WAYPOINT_ID_LENGTH = 64;
    private static final int MAX_WAYPOINT_TEXT_LENGTH = 64;
    private static final int MAX_PERMISSION_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 64;
    private static final int WORLD_HORIZONTAL_LIMIT = 30_000_000;
    private static final int WORLD_VERTICAL_LIMIT = 2_048;
    private static final Pattern WAYPOINT_ID =
        Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private BridgeProtocol() {}

    public interface Message {
        int proto();
    }

    public record Hello(int proto, String client, String version) implements Message {}

    public record Welcome(int proto, List<Waypoint> waypoints) implements Message {
        public Welcome {
            waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
        }

        public Welcome(int proto) {
            this(proto, List.of());
        }
    }

    public record HudSync(
            int proto,
            long revision,
            double ticksPerSecond,
            double tickMilliseconds) implements Message {}

    public record Notice(
            int proto,
            String id,
            String title,
            String body,
            Severity severity,
            int durationMillis) implements Message {}

    public record WaypointSync(int proto, List<Waypoint> waypoints) implements Message {
        public WaypointSync {
            waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
        }
    }

    public record TpResult(int proto, String id, boolean ok, String reason) implements Message {
        public TpResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record ScreenOpen(int proto, String screen) implements Message {
        public ScreenOpen {
            Objects.requireNonNull(screen, "screen");
        }
    }

    public record Waypoint(
            String id,
            String name,
            String icon,
            int x,
            int y,
            int z,
            String world,
            String group,
            String permission) {
        public Waypoint {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(permission, "permission");
        }
    }

    public record Unknown(int proto, String type) implements Message {}

    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public record DecodeResult(Optional<Message> message, Optional<String> error) {
        public DecodeResult {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(error, "error");
            if (message.isPresent() == error.isPresent()) {
                throw new IllegalArgumentException("decode result must contain either a message or an error");
            }
        }

        static DecodeResult success(Message message) {
            return new DecodeResult(Optional.of(message), Optional.empty());
        }

        static DecodeResult failure(String error) {
            return new DecodeResult(Optional.empty(), Optional.of(error));
        }
    }

    public static byte[] hello(String clientVersion) {
        String error = helloError(clientVersion);
        if (!error.isEmpty()) {
            throw new IllegalArgumentException(error);
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "hello");
        json.addProperty("proto", VERSION);
        json.addProperty("client", "fullmoon");
        json.addProperty("version", clientVersion);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] teleportRequest(String waypointId) {
        String error = waypointIdError(waypointId);
        if (!error.isEmpty()) {
            throw new IllegalArgumentException(error);
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "tp_request");
        json.addProperty("id", waypointId);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String helloError(String clientVersion) {
        if (clientVersion == null || clientVersion.isBlank()) {
            return "client version is required";
        }
        if (clientVersion.length() > MAX_CLIENT_VERSION_LENGTH) {
            return "client version is too long";
        }
        return "";
    }

    static String waypointIdError(String waypointId) {
        if (waypointId == null || waypointId.isBlank()) {
            return "waypoint id is required";
        }
        if (waypointId.length() > MAX_WAYPOINT_ID_LENGTH || !WAYPOINT_ID.matcher(waypointId).matches()) {
            return "waypoint id is invalid";
        }
        return "";
    }

    public static DecodeResult decode(byte[] payload) {
        if (payload == null) {
            return DecodeResult.failure("payload is required");
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            return DecodeResult.failure("payload exceeds 32767 bytes");
        }

        JsonObject json = parseObject(payload);
        if (json == null) {
            return DecodeResult.failure("payload is not a JSON object");
        }

        String type = string(json, "type");
        if (type.isEmpty()) {
            return DecodeResult.failure("payload type is required");
        }

        return switch (type) {
            case "hello" -> decodeHello(json);
            case "welcome" -> decodeWelcome(json);
            case "hud_sync" -> decodeHudSync(json);
            case "notice" -> decodeNotice(json);
            case "waypoint_sync" -> decodeWaypointSync(json);
            case "tp_result" -> decodeTpResult(json);
            case "screen_open" -> decodeScreenOpen(json);
            case "menu_open" -> MenuProtocol.decodeOpen(json);
            case "menu_close" -> MenuProtocol.decodeClose(json);
            case "casino_result" -> CasinoProtocol.decodeResult(json);
            default -> decodeUnknown(json, type);
        };
    }

    private static DecodeResult decodeHello(JsonObject json) {
        Integer proto = integer(json, "proto");
        if (proto == null || proto < 0) {
            return DecodeResult.failure("hello proto must be a non-negative integer");
        }
        String client = string(json, "client");
        String version = string(json, "version");
        if (client.isEmpty()) {
            return DecodeResult.failure("hello client is required");
        }
        String versionError = helloError(version);
        if (!versionError.isEmpty()) {
            return DecodeResult.failure(versionError);
        }
        return DecodeResult.success(new Hello(proto, client, version));
    }

    private static DecodeResult decodeWelcome(JsonObject json) {
        Integer proto = integer(json, "proto");
        if (proto == null || proto < 0) {
            return DecodeResult.failure("welcome proto must be a non-negative integer");
        }
        WaypointSnapshot snapshot = decodeWaypoints(json, false);
        return snapshot.error().isEmpty()
            ? DecodeResult.success(new Welcome(proto, snapshot.waypoints()))
            : DecodeResult.failure(snapshot.error());
    }

    private static DecodeResult decodeHudSync(JsonObject json) {
        Integer proto = integer(json, "proto");
        Long revision = longInteger(json, "revision");
        Double tps = finiteNumber(json, "tps");
        Double tickMillis = finiteNumber(json, "tick_ms");

        if (proto == null || proto < 0) {
            return DecodeResult.failure("hud_sync proto must be a non-negative integer");
        }
        if (revision == null || revision < 0) {
            return DecodeResult.failure("hud_sync revision must be non-negative");
        }
        if (tps == null || tps < 0 || tps > 20) {
            return DecodeResult.failure("hud_sync tps must be between 0 and 20");
        }
        if (tickMillis == null || tickMillis < 0 || tickMillis > 1_000) {
            return DecodeResult.failure("hud_sync tick_ms must be between 0 and 1000");
        }
        return DecodeResult.success(new HudSync(proto, revision, tps, tickMillis));
    }

    private static DecodeResult decodeNotice(JsonObject json) {
        Integer proto = integer(json, "proto");
        Integer duration = integer(json, "duration_ms");
        String id = string(json, "id");
        String title = string(json, "title");
        String body = string(json, "body");
        String severityName = string(json, "severity");

        if (proto == null || proto < 0) {
            return DecodeResult.failure("notice proto must be a non-negative integer");
        }
        String textError = noticeTextError(id, title, body);
        if (!textError.isEmpty()) {
            return DecodeResult.failure(textError);
        }

        Severity severity;
        try {
            severity = Severity.valueOf(severityName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return DecodeResult.failure("notice severity is invalid");
        }

        if (duration == null
                || duration < MIN_NOTICE_DURATION_MILLIS
                || duration > MAX_NOTICE_DURATION_MILLIS) {
            return DecodeResult.failure("notice duration_ms must be between 1000 and 10000");
        }
        return DecodeResult.success(new Notice(proto, id, title, body, severity, duration));
    }

    private static String noticeTextError(String id, String title, String body) {
        if (id.isEmpty()) {
            return "notice id is required";
        }
        if (id.length() > MAX_NOTICE_ID_LENGTH) {
            return "notice id is too long";
        }
        if (title.isEmpty()) {
            return "notice title is required";
        }
        if (title.length() > MAX_NOTICE_TITLE_LENGTH) {
            return "notice title is too long";
        }
        if (body.isEmpty()) {
            return "notice body is required";
        }
        if (body.length() > MAX_NOTICE_BODY_LENGTH) {
            return "notice body is too long";
        }
        return "";
    }

    private static DecodeResult decodeWaypointSync(JsonObject json) {
        Integer proto = operationalProto(json, "waypoint_sync");
        if (proto == null) {
            return DecodeResult.failure("waypoint_sync proto must be a non-negative integer");
        }
        WaypointSnapshot snapshot = decodeWaypoints(json, true);
        return snapshot.error().isEmpty()
            ? DecodeResult.success(new WaypointSync(proto, snapshot.waypoints()))
            : DecodeResult.failure(snapshot.error());
    }

    private static DecodeResult decodeTpResult(JsonObject json) {
        Integer proto = operationalProto(json, "tp_result");
        if (proto == null) {
            return DecodeResult.failure("tp_result proto must be a non-negative integer");
        }
        String id = string(json, "id");
        if (!waypointIdError(id).isEmpty()) {
            return DecodeResult.failure(id.isEmpty()
                ? "tp_result id is required" : "tp_result id is invalid");
        }
        Boolean ok = booleanValue(json, "ok");
        if (ok == null) {
            return DecodeResult.failure("tp_result ok must be a boolean");
        }
        String reason = string(json, "reason");
        if (!ok && reason.isEmpty()) {
            return DecodeResult.failure("tp_result reason is required when denied");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return DecodeResult.failure("tp_result reason is too long");
        }
        return DecodeResult.success(new TpResult(proto, id, ok, reason));
    }

    private static DecodeResult decodeScreenOpen(JsonObject json) {
        Integer proto = operationalProto(json, "screen_open");
        if (proto == null) {
            return DecodeResult.failure("screen_open proto must be a non-negative integer");
        }
        String screen = string(json, "screen");
        if (screen.isEmpty()) {
            return DecodeResult.failure("screen_open screen is required");
        }
        if (screen.length() > MAX_WAYPOINT_TEXT_LENGTH) {
            return DecodeResult.failure("screen_open screen is too long");
        }
        return DecodeResult.success(new ScreenOpen(proto, screen));
    }

    private static WaypointSnapshot decodeWaypoints(JsonObject json, boolean required) {
        if (!json.has("waypoints")) {
            return required ? WaypointSnapshot.failure("waypoints must be an array")
                            : WaypointSnapshot.success(List.of());
        }
        JsonElement value = json.get("waypoints");
        if (value == null || !value.isJsonArray()) {
            return WaypointSnapshot.failure("waypoints must be an array");
        }
        JsonArray array = value.getAsJsonArray();
        if (array.size() > MAX_WAYPOINTS) {
            return WaypointSnapshot.failure("waypoints exceed 128 entries");
        }

        List<Waypoint> waypoints = new ArrayList<>(array.size());
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            WaypointEntry entry = decodeWaypoint(array.get(index), index);
            if (!entry.error().isEmpty()) {
                return WaypointSnapshot.failure(entry.error());
            }
            Waypoint waypoint = entry.waypoint().orElseThrow();
            if (!ids.add(waypoint.id())) {
                return WaypointSnapshot.failure("waypoint id is duplicated: " + waypoint.id());
            }
            waypoints.add(waypoint);
        }
        return WaypointSnapshot.success(waypoints);
    }

    private static WaypointEntry decodeWaypoint(JsonElement element, int index) {
        if (!element.isJsonObject()) {
            return WaypointEntry.failure("waypoint " + index + " must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        String id = string(json, "id");
        String idError = waypointIdError(id);
        if (!idError.isEmpty()) {
            return WaypointEntry.failure("waypoint " + index + " "
                + idError.substring("waypoint ".length()));
        }
        String name = string(json, "name");
        if (name.isEmpty()) {
            return WaypointEntry.failure("waypoint " + index + " name is required");
        }
        if (name.length() > MAX_WAYPOINT_TEXT_LENGTH) {
            return WaypointEntry.failure("waypoint " + index + " name is too long");
        }

        Integer x = boundedCoordinate(json, "x", WORLD_HORIZONTAL_LIMIT);
        Integer y = boundedCoordinate(json, "y", WORLD_VERTICAL_LIMIT);
        Integer z = boundedCoordinate(json, "z", WORLD_HORIZONTAL_LIMIT);
        if (x == null || y == null || z == null) {
            return WaypointEntry.failure("waypoint " + index + " coordinates are invalid");
        }

        String world = string(json, "world");
        if (world.isEmpty()) {
            return WaypointEntry.failure("waypoint " + index + " world is required");
        }
        String icon = string(json, "icon");
        String group = string(json, "group");
        String permission = string(json, "perm");
        String optionalError = optionalWaypointTextError(index, icon, world, group, permission);
        if (!optionalError.isEmpty()) {
            return WaypointEntry.failure(optionalError);
        }
        return WaypointEntry.success(new Waypoint(
            id, name, icon, x, y, z, world, group, permission));
    }

    private static String optionalWaypointTextError(
            int index, String icon, String world, String group, String permission) {
        if (icon.length() > MAX_WAYPOINT_TEXT_LENGTH) {
            return "waypoint " + index + " icon is too long";
        }
        if (world.length() > MAX_WAYPOINT_TEXT_LENGTH) {
            return "waypoint " + index + " world is too long";
        }
        if (group.length() > MAX_WAYPOINT_TEXT_LENGTH) {
            return "waypoint " + index + " group is too long";
        }
        if (permission.length() > MAX_PERMISSION_LENGTH) {
            return "waypoint " + index + " perm is too long";
        }
        return "";
    }

    private static Integer boundedCoordinate(JsonObject json, String key, int limit) {
        Integer value = integer(json, key);
        return value == null || value < -limit || value > limit ? null : value;
    }

    private static Integer operationalProto(JsonObject json, String type) {
        if (!json.has("proto")) {
            return VERSION;
        }
        Integer proto = integer(json, "proto");
        return proto == null || proto < 0 ? null : proto;
    }

    private static Boolean booleanValue(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive value = json.getAsJsonPrimitive(key);
        return value.isBoolean() ? value.getAsBoolean() : null;
    }

    private static DecodeResult decodeUnknown(JsonObject json, String type) {
        Integer proto = integer(json, "proto");
        return DecodeResult.success(new Unknown(proto == null ? 0 : proto, type));
    }

    private static JsonObject parseObject(byte[] payload) {
        JsonObject direct = parseJsonObject(payload, 0, payload.length);
        if (direct != null) {
            return direct;
        }

        Frame frame = readFrame(payload);
        if (frame == null) {
            return null;
        }
        return parseJsonObject(payload, frame.offset(), frame.length());
    }

    private static JsonObject parseJsonObject(byte[] payload, int offset, int length) {
        try {
            JsonElement parsed = JsonParser.parseString(
                new String(payload, offset, length, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Frame readFrame(byte[] payload) {
        int value = 0;
        int shift = 0;
        for (int index = 0; index < Math.min(5, payload.length); index++) {
            int current = payload[index] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                int offset = index + 1;
                return value == payload.length - offset ? new Frame(offset, value) : null;
            }
            shift += 7;
        }
        return null;
    }

    private static Integer integer(JsonObject json, String key) {
        Long value = longInteger(json, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private static Long longInteger(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive(key);
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            return null;
        }
    }

    private static Double finiteNumber(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive(key);
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive(key);
        if (!primitive.isString()) {
            return "";
        }
        try {
            return primitive.getAsString().trim();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private record Frame(int offset, int length) {}

    private record WaypointSnapshot(List<Waypoint> waypoints, String error) {
        private WaypointSnapshot {
            waypoints = List.copyOf(waypoints);
        }

        private static WaypointSnapshot success(List<Waypoint> waypoints) {
            return new WaypointSnapshot(waypoints, "");
        }

        private static WaypointSnapshot failure(String error) {
            return new WaypointSnapshot(List.of(), error);
        }
    }

    private record WaypointEntry(Optional<Waypoint> waypoint, String error) {
        private static WaypointEntry success(Waypoint waypoint) {
            return new WaypointEntry(Optional.of(waypoint), "");
        }

        private static WaypointEntry failure(String error) {
            return new WaypointEntry(Optional.empty(), error);
        }
    }
}
