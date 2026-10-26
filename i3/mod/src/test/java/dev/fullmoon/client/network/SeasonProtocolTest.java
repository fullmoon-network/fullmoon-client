package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

final class SeasonProtocolTest {
    /** Two levels of a fifty-level pass: every field a server sends, once. */
    private static final String PASS = """
        {"type":"season_open","id":"season-7","revision":3,"tab":"track",
         "season":{"id":"s1","name":"시즌 1","phase":"live","endsIn":"17일 남음","endsAt":"2026-12-28T06:00:00+09:00"},
         "terms":{"point":"XP"},
         "me":{"level":2,"maxLevel":50,"xp":30,"into":19,"step":19,"plus":false,"claimable":1,"balance":null},
         "levels":[
          {"level":1,"at":0,
           "free":{"kind":"won","label":"5천원","material":"minecraft:gold_nugget","state":"claimed"},
           "plus":{"kind":"title","label":"초승달 동행","color":"light_purple","material":"minecraft:name_tag","state":"plus_locked"}},
          {"level":2,"at":11,
           "free":{"kind":"cosmetic","label":"달빛 곡괭이","id":"moonlight_pickaxe","model":"fullmoon.skin.moonlight_pickaxe.1",
                   "material":"minecraft:iron_pickaxe","state":"claimable"},
           "plus":null}],
         "missions":{
          "daily":[{"id":"daily_coal","name":"석탄 캐기","material":"minecraft:coal","progress":12,"goal":32,"unit":"개",
                    "xp":30,"tier":0,"tiers":1,"done":false,"plusExtra":false,"tracked":true,"trackable":true}],
          "weekly":[{"id":"weekly_voice","name":"음성 채널에서 함께하기","material":"minecraft:note_block","progress":40,"goal":180,
                     "unit":"분","xp":120,"tier":0,"tiers":1,"done":false,"plusExtra":false,"tracked":false,"discord":true}],
          "season":[],
          "resets":{"daily":"3시간 12분","weekly":"2일 4시간"}},
         "plus":{"owned":false,"price":"40만원","canBuy":false,"confirming":false,"perks":["레벨마다 하나 더"],
                 "reason":"돈이 조금 모자라요"},
         "notice":{"tone":"ok","text":"보상을 받았어요"}}
        """;

    @Test
    void aPassDecodesEveryFieldTheServerSends() {
        SeasonProtocol.Open pass = open(PASS);

        assertEquals("season-7", pass.id());
        assertEquals(3, pass.revision());
        assertEquals(SeasonProtocol.Tab.TRACK, pass.tab());
        assertEquals(SeasonProtocol.Phase.LIVE, pass.season().phase());
        assertEquals("XP", pass.point());
        assertEquals(Optional.empty(), pass.me().balance());
        assertEquals(1.0f, pass.me().fraction());
        assertEquals(2, pass.levels().size());
        assertEquals(1, pass.indexOf(2));
        assertEquals(-1, pass.indexOf(3));

        SeasonProtocol.Reward title = pass.levels().get(0).reward(SeasonProtocol.Track.PLUS).orElseThrow();
        assertEquals(SeasonProtocol.Kind.TITLE, title.kind());
        assertEquals("light_purple", title.color());
        assertEquals(SeasonProtocol.Status.PLUS_LOCKED, title.status());
        SeasonProtocol.Reward skin = pass.levels().get(1).reward(SeasonProtocol.Track.FREE).orElseThrow();
        assertEquals("moonlight_pickaxe", skin.cosmetic());
        assertEquals("fullmoon.skin.moonlight_pickaxe.1", skin.model());
        assertTrue(pass.levels().get(1).plus().isEmpty());

        SeasonProtocol.Mission coal = pass.missions().daily().get(0);
        assertTrue(coal.tracked());
        assertTrue(coal.trackable());
        assertEquals(12f / 32f, coal.fraction());
        SeasonProtocol.Mission voice = pass.missions().weekly().get(0);
        assertTrue(voice.discord());
        assertFalse(voice.trackable());
        assertEquals("2일 4시간", pass.missions().resets().orElseThrow().weekly());

        assertEquals(Optional.of("돈이 조금 모자라요"), pass.plus().reason());
        assertEquals(new SeasonProtocol.Notice(SeasonProtocol.Tone.OK, "보상을 받았어요"), pass.notice().orElseThrow());
    }

    @Test
    void theCollectionsAreImmutable() {
        SeasonProtocol.Open pass = open(PASS);

        assertThrows(UnsupportedOperationException.class, () -> pass.levels().clear());
        assertThrows(UnsupportedOperationException.class, () -> pass.missions().daily().clear());
        assertThrows(UnsupportedOperationException.class, () -> pass.plus().perks().clear());
    }

