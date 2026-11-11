package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.SeasonProtocol;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class SeasonSampleTest {
    @AfterEach
    void clear() {
        System.clearProperty(SeasonSample.PROPERTY);
    }

    @Test
    void nothingOpensUnlessTheRigAsks() {
        assertTrue(SeasonSample.requested().isEmpty());
        assertTrue(SeasonSample.feed(0).isEmpty());
    }

    @Test
    void theGoldenSnapshotPassesTheSameDecoderAsTheServer() {
        System.setProperty(SeasonSample.PROPERTY, "season-open");
        SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();
        JsonObject golden = SeasonSample.open();

        assertEquals(5, pass.levels().size());
        assertEquals(SeasonProtocol.Phase.LIVE, pass.season().phase());
        assertEquals(golden.getAsJsonObject("plus").get("price").getAsString(), pass.plus().price());
        assertEquals(golden.getAsJsonObject("me").get("balance").getAsString(),
            pass.me().balance().orElseThrow());
        assertEquals(SeasonProtocol.Status.PENDING,
            pass.levels().get(1).reward(SeasonProtocol.Track.FREE).orElseThrow().status());
        SeasonProtocol.Reward cosmetic =
            pass.levels().get(4).reward(SeasonProtocol.Track.PLUS).orElseThrow();
        assertEquals(SeasonProtocol.Kind.COSMETIC, cosmetic.kind());
        assertEquals("crescent_sword", cosmetic.cosmetic());
        assertEquals("fullmoon.skin.crescent_sword.1", cosmetic.model());
    }

    @Test
    void theSeasonFixturesAreAsciiAndRoundTripTheirServerValues() throws IOException {
        // The fixtures are server data; they hold the producer's exact strings, stored with the
        // non-English characters as ASCII escapes so the resource files stay byte-plain. The
        // parsed data is compared against itself as decoded, never re-typed by hand.
        for (String name : List.of("season_open.json", "season_progress.json")) {
            InputStream in = SeasonSample.class.getResourceAsStream("/assets/fullmoon/season/" + name);
            byte[] raw = in == null ? new byte[0] : in.readAllBytes();
            assertTrue(raw.length > 0, name);
            for (byte b : raw) {
                assertTrue(b >= 0, name + " must be plain ASCII");
            }
        }
        JsonObject golden = SeasonSample.open();
        assertEquals(golden.getAsJsonObject("season").get("name").getAsString(),
            open(golden).season().name(), "the fixture's own name reaches the screen unchanged");
        assertEquals(golden.getAsJsonObject("season").get("endsIn").getAsString(),
            open(golden).season().endsIn());
        assertEquals(golden.getAsJsonObject("season").get("endsAt").getAsString(),
            open(golden).season().endsAt());
        assertEquals(golden.getAsJsonObject("me").get("balance").getAsString(),
            open(golden).me().balance().orElseThrow());
        assertEquals(golden.getAsJsonObject("notice").get("text").getAsString(),
            open(golden).notice().orElseThrow().text());
        assertEquals(golden.getAsJsonObject("missions").getAsJsonObject("resets").get("daily").getAsString(),
            open(golden).missions().resets().orElseThrow().daily());
    }

    private static SeasonProtocol.Open open(JsonObject golden) {
        System.setProperty(SeasonSample.PROPERTY, "season-open");
        return SeasonSample.requested().orElseThrow();
    }

    @Test
    void theEndedFixtureCarriesTheProducerClosedState() {
        System.setProperty(SeasonSample.PROPERTY, "season-ended");
        SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();

        assertEquals(SeasonProtocol.Phase.ENDED, pass.season().phase());
        assertEquals("Season ended", SeasonCopy.context(pass.season()));
        // Every unclaimed cell is locked and the claim count is closed, as the producer sends it.
        assertEquals(0, pass.me().claimable());
        for (SeasonProtocol.Level level : pass.levels()) {
            level.free().filter(reward -> reward.status() != SeasonProtocol.Status.CLAIMED)
                .ifPresent(reward -> assertEquals(SeasonProtocol.Status.LOCKED, reward.status()));
            level.plus().filter(reward -> reward.status() != SeasonProtocol.Status.CLAIMED)
                .ifPresent(reward -> assertEquals(SeasonProtocol.Status.LOCKED, reward.status()));
        }
        assertFalse(pass.plus().canBuy());
        assertFalse(pass.plus().confirming());
        // The claimed cell stays claimed, and no amount was invented.
        assertEquals(SeasonProtocol.Status.CLAIMED,
            pass.levels().get(0).free().orElseThrow().status());
        assertEquals(SeasonSample.open().getAsJsonObject("me").get("xp").getAsString(),
            Long.toString(pass.me().xp()), "the fixture never invents an amount");
    }

    @Test
    void theMissionAndPlusFixturesChangeOnlyThePageAndAnErrorFlag() {
        System.setProperty(SeasonSample.PROPERTY, "season-missions");
        SeasonProtocol.Open missions = SeasonSample.requested().orElseThrow();
        assertEquals(SeasonProtocol.Tab.MISSIONS, missions.tab());
        assertTrue(missions.notice().isEmpty());

        System.setProperty(SeasonSample.PROPERTY, "season-plus");
        SeasonProtocol.Open plus = SeasonSample.requested().orElseThrow();
        assertEquals(SeasonProtocol.Tab.PLUS, plus.tab());
        assertFalse(plus.plus().canBuy());
        assertTrue(plus.plus().reason().isPresent());
        assertEquals(SeasonProtocol.Tone.ERROR, plus.notice().orElseThrow().tone());
        assertEquals(SeasonSample.open().getAsJsonObject("plus").get("price").getAsString(),
            plus.plus().price(), "the fixture never invents an amount");
    }

    @Test
    void theWidgetFeedLoopsAFinishedStageAndThenARecentMission() {
        System.setProperty(SeasonSample.PROPERTY, "season-hud");
        assertTrue(SeasonSample.requested().isEmpty());

        BridgeState.SeasonFeed flashing = SeasonSample.feed(60_500).orElseThrow();
        assertTrue(flashing.liveFlash(60_500).isPresent());
        assertTrue(flashing.liveRecent(60_500).isEmpty());
        BridgeState.SeasonFeed moving = SeasonSample.feed(63_000).orElseThrow();
        assertTrue(moving.liveFlash(63_000).isEmpty());
        assertEquals("daily_coal", moving.tracked().orElseThrow().id());
        assertTrue(moving.liveRecent(63_000).isPresent());
        assertTrue(SeasonSample.feed(65_500).orElseThrow().liveRecent(65_500).isEmpty());
    }

    @Test
    void theErrorFixtureRunsTheLiveSendPathAndTheOthersDoNot() {
        System.setProperty(SeasonSample.PROPERTY, "season-error");
        assertTrue(SeasonSample.liveErrorRequested());
        assertTrue(SeasonSample.requested().isPresent(), "it still opens the pass");

        System.setProperty(SeasonSample.PROPERTY, "season-open");
        assertFalse(SeasonSample.liveErrorRequested());
        System.clearProperty(SeasonSample.PROPERTY);
    }
}
