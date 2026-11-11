package dev.fullmoon.client.network;

import java.util.Objects;

/**
 * The play-session boundary the bridge resets on.
 *
 * <p>Minecraft reports a server switch as a fresh login packet. Fabric invokes
 * {@code ClientPlayConnectionEvents.JOIN} for every login packet, including a Velocity backend
 * transfer, and the server's channel registration follows that event. A respawn, a death, or an
 * ordinary dimension change is a {@code ClientboundRespawnPacket} handled by the same
 * {@code ClientPacketListener}; it does not produce a login packet. Those are therefore level
 * changes, not backend sessions, and must keep the current bridge snapshot.
 *
 * <p>A refused transfer never reaches the client as either event: the proxy rejects the
 * connection before a new backend sends a login packet, so the lobby session stays intact.
 */
public final class BackendSession {
    /** The observations the client actually receives, not assumptions about a transfer. */
    public enum Signal {
        /** A new login packet arrived; the backend behind it may be different. */
        PLAY_READY,
        /** The level was replaced within one backend: death, respawn, or a dimension. */
        LEVEL_CHANGE,
        /** The play connection ended. */
        DISCONNECT
    }

    private long epoch;
    private long openedAt;

    /**
     * Records one lifecycle observation and returns the session it belongs to. A
     * {@link Signal#PLAY_READY} opens the next session even if two arrive in the same millisecond,
     * because the second login can only mean the client moved again; the other two observations
     * return the session unchanged, and the channel keeps or closes what it holds.
     */
    public synchronized long observe(Signal signal, long now) {
        Objects.requireNonNull(signal, "signal");
        if (signal == Signal.PLAY_READY) {
            epoch = epoch == Long.MAX_VALUE ? 1 : epoch + 1;
            openedAt = now;
        }
        return epoch;
    }

    /** The number of the session a level change or a disconnect lands in. */
    public synchronized long epoch() {
        return epoch;
    }

    /** When the current session's login packet arrived. */
    public synchronized long openedAt() {
        return openedAt;
    }
}