    @Test
    void aMalformedFieldRejectsTheWholePass() {
        assertEquals("season_open me into must be a whole number from 0 to 19",
            error(PASS.replace("\"into\":19", "\"into\":20")));
        assertEquals("season_open me step must be a whole number from 1 to 1000000000000",
            error(PASS.replace("\"step\":19", "\"step\":0")));
        assertEquals("season_open me level must be a whole number from 0 to 50",
            error(PASS.replace("\"level\":2,\"maxLevel\"", "\"level\":51,\"maxLevel\"")));
        assertEquals("season_open level 1 is out of order",
            error(PASS.replace("{\"level\":2,\"at\":11", "{\"level\":1,\"at\":11")));
        assertEquals("season_open level 1 level must be a whole number from 1 to 50",
            error(PASS.replace("{\"level\":2,\"at\":11", "{\"level\":51,\"at\":11")));
        assertEquals("season_open terms point is required",
            error(PASS.replace("\"terms\":{\"point\":\"XP\"}", "\"terms\":{}")));
        assertEquals("season_open season phase is not supported: over",
            error(PASS.replace("\"phase\":\"live\"", "\"phase\":\"over\"")));
        assertEquals("season_open level 0 free state is not supported: lost",
            error(PASS.replace("\"state\":\"claimed\"", "\"state\":\"lost\"")));
        assertEquals("season_open level 1 free model is required",
            error(PASS.replace("\"model\":\"fullmoon.skin.moonlight_pickaxe.1\",", "")));
        assertEquals("season_open level 0 plus color is invalid",
            error(PASS.replace("light_purple", "Purple!")));
        assertEquals("season_open level 0 free material is invalid",
            error(PASS.replace("minecraft:gold_nugget", "Gold Nugget")));
        assertEquals("season_open mission id is duplicated: daily_coal",
            error(PASS.replace("\"id\":\"weekly_voice\"", "\"id\":\"daily_coal\"")));
        assertEquals("season_open plus perks must contain strings",
            error(PASS.replace("[\"레벨마다 하나 더\"]", "[1]")));
        assertEquals("season_open notice tone is not supported: warn",
            error(PASS.replace("\"tone\":\"ok\"", "\"tone\":\"warn\"")));
        assertEquals("season_open id is invalid", error(PASS.replace("season-7", "season 7")));
    }

    @Test
    void absentOptionalFlagsAndListsTakeTheirQuietValue() {
        SeasonProtocol.Open pass = open(PASS
            .replace(",\"discord\":true", "")
            .replace("\"resets\":{\"daily\":\"3시간 12분\",\"weekly\":\"2일 4시간\"}", "\"resets\":null")
            .replace(",\n \"notice\":{\"tone\":\"ok\",\"text\":\"보상을 받았어요\"}", ""));

        assertFalse(pass.missions().weekly().get(0).discord());
        assertTrue(pass.missions().resets().isEmpty());
        assertTrue(pass.notice().isEmpty());
    }

    @Test
    void progressCarriesTheWidgetsLines() {
        SeasonProtocol.Progress progress = progress("""
            {"type":"season_progress","level":27,"into":95,"step":147,
             "tracked":{"id":"daily_coal","name":"석탄 캐기","material":"minecraft:coal","progress":13,"goal":32,"unit":"개","done":false},
             "recent":{"id":"daily_iron","name":"철광석 캐기","material":"minecraft:raw_iron","progress":6,"goal":16,"unit":"개","done":false},
             "flash":{"mission":"daily_coal","label":"석탄 캐기 완료"}}
            """);

        assertFalse(progress.hidden());
        assertEquals("daily_coal", progress.tracked().orElseThrow().id());
        assertEquals("daily_iron", progress.recent().orElseThrow().id());
        assertEquals("석탄 캐기 완료", progress.flash().orElseThrow().label());
        assertEquals(95f / 147f, progress.fraction());
    }

    @Test
    void levelZeroHidesTheWidgetWhateverElseItCarries() {
        SeasonProtocol.Progress progress = progress("""
            {"type":"season_progress","level":0,"tracked":null}
            """);

        assertTrue(progress.hidden());
        assertTrue(progress.tracked().isEmpty());
    }

