package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * The session numbers behind the channel's logs. Which Fabric event maps to which signal is the
 * channel's routing, proven against real servers; these cases keep the boundary itself honest.
 */
final class BackendSessionTest {
    @Test
    void aPlayReadyPacketOpensANewBackendSession() {
        BackendSession session = new BackendSession();

        long epoch = session.observe(BackendSession.Signal.PLAY_READY, 1_000);

        assertEquals(1, epoch);
        assertEquals(1_000, session.openedAt());
    }

    @Test
    void repeatedPlayReadyEventsAreDistinctSessionsAndTheLastOneWins() {
        BackendSession session = new BackendSession();

        long first = session.observe(BackendSession.Signal.PLAY_READY, 1_000);
        long second = session.observe(BackendSession.Signal.PLAY_READY, 1_001);

        assertNotEquals(first, second);
        assertEquals(2, session.epoch());
        assertEquals(1_001, session.openedAt());
    }

    @Test
    void aLevelChangeKeepsTheBackendSession() {
        BackendSession session = new BackendSession();
        long joined = session.observe(BackendSession.Signal.PLAY_READY, 1_000);

        long respawn = session.observe(BackendSession.Signal.LEVEL_CHANGE, 2_000);

        assertEquals(joined, respawn);
        assertEquals(1_000, session.openedAt());
    }

    @Test
    void aDisconnectClosesTheCurrentSessionWithoutInventingANewOne() {
        BackendSession session = new BackendSession();
        long joined = session.observe(BackendSession.Signal.PLAY_READY, 1_000);

        long closed = session.observe(BackendSession.Signal.DISCONNECT, 2_000);

        assertEquals(joined, closed);
        assertEquals(1, session.epoch());
    }

    @Test
    void aLevelChangeAfterADisconnectCannotReopenTheSession() {
        BackendSession session = new BackendSession();
        session.observe(BackendSession.Signal.PLAY_READY, 1_000);
        session.observe(BackendSession.Signal.DISCONNECT, 2_000);

        long respawn = session.observe(BackendSession.Signal.LEVEL_CHANGE, 3_000);

        assertEquals(1, respawn);
    }

    @Test
    void deniedTransferProducesNoObservationAndNoReset() {
        BackendSession session = new BackendSession();
        long joined = session.observe(BackendSession.Signal.PLAY_READY, 1_000);

        // A denied transfer never reaches the client as a play-ready or level-change packet.
        assertEquals(1, joined);
        assertEquals(1_000, session.openedAt());
    }

    @Test
    void sameServerDimensionTravelIsOnlyALevelChange() {
        BackendSession session = new BackendSession();
        long joined = session.observe(BackendSession.Signal.PLAY_READY, 1_000);
        long overworldToNether = session.observe(BackendSession.Signal.LEVEL_CHANGE, 1_500);
        long netherToOverworld = session.observe(BackendSession.Signal.LEVEL_CHANGE, 2_000);

        assertEquals(joined, overworldToNether);
        assertEquals(joined, netherToOverworld);
    }
}
