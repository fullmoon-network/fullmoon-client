package dev.fullmoon.client.network;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The client half of coin-bridge's {@code casino_result}: one settled bet, pushed after the money
 * moved. Presentation only — the payload carries no stake and no balance, and a client that
 * rejects it still has the server's chat line.
 */
public final class CasinoProtocol {
    private static final double MAX_MULTIPLIER = 1_000;
    private static final int POCKETS = 37;
    private static final int PERCENTILE = 100;
    private static final int MAX_REELS = 5;
    private static final int MAX_BET_LENGTH = 32;
    private static final Pattern SYMBOL_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    private CasinoProtocol() {}

    public record Result(
            int proto,
            Game game,
            boolean won,
            double payoutMultiplier,
            Detail detail) implements BridgeProtocol.Message {
        public Result {
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(detail, "detail");
        }
    }

    public enum Game {
        COINFLIP("coinflip"),
        DICE("dice"),
        ROULETTE("roulette"),
        SLOTS("slots");

        private final String wireName;

        Game(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static Optional<Game> fromWire(String value) {
            for (Game game : values()) {
                if (game.wireName.equals(value)) {
                    return Optional.of(game);
                }
            }
            return Optional.empty();
        }
    }

    public sealed interface Detail permits Coin, Roll, Spin, Reels {}

    public record Coin() implements Detail {}

    /** A percentile roll: {@code roll} in 0..99 wins when it lands under {@code target}. */
    public record Roll(int roll, int target) implements Detail {}

    public record Spin(int pocket, String bet) implements Detail {
        public Spin {
            Objects.requireNonNull(bet, "bet");
        }
    }

    /** Reel symbol ids left to right; {@code matched} is the largest run of one symbol. */
    public record Reels(List<String> symbols, int matched) implements Detail {
        public Reels {
            symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        }
    }

    static BridgeProtocol.DecodeResult decodeResult(JsonObject json) {
        Integer proto = operationalProto(json);
        if (proto == null) {
            return failure("casino_result proto must be a non-negative integer");
        }
        String gameName = string(json, "game");
        if (gameName.isEmpty()) {
            return failure("casino_result game is required");
        }
        Optional<Game> game = Game.fromWire(gameName);
        if (game.isEmpty()) {
            return failure("casino_result game is not supported: " + gameName);
        }
        Boolean won = booleanValue(json, "won");
        if (won == null) {
            return failure("casino_result won must be a boolean");
        }
        Double multiplier = finiteNumber(json, "payout_multiplier");
        if (multiplier == null || multiplier < 0 || multiplier > MAX_MULTIPLIER) {
            return failure("casino_result payout_multiplier must be between 0 and 1000");
        }
        if (won && multiplier == 0) {
            return failure("casino_result payout_multiplier must be positive when won");
        }
        JsonObject detail = json.has("detail") && json.get("detail").isJsonObject()
            ? json.getAsJsonObject("detail") : new JsonObject();
        DetailEntry entry = switch (game.orElseThrow()) {
            case COINFLIP -> DetailEntry.success(new Coin());
            case DICE -> decodeRoll(detail);
            case ROULETTE -> decodeSpin(detail);
            case SLOTS -> decodeReels(detail);
        };
        if (!entry.error().isEmpty()) {
            return failure(entry.error());
        }
        return BridgeProtocol.DecodeResult.success(new Result(
            proto, game.orElseThrow(), won, multiplier, entry.detail().orElseThrow()));
    }

    private static DetailEntry decodeRoll(JsonObject detail) {
        Integer roll = integer(detail, "roll");
        if (roll == null || roll < 0 || roll >= PERCENTILE) {
            return DetailEntry.failure("casino_result dice roll must be between 0 and 99");
        }
        Integer target = integer(detail, "target");
        if (target == null || target < 0 || target > PERCENTILE) {
            return DetailEntry.failure("casino_result dice target must be between 0 and 100");
        }
        return DetailEntry.success(new Roll(roll, target));
    }

    private static DetailEntry decodeSpin(JsonObject detail) {
        Integer pocket = integer(detail, "pocket");
        if (pocket == null || pocket < 0 || pocket >= POCKETS) {
            return DetailEntry.failure("casino_result roulette pocket must be between 0 and 36");
        }
        String bet = string(detail, "bet_type");
        if (bet.isEmpty()) {
            return DetailEntry.failure("casino_result roulette bet_type is required");
        }
        if (bet.length() > MAX_BET_LENGTH) {
            return DetailEntry.failure("casino_result roulette bet_type is too long");
        }
        return DetailEntry.success(new Spin(pocket, bet));
    }

    private static DetailEntry decodeReels(JsonObject detail) {
        JsonElement value = detail.get("reels");
        if (value == null || !value.isJsonArray()) {
            return DetailEntry.failure("casino_result slots reels must be an array");
        }
        JsonArray array = value.getAsJsonArray();
        if (array.isEmpty() || array.size() > MAX_REELS) {
            return DetailEntry.failure("casino_result slots reels must hold 1 to 5 symbols");
        }
        List<String> symbols = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            String symbol = element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString().trim() : "";
            if (!SYMBOL_ID.matcher(symbol).matches()) {
                return DetailEntry.failure("casino_result slots symbol is invalid");
            }
            symbols.add(symbol);
        }
        Integer matched = integer(detail, "matched");
        if (matched == null || matched < 1 || matched > symbols.size()) {
            return DetailEntry.failure("casino_result slots matched must be between 1 and the reel count");
        }
        return DetailEntry.success(new Reels(symbols, matched));
    }

    private static Integer operationalProto(JsonObject json) {
        if (!json.has("proto")) {
            return BridgeProtocol.VERSION;
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

    private static Integer integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive(key);
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
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
        return primitive.isString() ? primitive.getAsString().trim() : "";
    }

    private static BridgeProtocol.DecodeResult failure(String error) {
        return BridgeProtocol.DecodeResult.failure(error);
    }

    private record DetailEntry(Optional<Detail> detail, String error) {
        private static DetailEntry success(Detail detail) {
            return new DetailEntry(Optional.of(detail), "");
        }

        private static DetailEntry failure(String error) {
            return new DetailEntry(Optional.empty(), error);
        }
    }
}
