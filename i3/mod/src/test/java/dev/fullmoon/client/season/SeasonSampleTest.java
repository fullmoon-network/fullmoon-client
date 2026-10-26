package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.SeasonProtocol;

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
    void everyStandingPassesTheSameDecoderAsTheServer() {
        for (String standing : new String[] {"season-plus", "season-free", "season-claim"}) {
            System.setProperty(SeasonSample.PROPERTY, standing);
            SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();
            assertEquals(50, pass.levels().size(), standing);
            assertEquals(claimable(pass), pass.me().claimable(), standing + " counts what it shows");
        }
    }

    @Test
    void thePlusHolderIsMidSeasonWithAHeldReward() {
        System.setProperty(SeasonSample.PROPERTY, "season-plus");
        SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();

        assertEquals(27, pass.me().level());
        assertEquals(SeasonProtocol.Status.PENDING,
            pass.levels().get(24).reward(SeasonProtocol.Track.FREE).orElseThrow().status());
        SeasonProtocol.Reward sword = pass.levels().get(9).reward(SeasonProtocol.Track.PLUS).orElseThrow();
        assertEquals(SeasonProtocol.Kind.COSMETIC, sword.kind());
        assertEquals("fullmoon.skin.crescent_sword.1", sword.model());
        assertEquals("light_purple", pass.levels().get(0).reward(SeasonProtocol.Track.PLUS).orElseThrow().color());
    }

    @Test
    void theFreePlayerSeesPlusCellsLockedBehindThePurchase() {
        System.setProperty(SeasonSample.PROPERTY, "season-free");
        SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();

        assertEquals(SeasonProtocol.Status.PLUS_LOCKED,
            pass.levels().get(11).reward(SeasonProtocol.Track.PLUS).orElseThrow().status());
        assertEquals(SeasonProtocol.Status.LOCKED,
            pass.levels().get(12).reward(SeasonProtocol.Track.PLUS).orElseThrow().status());
        assertTrue(pass.plus().canBuy());
    }

    @Test
    void theGraceAfterTheSeasonHasAFullLastLevelAndNoMissions() {
        System.setProperty(SeasonSample.PROPERTY, "season-claim");
        SeasonProtocol.Open pass = SeasonSample.requested().orElseThrow();

        assertEquals(SeasonProtocol.Phase.CLAIM, pass.season().phase());
        assertEquals(pass.me().step(), pass.me().into());
        assertTrue(pass.missions().daily().isEmpty());
        assertEquals(SeasonProtocol.Tone.ERROR, pass.notice().orElseThrow().tone());
    }

    @Test
    void theLevelCurveIsTheVanillaOneScaled() {
        assertEquals(0, SeasonSample.at(1));
        assertEquals(11, SeasonSample.at(2));
        assertEquals(Math.round(352 * 1.6), SeasonSample.at(17));
        assertEquals(Math.round(1_395 * 1.6), SeasonSample.at(31));
    }

    @Test
    void theWidgetFeedLoopsAFinishedStageAndThenARecentMission() {
        System.setProperty(SeasonSample.PROPERTY, "season-plus");

        BridgeState.SeasonFeed flashing = SeasonSample.feed(60_500).orElseThrow();
        assertTrue(flashing.liveFlash(60_500).isPresent());
        assertTrue(flashing.liveRecent(60_500).isEmpty());
        BridgeState.SeasonFeed moving = SeasonSample.feed(63_000).orElseThrow();
        assertTrue(moving.liveFlash(63_000).isEmpty());
        assertEquals("daily_iron", moving.liveRecent(63_000).orElseThrow().id());
        assertTrue(SeasonSample.feed(65_500).orElseThrow().liveRecent(65_500).isEmpty());
    }

    private static long claimable(SeasonProtocol.Open pass) {
        return pass.levels().stream()
            .flatMap(level -> java.util.stream.Stream.of(level.free(), level.plus()))
            .flatMap(java.util.Optional::stream)
            .filter(reward -> reward.status() == SeasonProtocol.Status.CLAIMABLE)
            .count();
    }
}
