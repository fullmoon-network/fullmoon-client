package dev.fullmoon.client.network;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class MenuProtocol {
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_LABEL_LENGTH = 64;
    private static final int MAX_MATERIAL_LENGTH = 96;
    private static final int MAX_DETAIL_LENGTH = 160;
    private static final int MAX_DETAILS = 8;
    private static final int MAX_ITEMS = 54;
    private static final int MAX_ICON_LENGTH = 64;
    private static final Pattern MENU_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern MATERIAL_ID =
        Pattern.compile("[a-z0-9._-]+:[a-z0-9/._-]+");
    private static final Pattern ICON_ID =
        Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private MenuProtocol() {}

    public record Open(
            int proto,
            String id,
            long revision,
            String title,
            int rows,
            List<Item> items) implements BridgeProtocol.Message {
        public Open {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    public record Close(int proto, String id, long revision) implements BridgeProtocol.Message {
        public Close {
            Objects.requireNonNull(id, "id");
        }
    }

    public record Item(
            int slot,
            String label,
            String material,
            int count,
            List<String> details,
            List<Click> actions,
            String icon) {
        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(material, "material");
            details = List.copyOf(Objects.requireNonNull(details, "details"));
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            Objects.requireNonNull(icon, "icon");
        }
    }

    public enum Click {
        LEFT("left"),
        SHIFT_LEFT("shift_left");

        private final String wireName;

        Click(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static Optional<Click> fromWire(String value) {
            for (Click click : values()) {
                if (click.wireName.equals(value)) {
                    return Optional.of(click);
                }
            }
            return Optional.empty();
        }
    }

    public static byte[] action(String menuId, long revision, int slot, Click click) {
        String error = requestError(menuId, revision, slot);
        if (!error.isEmpty()) {
            throw new IllegalArgumentException(error);
        }
        Objects.requireNonNull(click, "click");

        JsonObject json = new JsonObject();
        json.addProperty("type", "menu_action");
        json.addProperty("id", menuId);
        json.addProperty("revision", revision);
        json.addProperty("slot", slot);
        json.addProperty("click", click.wireName());
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] close(String menuId, long revision) {
        String error = requestError(menuId, revision, 0);
        if (!error.isEmpty()) {
            throw new IllegalArgumentException(error);
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "menu_close");
        json.addProperty("id", menuId);
        json.addProperty("revision", revision);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    static BridgeProtocol.DecodeResult decodeOpen(JsonObject json) {
        Integer proto = operationalProto(json);
        if (proto == null) {
            return failure("menu_open proto must be a non-negative integer");
        }
        String id = string(json, "id");
        if (id.isEmpty()) {
            return failure("menu_open id is required");
        }
        if (!validId(id)) {
            return failure("menu_open id is invalid");
        }
        Long revision = longInteger(json, "revision");
        if (revision == null || revision < 0) {
            return failure("menu_open revision must be non-negative");
        }
        String title = string(json, "title");
        if (title.isEmpty()) {
            return failure("menu_open title is required");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return failure("menu_open title is too long");
        }
        Integer rows = integer(json, "rows");
        if (rows == null || rows < 1 || rows > 6) {
            return failure("menu_open rows must be between 1 and 6");
        }
        return decodeItems(json, rows).toResult(proto, id, revision, title, rows);
    }

    static BridgeProtocol.DecodeResult decodeClose(JsonObject json) {
        Integer proto = operationalProto(json);
        if (proto == null) {
            return failure("menu_close proto must be a non-negative integer");
        }
        String id = string(json, "id");
        if (id.isEmpty()) {
            return failure("menu_close id is required");
        }
        if (!validId(id)) {
            return failure("menu_close id is invalid");
        }
        Long revision = longInteger(json, "revision");
        if (revision == null || revision < 0) {
            return failure("menu_close revision must be non-negative");
        }
        return BridgeProtocol.DecodeResult.success(new Close(proto, id, revision));
    }

    private static Items decodeItems(JsonObject json, int rows) {
        if (!json.has("items") || !json.get("items").isJsonArray()) {
            return Items.failure("menu_open items must be an array");
        }
        JsonArray array = json.getAsJsonArray("items");
        if (array.size() > MAX_ITEMS) {
            return Items.failure("menu_open items exceed 54 entries");
        }
        List<Item> items = new ArrayList<>(array.size());
        Set<Integer> slots = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            ItemEntry entry = decodeItem(array.get(index), index, rows * 9);
            if (!entry.error().isEmpty()) {
                return Items.failure(entry.error());
            }
            Item item = entry.item().orElseThrow();
            if (!slots.add(item.slot())) {
                return Items.failure("menu item slot is duplicated: " + item.slot());
            }
            items.add(item);
        }
        return Items.success(items);
    }

    private static ItemEntry decodeItem(JsonElement element, int index, int size) {
        if (!element.isJsonObject()) {
            return ItemEntry.failure("menu item " + index + " must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        Integer slot = integer(json, "slot");
        if (slot == null || slot < 0 || slot >= size) {
            return ItemEntry.failure("menu item " + index + " slot is outside the menu");
        }
        String label = string(json, "label");
        if (label.isEmpty()) {
            return ItemEntry.failure("menu item " + index + " label is required");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            return ItemEntry.failure("menu item " + index + " label is too long");
        }
        String material = string(json, "material");
        if (!MATERIAL_ID.matcher(material).matches() || material.length() > MAX_MATERIAL_LENGTH) {
            return ItemEntry.failure("menu item " + index + " material is invalid");
        }
        Integer count = integer(json, "count");
        if (count == null || count < 1 || count > 99) {
            return ItemEntry.failure("menu item " + index + " count must be between 1 and 99");
        }
        TextList details = decodeTextList(json, index);
        if (!details.error().isEmpty()) {
            return ItemEntry.failure(details.error());
        }
        Clicks actions = decodeClicks(json, index);
        if (!actions.error().isEmpty()) {
            return ItemEntry.failure(actions.error());
        }
        String icon = string(json, "icon");
        if (!icon.isEmpty() && !ICON_ID.matcher(icon).matches()) {
            return ItemEntry.failure("menu item " + index + " icon is invalid");
        }
        return ItemEntry.success(new Item(
            slot, label, material, count, details.values(), actions.values(), icon));
    }

    private static TextList decodeTextList(JsonObject json, int index) {
        String field = "menu item " + index + " details";
        if (!json.has("details") || !json.get("details").isJsonArray()) {
            return TextList.failure(field + " must be an array");
        }
        JsonArray array = json.getAsJsonArray("details");
        if (array.size() > MAX_DETAILS) {
            return TextList.failure(field + " exceed " + MAX_DETAILS + " entries");
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return TextList.failure(field + " must contain strings");
            }
            String value = element.getAsString().trim();
            if (value.length() > MAX_DETAIL_LENGTH) {
                return TextList.failure(field + " contain oversized text");
            }
            values.add(value);
        }
        return TextList.success(values);
    }

    private static Clicks decodeClicks(JsonObject json, int index) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) {
            return Clicks.failure("menu item " + index + " actions must be an array");
        }
        JsonArray array = json.getAsJsonArray("actions");
        if (array.size() > Click.values().length) {
            return Clicks.failure("menu item " + index + " has too many actions");
        }
        List<Click> clicks = new ArrayList<>(array.size());
        Set<Click> unique = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return Clicks.failure("menu item " + index + " action is invalid");
            }
            Optional<Click> click = Click.fromWire(element.getAsString());
            if (click.isEmpty() || !unique.add(click.orElseThrow())) {
                return Clicks.failure("menu item " + index + " action is invalid");
            }
            clicks.add(click.orElseThrow());
        }
        return Clicks.success(clicks);
    }

    private static String requestError(String menuId, long revision, int slot) {
        if (!validId(menuId)) {
            return "menu id is invalid";
        }
        if (revision < 0) {
            return "menu revision must be non-negative";
        }
        if (slot < 0 || slot >= MAX_ITEMS) {
            return "menu slot must be between 0 and 53";
        }
        return "";
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH
            && MENU_ID.matcher(id).matches();
    }

    private static Integer operationalProto(JsonObject json) {
        if (!json.has("proto")) {
            return BridgeProtocol.VERSION;
        }
        Integer proto = integer(json, "proto");
        return proto == null || proto < 0 ? null : proto;
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

    private static BridgeProtocol.DecodeResult failure(String error) {
        return BridgeProtocol.DecodeResult.failure(error);
    }

    private record Items(List<Item> items, String error) {
        private Items {
            items = List.copyOf(items);
        }

        private static Items success(List<Item> items) {
            return new Items(items, "");
        }

        private static Items failure(String error) {
            return new Items(List.of(), error);
        }

        private BridgeProtocol.DecodeResult toResult(
                int proto, String id, long revision, String title, int rows) {
            return error.isEmpty()
                ? BridgeProtocol.DecodeResult.success(new Open(proto, id, revision, title, rows, items))
                : BridgeProtocol.DecodeResult.failure(error);
        }
    }

    private record ItemEntry(Optional<Item> item, String error) {
        private static ItemEntry success(Item item) {
            return new ItemEntry(Optional.of(item), "");
        }

        private static ItemEntry failure(String error) {
            return new ItemEntry(Optional.empty(), error);
        }
    }

    private record TextList(List<String> values, String error) {
        private TextList {
            values = List.copyOf(values);
        }

        private static TextList success(List<String> values) {
            return new TextList(values, "");
        }

        private static TextList failure(String error) {
            return new TextList(List.of(), error);
        }
    }

    private record Clicks(List<Click> values, String error) {
        private Clicks {
            values = List.copyOf(values);
        }

        private static Clicks success(List<Click> values) {
            return new Clicks(values, "");
        }

        private static Clicks failure(String error) {
            return new Clicks(List.of(), error);
        }
    }
}
