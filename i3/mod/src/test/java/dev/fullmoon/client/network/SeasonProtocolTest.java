package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

final class SeasonProtocolTest {
    private static final String OPEN = "/assets/fullmoon/season/season_open.json";
    private static final String PROGRESS = "/assets/fullmoon/season/season_progress.json";

    @Test
    void theServerGoldenDecodesEveryFieldItSends() {
        SeasonProtocol.Open pass = open(golden(OPEN));

        assertEquals("2ac6f3ea-8f45-4d72-bb9c-cbded41b57d1", pass.id());
        assertEquals(3, pass.revision());
        assertEquals(SeasonProtocol.Tab.TRACK, pass.tab());
        assertEquals(SeasonProtocol.Phase.LIVE, pass.season().phase());
        assertEquals("XP", pass.point());
        assertEquals(3, pass.me().level());
        assertEquals(1, pass.me().claimable());
        assertTrue(pass.me().balance().isPresent());
        assertEquals(5, pass.levels().size());
        assertEquals(3, pass.indexOf(4));
        assertEquals(-1, pass.indexOf(6), "a level the server did not send is not invented");
        assertEquals(SeasonProtocol.Status.PENDING,
            pass.levels().get(1).reward(SeasonProtocol.Track.FREE).orElseThrow().status());
        assertEquals(SeasonProtocol.Kind.COSMETIC,
            pass.levels().get(4).reward(SeasonProtocol.Track.PLUS).orElseThrow().kind());
        assertEquals(SeasonProtocol.Status.PLUS_LOCKED,
            pass.levels().get(0).reward(SeasonProtocol.Track.PLUS).orElseThrow().status());
        assertFalse(pass.missions().daily().isEmpty());
        assertTrue(pass.notice().isPresent());
        assertFalse(pass.plus().perks().isEmpty());
        assertTrue(pass.missions().daily().get(0).trackable());
    }

    @Test
    void theEndedPhaseIsAValidSnapshotAndKeepsItsCells() {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("season").addProperty("phase", "ended");
        json.getAsJsonObject("season").addProperty("endsIn", "");

        SeasonProtocol.Open pass = open(json);

        assertEquals(SeasonProtocol.Phase.ENDED, pass.season().phase());
        assertEquals(5, pass.levels().size(), "the closed season still shows its track");
    }

    @Test
    void aCosmeticCellCarriesItsItemIdAndModelString() {
        SeasonProtocol.Reward cosmetic = open(golden(OPEN)).levels().get(4)
            .reward(SeasonProtocol.Track.PLUS).orElseThrow();

        assertEquals("crescent_sword", cosmetic.cosmetic());
        assertEquals("fullmoon.skin.crescent_sword.1", cosmetic.model());
        assertEquals("minecraft:iron_sword", cosmetic.material());
        assertEquals(SeasonProtocol.Status.LOCKED, cosmetic.status());
    }

    @Test
    void nullableBalanceResetsAndNoticeAreEmptyNotInvented() {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("me").add("balance", JsonNull.INSTANCE);
        json.getAsJsonObject("missions").add("resets", JsonNull.INSTANCE);
        json.getAsJsonObject("plus").add("reason", JsonNull.INSTANCE);
        json.add("notice", JsonNull.INSTANCE);

        SeasonProtocol.Open pass = open(json);

        assertTrue(pass.me().balance().isEmpty());
        assertTrue(pass.missions().resets().isEmpty());
        assertTrue(pass.plus().reason().isEmpty());
        assertTrue(pass.notice().isEmpty());
    }

    @Test
    void theCollectionsAreImmutable() {
        SeasonProtocol.Open pass = open(golden(OPEN));

        assertThrows(UnsupportedOperationException.class, () -> pass.levels().clear());
        assertThrows(UnsupportedOperationException.class, () -> pass.missions().daily().clear());
        assertThrows(UnsupportedOperationException.class, () -> pass.plus().perks().clear());
    }

