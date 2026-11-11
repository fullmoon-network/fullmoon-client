package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

/**
 * The pass's one-request-at-a-time state, against a fixed clock: the dead ends a send can reach —
 * busy, unreachable bridge, no answer — and the words that bring the player out of them.
 */
final class SeasonPendingTest {
    private long now = 1_000;

    @Test
    void aRequestWaitsForItsAnswerAndBusyStaysUntilItComes() {
        SeasonPending pending = pending();

        assertTrue(pending.send(action(), 4, () -> true));
        assertTrue(pending.busy());

        // A snapshot at the revision sent is a repeat, not the answer; the wait holds.
        assertFalse(pending.settle(4));
        assertTrue(pending.busy());
        assertTrue(pending.problem().isEmpty());

        assertTrue(pending.settle(5));
        assertFalse(pending.busy());
        assertTrue(pending.problem().isEmpty());
    }

    @Test
    void aSecondRequestWhileOneIsOutIsRefusedWithTheWayOut() {
        SeasonPending pending = pending();
        SeasonProtocol.Action first = action();
        pending.send(first, 4, () -> true);

        assertFalse(pending.send(new SeasonProtocol.ClaimAll(), 4, () -> true));

        assertEquals(SeasonPending.BUSY, pending.problem().orElseThrow());
        assertEquals(first, pending.pending().orElseThrow(), "the refused request does not displace the one out");
    }

    @Test
    void anUnreachableBridgeOrAFailedSendSaysSoAndTheWordsStay() {
        SeasonPending pending = pending();

        assertFalse(pending.send(action(), 4, () -> false));

        assertEquals(SeasonPending.UNREACHABLE, pending.problem().orElseThrow());
        assertFalse(pending.busy());

        // A stale snapshot that fails to settle does not lift the words either.
        assertFalse(pending.settle(3));
        assertTrue(pending.problem().isPresent());
    }

    @Test
    void aFreshAuthoritativeSnapshotClearsTheWordsEvenWithNoRequestOut() {
        SeasonPending pending = pending();
        pending.send(action(), 4, () -> false);
        assertTrue(pending.problem().isPresent());

        // The screen clears the words with abandon when a newer snapshot is accepted.
        pending.abandon();

        assertTrue(pending.problem().isEmpty());
    }

    @Test
    void noAnswerInTimeEndsTheWaitWithTheRecoverySentence() {
        SeasonPending pending = pending();
        pending.send(action(), 4, () -> true);

        now += SeasonPending.TIMEOUT_MILLIS - 1;
        assertFalse(pending.timedOut());
        now += 1;
        assertTrue(pending.timedOut());

        pending.timeout();

        assertFalse(pending.busy());
        assertEquals(SeasonPending.TIMEOUT, pending.problem().orElseThrow());
    }

    @Test
    void aServerInitiatedSnapshotNewerThanTheOneSentEndsTheWait() {
        SeasonPending pending = pending();
        pending.send(action(), 4, () -> true);

        assertTrue(pending.settle(6));

        assertFalse(pending.busy());
        assertTrue(pending.problem().isEmpty());
    }

    @Test
    void thePendingActionIsKnownSoTheScreenCanReadItsAnswer() {
        SeasonPending pending = pending();
        SeasonProtocol.SwitchTab tab = new SeasonProtocol.SwitchTab(SeasonProtocol.Tab.PLUS);

        pending.send(tab, 4, () -> true);

        assertEquals(tab, pending.pending().orElseThrow());
        pending.settle(5);
        assertTrue(pending.pending().isEmpty());
    }

    private SeasonPending pending() {
        return new SeasonPending(() -> now);
    }

    private SeasonProtocol.Action action() {
        return new SeasonProtocol.ClaimAll();
    }
}
