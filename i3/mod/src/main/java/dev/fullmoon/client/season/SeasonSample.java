package dev.fullmoon.client.season;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.SeasonProtocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.gui.screens.Screen;

/**
 * The beta season the server's own test producer sent, used only to photograph the screen and the
 * widget without a server. The two source documents are copied byte for byte from the server's
 * golden fixtures; the variants change only a phase, a tab, or a frame flag, never an amount.
 *
 * <p>Every fixture goes through the same decoder a live payload does, so a fixture that drifts
 * outside the contract fails the way a server would. The capture rig is the only caller.
 */
public final class SeasonSample {
    public static final String PROPERTY = "fullmoon.devScreen";
    private static final String OPEN = "season_open.json";
    private static final String PROGRESS = "season_progress.json";
    private static final Set<String> NAMES = Set.of(
        "season-open", "season-ended", "season-missions", "season-plus", "season-hud",
        "season-error");
    private static final long HUD_LOOP = 6_000;
    private static final long FLASH_SHOWN = 2_000;

    private SeasonSample() {}

    /** The pass the {@value #PROPERTY} system property names, if it names one. */
    public static Optional<SeasonProtocol.Open> requested() {
        String name = System.getProperty(PROPERTY, "");
        if (!NAMES.contains(name) || name.equals("season-hud")) {
            return Optional.empty();
        }
        JsonObject json = open();
        switch (name) {
            case "season-ended" -> {
                JsonObject season = json.getAsJsonObject("season");
                season.addProperty("phase", "ended");
                season.addProperty("endsIn", "");
                // The producer's own ended state: every unclaimed cell locks and the claim count
                // closes; the purchase is refused with the window shut as well.
                for (JsonElement entry : json.getAsJsonArray("levels")) {
                    JsonObject level = entry.getAsJsonObject();
                    for (String track : List.of("free", "plus")) {
                        if (level.has(track) && !level.get(track).isJsonNull()) {
                            JsonObject reward = level.getAsJsonObject(track);
                            if (!"claimed".equals(reward.get("state").getAsString())) {
                                reward.addProperty("state", "locked");
                            }
                        }
                    }
                }
                json.getAsJsonObject("me").addProperty("claimable", 0);
                JsonObject plus = json.getAsJsonObject("plus");
                plus.addProperty("canBuy", false);
                plus.addProperty("confirming", false);
            }
            case "season-missions" -> {
                json.addProperty("tab", "missions");
                json.add("notice", JsonNull.INSTANCE);
            }
            case "season-plus" -> {
                json.addProperty("tab", "plus");
                JsonObject plus = json.getAsJsonObject("plus");
                plus.addProperty("canBuy", false);
                plus.addProperty("confirming", false);
                plus.addProperty("reason", "Plus is not available on this account");
                JsonObject notice = new JsonObject();
                notice.addProperty("tone", "error");
                notice.addProperty("text", "This purchase is not available right now");
                json.add("notice", notice);
            }
            default -> {
                // The live golden, unchanged.
            }
        }
        return BridgeProtocol.decode(bytes(json)).message()
            .filter(SeasonProtocol.Open.class::isInstance)
            .map(SeasonProtocol.Open.class::cast);
    }

    /**
     * Whether the requested fixture is the one that runs the screen's own send path against a
     * bridge-less backend: the refusal it draws is the client's, not a fixture's.
     */
    public static boolean liveErrorRequested() {
        return "season-error".equals(System.getProperty(PROPERTY, ""));
    }

    public static Screen screen(Screen parent, SeasonProtocol.Open pass) {
        return new SeasonScreen(parent, pass, true);
    }

    /** The widget's feed while a season fixture is up, decoded like a live {@code season_progress}. */
    public static Optional<BridgeState.SeasonFeed> feed(long now) {
        String name = System.getProperty(PROPERTY, "");
        if (!name.equals("season-hud")) {
            return Optional.empty();
        }
        return BridgeProtocol.decode(bytes(progress())).message()
            .filter(SeasonProtocol.Progress.class::isInstance)
            .map(SeasonProtocol.Progress.class::cast)
            .map(progress -> {
                long start = now - now % HUD_LOOP;
                long recentAt = now - start < FLASH_SHOWN
                    ? start - BridgeState.SEASON_RECENT_MILLIS : start + FLASH_SHOWN;
                return new BridgeState.SeasonFeed(progress.level(), progress.into(), progress.step(),
                    progress.tracked(), progress.recent(), recentAt, progress.flash(), start);
            });
    }

    static JsonObject open() {
        return resource(OPEN);
    }

    static JsonObject progress() {
        return resource(PROGRESS);
    }

    private static JsonObject resource(String name) {
        try (InputStream in = SeasonSample.class.getResourceAsStream(
                "/assets/fullmoon/season/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing season fixture " + name);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException("Could not read season fixture " + name, error);
        }
    }

    private static byte[] bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