    @Test
    void aMalformedFieldRejectsTheWholeSnapshot() {
        assertEquals("season_open id is invalid",
            error(with("id", "season 7")));
        assertEquals("season_open revision must be a whole number from 0 to 9223372036854775807",
            error(with("revision", -1)));
        assertEquals("season_open tab is not supported: shop",
            error(with("tab", "shop")));
        assertEquals("season_open season phase is not supported: closed",
            error(season("phase", "closed")));
        assertEquals("season_open me maxLevel must be a whole number from 1 to 200",
            error(me("maxLevel", 0)));
        assertEquals("season_open me level must be a whole number from 0 to 5",
            error(me("level", 6)));
        assertEquals("season_open me step must be a whole number from 1 to 1000000000000",
            error(me("step", 0)));
        assertEquals("season_open me balance must be a string",
            error(me("balance", 50)));
        assertEquals("season_open levels must not be empty",
            error(levels(new JsonArray())));
        assertEquals("season_open level 0 free state is not supported: lost",
            error(firstReward("state", "lost")));
        assertEquals("season_open level 4 plus model is required",
            error(cosmeticReward("model", null)));
        assertEquals("season_open level 0 free material is invalid",
            error(firstReward("material", "Gold Nugget")));
        assertEquals("season_open mission id is duplicated: daily_coal",
            error(missionId("weekly_diamonds", "daily_coal")));
        assertEquals("season_open missions resets must be an object",
            error(missions("resets", 7)));
        assertEquals("season_open plus perks must contain strings",
            error(plusPerks("[7]")));
        assertEquals("season_open notice tone is not supported: warn",
            error(notice("warn")));
        assertEquals("season_open season name is required",
            error(season("name", "")));
    }

    @Test
    void aDisplayLineLongerThanTheClientBoundIsEllipsizedNotRefused() {
        // The producer binds only some display lines; the client presents what fits rather than
        // refusing a snapshot its own server can legally emit.
        SeasonProtocol.Open pass = open(season("name", "x".repeat(65)));
        assertEquals("x".repeat(61) + "...", pass.season().name());
        assertEquals(64, pass.season().name().length());

        assertEquals("y".repeat(29) + "...",
            open(firstReward("label", "y".repeat(33))).levels().get(0).free().orElseThrow().label());

        assertEquals("z".repeat(61) + "...",
            open(mission("name", "z".repeat(65))).missions().daily().get(0).name());

        assertEquals("p".repeat(157) + "...",
            open(noticeText("p".repeat(161))).notice().orElseThrow().text());

        assertEquals("q".repeat(157) + "...",
            open(plusReason("q".repeat(161))).plus().reason().orElseThrow());

        assertEquals("r".repeat(61) + "...",
            open(plusPerks("[\"" + "r".repeat(65) + "\"]")).plus().perks().get(0));

        assertEquals("s".repeat(29) + "...",
            open(me("balance", "s".repeat(33))).me().balance().orElseThrow());
    }

    @Test
    void identityAndNumericBoundsStayStrictWhileDisplayLinesGiveSlack() {
        assertEquals("season_open id is invalid", error(with("id", "season 7")));
        assertEquals("season_open me maxLevel must be a whole number from 1 to 200",
            error(me("maxLevel", 201)));
        assertEquals("season_open level 0 free material is invalid",
            error(firstReward("material", "Gold Nugget")));
        assertEquals("season_open revision must be a whole number from 0 to 9223372036854775807",
            error(with("revision", -1)));
    }

    @Test
    void progressCarriesTheWidgetsLinesAndAHiddenLevelDrawsNothing() {
        SeasonProtocol.Progress progress = progress(golden(PROGRESS));

        assertFalse(progress.hidden());
        assertEquals("daily_coal", progress.tracked().orElseThrow().id());
        assertEquals("season_hunter", progress.recent().orElseThrow().id());
        assertEquals(5f / 17f, progress.fraction());
        assertTrue(progress.flash().isPresent());

        SeasonProtocol.Progress hidden = progress(json("""
            {"type":"season_progress","level":0,"tracked":null}
            """));
        assertTrue(hidden.hidden());
        assertTrue(hidden.tracked().isEmpty());
        assertTrue(hidden.recent().isEmpty());
        assertTrue(hidden.flash().isEmpty());
        assertEquals(0.0f, hidden.fraction());
    }

