package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;

/**
 * How loudly a control speaks.
 *
 * <p>Two voices, and no third. {@link #QUIET} is every control on a surface; {@link #LOUD} is
 * the one action a surface is about, and a screen with two loud controls has not decided what it
 * is for. Both answer all eight states, so a state cannot exist in one voice and be missing in
 * the other.
 *
 * <p>Grounds are tints of the glass: a quiet control is six percent of white on the pane, hover
 * lifts it to ten, a press sinks it ten percent into shadow. Ink is always opaque.
 */
public enum Voice {
    /** Ground-level control: a surface can hold as many of these as it needs. */
    QUIET {
        @Override
        public Chrome chrome(State state) {
            return switch (state) {
                case REST, FOCUS, FOCUS_VISIBLE ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL, Tokens.Color.INK_PRIMARY, Tokens.Color.LINE_STRONG);
                case HOVER ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL_HOVER, Tokens.Color.INK_PRIMARY, Tokens.Color.LINE_STRONG);
                case ACTIVE ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL_PRESSED, Tokens.Color.INK_PRIMARY, Tokens.Color.LINE_STRONG);
                case DISABLED ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL_DISABLED, Tokens.Color.INK_DISABLED, Tokens.Color.LINE_HAIRLINE);
                case LOADING ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL, Tokens.Color.INK_TERTIARY, Tokens.Color.LINE_HAIRLINE);
                case ERROR ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL, Tokens.Color.INK_PRIMARY, Tokens.Color.STATUS_DANGER);
            };
        }

        @Override
        public int ring() {
            return Tokens.Color.ACCENT;
        }
    },

    /** The one action a surface is about. */
    LOUD {
        /**
         * Held, it darkens to {@code accent.pressed} rather than lifting: a control that gets
         * brighter under the finger reads as an animation, not a press. Hover has no fill left to
         * move, so its edge carries it instead.
         */
        @Override
        public Chrome chrome(State state) {
            return switch (state) {
                case REST, FOCUS, FOCUS_VISIBLE ->
                    new Chrome(Tokens.Color.ACCENT, Tokens.Color.INK_ON_ACCENT, Tokens.Color.ACCENT);
                case HOVER ->
                    new Chrome(Tokens.Color.ACCENT, Tokens.Color.INK_ON_ACCENT, Tokens.Color.INK_ON_ACCENT);
                case ACTIVE ->
                    new Chrome(Tokens.Color.ACCENT_PRESSED, Tokens.Color.INK_ON_ACCENT, Tokens.Color.ACCENT_PRESSED);
                case DISABLED ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL_DISABLED, Tokens.Color.INK_DISABLED, Tokens.Color.LINE_HAIRLINE);
                case LOADING ->
                    new Chrome(Tokens.Color.ACCENT_WASH, Tokens.Color.INK_SECONDARY, Tokens.Color.ACCENT_WASH);
                case ERROR ->
                    new Chrome(Tokens.Color.SURFACE_CONTROL, Tokens.Color.INK_PRIMARY, Tokens.Color.STATUS_DANGER);
            };
        }

        /** An accent ring around an accent fill would read as one fat blob, so this one is ink. */
        @Override
        public int ring() {
            return Tokens.Color.INK_PRIMARY;
        }
    };

    public abstract Chrome chrome(State state);

    /** The ring a keyboard focus draws outside the control's bounds. */
    public abstract int ring();
}
