package dev.fullmoon.client.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.sound.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The event half of a screen: which control the pointer is over, which one holds the keyboard,
 * and what becomes of a key nobody asked for.
 *
 * <p>It does not draw. Draw order belongs to whatever laid the controls out — a popover has to
 * paint over the row that opened it, and only the layout knows about both. Leaving drawing out
 * is also what makes these rules testable rather than eyeballed: the only game code on this
 * path is {@link InputConstants}' key numbers, and those are constant variables that javac folds
 * into the bytecode, so no game class is loaded when the rules run headless. A {@link Chord}
 * arrives already translated, by the screen — {@link Chord#from} is the one step of the key path
 * that needs a running game, and it happens before the surface sees anything.
 */
public final class Surface {
    private final List<Widget> widgets = new ArrayList<>();
    private final Focus focus = new Focus();

    /** The widget the pointer went down on, until it comes back up. */
    private Widget captured;

    /** The widget the pointer is over, as of the last {@link #hover}. */
    private Widget hovered;

    /** Where the pointer can reach the widgets {@link #clipped} accepts; null = everywhere. */
    private Box window;
    private Predicate<Widget> clipped = widget -> false;

    /** Registration order is Tab order, and later arrivals sit on top when the pointer lands. */
    public <W extends Widget> W add(W widget) {
        widgets.add(widget);
        focus.add(widget);
        return widget;
    }

    public List<Widget> widgets() {
        return Collections.unmodifiableList(widgets);
    }

    public Focus focus() {
        return focus;
    }

    /** Draws the normal pass in registration order, then every open overlay above it. */
    public void draw(Painter painter) {
        drawNormal(painter, widget -> true);
        drawOverlays(painter);
    }

    /** The normal pass for the widgets {@code which} accepts, in registration order. */
    public void drawNormal(Painter painter, Predicate<Widget> which) {
        for (Widget widget : widgets) {
            if (which.test(widget)) {
                widget.draw(painter, state(widget));
            }
        }
    }

    /** Every open overlay, above everything else. */
    public void drawOverlays(Painter painter) {
        for (Widget widget : widgets) {
            widget.drawOverlay(painter, state(widget));
        }
    }

    /**
     * Confines the pointer to {@code window} for the widgets {@code which} accepts: a page that
     * scrolls draws only inside its window, and a control scrolled out of it must not answer a
     * press or show a hint from under the chrome. An open overlay is never confined. Null lifts it.
     */
    public void window(Box window, Predicate<Widget> which) {
        this.window = window;
        this.clipped = which == null ? widget -> false : which;
    }

    /** The widget holding the keyboard, or null. */
    public Widget held() {
        return focus.held() instanceof Widget widget ? widget : null;
    }

    /** The widget holding the pointer, or null. */
    public Widget captured() {
        return captured;
    }

    /** The widget under the pointer, or null. Read off the last {@link #hover}, not recomputed. */
    public Widget hovered() {
        return hovered;
    }

    /**
     * The control whose {@link Widget#hint} is worth putting on screen, or null. The pointer wins
     * over the ring because it is the more recent of the two: a player who has reached for the
     * mouse is asking about whatever is under it. With the pointer over nothing, the keyboard's own
     * control answers — a hint only a mouse can reach is a hint half the players never see.
     */
    public Widget tipped() {
        if (hovered != null && !hovered.hint().isEmpty()) {
            return hovered;
        }
        Widget holder = held();
        return holder != null && focus.rings(holder) && !holder.hint().isEmpty() ? holder : null;
    }

    /**
     * The state a widget is in on this surface, and the frame's answer to whether it wears the
     * ring. The ring is settled here rather than folded into the state because only one of the two
     * is a single value: a focused control that is also hovered has to report the hover and keep
     * the ring, and a state enum cannot say both. Where the keyboard is arrives the same way and
     * separately, because a ring answers to how focus got here and a caret only to whether it did.
     */
    public State state(Widget widget) {
        widget.holding(focus.holds(widget));
        widget.ringing(focus.rings(widget));
        State state = widget.state(focus);
        widget.observe(state);
        return state;
    }

    /**
     * The topmost widget under the pointer, or null. A control that is over the surface is hit
     * before anything it covers; among equals, later-added widgets win. Tab order is registration
     * order and stays out of this: a select that opens has to be clickable over the row below it
     * without also having to be the last control a player reaches.
     */
    public Widget at(double mx, double my) {
        Widget over = under(mx, my, true);
        return over != null ? over : under(mx, my, false);
    }

    /** The last-added widget under the pointer, among those that are or are not on top. */
    private Widget under(double mx, double my, boolean overlaying) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.overlaying() == overlaying && widget.reach().holds(mx, my)
                    && (window == null || overlaying || !clipped.test(widget) || window.holds(mx, my))) {
                return widget;
            }
        }
        return null;
    }

    /**
     * Re-reads what the pointer is over. Cheap and idempotent, so a screen can call it every
     * frame — which it has to, because a layout can change under a pointer that never moved.
     *
     * <p>While a widget holds the capture it is the only one that can be hovered, and its press
     * follows the pointer in and out of its bounds: dragging off a held button has to let go of
     * it, or the only way out of a press is to commit to it.
     */
    public void hover(double mx, double my) {
        Widget over = captured != null
            ? (captured.reach().holds(mx, my) ? captured : null)
            : at(mx, my);
        hovered = over;
        for (Widget widget : widgets) {
            widget.hovered(widget == over);
        }
        if (over != null) {
            over.hovering(mx, my);
        }
        if (captured != null) {
            captured.pressed(over == captured);
        }
    }

    /** The pointer moved: same as {@link #hover}, and the captured widget hears about it. */
    public void pointer(double mx, double my) {
        hover(mx, my);
        if (captured != null) {
            captured.drag(mx, my);
        }
    }

    /** Mouse down. True when the surface took the click. */
    public boolean press(double mx, double my) {
        Widget hit = at(mx, my);
        Widget was = held();
        if (hit == null) {
            focus.clear();
            handedOff(was);
            return false;
        }
        // A dead control is not a hole in the surface. Letting the click through to whatever sits
        // behind it punishes the player's aim for the control being off; the control shakes its
        // head instead, so the click is seen to have landed.
        if (!hit.state(focus).live()) {
            hit.nudge();
            UiSounds.play(UiSounds.Cue.ERROR);
            return true;
        }
        focus.point(hit);
        handedOff(was);
        if (hit.press(mx, my)) {
            captured = hit;
            hit.pressed(true);
        }
        return true;
    }

    /** Mouse up. True when a widget was holding the pointer. */
    public boolean release(double mx, double my) {
        Widget holder = captured;
        if (holder == null) {
            return false;
        }
        captured = null;
        holder.pressed(false);
        holder.release(mx, my, holder.reach().holds(mx, my));
        // The pointer has not moved but what is under it may have: a control that acted on the
        // release can have just gone disabled, and hover has to be read again from scratch.
        hover(mx, my);
        return true;
    }

    /**
     * A key press, as a GLFW code. The widget holding the keyboard gets first refusal — a text
     * field has to keep Space for itself — and only then is the key read as traversal or as
     * activation. An unclaimed key returns false, so the screen's own Esc still closes it.
     *
     * <p>A control in flight keeps the ring and answers nothing, so the keyboard reaches it for
     * traversal only. Tab is read before the holder is asked to activate, and outside the gate:
     * a request that leaves the player unable to leave the control they fired it from has taken
     * the surface away, not just the control.
     */
    public boolean key(Chord chord) {
        Widget holder = held();
        boolean answers = holder != null && holder.state(focus).live();
        if (answers && holder.key(chord)) {
            return true;
        }
        if (chord.is(InputConstants.KEY_TAB)) {
            boolean moved = focus.advance(chord.shift() ? -1 : 1);
            if (moved) {
                handedOff(holder);
                UiSounds.play(UiSounds.Cue.FOCUS);
            }
            return moved;
        }
        if (answers && chord.activates()) {
            holder.act();
            return true;
        }
        return false;
    }

    /** A typed codepoint goes to the keyboard holder and nowhere else. */
    public boolean type(int codepoint) {
        Widget holder = held();
        return holder != null && holder.state(focus).live() && holder.type(codepoint);
    }

    /** A scroll goes to whatever is under the pointer, capture or no capture. */
    public boolean scroll(double mx, double my, double amount) {
        Widget over = at(mx, my);
        return over != null && over.state(focus).live() && over.scroll(amount);
    }

    /**
     * Tells a control that the keyboard has left it, at the three places it can actually leave:
     * a press that lands on another control, a press on the ground, and Tab. There is nowhere
     * else to learn this — a surface does not tick, and comparing holders every frame would
     * announce a hand-off that never happened the moment a holder went disabled.
     */
    private void handedOff(Widget was) {
        if (was != null && was != held()) {
            was.blurred();
        }
    }
}
