package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

final class BridgeStateTest {
    @Test
    void exactWelcomeActivatesTheSession() {
        BridgeState state = BridgeState.apply(
            BridgeState.connected(1_000), new BridgeProtocol.Welcome(1), 1_100);

        assertEquals(BridgeState.Mode.ACTIVE, state.mode());
        assertEquals(1, state.serverProtocol());
    }

    @Test
    void welcomeAndSyncReplaceTheServerOwnedWaypointSnapshot() {
        BridgeProtocol.Waypoint gate = waypoint("gate", 0, 64, 0);
        BridgeProtocol.Waypoint keep = waypoint("keep", 40, 80, -60);
        BridgeState welcomed = BridgeState.apply(
            BridgeState.connected(1_000), new BridgeProtocol.Welcome(1, List.of(gate)), 1_100);
        BridgeState synced = BridgeState.apply(
            welcomed, new BridgeProtocol.WaypointSync(1, List.of(keep)), 1_200);

        assertEquals(List.of(gate), welcomed.waypoints());
        assertEquals(List.of(keep), synced.waypoints());
        assertTrue(welcomed.waypoints() != synced.waypoints());
    }

    @Test
    void oldAndNewServersFallBackWithoutSyntheticData() {
        BridgeState oldServer = BridgeState.apply(
            BridgeState.connected(1_000), new BridgeProtocol.Welcome(0), 1_100);
        BridgeState newServer = BridgeState.apply(
            BridgeState.connected(1_000), new BridgeProtocol.Welcome(2), 1_100);

        assertEquals(BridgeState.Mode.FALLBACK, oldServer.mode());
        assertEquals(BridgeState.Mode.INCOMPATIBLE, newServer.mode());
        assertTrue(oldServer.metrics().isEmpty());
        assertTrue(newServer.notice().isEmpty());
    }

    @Test
    void aSilentServerFallsBackAfterTheHandshakeBudget() {
        BridgeState waiting = BridgeState.connected(1_000);

        assertSame(waiting, BridgeState.tick(waiting, 5_999));
        assertEquals(BridgeState.Mode.FALLBACK, BridgeState.tick(waiting, 6_000).mode());
    }

    @Test
    void operationalPayloadsAreIgnoredUntilHandshakeCompletes() {
        BridgeState waiting = BridgeState.connected(1_000);
        BridgeProtocol.HudSync sync = new BridgeProtocol.HudSync(1, 1, 19.9, 8.2);
        BridgeProtocol.Notice notice = notice("first", 4_000);

        assertSame(waiting, BridgeState.apply(waiting, sync, 1_100));
        assertSame(waiting, BridgeState.apply(waiting, notice, 1_100));
        assertSame(waiting, BridgeState.apply(waiting,
            new BridgeProtocol.WaypointSync(1, List.of(waypoint("gate", 0, 64, 0))), 1_100));
    }

    @Test
    void aKnownWarpRequestWaitsForTheMatchingServerDecision() {
        BridgeState active = activeWith(waypoint("gate", 0, 64, 0));
        BridgeState requested = BridgeState.requestWarp(active, "gate", 2_000);
        BridgeState unrelated = BridgeState.apply(requested,
            new BridgeProtocol.TpResult(1, "keep", false, "cooldown"), 2_100);
        BridgeState accepted = BridgeState.apply(unrelated,
            new BridgeProtocol.TpResult(1, "gate", true, ""), 2_200);

        assertEquals("gate", requested.pendingWarp().orElseThrow().id());
        assertTrue(requested.warpOutcome().isEmpty());
        assertSame(requested, unrelated);
        assertTrue(accepted.pendingWarp().isEmpty());
        assertTrue(accepted.warpOutcome().orElseThrow().ok());
        assertEquals("gate", accepted.warpOutcome().orElseThrow().id());
    }

    @Test
    void warpRequestRejectsUnknownInactiveAndConcurrentIds() {
        BridgeState active = activeWith(waypoint("gate", 0, 64, 0));
        BridgeState requested = BridgeState.requestWarp(active, "gate", 2_000);
        BridgeState disconnected = BridgeState.disconnected();

        assertSame(active, BridgeState.requestWarp(active, "missing", 2_000));
        assertSame(disconnected, BridgeState.requestWarp(disconnected, "gate", 2_000));
        assertSame(requested, BridgeState.requestWarp(requested, "gate", 2_100));
    }

