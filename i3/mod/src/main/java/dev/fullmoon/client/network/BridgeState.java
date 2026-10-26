package dev.fullmoon.client.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BridgeState(
        Mode mode,
        int serverProtocol,
        long connectedAt,
        Optional<Metrics> metrics,
        Optional<ActiveNotice> notice,
        List<BridgeProtocol.Waypoint> waypoints,
        Optional<PendingWarp> pendingWarp,
        Optional<WarpOutcome> warpOutcome,
        Optional<CasinoReveal> casino,
        Optional<SeasonFeed> season) {
    public static final long HANDSHAKE_TIMEOUT_MILLIS = 5_000;
    public static final long METRICS_STALE_MILLIS = 5_000;
    public static final long WARP_TIMEOUT_MILLIS = 5_000;
    public static final long WARP_OUTCOME_MILLIS = 4_000;
    public static final long CASINO_CARD_MILLIS = 4_500;
    public static final long SEASON_RECENT_MILLIS = 3_000;
    public static final long SEASON_FLASH_MILLIS = 2_000;

    public BridgeState {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(notice, "notice");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
        Objects.requireNonNull(pendingWarp, "pendingWarp");
        Objects.requireNonNull(warpOutcome, "warpOutcome");
        Objects.requireNonNull(casino, "casino");
        Objects.requireNonNull(season, "season");
    }

    public enum Mode {
        DISCONNECTED,
        WAITING,
        ACTIVE,
        FALLBACK,
        INCOMPATIBLE
    }

    public record Metrics(
            long revision,
            double ticksPerSecond,
            double tickMilliseconds,
            long receivedAt) {}

    public record ActiveNotice(
            String id,
            String title,
            String body,
            BridgeProtocol.Severity severity,
            long receivedAt,
            long expiresAt) {
        public ActiveNotice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(severity, "severity");
        }
    }

    public record PendingWarp(String id, long requestedAt) {
        public PendingWarp {
            Objects.requireNonNull(id, "id");
        }
    }

    public record WarpOutcome(String id, boolean ok, String reason, long receivedAt) {
        public WarpOutcome {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record CasinoReveal(CasinoProtocol.Result result, long receivedAt) {
        public CasinoReveal {
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * What the season widget follows: the level and the tracked mission as last sent, and the two
     * short-lived lines with the moment each arrived. A message that carries no recent mission or
     * no finished stage leaves the one on screen to run out its own time.
     */
    public record SeasonFeed(
            int level,
            long into,
            long step,
            Optional<SeasonProtocol.Goal> tracked,
            Optional<SeasonProtocol.Goal> recent,
            long recentAt,
            Optional<SeasonProtocol.Flash> flash,
            long flashAt) {
        public SeasonFeed {
            Objects.requireNonNull(tracked, "tracked");
            Objects.requireNonNull(recent, "recent");
            Objects.requireNonNull(flash, "flash");
        }

        public float fraction() {
            return step <= 0 ? 0.0f : Math.clamp((float) into / step, 0.0f, 1.0f);
        }

        /** The recent line while it lasts, unless it has since become the tracked mission. */
        public Optional<SeasonProtocol.Goal> liveRecent(long now) {
            return recent.filter(goal -> now - recentAt < SEASON_RECENT_MILLIS
                && tracked.map(t -> !t.id().equals(goal.id())).orElse(true));
        }

        public Optional<SeasonProtocol.Flash> liveFlash(long now) {
            return flash.filter(value -> now - flashAt < SEASON_FLASH_MILLIS);
        }
    }

    public static BridgeState disconnected() {
        return empty(Mode.DISCONNECTED, 0, 0);
    }

    public static BridgeState connected(long now) {
        return empty(Mode.WAITING, 0, now);
    }

    public static BridgeState apply(BridgeState state, BridgeProtocol.Message message, long now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");

        if (message instanceof BridgeProtocol.Welcome welcome) {
            return welcome(state, welcome);
        }
        if (state.mode != Mode.ACTIVE || message.proto() != state.serverProtocol) {
            return state;
        }
        if (message instanceof BridgeProtocol.HudSync sync) {
            return metrics(state, sync, now);
        }
        if (message instanceof BridgeProtocol.Notice incoming) {
            return notice(state, incoming, now);
        }
        if (message instanceof BridgeProtocol.WaypointSync sync) {
            return waypoints(state, sync.waypoints());
        }
        if (message instanceof BridgeProtocol.TpResult result) {
            return warpResult(state, result, now);
        }
        if (message instanceof CasinoProtocol.Result result) {
            return casino(state, new CasinoReveal(result, now));
        }
        if (message instanceof SeasonProtocol.Progress progress) {
            return season(state, progress, now);
        }
        return state;
    }

    public static BridgeState requestWarp(BridgeState state, String waypointId, long now) {
        Objects.requireNonNull(state, "state");
        if (state.mode != Mode.ACTIVE || state.pendingWarp.isPresent()
                || state.waypoints.stream().noneMatch(waypoint -> waypoint.id().equals(waypointId))) {
            return state;
        }
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, state.notice,
            state.waypoints, Optional.of(new PendingWarp(waypointId, now)), Optional.empty(),
            state.casino, state.season);
    }

    public static BridgeState failWarp(
            BridgeState state, String waypointId, String reason, long now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(waypointId, "waypointId");
        Objects.requireNonNull(reason, "reason");
        Optional<PendingWarp> pending = state.pendingWarp;
        if (pending.isEmpty() || !pending.orElseThrow().id().equals(waypointId)) {
            return state;
        }
        return withWarpOutcome(state, new WarpOutcome(waypointId, false, reason, now));
    }

    public static BridgeState tick(BridgeState state, long now) {
        Objects.requireNonNull(state, "state");
        if (state.mode == Mode.WAITING && now - state.connectedAt >= HANDSHAKE_TIMEOUT_MILLIS) {
            return empty(Mode.FALLBACK, 0, state.connectedAt);
        }
        Optional<PendingWarp> pending = state.pendingWarp;
        if (pending.isPresent()
                && now - pending.orElseThrow().requestedAt() >= WARP_TIMEOUT_MILLIS) {
            return withWarpOutcome(state,
                new WarpOutcome(pending.orElseThrow().id(), false, "timeout", now));
        }
        return state;
    }

    public static Optional<Metrics> liveMetrics(BridgeState state, long now) {
        Objects.requireNonNull(state, "state");
        return state.metrics.filter(value -> now - value.receivedAt < METRICS_STALE_MILLIS);
    }

    public static Optional<ActiveNotice> liveNotice(BridgeState state, long now) {
        Objects.requireNonNull(state, "state");
        return state.notice.filter(value -> now < value.expiresAt);
    }

    public static Optional<WarpOutcome> liveWarpOutcome(BridgeState state, long now) {
        Objects.requireNonNull(state, "state");
        return state.warpOutcome.filter(
            value -> now - value.receivedAt < WARP_OUTCOME_MILLIS);
    }

    public static Optional<CasinoReveal> liveCasino(BridgeState state, long now) {
        Objects.requireNonNull(state, "state");
        return state.casino.filter(value -> now - value.receivedAt < CASINO_CARD_MILLIS);
    }

    private static BridgeState welcome(BridgeState state, BridgeProtocol.Welcome welcome) {
        if (state.mode != Mode.WAITING) {
            return state;
        }
        if (welcome.proto() < BridgeProtocol.VERSION) {
            return empty(Mode.FALLBACK, welcome.proto(), state.connectedAt);
        }
        if (welcome.proto() > BridgeProtocol.VERSION) {
            return empty(Mode.INCOMPATIBLE, welcome.proto(), state.connectedAt);
        }
        return new BridgeState(
            Mode.ACTIVE, welcome.proto(), state.connectedAt, Optional.empty(), Optional.empty(),
            welcome.waypoints(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static BridgeState metrics(
            BridgeState state, BridgeProtocol.HudSync sync, long now) {
        Optional<Metrics> current = state.metrics;
        if (current.isPresent() && current.orElseThrow().revision() >= sync.revision()) {
            return state;
        }
        Metrics next = new Metrics(
            sync.revision(), sync.ticksPerSecond(), sync.tickMilliseconds(), now);
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, Optional.of(next), state.notice,
            state.waypoints, state.pendingWarp, state.warpOutcome, state.casino, state.season);
    }

    private static BridgeState notice(
            BridgeState state, BridgeProtocol.Notice incoming, long now) {
        ActiveNotice next = new ActiveNotice(
            incoming.id(), incoming.title(), incoming.body(), incoming.severity(),
            now, now + incoming.durationMillis());
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, Optional.of(next),
            state.waypoints, state.pendingWarp, state.warpOutcome, state.casino, state.season);
    }

    private static BridgeState waypoints(
            BridgeState state, List<BridgeProtocol.Waypoint> waypoints) {
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, state.notice,
            waypoints, state.pendingWarp, state.warpOutcome, state.casino, state.season);
    }

    private static BridgeState warpResult(
            BridgeState state, BridgeProtocol.TpResult result, long now) {
        Optional<PendingWarp> pending = state.pendingWarp;
        if (pending.isEmpty() || !pending.orElseThrow().id().equals(result.id())) {
            return state;
        }
        return withWarpOutcome(state,
            new WarpOutcome(result.id(), result.ok(), result.reason(), now));
    }

    private static BridgeState casino(BridgeState state, CasinoReveal reveal) {
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, state.notice,
            state.waypoints, state.pendingWarp, state.warpOutcome, Optional.of(reveal), state.season);
    }

    private static BridgeState season(BridgeState state, SeasonProtocol.Progress progress, long now) {
        Optional<SeasonFeed> next = Optional.empty();
        if (!progress.hidden()) {
            Optional<SeasonFeed> was = state.season;
            Optional<SeasonProtocol.Goal> recent = progress.recent().or(() -> was.flatMap(SeasonFeed::recent));
            long recentAt = progress.recent().isPresent() ? now : was.map(SeasonFeed::recentAt).orElse(0L);
            Optional<SeasonProtocol.Flash> flash = progress.flash().or(() -> was.flatMap(SeasonFeed::flash));
            long flashAt = progress.flash().isPresent() ? now : was.map(SeasonFeed::flashAt).orElse(0L);
            next = Optional.of(new SeasonFeed(progress.level(), progress.into(), progress.step(),
                progress.tracked(), recent, recentAt, flash, flashAt));
        }
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, state.notice,
            state.waypoints, state.pendingWarp, state.warpOutcome, state.casino, next);
    }

    private static BridgeState withWarpOutcome(BridgeState state, WarpOutcome outcome) {
        return new BridgeState(
            state.mode, state.serverProtocol, state.connectedAt, state.metrics, state.notice,
            state.waypoints, Optional.empty(), Optional.of(outcome), state.casino, state.season);
    }

    private static BridgeState empty(Mode mode, int serverProtocol, long connectedAt) {
        return new BridgeState(
            mode, serverProtocol, connectedAt, Optional.empty(), Optional.empty(), List.of(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