    @Test
    void progressRejectsAnOverfullLevelOrAnUnnamedGoal() {
        assertEquals("season_progress into must be a whole number from 0 to 147",
            error(json("""
                {"type":"season_progress","level":3,"into":148,"step":147,"tracked":null}
                """)));
        assertEquals("season_progress tracked name is required",
            error(json("""
                {"type":"season_progress","level":3,"into":1,"step":17,
                 "tracked":{"id":"daily_coal","material":"minecraft:coal","progress":1,"goal":32,"done":false}}
                """)));
        assertEquals("season_progress flash mission is invalid",
            error(json("""
                {"type":"season_progress","level":3,"into":1,"step":17,"flash":{"mission":"Coal!","label":"Done"}}
                """)));
    }

    @Test
    void actionsNameTheSnapshotTheyWereMadeAgainst() {
        assertEquals("""
            {"type":"season_action","id":"session-7","revision":3,"action":"claim","level":12,"track":"plus"}""",
            text(SeasonProtocol.action("session-7", 3,
                new SeasonProtocol.Claim(12, SeasonProtocol.Track.PLUS))));
        assertEquals("claim_all",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.ClaimAll()), "action"));
        assertEquals("buy_plus",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.BuyPlus()), "action"));
        assertEquals("untrack",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.Untrack()), "action"));
        assertEquals("daily_coal",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.TrackMission("daily_coal")), "mission"));
        assertEquals("missions",
            field(SeasonProtocol.action("s", 1,
                new SeasonProtocol.SwitchTab(SeasonProtocol.Tab.MISSIONS)), "tab"));
        assertEquals("""
            {"type":"season_close","id":"session-7","revision":4}""",
            text(SeasonProtocol.close("session-7", 4)));
        assertEquals("""
            {"type":"season_hud","visible":true}""", text(SeasonProtocol.hud(true)));
    }

    @Test
    void actionsRefuseWhatNoServerCouldHaveSent() {
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("session 7", 1, new SeasonProtocol.ClaimAll()));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", -1, new SeasonProtocol.ClaimAll()));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", 1, new SeasonProtocol.Claim(0, SeasonProtocol.Track.FREE)));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", 1, new SeasonProtocol.TrackMission("Coal!")));
        assertThrows(IllegalArgumentException.class, () -> SeasonProtocol.close("", 1));
    }

    @Test
    void unknownMessagesStayUnknownAndDoNotBecomeSeasonData() {
        BridgeProtocol.Unknown unknown = assertInstanceOf(BridgeProtocol.Unknown.class,
            BridgeProtocol.decode(json("""
                {"type":"season_future","proto":1,"level":3}
                """).toString().getBytes(StandardCharsets.UTF_8)).message().orElseThrow());

        assertEquals("season_future", unknown.type());
    }

    @Test
    void helloNamesTheSurfacesAndPacksOnlyWhenThereAreAny() {
        JsonObject bare = JsonParser.parseString(text(BridgeProtocol.hello("3.3.0-beta.1"))).getAsJsonObject();
        JsonObject full = JsonParser.parseString(text(BridgeProtocol.hello("3.3.0-beta.1",
            List.of("season_v1", "season_hud"),
            List.of(new BridgeProtocol.Pack(
                "fullmoon-gui", "175de05a740e46b47a2db6533c3d217c1c14cb19"))))).getAsJsonObject();

        assertFalse(bare.has("features"));
        assertFalse(bare.has("packs"));
        assertEquals("[\"season_v1\",\"season_hud\"]", full.get("features").toString());
        assertEquals("[{\"id\":\"fullmoon-gui\",\"sha1\":\"175de05a740e46b47a2db6533c3d217c1c14cb19\"}]",
            full.get("packs").toString());
    }

    @Test
    void helloBoundsAndDeduplicatesWhatTheServerWillParse() {
        assertEquals("[\"season_v1\"]", JsonParser.parseString(text(BridgeProtocol.hello("3.3.0-beta.1",
            List.of("season_v1", "season_v1"), List.of()))).getAsJsonObject().get("features").toString());
        assertThrows(IllegalArgumentException.class,
            () -> BridgeProtocol.hello("3.3.0-beta.1", List.of("Season V1"), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> BridgeProtocol.hello("3.3.0-beta.1",
                java.util.stream.IntStream.range(0, 33).mapToObj(i -> "feature_" + i).toList(), List.of()));
        assertEquals("pack sha1 must be 40 lowercase hex digits", assertThrows(
            IllegalArgumentException.class,
            () -> new BridgeProtocol.Pack("fullmoon-gui", "175DE05A740E46B47A2DB6533C3D217C1C14CB19"))
            .getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> new BridgeProtocol.Pack("", "0".repeat(40)));
        assertEquals("hello packs exceed 8 entries", assertThrows(IllegalArgumentException.class,
            () -> BridgeProtocol.hello("3.3.0-beta.1", List.of(), java.util.Collections.nCopies(9,
                new BridgeProtocol.Pack("pack", "0".repeat(40))))).getMessage());
    }

    private static JsonObject golden(String resource) {
        try (InputStream in = SeasonProtocolTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject with(String key, Object value) {
        JsonObject json = golden(OPEN);
        json.add(key, JsonParser.parseString(
            value instanceof String string ? "\"" + string + "\"" : value.toString()));
        return json;
    }

    private static JsonObject season(String key, Object value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("season").add(key, JsonParser.parseString(
            value instanceof String string ? "\"" + string + "\"" : value.toString()));
        return json;
    }

    private static JsonObject me(String key, Object value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("me").add(key, JsonParser.parseString(
            value instanceof String string ? "\"" + string + "\"" : value.toString()));
        return json;
    }

    private static JsonObject missions(String key, Object value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("missions").add(key, JsonParser.parseString(value.toString()));
        return json;
    }

    private static JsonObject mission(String key, Object value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("missions").getAsJsonArray("daily").get(0).getAsJsonObject()
            .add(key, JsonParser.parseString(
                value instanceof String string ? "\"" + string + "\"" : value.toString()));
        return json;
    }

    private static JsonObject noticeText(String value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("notice").addProperty("text", value);
        return json;
    }

    private static JsonObject plusReason(String value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("plus").addProperty("reason", value);
        return json;
    }

    private static JsonObject plusPerks(Object value) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("plus").add("perks", JsonParser.parseString(value.toString()));
        return json;
    }

    private static JsonObject notice(String tone) {
        JsonObject json = golden(OPEN);
        json.getAsJsonObject("notice").addProperty("tone", tone);
        return json;
    }

    private static JsonObject levels(JsonArray array) {
        JsonObject json = golden(OPEN);
        json.add("levels", array);
        return json;
    }

    private static JsonObject firstReward(String key, Object value) {
        JsonObject json = golden(OPEN);
        JsonObject reward = json.getAsJsonArray("levels").get(0).getAsJsonObject()
            .getAsJsonObject("free");
        put(reward, key, value);
        return json;
    }

    private static JsonObject cosmeticReward(String key, Object value) {
        JsonObject json = golden(OPEN);
        JsonObject reward = json.getAsJsonArray("levels").get(4).getAsJsonObject()
            .getAsJsonObject("plus");
        put(reward, key, value);
        return json;
    }

    private static void put(JsonObject target, String key, Object value) {
        if (value == null) {
            target.remove(key);
        } else {
            target.add(key, JsonParser.parseString(
                value instanceof String string ? "\"" + string + "\"" : value.toString()));
        }
    }

    private static JsonObject missionId(String from, String to) {
        JsonObject json = golden(OPEN);
        JsonObject weekly = json.getAsJsonObject("missions").getAsJsonArray("weekly")
            .get(0).getAsJsonObject();
        if (weekly.get("id").getAsString().equals(from)) {
            weekly.addProperty("id", to);
        }
        return json;
    }

    private static SeasonProtocol.Open open(JsonObject json) {
        return assertInstanceOf(SeasonProtocol.Open.class,
            BridgeProtocol.decode(json.toString().getBytes(StandardCharsets.UTF_8))
                .message().orElseThrow());
    }

    private static SeasonProtocol.Progress progress(JsonObject json) {
        return assertInstanceOf(SeasonProtocol.Progress.class,
            BridgeProtocol.decode(json.toString().getBytes(StandardCharsets.UTF_8))
                .message().orElseThrow());
    }

    private static String error(JsonObject json) {
        return BridgeProtocol.decode(json.toString().getBytes(StandardCharsets.UTF_8))
            .error().orElseThrow();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String field(byte[] bytes, String key) {
        return JsonParser.parseString(text(bytes)).getAsJsonObject().get(key).getAsString();
    }
}
