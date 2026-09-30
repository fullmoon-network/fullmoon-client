package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * A switch. It commits the moment it is thrown — a switch that needs confirming afterwards is a
 * checkbox in a form and should be drawn as one.
 *
 * <p>The track speaks {@link Voice#LOUD} when it is on and {@link Voice#QUIET} when it is off,
 * which is what makes the two readable apart without a caption. The label is inked from
 * {@code QUIET} either way: it sits on the panel, not on the track, and on-accent ink on the
 * panel ground is invisible. The focus ring stays accent in both positions so keyboard focus
 * does not change identity when the switch is thrown.
 */
public final class Toggle extends Widget {
    public static final int HEIGHT = Tokens.Space.SECTION;
    public static final int TRACK_W = Tokens.Space.BAY;
    public static final int TRACK_H = Tokens.Space.GUTTER;

    /** {@code java.util.function} has no boolean consumer and boxing one is not worth it. */
    public interface Switched {
        void accept(boolean on);
    }

    private final Switched onChange;
    private boolean on;

    public Toggle(String label, boolean on, Switched onChange) {
        super(Voice.QUIET, label);
        this.on = on;
        this.onChange = onChange;
    }

    public boolean on() {
        return on;
    }

    /** Throws the switch from outside, for a page whose subject changed under it. */
    public void on(boolean value) {
        on = value;
    }

    /** Label, gutter and track. An unlabelled switch is just the track — the gallery wants that. */
    public int measure() {
        int text = label().isEmpty()
            ? 0
            : Typeset.width(Tokens.Type.BODY, label()) + Tokens.Space.LOOSE;
        return text + TRACK_W;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Voice track = on ? Voice.LOUD : Voice.QUIET;
        Chrome skin = track.chrome(state);

        Box slot = new Box(b.right() - TRACK_W, b.midY() - TRACK_H / 2, TRACK_W, TRACK_H);
        painter.fill(slot.x(), slot.y(), slot.w(), slot.h(), Tokens.Radius.ROUND, skin.fill());
        painter.border(slot.x(), slot.y(), slot.w(), slot.h(), Tokens.Radius.ROUND,
            Tokens.Stroke.HAIR, skin.line());

        float centre = slot.y() + TRACK_H / 2.0f;
        // A switch with a request out is between positions: the player has thrown it and nothing
        // has said which end it lands on yet. Parking the knob mid-track is the only thing this
        // control can say that a disabled one cannot, and a switch that reported LOADING by
        // dimming would be a disabled switch with extra steps.
        float knobX = state == State.LOADING
            ? slot.x() + slot.w() / 2.0f
            : on ? slot.right() - TRACK_H / 2.0f : slot.x() + TRACK_H / 2.0f;
        painter.dot(knobX, centre, TRACK_H / 2.0f - Tokens.Space.TIGHT, skin.ink());

        if (!label().isEmpty()) {
            Typeset.draw(painter, Tokens.Type.BODY, label(), b.x(),
                Typeset.centred(Tokens.Type.BODY, b.y(), b.h()), Voice.QUIET.chrome(state).ink());
        }
        // With no label the bounds are the track, so the ring has to be a pill too.
        ring(painter, state, label().isEmpty() ? Tokens.Radius.ROUND : Tokens.Radius.MD,
            Voice.QUIET.ring());
    }

    @Override
    protected void act() {
        on = !on;
        onChange.accept(on);
    }
}
