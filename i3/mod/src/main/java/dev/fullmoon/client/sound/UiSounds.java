package dev.fullmoon.client.sound;

import java.util.Random;
import java.util.function.Consumer;

import dev.fullmoon.client.design.Tokens;

/**
 * The client's own UI cues: a soft tick when the focus moves, a confirm, a way back, a menu
 * opening and closing, a refusal, a tab change. Original sounds, synthesised for Fullmoon by
 * {@code i3/design/make-ui-sounds.py}; the events are registered under {@code fullmoon:ui.*}.
 *
 * <p>Nothing here touches the game. {@link #play} hands a {@link Cue} to whatever sink the client
 * installed at start-up, so a surface test can run the whole key path headless and a rate limit
 * can be proved without a sound engine. The gate is the interesting part: a held arrow key
 * repeats faster than a tick can decay, so ticks closer together than
 * {@link Tokens.Sound#FOCUS_INTERVAL_MS} are dropped, and the ones that play take a small random
 * pitch so a run of them does not read as one machine.
 */
public final class UiSounds {
    public enum Cue {
        FOCUS("ui.focus"),
        CONFIRM("ui.confirm"),
        BACK("ui.back"),
        OPEN("ui.open"),
        CLOSE("ui.close"),
        ERROR("ui.error"),
        TAB("ui.tab");

        private final String path;

        Cue(String path) {
            this.path = path;
        }

        /** The sound event's path under the {@code fullmoon} namespace. */
        public String path() {
            return path;
        }

        /** Whether repeats of this cue are rate-limited. Only the tick is ever asked for fast enough to need it. */
        public boolean gated() {
            return this == FOCUS;
        }
    }

    /** A cue as it is about to be played: which one, and at what pitch. */
    public record Note(Cue cue, float pitch) {}

    /**
     * Decides whether a cue plays now, and at what pitch. Pure: time and randomness come in as
     * arguments, so the rule can be checked in a test to the millisecond.
     */
    public static final class Gate {
        private long lastFocusMs = Long.MIN_VALUE;

        /** The note to play for {@code cue} at {@code nowMs}, or null when the gate holds it back. */
        public Note admit(Cue cue, long nowMs, Random random) {
            if (cue.gated()) {
                if (nowMs - lastFocusMs < Tokens.Sound.FOCUS_INTERVAL_MS) {
                    return null;
                }
                lastFocusMs = nowMs;
                float spread = Tokens.Sound.FOCUS_PITCH_JITTER;
                return new Note(cue, 1.0f + (random.nextFloat() * 2.0f - 1.0f) * spread);
            }
            return new Note(cue, 1.0f);
        }
    }

    private static final Gate GATE = new Gate();
    private static final Random RANDOM = new Random();
    private static volatile Consumer<Note> sink = note -> {};
    private static volatile boolean enabled = true;

    private UiSounds() {}

    /** Installs the thing that actually makes a sound. The client does this once at start-up. */
    public static void sink(Consumer<Note> player) {
        sink = player;
    }

    /** The player's UI-sound setting, mirrored here so nothing under ui/ reads a setting. */
    public static void enabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void play(Cue cue) {
        if (!enabled) {
            return;
        }
        Note note = GATE.admit(cue, System.currentTimeMillis(), RANDOM);
        if (note != null) {
            sink.accept(note);
        }
    }
}