    @Test
    void pendingWarpTimesOutAndItsOutcomeExpiresFromTheSurface() {
        BridgeState requested = BridgeState.requestWarp(
            activeWith(waypoint("gate", 0, 64, 0)), "gate", 2_000);

        assertSame(requested, BridgeState.tick(requested, 6_999));
        BridgeState timedOut = BridgeState.tick(requested, 7_000);

        assertTrue(timedOut.pendingWarp().isEmpty());
        assertEquals("timeout", timedOut.warpOutcome().orElseThrow().reason());
        assertTrue(BridgeState.liveWarpOutcome(timedOut, 10_999).isPresent());
        assertTrue(BridgeState.liveWarpOutcome(timedOut, 11_000).isEmpty());
    }

    @Test
    void aLocalSendFailureUsesTheSameVisibleOutcomePath() {
        BridgeState requested = BridgeState.requestWarp(
            activeWith(waypoint("gate", 0, 64, 0)), "gate", 2_000);

        BridgeState failed = BridgeState.failWarp(requested, "gate", "client_send", 2_100);

        assertTrue(failed.pendingWarp().isEmpty());
        assertFalse(failed.warpOutcome().orElseThrow().ok());
        assertEquals("client_send", failed.warpOutcome().orElseThrow().reason());
    }

    @Test
    void metricsAdvanceMonotonicallyAndGoStale() {
        BridgeState active = active();
        BridgeState first = BridgeState.apply(
            active, new BridgeProtocol.HudSync(1, 4, 19.7, 14.1), 2_000);
        BridgeState older = BridgeState.apply(
            first, new BridgeProtocol.HudSync(1, 3, 20.0, 1.0), 2_100);
        BridgeState newer = BridgeState.apply(
            older, new BridgeProtocol.HudSync(1, 5, 19.9, 10.2), 2_200);

        assertSame(first, older);
        assertEquals(5, newer.metrics().orElseThrow().revision());
        assertTrue(BridgeState.liveMetrics(newer, 7_199).isPresent());
        assertTrue(BridgeState.liveMetrics(newer, 7_200).isEmpty());
    }

    @Test
    void mismatchedOperationalProtocolCannotChangeLiveState() {
        BridgeState active = active();

        assertSame(active, BridgeState.apply(
            active, new BridgeProtocol.HudSync(0, 1, 19.9, 8.2), 2_000));
        assertSame(active, BridgeState.apply(active, new BridgeProtocol.Unknown(2, "future"), 2_000));
        assertSame(active, BridgeState.apply(active, new BridgeProtocol.Hello(1, "fullmoon", "3.0.0"), 2_000));
        assertSame(active, BridgeState.tick(active, 20_000));
    }

    @Test
    void noticesReplaceByArrivalAndExpireExactly() {
        BridgeState active = active();
        BridgeState first = BridgeState.apply(active, notice("first", 4_000), 2_000);
        BridgeState second = BridgeState.apply(first, notice("second", 5_000), 2_500);

        assertEquals("second", BridgeState.liveNotice(second, 7_499).orElseThrow().id());
        assertTrue(BridgeState.liveNotice(second, 7_500).isEmpty());
    }

    @Test
    void disconnectReturnsAnEmptyImmutableState() {
        BridgeState state = BridgeState.apply(
            active(), new BridgeProtocol.HudSync(1, 1, 19.9, 8.2), 2_000);

        BridgeState disconnected = BridgeState.disconnected();

        assertEquals(BridgeState.Mode.DISCONNECTED, disconnected.mode());
        assertTrue(disconnected.metrics().isEmpty());
        assertTrue(disconnected.notice().isEmpty());
        assertTrue(disconnected.waypoints().isEmpty());
        assertTrue(disconnected.pendingWarp().isEmpty());
        assertTrue(disconnected.warpOutcome().isEmpty());
        assertFalse(state.equals(disconnected));
    }

