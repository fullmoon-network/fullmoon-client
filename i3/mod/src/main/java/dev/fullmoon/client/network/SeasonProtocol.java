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

/**
 * The client half of the season pass: {@code season_open}, the whole pass as one snapshot, and
 * {@code season_progress}, the few figures the HUD widget follows.
 *
 * <p>Every string a player reads — an amount, the time left, the point's own name — arrives
 * finished. The client knows no price, no scale and no rule: it draws the snapshot and sends back
 * which advertised thing was chosen, and the server answers with the next snapshot.
 */
public final class SeasonProtocol {
    static final int MAX_LEVELS = 200;
    static final int MAX_MISSIONS = 16;
    static final int MAX_PERKS = 8;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_LABEL_LENGTH = 32;
    private static final int MAX_FIGURE_LENGTH = 32;
    private static final int MAX_UNIT_LENGTH = 8;
    private static final int MAX_POINT_LENGTH = 16;
    private static final int MAX_LINE_LENGTH = 160;
    private static final int MAX_MATERIAL_LENGTH = 96;
    private static final int MAX_TIERS = 16;
    private static final long MAX_COUNT = 1_000_000_000_000L;
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern MATERIAL_ID = Pattern.compile("[a-z0-9._-]+:[a-z0-9/._-]+");
    private static final Pattern COLOR = Pattern.compile("[a-z_]{1,16}");

    private SeasonProtocol() {}

    public enum Tab { TRACK, MISSIONS, PLUS }

    /** Where the season stands: before it starts, while it runs, and the grace after it in which rewards can still be taken. */
    public enum Phase { UPCOMING, LIVE, CLAIM }

    public enum Kind { WON, TITLE, COSMETIC }

    /** A reward cell's standing. {@code PENDING} is a cell the day's money limit holds back until it resets. */
    public enum Status { CLAIMED, CLAIMABLE, LOCKED, PLUS_LOCKED, PENDING }

    public enum Track { FREE, PLUS }

    public enum Tone { OK, ERROR }

