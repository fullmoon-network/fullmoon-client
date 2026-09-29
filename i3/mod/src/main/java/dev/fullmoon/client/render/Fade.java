package dev.fullmoon.client.render;

import dev.fullmoon.client.design.Tokens;

/**
 * A pane's way in and way out: {@link Tokens.Duration#OPEN} of rising fade on arrival, and
 * {@link Tokens.Duration#CLOSE} of fall once {@link #close} is called, after which the screen that
 * owns it is {@link #gone()} and can hand back to its parent. Reduced motion skips both.
 */
public final class Fade {
    private final long openedAt;
    private long closingAt;

    private Fade(long openedAt) {
        this.openedAt = openedAt;
    }

    /** A pane that has just been opened: it rises in. */
    public static Fade opening() {
        return new Fade(Motion.reduced() ? 0 : System.nanoTime());
    }

    /** A pane that replaces one already up: it is simply there. */
    public static Fade settled() {
        return new Fade(0);
    }

    /** Starts the way out. Under reduced motion the pane is gone at once. */
    public void close() {
        if (closingAt == 0) {
            closingAt = Motion.reduced() ? 1 : System.nanoTime();
        }
    }

    public boolean closing() {
        return closingAt != 0;
    }

    /** Whether the way out has run its course. */
    public boolean gone() {
        return closingAt != 0 && (closingAt == 1 || (System.nanoTime() - closingAt) / 1_000_000L >= Tokens.Duration.CLOSE);
    }

    /** 0 to 1: how far the pane has come in, or how much of it is left on the way out. */
    public float appearance() {
        if (closingAt != 0) {
            if (closingAt == 1) {
                return 0.0f;
            }
            long elapsed = (System.nanoTime() - closingAt) / 1_000_000L;
            return 1.0f - Motion.eased(elapsed, Tokens.Duration.CLOSE, Tokens.Easing.IN);
        }
        if (openedAt == 0) {
            return 1.0f;
        }
        long elapsed = (System.nanoTime() - openedAt) / 1_000_000L;
        return Motion.eased(elapsed, Tokens.Duration.OPEN, Tokens.Easing.OUT);
    }
}
