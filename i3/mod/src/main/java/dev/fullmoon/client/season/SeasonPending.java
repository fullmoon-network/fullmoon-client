package dev.fullmoon.client.season;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import dev.fullmoon.client.network.SeasonProtocol;

/**
 * The one request the pass may have out at a time, and what is said while it waits.
 *
 * <p>Every action — a claim, a track, a tab change — is sent against the snapshot on screen, and
 * the server's answer is the next snapshot. Between send and answer the pass shows dots and takes
 * no second request. When the bridge is unreachable, the send fails, or no answer arrives in
 * time, the pass says so and how to recover; the words stay until a fresh authoritative snapshot
 * replaces them, because a silent dead end is the one thing worse than a failed action.
 */
final class SeasonPending {
    static final long TIMEOUT_MILLIS = 5_000;

    private final LongSupplier clock;
    private long requestedAt;
    private long requestedRevision;
    private SeasonProtocol.Action action;

    SeasonPending(LongSupplier clock) {
        this.clock = clock;
    }

    boolean busy() {
        return action != null;
    }

    /** The request the answer has to beat, or an empty pass with no request out. */
    Optional<SeasonProtocol.Action> pending() {
        return Optional.ofNullable(action);
    }

    /**
     * Sends one action against {@code revision}. False with a problem set when a request is
     * already out or the send itself failed; true with the wait started otherwise.
     */
    boolean send(SeasonProtocol.Action sent, long revision, BooleanSupplier send) {
        if (busy()) {
            problem = BUSY;
            return false;
        }
        if (!send.getAsBoolean()) {
            problem = UNREACHABLE;
            return false;
        }
        action = sent;
        requestedRevision = revision;
        requestedAt = clock.getAsLong();
        return true;
    }

    /** Whether the wait has run past the server's answer budget; the caller turns it into a timeout. */
    boolean timedOut() {
        return busy() && clock.getAsLong() - requestedAt >= TIMEOUT_MILLIS;
    }

    /** The wait ends with no answer: the controls clear and the pass says how to recover. */
    void timeout() {
        action = null;
        problem = TIMEOUT;
    }

    /**
     * A snapshot arrived. True when it is a fresh answer to the request — at a revision past the
     * one sent — which clears the wait and anything it said; false for a stale or repeated
     * snapshot, which leaves the wait as it was.
     */
    boolean settle(long revision) {
        if (busy() && revision > requestedRevision) {
            action = null;
            problem = null;
            return true;
        }
        return false;
    }

    /** Ends the wait without an answer, as a backend change does; the caller owns the wording. */
    void abandon() {
        action = null;
        problem = null;
    }

    /** What the pass currently says, if anything. */
    Optional<String> problem() {
        return Optional.ofNullable(problem);
    }

    static final String BUSY =
        "One request at a time; wait for the answer or close and reopen the pass.";
    static final String UNREACHABLE =
        "The bridge is not reachable right now; close and reopen the pass to try again.";
    static final String TIMEOUT =
        "The server did not answer the last request; close and reopen the pass to try again.";

    private String problem;
}
