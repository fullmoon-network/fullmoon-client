package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Motion;
import dev.fullmoon.client.render.Painter;

/**
 * A control on a surface: a box, the signals it knows about itself, and what it does when the
 * pointer or the keyboard reaches it.
 *
 * <p>A widget holds no focus bit of its own. {@link Surface} passes the ring in when it asks for
 * a state, because a widget that remembered whether it was focused would drift out of step with
 * the one object that actually moves the keyboard.
 *
 * <p>{@link #draw} is handed the state rather than working it out, so the same drawing code that
 * runs in a live surface can be asked for all eight states side by side.
 *
 * <p>A change of state is remembered with the moment it happened, so a control can crossfade its
 * ground from the state it was in to the one it is in. The gallery, which hands states straight
 * to {@link #draw}, never records a change and so draws every cell settled.
 */
public abstract class Widget implements Focus.Target {
    /** Clear of the control's own edge by a hairline, so the two do not read as one thick line. */
    private static final int RING_GAP = Tokens.Space.TIGHT + Tokens.Stroke.HAIR;

    private final Voice voice;
    private final String label;
    private Box bounds = Box.EMPTY;
    private String hint = "";
    private State.Signals own = State.Signals.REST;
    private boolean ringing;
    private boolean holding;

    private State seen = State.REST;
    private State before = State.REST;
    private long changedAt;
    private long nudgedAt = Long.MIN_VALUE;

    protected Widget(Voice voice, String label) {
        this.voice = voice;
        this.label = label;
    }

    public final Voice voice() {
        return voice;
    }

    /**
     * What this control is called. Where the words go is the widget's business — a switch sets
     * them beside its track, a button inside itself — but the name lives up here because a
     * surface that has to say which control holds the keyboard cannot ask a subclass.
     */
    public final String label() {
        return label;
    }

    public final Box bounds() {
        return bounds;
    }

    /**
     * What {@link Tooltip} says when the pointer rests on this control or the keyboard arrives at
     * it. Empty by default, and deliberately so: a tooltip that repeats the label is a second copy
     * of something the player has already read, in a box they now have to look past.
     */
    public final Widget hint(String text) {
        hint = text;
        return this;
    }

    public final String hint() {
        return hint;
    }

    /** Layout hands a widget its box. A widget never picks its own. */
    public final void place(Box box) {
        bounds = box;
    }

    /**
     * The box the pointer is tested against, which is the bounds for every control that does not
     * open. A list that hangs below the control which opened it has to be clickable where it
     * draws; the ring stays on {@link #bounds()}, because the ring belongs to the closed control.
     */
    protected Box reach() {
        return bounds;
    }

    public final void enabled(boolean value) {
        own = own.enabled(value);
    }

    /** A request is in flight: the control still holds the keyboard but answers nothing. */
    public final void busy(boolean value) {
        own = own.busy(value);
    }

    public final void invalid(boolean value) {
        own = own.invalid(value);
    }

    /** The state this widget is in, given who is holding the keyboard. */
    public final State state(Focus focus) {
        return state(focus.holds(this), focus.visible());
    }

    /**
     * The state of a widget owned by another widget, which has no {@link Focus} to ask. A panel is
     * one keyboard stop for a whole list, and the row that stop is on is the row that has to look
     * like it: a surface reaches its controls through the focus above, and a control reaches its
     * own parts through here.
     */
    final State state(boolean focused, boolean keyboard) {
        return State.of(own.focused(focused, keyboard));
    }

    @Override
    public boolean takesFocus() {
        // Busy does not give up the ring: taking the keyboard away from the player because a
        // request is in flight loses their place on the surface for no reason they can see.
        return own.enabled();
    }

    public abstract void draw(Painter painter, State state);

    /**
     * A second drawing pass, run over every widget's first. Only a control that opens needs it: a
     * list that hangs below its own row has to paint over the row beneath, and draw order is the
     * layout's to decide, so the surface cannot settle this by reordering anything.
     */
    protected void drawOverlay(Painter painter, State state) {}

    /**
     * Whether this control is over the rest of the surface right now. Drawn last and hit first are
     * one fact and not two: a list that paints over the row beneath it has to be clickable there,
     * or the pointer lands on whatever it is covering. A control that answers true here is a
     * control that draws in {@link #drawOverlay}.
     */
    protected boolean overlaying() {
        return false;
    }

