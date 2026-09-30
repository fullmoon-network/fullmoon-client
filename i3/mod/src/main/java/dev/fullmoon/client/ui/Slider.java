package dev.fullmoon.client.ui;

import java.util.function.IntConsumer;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A value on a span: a label, the rail it travels, and a tabular readout of where it stopped.
 *
 * <p>The value is an integer on a step grain rather than a float. A float has to be formatted
 * before it can be shown and rounds differently every time it is, which is how a volume control
 * ends up reading 0.7300000001; a grain the control knows about can be stepped by an arrow key and
 * printed without a decision. Both ends of the range are always on the grain, whether or not the
 * step divides the span.
 *
 * <p>Loading keeps the knob where the player left it and takes the number away. The position is
 * local and still true; the number is the part nothing has confirmed. {@link Toggle} does the
 * opposite for the same reason — there, the position is exactly what is in doubt.
 */
public final class Slider extends Widget {
    public static final int HEIGHT = Tokens.Space.SECTION;

    /** The knob's diameter, and so the shortest a rail-only slider can be drawn. */
    public static final int KNOB = Tokens.Space.LOOSE;

    private static final int RAIL_H = Tokens.Space.SNUG;

    private final IntConsumer onChange;
    private final String unit;
    private final boolean readout;
    private final int min;
    private final int max;
    private final int step;
    private int value;

    /** The bare rail: no label, no readout. What a narrow row and the state matrix ask for. */
    public Slider(int min, int max, int step, int value, IntConsumer onChange) {
        this("", "", min, max, step, value, onChange, false);
    }

    public Slider(String label, String unit, int min, int max, int step, int value,
            IntConsumer onChange) {
        this(label, unit, min, max, step, value, onChange, true);
    }

    private Slider(String label, String unit, int min, int max, int step, int value,
            IntConsumer onChange, boolean readout) {
        super(Voice.QUIET, label);
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChange = onChange;
        this.readout = readout;
        this.value = quantised(value);
    }

    public int value() {
        return value;
    }

    /** Sets the value without telling anyone: this is layout's doing, not the player's. */
    public void value(int raw) {
        value = quantised(raw);
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        Box rail = rail();
        float knob = knobAt(travelStart(rail), travelEnd(rail));

        painter.fill(rail.x(), rail.y(), rail.w(), rail.h(), Tokens.Radius.ROUND,
            Tokens.Color.SURFACE_SUNKEN);
        if (knob > rail.x()) {
            painter.fill(rail.x(), rail.y(), knob - rail.x(), rail.h(), Tokens.Radius.ROUND,
                travelled(state));
        }
        painter.border(rail.x(), rail.y(), rail.w(), rail.h(), Tokens.Radius.ROUND,
            Tokens.Stroke.HAIR, chrome.line());
        painter.dot(knob, rail.y() + RAIL_H / 2.0f, KNOB / 2.0f, chrome.ink());

        if (!label().isEmpty()) {
            Typeset.draw(painter, Tokens.Type.BODY, label(), b.x(),
                Typeset.centred(Tokens.Type.BODY, b.y(), b.h()), chrome.ink());
        }
        if (readout && state == State.LOADING) {
            Dots.draw(painter, b.right() - cell() / 2.0f, b.midY(), chrome.ink());
        } else if (readout) {
            Typeset.tabularRight(painter, Tokens.Type.STRONG, say(value), b.right(),
                Typeset.centred(Tokens.Type.STRONG, b.y(), b.h()), chrome.ink());
        }
        ring(painter, state, Tokens.Radius.MD);
    }

    /**
     * The travelled part of the rail, which is what makes a slider's value read as the accent
     * rather than as more chrome. Error is the exception and not an oversight: {@link Voice#LOUD}
     * inks its error fill in the panel colour, so a bar painted from it would leave the control
     * with no value on it at all, and a value that fails its rule is the thing worth pointing at.
     */
    private static int travelled(State state) {
        return state == State.ERROR
            ? Voice.QUIET.chrome(state).line()
            : Voice.LOUD.chrome(state).fill();
    }

    @Override
    protected boolean press(double mx, double my) {
        // A slider's name is not a handle. Clicking it focuses the control and leaves the value
        // where it was; sending it to one end because the player clicked a word is not a nudge.
        if (mx < rail().x()) {
            return false;
        }
        follow(mx);
        return true;
    }

    @Override
    protected void drag(double mx, double my) {
        follow(mx);
    }

    @Override
    protected boolean key(Chord chord) {
        if (chord.is(InputConstants.KEY_LEFT) || chord.is(InputConstants.KEY_DOWN)) {
            return commit(value - step);
        }
        if (chord.is(InputConstants.KEY_RIGHT) || chord.is(InputConstants.KEY_UP)) {
            return commit(value + step);
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            return commit(min);
        }
        if (chord.is(InputConstants.KEY_END)) {
            return commit(max);
        }
        return false;
    }

    /**
     * The value a pointer at {@code mx} lands on, for a knob travel that runs {@code left..right}.
     *
     * <p>The travel arrives as two numbers instead of being read off the bounds because its ends
     * are measured text and this arithmetic is not. The mapping from a position to a step is the
     * part worth pinning down, and a test for it cannot start a game to get a font.
     */
    int valueAt(double mx, int left, int right) {
        if (right <= left) {
            return value;
        }
        double t = (mx - left) / (right - left);
        return quantised(min + (int) Math.round(t * (max - min)));
    }

    /** Where the knob's centre sits on a travel that runs {@code left..right}. */
    float knobAt(int left, int right) {
        return max == min
            ? left
            : left + (right - left) * (value - min) / (float) (max - min);
    }

    private void follow(double mx) {
        Box rail = rail();
        commit(valueAt(mx, travelStart(rail), travelEnd(rail)));
    }

    /** True either way: a slider at the end of its rail has still answered for the key. */
    private boolean commit(int raw) {
        int next = quantised(raw);
        if (next != value) {
            value = next;
            onChange.accept(value);
        }
        return true;
    }

    /** Clamped into the range, then snapped to the grain — except at the ends, which are on it. */
    private int quantised(int raw) {
        int clamped = Math.clamp(raw, min, max);
        return Math.min(max, min + Math.round((clamped - min) / (float) step) * step);
    }

    /** The groove, between the label and the readout. */
    private Box rail() {
        Box b = bounds();
        int left = b.x() + (label().isEmpty()
            ? 0
            : Typeset.width(Tokens.Type.BODY, label()) + Tokens.Space.LOOSE);
        int right = b.right() - (readout ? cell() + Tokens.Space.LOOSE : 0);
        int top = b.midY() - RAIL_H / 2;
        return Box.between(left, top, right, top + RAIL_H);
    }

    private static int travelStart(Box rail) {
        return rail.x() + KNOB / 2;
    }

    private static int travelEnd(Box rail) {
        return rail.right() - KNOB / 2;
    }

    /** Room for the widest reading this slider has, so the rail does not shorten as digits land. */
    private int cell() {
        String low = say(min);
        String high = say(max);
        return Math.max(Dots.width(),
            Typeset.tabularWidth(Tokens.Type.STRONG,
                low.length() >= high.length() ? low : high));
    }

    private String say(int v) {
        return v + unit;
    }
}