    @Test
    void aNewBackendSessionDropsEveryBackendOwnedValue() {
        BridgeState busy = BridgeState.requestWarp(
            activeWith(waypoint("gate", 0, 64, 0)), "gate", 2_000);
        busy = BridgeState.apply(busy, new BridgeProtocol.HudSync(1, 4, 19.7, 14.1), 2_100);
        busy = BridgeState.apply(busy, notice("maintenance", 4_000), 2_200);
        busy = BridgeState.apply(busy, coin(1, true), 2_300);
        busy = BridgeState.apply(busy, progress(1, 3, 5, 17), 2_400);

        assertEquals(BridgeState.Mode.ACTIVE, busy.mode());
        assertTrue(busy.metrics().isPresent());
        assertTrue(busy.notice().isPresent());
        assertTrue(busy.pendingWarp().isPresent());
        assertTrue(busy.casino().isPresent());
        assertTrue(busy.season().isPresent());

        BridgeState fresh = BridgeState.connected(5_000);

        assertEquals(BridgeState.Mode.WAITING, fresh.mode());
        assertEquals(5_000, fresh.connectedAt());
        assertTrue(fresh.metrics().isEmpty());
        assertTrue(fresh.notice().isEmpty());
        assertTrue(fresh.waypoints().isEmpty());
        assertTrue(fresh.pendingWarp().isEmpty());
        assertTrue(fresh.warpOutcome().isEmpty());
        assertTrue(fresh.casino().isEmpty());
        assertTrue(fresh.season().isEmpty());
    }

    @Test
    void seasonProgressFeedsTheHudOnlyInAnActiveMatchingSession() {
        BridgeState waiting = BridgeState.connected(1_000);
        SeasonProtocol.Progress value = progress(1, 3, 5, 17);

        assertSame(waiting, BridgeState.apply(waiting, value, 1_100));
        BridgeState active = active();
        assertTrue(BridgeState.apply(active, progress(2, 3, 5, 17), 1_200).season().isEmpty());

        BridgeState fed = BridgeState.apply(active, value, 1_300);
        assertEquals(3, fed.season().orElseThrow().level());
        assertEquals("daily_coal", fed.season().orElseThrow().tracked().orElseThrow().id());
        assertEquals(5f / 17f, fed.season().orElseThrow().fraction());
    }

    @Test
    void hiddenSeasonProgressClearsTheFeedInsteadOfDrawingAZeroLevel() {
        BridgeState fed = BridgeState.apply(active(), progress(1, 3, 5, 17), 2_000);

        BridgeState hidden = BridgeState.apply(fed, progress(1, 0, 0, 0), 2_100);

        assertTrue(hidden.season().isEmpty());
    }

    @Test
    void aNewerWelcomeDisablesBridgeFeaturesAndDropsTheOldSnapshot() {
        BridgeState busy = BridgeState.apply(
            activeWith(waypoint("gate", 0, 64, 0)), progress(1, 3, 5, 17), 2_000);

        BridgeState incompatible = BridgeState.apply(
            BridgeState.connected(2_000), new BridgeProtocol.Welcome(2), 2_100);

        assertEquals(BridgeState.Mode.INCOMPATIBLE, incompatible.mode());
        assertTrue(incompatible.waypoints().isEmpty());
        assertTrue(incompatible.season().isEmpty());
        assertTrue(incompatible.notice().isEmpty());
        assertSame(busy, BridgeState.apply(busy, new BridgeProtocol.Welcome(2), 2_200),
            "there is no renegotiation inside a live session");
    }

    @Test
    void aSilentOrDeniedTransferLeavesAnActiveSnapshotIntact() {
        BridgeState active = activeWith(waypoint("gate", 0, 64, 0));

        assertSame(active, BridgeState.tick(active, 60_000));

        BridgeState afterDiagnostic = BridgeState.apply(
            active, new BridgeProtocol.Unknown(1, "transfer_denied"), 60_100);
        assertSame(active, afterDiagnostic);
        assertEquals(List.of(waypoint("gate", 0, 64, 0)), afterDiagnostic.waypoints());
    }

