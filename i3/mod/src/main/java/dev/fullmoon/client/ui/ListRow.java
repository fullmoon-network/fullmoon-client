package dev.fullmoon.client.ui;

import java.util.Objects;
import java.util.function.Supplier;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * A line in a list: a name, a value on the right, and a tick on the one that is chosen.
 *
 * <p>A row is a strip of a panel rather than a control standing on one, which is why it does not
 * take its ground from {@link Voice}. A voice's resting ground is a control's ground, and forty of
 * those stacked read as forty buttons; the tokens say the same thing from the other side, where
 * {@code surface.raised} is documented as a hovered row and not a resting one. So rest is the well
 * showing through and hover is the row lifting out of it.
 *
 * <p>Selected is not a state, for the reason {@link Toggle}'s on and {@link Select}'s open are not:
 * it outlives all eight and has to stay legible in every one. It shows as the accent tick a section
 * head wears, two pixels of it; the keyboard's own mark is the same tick a hairline wide over a
 * raised ground, so a chosen row the keyboard is standing on still says both things at once. Not a
 * ring — a ring is drawn outside a control's bounds, and a row has no outside: the viewport it
 * scrolls in would cut three sides off it.
 */
public final class ListRow extends Widget {
    public static final int HEIGHT = Tokens.Space.SECTION;

    /** Room for the widest tick, so a row's name never shifts when the tick changes under it. */
    private static final int GUTTER = Tokens.Space.SNUG + Tokens.Stroke.FOCUS + Tokens.Space.BASE;

    /**
     * How a row draws: the ground behind it, the tick beside its name and the ink of the name. One
     * value because it is one decision — a chosen row and the row the keyboard is on both lift out
     * of the well, and what separates them is the tick. A width of zero is no tick at all.
     */
    record Look(int ground, int tick, int tickWidth, int ink) {}

    private final Supplier<String> meta;
    private final Runnable onPick;
    private boolean selected;

    public ListRow(String label, String meta, Runnable onPick) {
        this(label, () -> meta, onPick);
        Objects.requireNonNull(meta, "meta");
    }

    public ListRow(String label, Supplier<String> meta, Runnable onPick) {
        super(Voice.QUIET, label);
        this.meta = Objects.requireNonNull(meta, "meta");
        this.onPick = onPick;
    }

    String meta() {
        return Objects.requireNonNull(meta.get(), "meta value");
    }

    public boolean selected() {
        return selected;
    }

    /** Selection belongs to whatever owns the list: one row cannot know it is the only one. */
    public void selected(boolean value) {
        selected = value;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        Look look = look(state);
        painter.fill(b.x(), b.y(), b.w(), b.h(), look.ground());

        int textY = Typeset.centred(Tokens.Type.BODY, b.y(), b.h());
        if (look.tickWidth() > 0) {
            // The palace marks a row with a diamond: filled for the chosen row, outlined for the
            // keyboard's own stop, so the two widths still read apart.
            boolean chosen = look.tickWidth() >= Tokens.Stroke.FOCUS;
            painter.diamond(b.x() + Tokens.Space.SNUG + Tokens.Space.TIGHT,
                Typeset.capTop(Tokens.Type.BODY, textY) + Typeset.capHeight(Tokens.Type.BODY) / 2.0f,
                chosen ? 3.5f : 3.0f, chosen ? 0.0f : Tokens.Stroke.HAIR, look.tick());
        }

        int left = b.x() + GUTTER;
        int right = b.right() - Tokens.Space.COZY;
        if (state == State.LOADING) {
            Dots.draw(painter, right - Dots.width() / 2.0f, b.midY(), chrome.ink());
            right -= Dots.width() + Tokens.Space.LOOSE;
        } else if (!meta().isEmpty()) {
            right -= Typeset.tabularRight(painter, Tokens.Type.LABEL, meta(), right,
                Typeset.centred(Tokens.Type.LABEL, b.y(), b.h()), Tokens.Color.INK_TERTIARY)
                + Tokens.Space.LOOSE;
        }

        String visible = Typeset.fittingPrefix(Tokens.Type.BODY, label(),
            Math.max(0, right - left));
        Typeset.draw(painter, Tokens.Type.BODY, visible, left, textY, look.ink());
    }

    /** What this row draws as. Package-private because the panel's sweep is the proof of it. */
    Look look(State state) {
        Chrome chrome = voice().chrome(state);
        return new Look(ground(state, chrome), tickColor(state, chrome), tickWidth(state),
            ink(state, chrome));
    }

    @Override
    protected void act() {
        onPick.run();
    }

    /**
     * A row something has reached takes the voice's own ground, which is what makes a hovered row and
     * a marked one two different lifts rather than one. Everything else is the well showing through —
     * or the wash, on the row that was chosen, because being chosen outlives all eight.
     */
    private int ground(State state, Chrome chrome) {
        return switch (state) {
            case HOVER, ACTIVE, FOCUS_VISIBLE -> chrome.fill();
            case REST, FOCUS, DISABLED, LOADING, ERROR ->
                selected ? Tokens.Color.ACCENT_WASH : Tokens.Color.SURFACE_SUNKEN;
        };
    }

    /**
     * One tick, three reasons for it: the row is chosen, the keyboard is on it, or it is wrong.
     * Chosen is the wide one, because it is the only one of the three that outlives the state — a
     * row the keyboard has left is still the chosen row, and on a ground that is already lifted the
     * width is the only thing left to say so with.
     */
    private int tickWidth(State state) {
        if (selected) {
            return Tokens.Stroke.FOCUS;
        }
        return state == State.FOCUS_VISIBLE || state == State.ERROR ? Tokens.Stroke.HAIR : 0;
    }

    /**
     * An accent tick on a row that answers nothing would be claiming that it does. A press darkens
     * it, which on the chosen row is the whole of the press: its ground is already the wash.
     */
    private static int tickColor(State state, Chrome chrome) {
        if (state == State.ERROR) {
            return chrome.line();
        }
        if (!state.live()) {
            return chrome.ink();
        }
        return state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT;
    }

    /** A name brightens when something reaches the row. Untouched, it sits back at secondary. */
    private int ink(State state, Chrome chrome) {
        return switch (state) {
            case REST, FOCUS -> selected ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY;
            default -> chrome.ink();
        };
    }
}