    public record Open(
            int proto,
            String id,
            long revision,
            Tab tab,
            Season season,
            String point,
            Me me,
            List<Level> levels,
            Missions missions,
            Plus plus,
            Optional<Notice> notice) implements BridgeProtocol.Message {
        public Open {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(tab, "tab");
            Objects.requireNonNull(season, "season");
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(me, "me");
            levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
            Objects.requireNonNull(missions, "missions");
            Objects.requireNonNull(plus, "plus");
            Objects.requireNonNull(notice, "notice");
        }

        /** The column of {@code level}, or -1 when the track has none. */
        public int indexOf(int level) {
            for (int i = 0; i < levels.size(); i++) {
                if (levels.get(i).level() == level) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** {@code endsIn} is the server's own words for the time to the phase's next boundary. */
    public record Season(String id, String name, Phase phase, String endsIn, String endsAt) {
        public Season {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(endsIn, "endsIn");
            Objects.requireNonNull(endsAt, "endsAt");
        }
    }

    /**
     * The viewer's own standing. {@code into} of {@code step} is the way to the next level; on the
     * last level the two are equal. {@code balance} is empty when the server could not read it.
     */
    public record Me(
            int level,
            int maxLevel,
            long xp,
            long into,
            long step,
            boolean plus,
            int claimable,
            Optional<String> balance) {
        public Me {
            Objects.requireNonNull(balance, "balance");
        }

        public float fraction() {
            return step <= 0 ? 0.0f : Math.clamp((float) into / step, 0.0f, 1.0f);
        }
    }

    /** One column of the track; {@code at} is the season total it opens at. Either cell may be absent. */
    public record Level(int level, long at, Optional<Reward> free, Optional<Reward> plus) {
        public Level {
            Objects.requireNonNull(free, "free");
            Objects.requireNonNull(plus, "plus");
        }

        public Optional<Reward> reward(Track track) {
            return track == Track.FREE ? free : plus;
        }
    }

    /**
     * A reward cell. {@code color} is a title's server colour name; {@code cosmetic} and
     * {@code model} name a cosmetic and the item model string its icon wears. Each is empty where
     * the kind has none.
     */
    public record Reward(
            Kind kind,
            String label,
            String material,
            Status status,
            String color,
            String cosmetic,
            String model) {
        public Reward {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(cosmetic, "cosmetic");
            Objects.requireNonNull(model, "model");
        }
    }

    public record Missions(
            List<Mission> daily,
            List<Mission> weekly,
            List<Mission> season,
            Optional<Resets> resets) {
        public Missions {
            daily = List.copyOf(Objects.requireNonNull(daily, "daily"));
            weekly = List.copyOf(Objects.requireNonNull(weekly, "weekly"));
            season = List.copyOf(Objects.requireNonNull(season, "season"));
            Objects.requireNonNull(resets, "resets");
        }
    }

    /** The server's words for the time until today's and this week's missions turn over. */
    public record Resets(String daily, String weekly) {
        public Resets {
            Objects.requireNonNull(daily, "daily");
            Objects.requireNonNull(weekly, "weekly");
        }
    }

    /**
     * A mission row. {@code trackable} is whether the server will take a {@code track} for it —
     * a lobby, a mission counted in Discord, a finished one or a Plus extra the viewer cannot do
     * are all false. {@code tier} of {@code tiers} is how many stages are done.
     */
    public record Mission(
            String id,
            String name,
            String material,
            long progress,
            long goal,
            String unit,
            int xp,
            int tier,
            int tiers,
            boolean done,
            boolean plusExtra,
            boolean tracked,
            boolean trackable,
            boolean discord) {
        public Mission {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(unit, "unit");
        }

        public float fraction() {
            return done ? 1.0f : Math.clamp((float) progress / goal, 0.0f, 1.0f);
        }
    }

    /** {@code reason} says why {@code canBuy} is false, when the server has a reason to give. */
    public record Plus(
            boolean owned,
            String price,
            boolean canBuy,
            boolean confirming,
            List<String> perks,
            Optional<String> reason) {
        public Plus {
            Objects.requireNonNull(price, "price");
            perks = List.copyOf(Objects.requireNonNull(perks, "perks"));
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record Notice(Tone tone, String text) {
        public Notice {
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * What the HUD widget follows. A level of 0 is the server saying there is nothing to show.
     * {@code recent} is a mission that just moved and is not the tracked one; {@code flash} names a
     * stage that was just finished.
     */
    public record Progress(
            int proto,
            int level,
            long into,
            long step,
            Optional<Goal> tracked,
            Optional<Goal> recent,
            Optional<Flash> flash) implements BridgeProtocol.Message {
        public Progress {
            Objects.requireNonNull(tracked, "tracked");
            Objects.requireNonNull(recent, "recent");
            Objects.requireNonNull(flash, "flash");
        }

        public boolean hidden() {
            return level == 0;
        }

        public float fraction() {
            return step <= 0 ? 0.0f : Math.clamp((float) into / step, 0.0f, 1.0f);
        }
    }

    public record Goal(String id, String name, String material, long progress, long goal, String unit, boolean done) {
        public Goal {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(unit, "unit");
        }

        public float fraction() {
            return done ? 1.0f : Math.clamp((float) progress / goal, 0.0f, 1.0f);
        }
    }

    public record Flash(String mission, String label) {
        public Flash {
            Objects.requireNonNull(mission, "mission");
            Objects.requireNonNull(label, "label");
        }
    }

    /** What a player can ask of an open pass: one of these, against the snapshot it saw. */
    public sealed interface Action permits Claim, ClaimAll, BuyPlus, TrackMission, Untrack, SwitchTab {}

    public record Claim(int level, Track track) implements Action {
        public Claim {
            Objects.requireNonNull(track, "track");
        }
    }

    public record ClaimAll() implements Action {}

    /** The first press asks the server to confirm; the second, against the confirming snapshot, buys. */
    public record BuyPlus() implements Action {}

    public record TrackMission(String mission) implements Action {
        public TrackMission {
            Objects.requireNonNull(mission, "mission");
        }
    }

    public record Untrack() implements Action {}

    public record SwitchTab(Tab tab) implements Action {
        public SwitchTab {
            Objects.requireNonNull(tab, "tab");
        }
    }

    public static byte[] action(String sessionId, long revision, Action action) {
        requireSession(sessionId, revision);
        Objects.requireNonNull(action, "action");
        JsonObject json = new JsonObject();
        json.addProperty("type", "season_action");
        json.addProperty("id", sessionId);
        json.addProperty("revision", revision);
        switch (action) {
            case Claim claim -> {
                if (claim.level() < 1 || claim.level() > MAX_LEVELS) {
                    throw new IllegalArgumentException("season claim level is outside the track");
                }
                json.addProperty("action", "claim");
                json.addProperty("level", claim.level());
                json.addProperty("track", wire(claim.track()));
            }
            case ClaimAll ignored -> json.addProperty("action", "claim_all");
            case BuyPlus ignored -> json.addProperty("action", "buy_plus");
            case TrackMission track -> {
                if (!KEY.matcher(track.mission()).matches()) {
                    throw new IllegalArgumentException("season mission id is invalid");
                }
                json.addProperty("action", "track");
                json.addProperty("mission", track.mission());
            }
            case Untrack ignored -> json.addProperty("action", "untrack");
            case SwitchTab tab -> {
                json.addProperty("action", "tab");
                json.addProperty("tab", wire(tab.tab()));
            }
        }
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] close(String sessionId, long revision) {
        requireSession(sessionId, revision);
        JsonObject json = new JsonObject();
        json.addProperty("type", "season_close");
        json.addProperty("id", sessionId);
        json.addProperty("revision", revision);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Whether the widget is on the player's HUD; while it is, the server's progress bar stays away. */
    public static byte[] hud(boolean visible) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "season_hud");
        json.addProperty("visible", visible);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void requireSession(String sessionId, long revision) {
        if (sessionId == null || !SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("season session id is invalid");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("season revision must be non-negative");
        }
    }

    static BridgeProtocol.DecodeResult decodeOpen(JsonObject json) {
        try {
            return BridgeProtocol.DecodeResult.success(open(json));
        } catch (Malformed error) {
            return BridgeProtocol.DecodeResult.failure(error.getMessage());
        }
    }

    static BridgeProtocol.DecodeResult decodeProgress(JsonObject json) {
        try {
            return BridgeProtocol.DecodeResult.success(progress(json));
        } catch (Malformed error) {
            return BridgeProtocol.DecodeResult.failure(error.getMessage());
        }
    }

    private static Open open(JsonObject json) {
        String where = "season_open";
        int proto = proto(json, where);
        String id = text(json, "id", where, MAX_ID_LENGTH);
        if (!SESSION_ID.matcher(id).matches()) {
            throw new Malformed(where + " id is invalid");
        }
        long revision = whole(json, "revision", where, 0, Long.MAX_VALUE);
        Tab tab = choice(json, "tab", where, Tab.class);
        Season season = season(object(json, "season", where));
        String point = text(object(json, "terms", where), "point", where + " terms", MAX_POINT_LENGTH);
        Me me = me(object(json, "me", where));
        List<Level> levels = levels(json, me.maxLevel());
        Missions missions = missions(object(json, "missions", where));
        Plus plus = plus(object(json, "plus", where));
        Optional<Notice> notice = nullableObject(json, "notice", where).map(SeasonProtocol::notice);
        return new Open(proto, id, revision, tab, season, point, me, levels, missions, plus, notice);
    }

    private static Season season(JsonObject json) {
        String where = "season_open season";
        String id = text(json, "id", where, MAX_LABEL_LENGTH);
        String name = text(json, "name", where, MAX_NAME_LENGTH);
        Phase phase = choice(json, "phase", where, Phase.class);
        String endsIn = optionalText(json, "endsIn", where, MAX_FIGURE_LENGTH);
        String endsAt = optionalText(json, "endsAt", where, MAX_FIGURE_LENGTH);
        return new Season(id, name, phase, endsIn, endsAt);
    }

    private static Me me(JsonObject json) {
        String where = "season_open me";
        int maxLevel = (int) whole(json, "maxLevel", where, 1, MAX_LEVELS);
        int level = (int) whole(json, "level", where, 0, maxLevel);
        long xp = whole(json, "xp", where, 0, MAX_COUNT);
        long step = whole(json, "step", where, 1, MAX_COUNT);
        long into = whole(json, "into", where, 0, step);
        boolean plus = flag(json, "plus", where);
        int claimable = (int) whole(json, "claimable", where, 0, MAX_LEVELS * 2L);
        Optional<String> balance = nullableText(json, "balance", where, MAX_FIGURE_LENGTH);
        return new Me(level, maxLevel, xp, into, step, plus, claimable, balance);
    }

    private static List<Level> levels(JsonObject json, int maxLevel) {
        JsonArray array = array(json, "levels", "season_open", MAX_LEVELS);
        if (array.isEmpty()) {
            throw new Malformed("season_open levels must not be empty");
        }
        List<Level> levels = new ArrayList<>(array.size());
        int last = 0;
        for (int index = 0; index < array.size(); index++) {
            String where = "season_open level " + index;
            JsonObject entry = element(array.get(index), where);
            int level = (int) whole(entry, "level", where, 1, maxLevel);
            if (level <= last) {
                throw new Malformed(where + " is out of order");
            }
            last = level;
            long at = whole(entry, "at", where, 0, MAX_COUNT);
            Optional<Reward> free = nullableObject(entry, "free", where).map(o -> reward(o, where + " free"));
            Optional<Reward> plus = nullableObject(entry, "plus", where).map(o -> reward(o, where + " plus"));
            levels.add(new Level(level, at, free, plus));
        }
        return levels;
    }

    private static Reward reward(JsonObject json, String where) {
        Kind kind = choice(json, "kind", where, Kind.class);
        String label = text(json, "label", where, MAX_LABEL_LENGTH);
        String material = material(json, where);
        Status status = choice(json, "state", where, Status.class);
        String color = optionalText(json, "color", where, MAX_LABEL_LENGTH);
        if (!color.isEmpty() && !COLOR.matcher(color).matches()) {
            throw new Malformed(where + " color is invalid");
        }
        String cosmetic = "";
        String model = "";
        if (kind == Kind.COSMETIC) {
            cosmetic = key(json, "id", where);
            model = key(json, "model", where);
        }
        return new Reward(kind, label, material, status, color, cosmetic, model);
    }

    private static Missions missions(JsonObject json) {
        Set<String> ids = new HashSet<>();
        List<Mission> daily = missionList(json, "daily", ids);
        List<Mission> weekly = missionList(json, "weekly", ids);
        List<Mission> season = missionList(json, "season", ids);
        Optional<Resets> resets = nullableObject(json, "resets", "season_open missions").map(o -> new Resets(
            optionalText(o, "daily", "season_open missions resets", MAX_FIGURE_LENGTH),
            optionalText(o, "weekly", "season_open missions resets", MAX_FIGURE_LENGTH)));
        return new Missions(daily, weekly, season, resets);
    }

    private static List<Mission> missionList(JsonObject json, String key, Set<String> ids) {
        JsonArray array = array(json, key, "season_open missions", MAX_MISSIONS);
        List<Mission> missions = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            String where = "season_open mission " + key + " " + index;
            Mission mission = mission(element(array.get(index), where), where);
            if (!ids.add(mission.id())) {
                throw new Malformed("season_open mission id is duplicated: " + mission.id());
            }
            missions.add(mission);
        }
        return missions;
    }

    private static Mission mission(JsonObject json, String where) {
        String id = key(json, "id", where);
        String name = text(json, "name", where, MAX_NAME_LENGTH);
        String material = material(json, where);
        long goal = whole(json, "goal", where, 1, MAX_COUNT);
        long progress = whole(json, "progress", where, 0, MAX_COUNT);
        String unit = optionalText(json, "unit", where, MAX_UNIT_LENGTH);
        int xp = (int) whole(json, "xp", where, 0, Integer.MAX_VALUE);
        int tiers = (int) whole(json, "tiers", where, 1, MAX_TIERS);
        int tier = (int) whole(json, "tier", where, 0, tiers);
        return new Mission(id, name, material, progress, goal, unit, xp, tier, tiers,
            flag(json, "done", where), flag(json, "plusExtra", where), flag(json, "tracked", where),
            flag(json, "trackable", where, false), flag(json, "discord", where, false));
    }

    private static Plus plus(JsonObject json) {
        String where = "season_open plus";
        boolean owned = flag(json, "owned", where);
        String price = text(json, "price", where, MAX_FIGURE_LENGTH);
        boolean canBuy = flag(json, "canBuy", where);
        boolean confirming = flag(json, "confirming", where);
        JsonArray array = array(json, "perks", where, MAX_PERKS);
        List<String> perks = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new Malformed(where + " perks must contain strings");
            }
            String perk = element.getAsString().trim();
            if (perk.isEmpty() || perk.length() > MAX_NAME_LENGTH) {
                throw new Malformed(where + " perks contain an empty or oversized line");
            }
            perks.add(perk);
        }
        Optional<String> reason = nullableText(json, "reason", where, MAX_LINE_LENGTH);
        return new Plus(owned, price, canBuy, confirming, perks, reason);
    }

    private static Notice notice(JsonObject json) {
        String where = "season_open notice";
        return new Notice(choice(json, "tone", where, Tone.class), text(json, "text", where, MAX_LINE_LENGTH));
    }

    private static Progress progress(JsonObject json) {
        String where = "season_progress";
        int proto = proto(json, where);
        int level = (int) whole(json, "level", where, 0, MAX_LEVELS);
        if (level == 0) {
            return new Progress(proto, 0, 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
        }
        long step = whole(json, "step", where, 1, MAX_COUNT);
        long into = whole(json, "into", where, 0, step);
        Optional<Goal> tracked = nullableObject(json, "tracked", where).map(o -> goal(o, where + " tracked"));
        Optional<Goal> recent = nullableObject(json, "recent", where).map(o -> goal(o, where + " recent"));
        Optional<Flash> flash = nullableObject(json, "flash", where).map(o -> new Flash(
            key(o, "mission", where + " flash"), text(o, "label", where + " flash", MAX_NAME_LENGTH)));
        return new Progress(proto, level, into, step, tracked, recent, flash);
    }

    private static Goal goal(JsonObject json, String where) {
        String id = key(json, "id", where);
        String name = text(json, "name", where, MAX_NAME_LENGTH);
        String material = material(json, where);
        long goal = whole(json, "goal", where, 1, MAX_COUNT);
        long progress = whole(json, "progress", where, 0, MAX_COUNT);
        String unit = optionalText(json, "unit", where, MAX_UNIT_LENGTH);
        return new Goal(id, name, material, progress, goal, unit, flag(json, "done", where));
    }

    private static int proto(JsonObject json, String where) {
        if (!json.has("proto")) {
            return BridgeProtocol.VERSION;
        }
        return (int) whole(json, "proto", where, 0, Integer.MAX_VALUE);
    }

    private static String key(JsonObject json, String key, String where) {
        String value = text(json, key, where, MAX_ID_LENGTH);
        if (!KEY.matcher(value).matches()) {
            throw new Malformed(where + " " + key + " is invalid");
        }
        return value;
    }

    private static String material(JsonObject json, String where) {
        String value = text(json, "material", where, MAX_MATERIAL_LENGTH);
        if (!MATERIAL_ID.matcher(value).matches()) {
            throw new Malformed(where + " material is invalid");
        }
        return value;
    }

    private static String text(JsonObject json, String key, String where, int max) {
        String value = optionalText(json, key, where, max);
        if (value.isEmpty()) {
            throw new Malformed(where + " " + key + " is required");
        }
        return value;
    }

    /** A string that may be left out; present, it has to be a string that fits. */
    private static String optionalText(JsonObject json, String key, String where, int max) {
        return nullableText(json, key, where, max).orElse("");
    }

    private static Optional<String> nullableText(JsonObject json, String key, String where, int max) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new Malformed(where + " " + key + " must be a string");
        }
        String text = value.getAsString().trim();
        if (text.length() > max) {
            throw new Malformed(where + " " + key + " is too long");
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static long whole(JsonObject json, String key, String where, long min, long max) {
        JsonElement value = json.get(key);
        Long number = null;
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                number = new BigDecimal(value.getAsString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException error) {
                number = null;
            }
        }
        if (number == null || number < min || number > max) {
            throw new Malformed(where + " " + key + " must be a whole number from " + min + " to " + max);
        }
        return number;
    }

    private static boolean flag(JsonObject json, String key, String where) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new Malformed(where + " " + key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    /** A flag a newer server adds: left out, it is {@code absent}; present, it has to be a boolean. */
    private static boolean flag(JsonObject json, String key, String where, boolean absent) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? absent : flag(json, key, where);
    }

    private static <E extends Enum<E>> E choice(JsonObject json, String key, String where, Class<E> type) {
        String value = optionalText(json, key, where, MAX_LABEL_LENGTH);
        for (E constant : type.getEnumConstants()) {
            if (wire(constant).equals(value)) {
                return constant;
            }
        }
        throw new Malformed(where + " " + key + " is not supported: " + value);
    }

    private static JsonObject object(JsonObject json, String key, String where) {
        return nullableObject(json, key, where)
            .orElseThrow(() -> new Malformed(where + " " + key + " must be an object"));
    }

    private static Optional<JsonObject> nullableObject(JsonObject json, String key, String where) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonObject()) {
            throw new Malformed(where + " " + key + " must be an object");
        }
        return Optional.of(value.getAsJsonObject());
    }

    private static JsonObject element(JsonElement element, String where) {
        if (!element.isJsonObject()) {
            throw new Malformed(where + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String key, String where, int max) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new Malformed(where + " " + key + " must be an array");
        }
        JsonArray array = value.getAsJsonArray();
        if (array.size() > max) {
            throw new Malformed(where + " " + key + " exceed " + max + " entries");
        }
        return array;
    }

    /** One malformed field rejects the whole message, exactly like a malformed menu snapshot. */
    private static final class Malformed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Malformed(String message) {
            super(message, null, false, false);
        }
    }
}