    @Test
    void decodedPayloadsDriveTheReducerEndToEnd() {
        BridgeProtocol.Message welcome = BridgeProtocol.decode(
            "{\"type\":\"welcome\",\"proto\":1}".getBytes(StandardCharsets.UTF_8))
            .message().orElseThrow();
        BridgeProtocol.Message sync = BridgeProtocol.decode(
            ("{\"type\":\"hud_sync\",\"proto\":1,\"revision\":9,"
                + "\"tps\":19.95,\"tick_ms\":7.4}").getBytes(StandardCharsets.UTF_8))
            .message().orElseThrow();

        BridgeState state = BridgeState.apply(BridgeState.connected(1_000), welcome, 1_100);
        BridgeState synced = BridgeState.apply(state, sync, 1_200);

        assertEquals(BridgeState.Mode.ACTIVE, synced.mode());
        assertEquals(19.95, synced.metrics().orElseThrow().ticksPerSecond());
    }

    @Test
    void casinoResultsNeedAnActiveMatchingSession() {
        CasinoProtocol.Result coin = coin(1, true);

        BridgeState waiting = BridgeState.connected(1_000);

        assertSame(waiting, BridgeState.apply(waiting, coin, 1_050));
        assertTrue(BridgeState.apply(active(), coin(2, true), 2_000).casino().isEmpty());
        assertEquals(coin, BridgeState.apply(active(), coin, 2_000).casino().orElseThrow().result());
    }

    @Test
    void aNewerCasinoResultReplacesTheCardAndExpiresExactly() {
        BridgeState first = BridgeState.apply(active(), coin(1, true), 2_000);
        BridgeState second = BridgeState.apply(first, coin(1, false), 3_000);
        long expiry = 3_000 + BridgeState.CASINO_CARD_MILLIS;

        assertFalse(BridgeState.liveCasino(second, expiry - 1).orElseThrow().result().won());
        assertTrue(BridgeState.liveCasino(second, expiry).isEmpty());
    }

    @Test
    void aReconnectStartsWithoutACard() {
        BridgeState shown = BridgeState.apply(active(), coin(1, true), 2_000);

        assertTrue(shown.casino().isPresent());
        assertTrue(BridgeState.disconnected().casino().isEmpty());
        assertTrue(active().casino().isEmpty());
    }

    private static BridgeState active() {
        return BridgeState.apply(
            BridgeState.connected(1_000), new BridgeProtocol.Welcome(1), 1_100);
    }

    private static BridgeState activeWith(BridgeProtocol.Waypoint waypoint) {
        return BridgeState.apply(BridgeState.connected(1_000),
            new BridgeProtocol.Welcome(1, List.of(waypoint)), 1_100);
    }

    private static BridgeProtocol.Waypoint waypoint(String id, int x, int y, int z) {
        return new BridgeProtocol.Waypoint(
            id, id, "moon", x, y, z, "world", "palace", "");
    }

    private static SeasonProtocol.Progress progress(int proto, int level, long into, long step) {
        String text = "{\"type\":\"season_progress\",\"proto\":" + proto + ",\"level\":" + level
            + ",\"into\":" + into + ",\"step\":" + step
            + ",\"tracked\":{\"id\":\"daily_coal\",\"name\":\"Mine coal\","
            + "\"material\":\"minecraft:coal\",\"progress\":13,\"goal\":32,"
            + "\"unit\":\" blocks\",\"done\":false}}";
        return assertInstanceOf(SeasonProtocol.Progress.class,
            BridgeProtocol.decode(text.getBytes(StandardCharsets.UTF_8)).message().orElseThrow());
    }

    private static BridgeProtocol.Notice notice(String id, int durationMillis) {
        return new BridgeProtocol.Notice(
            1, id, "Maintenance", "Restart in five minutes",
            BridgeProtocol.Severity.WARNING, durationMillis);
    }

    private static CasinoProtocol.Result coin(int proto, boolean won) {
        return new CasinoProtocol.Result(
            proto, CasinoProtocol.Game.COINFLIP, won, won ? 2 : 0, new CasinoProtocol.Coin());
    }
}