    /** The ring, outside the control's bounds so it never covers the control's own edge. */
    protected final void ring(Painter painter, State state, float radius) {
        ring(painter, state, radius, voice.ring());
    }

    protected final void ring(Painter painter, State state, float radius, int color) {
        // A state is one value, so it can only name the loudest thing true about a control: focus
        // is lost the moment the pointer arrives or a request goes out. The ring cannot go with it
        // — losing the player's place because they moved the mouse is how a keyboard user ends up
        // hunting for it. FOCUS_VISIBLE is how a gallery asks for a ring with no surface behind
        // it; `ringing` is how a live one keeps it up underneath a louder state.
        if (state != State.FOCUS_VISIBLE && !ringing) {
            return;
        }
        Box around = bounds.inset(-RING_GAP);
        painter.border(around.x(), around.y(), around.w(), around.h(),
            radius + RING_GAP, Tokens.Stroke.FOCUS, color);
    }

    /** The surface saw this widget in {@code state}; a change starts the crossfade clock. */
    final void observe(State state) {
        if (state != seen) {
            before = seen;
            seen = state;
            changedAt = System.nanoTime();
        }
    }

    /** The state this widget was in before the one it is in now. */
    protected final State before() {
        return before;
    }

    /**
     * How far the crossfade from {@link #before()} has come, 0 to 1, eased on the fast duration.
     * A widget that was never observed by a surface — a gallery cell — is always settled.
     */
    protected final float settle() {
        if (changedAt == 0) {
            return 1.0f;
        }
        long elapsed = (System.nanoTime() - changedAt) / 1_000_000L;
        return Motion.eased(elapsed, Tokens.Duration.FAST, Tokens.Easing.OUT);
    }

    /** A press landed on this control while it could not answer: it shakes its head. */
    protected final void nudge() {
        nudgedAt = System.nanoTime();
    }

    /** The horizontal offset a nudge puts on the control's contents this frame. */
    protected final float nudgeOffset() {
        if (nudgedAt == Long.MIN_VALUE) {
            return 0.0f;
        }
        return Motion.nudge((System.nanoTime() - nudgedAt) / 1_000_000L);
    }

    /** Mouse down inside the bounds. Returning true captures the pointer until it comes up. */
    protected boolean press(double mx, double my) {
        return true;
    }

    /**
     * Where the pointer is, sent every frame to the one widget under it. A control made of parts —
     * a list of rows, a rail of tabs — cannot read its own hover off a single boolean.
     */
    protected void hovering(double mx, double my) {}

    /** The pointer moved while this widget held the capture. */
    protected void drag(double mx, double my) {}

    /** The capture ended. {@code inside} is false when the pointer was dragged off first. */
    protected void release(double mx, double my, boolean inside) {
        if (inside) {
            act();
        }
    }

    /** A key press while this widget holds the keyboard. True if consumed. */
    protected boolean key(Chord chord) {
        return false;
    }

    /** A typed codepoint — not a char, because a client that has to set Hangul cannot use one. */
    protected boolean type(int codepoint) {
        return false;
    }

    /** A scroll while the pointer is over this widget. */
    protected boolean scroll(double amount) {
        return false;
    }

    /** What Enter, Space and a click that comes up inside the bounds all do. */
    protected void act() {}

    /** The keyboard has gone elsewhere. A control that holds an edit commits it here. */
    protected void blurred() {}

    /** Whether this control is wearing the ring. Surface-owned, like hover and press. */
    protected final boolean ringing() {
        return ringing;
    }

    /**
     * Whether the keyboard sits on this control, whatever louder state is being reported over it.
     *
     * <p>Two facts about focus arrive from the surface rather than one, for the reason {@link State}
     * gives for the ring: a focused control that is also hovered reports the hover, and an enum
     * cannot say two things. The ring answers to how focus arrived, and a caret answers to whether
     * it is here at all — a field clicked into has one and no ring.
     */
    protected final boolean holding() {
        return holding;
    }

    /** Surface-owned: hover and press are facts about the pointer, not about the widget. */
    final void hovered(boolean value) {
        own = own.hovered(value);
    }

    /** Whether the pointer is on this control. A composite has to pass its own answer to its parts. */
    protected final boolean hovered() {
        return own.hovered();
    }

    final void ringing(boolean value) {
        ringing = value;
    }

    final void holding(boolean value) {
        holding = value;
    }

    final void pressed(boolean value) {
        own = own.pressed(value);
    }
}