    @Test
    void progressRejectsAnOverfullLevelOrAnUnnamedGoal() {
        assertEquals("season_progress into must be a whole number from 0 to 147", error("""
            {"type":"season_progress","level":27,"into":148,"step":147,"tracked":null}
            """));
        assertEquals("season_progress tracked name is required", error("""
            {"type":"season_progress","level":27,"into":1,"step":147,
             "tracked":{"id":"daily_coal","material":"minecraft:coal","progress":1,"goal":32,"done":false}}
            """));
        assertEquals("season_progress flash mission is invalid", error("""
            {"type":"season_progress","level":27,"into":1,"step":147,"flash":{"mission":"Coal!","label":"완료"}}
            """));
    }

    @Test
    void actionsNameTheSnapshotTheyWereMadeAgainst() {
        assertEquals("""
            {"type":"season_action","id":"season-7","revision":3,"action":"claim","level":12,"track":"plus"}""",
            text(SeasonProtocol.action("season-7", 3, new SeasonProtocol.Claim(12, SeasonProtocol.Track.PLUS))));
        assertEquals("claim_all", field(SeasonProtocol.action("s", 1, new SeasonProtocol.ClaimAll()), "action"));
        assertEquals("buy_plus", field(SeasonProtocol.action("s", 1, new SeasonProtocol.BuyPlus()), "action"));
        assertEquals("untrack", field(SeasonProtocol.action("s", 1, new SeasonProtocol.Untrack()), "action"));
        assertEquals("daily_coal",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.TrackMission("daily_coal")), "mission"));
        assertEquals("missions",
            field(SeasonProtocol.action("s", 1, new SeasonProtocol.SwitchTab(SeasonProtocol.Tab.MISSIONS)), "tab"));
        assertEquals("""
            {"type":"season_close","id":"season-7","revision":4}""", text(SeasonProtocol.close("season-7", 4)));
        assertEquals("""
            {"type":"season_hud","visible":true}""", text(SeasonProtocol.hud(true)));
    }

    @Test
    void actionsRefuseWhatNoServerCouldHaveSent() {
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("season 7", 1, new SeasonProtocol.ClaimAll()));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", -1, new SeasonProtocol.ClaimAll()));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", 1, new SeasonProtocol.Claim(0, SeasonProtocol.Track.FREE)));
        assertThrows(IllegalArgumentException.class,
            () -> SeasonProtocol.action("s", 1, new SeasonProtocol.TrackMission("Coal!")));
        assertThrows(IllegalArgumentException.class, () -> SeasonProtocol.close("", 1));
    }

    @Test
    void helloNamesTheSurfacesAndPacksOnlyWhenThereAreAny() {
        JsonObject bare = JsonParser.parseString(text(BridgeProtocol.hello("3.3.0"))).getAsJsonObject();
        JsonObject full = JsonParser.parseString(text(BridgeProtocol.hello("3.3.0", List.of("season_v1", "season_hud"),
            List.of(new BridgeProtocol.Pack("fullmoon-gui", "8da2a629de6766dd20b396be552d4079a87ffe22"))))).getAsJsonObject();

        assertFalse(bare.has("features"));
        assertFalse(bare.has("packs"));
        assertEquals("[\"season_v1\",\"season_hud\"]", full.get("features").toString());
        assertEquals("[{\"id\":\"fullmoon-gui\",\"sha1\":\"8da2a629de6766dd20b396be552d4079a87ffe22\"}]",
            full.get("packs").toString());
        assertInstanceOf(BridgeProtocol.Hello.class,
            BridgeProtocol.decode(BridgeProtocol.hello("3.3.0", List.of("season_v1"), List.of())).message().orElseThrow());
    }

    @Test
    void helloRefusesAMalformedFeatureOrPack() {
        assertThrows(IllegalArgumentException.class,
            () -> BridgeProtocol.hello("3.3.0", List.of("Season V1"), List.of()));
        assertEquals("pack sha1 must be 40 lowercase hex digits", assertThrows(IllegalArgumentException.class,
            () -> new BridgeProtocol.Pack("fullmoon-gui", "8DA2A629")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> new BridgeProtocol.Pack("", "0".repeat(40)));
    }

    private static SeasonProtocol.Open open(String text) {
        return assertInstanceOf(SeasonProtocol.Open.class,
            BridgeProtocol.decode(text.getBytes(StandardCharsets.UTF_8)).message().orElseThrow());
    }

    private static SeasonProtocol.Progress progress(String text) {
        return assertInstanceOf(SeasonProtocol.Progress.class,
            BridgeProtocol.decode(text.getBytes(StandardCharsets.UTF_8)).message().orElseThrow());
    }

    private static String error(String text) {
        return BridgeProtocol.decode(text.getBytes(StandardCharsets.UTF_8)).error().orElseThrow();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String field(byte[] bytes, String key) {
        return JsonParser.parseString(text(bytes)).getAsJsonObject().get(key).getAsString();
    }
}
