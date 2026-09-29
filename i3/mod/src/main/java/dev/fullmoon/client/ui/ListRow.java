package dev.fullmoon.client.ui;

import java.util.Objects;
import java.util.function.Supplier;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * A line in a list: a name in the row face, a value on the right in the body face, and a gold bar
 * on the one that is chosen.
 *
 * <p>A row is a strip of the glass rather than a control standing on it, which is why it does not
 * take its ground from {@link Voice}. Rest is the pane showing through; hover is the pane lifting
 * five percent; chosen is a gold wash with a two-pixel bar on its left edge, which outlives all
 * eight states and has to stay legible in every one. The keyboard's own stop, when it is not the
 * chosen row, is a hairline bar in tertiary ink over the same lift — so a chosen row the keyboard
 * is standing on still says both things at once. Not a ring: a ring is drawn outside a control's
 * bounds, and a row has no outside; the viewport it scrolls in would cut three sides off it.
 *
 * <p>In a {@link ListPanel} the panel draws the chosen row's wash and bar, because that bar glides
 * between rows and a row cannot draw itself between two places. A row the panel owns draws
 * everything but those two.
 */
public final class ListRow extends Widget {
    public static final int HEIGHT = Tokens.Size.ROW_ONE;

    /** Room for the bar, so a row's name never shifts when the bar changes under it. */
    private static final int GUTTER = Tokens.Space.LOOSE;

    /**
     * How a row draws: the ground behind it, the bar on its left edge and the ink of the name. One
     * value because it is one decision. A width of zero is no bar at all.
     */
    record Look(int ground, int tick, int tickWidth, int ink) {}

    private final Supplier<String> meta;
    private final Runnable onPick;
    private boolean selected;
    private boolean owned;

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

    /** Whether a panel draws the chosen wash and bar for this row. */
    void owned(boolean value) {
        owned = value;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        Look look = look(state);
        if (look.ground() != 0) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), look.ground());
        }
        if (look.tickWidth() > 0) {
            painter.fill(b.x(), b.y(), look.tickWidth(), b.h(), look.tick());
        }

        int nameY = Typeset.centred(Tokens.Type.ROW, b.y(), b.h());
        int metaY = Typeset.centred(Tokens.Type.BODY, b.y(), b.h());
        int left = b.x() + GUTTER;
        int right = b.right() - Tokens.Space.LOOSE;
        if (state == State.LOADING) {
            Dots.draw(painter, right - Dots.width() / 2.0f, b.midY(), chrome.ink());
            right -= Dots.width() + Tokens.Space.LOOSE;
        } else if (!meta().isEmpty()) {
            right -= Typeset.tabularRight(painter, Tokens.Type.BODY, meta(), right,
                metaY, Tokens.Color.INK_TERTIARY) + Tokens.Space.LOOSE;
        }

        String visible = Typeset.ellipsized(Tokens.Type.ROW, label(), Math.max(0, right - left));
        Typeset.draw(painter, Tokens.Type.ROW, visible, left, nameY, look.ink());
    }

    /** What this row draws as. Package-private because the panel's sweep is the proof of it. */
    Look look(State state) {
        Chrome chrome = voice().chrome(state);
        return new Look(ground(state), tickColor(state, chrome), tickWidth(state),
            ink(state, chrome));
    }

    @Override
    protected void act() {
        onPick.run();
    }

    /**
     * A hovered row lifts, a pressed one sinks, a chosen one wears the wash whatever else is true
     * of it — lifted under the pointer, lit under the keyboard's ring — and every other row is
     * the glass showing through: a ground of zero draws nothing. An owned row leaves the wash to
     * its panel and only lifts.
     */
    private int ground(State state) {
        boolean chosen = selected && !owned;
        return switch (state) {
            case HOVER -> chosen ? Tokens.Color.ACCENT_WASH_LIFT : Tokens.Color.SURFACE_RAISED;
            case ACTIVE -> Tokens.Color.SURFACE_CONTROL_PRESSED;
            case FOCUS_VISIBLE -> chosen ? Tokens.Color.ACCENT_GLOW : 0;
            case REST, FOCUS, DISABLED, LOADING, ERROR -> chosen ? Tokens.Color.ACCENT_WASH : 0;
        };
    }

    /**
     * One bar, three reasons for it: the row is chosen, the keyboard is on it, or it is wrong.
     * Chosen is the wide one, because it is the only one of the three that outlives the state.
     */
    private int tickWidth(State state) {
        if (selected) {
            return owned ? 0 : Tokens.Stroke.BAR;
        }
        return state == State.FOCUS_VISIBLE || state == State.ERROR ? Tokens.Stroke.HAIR : 0;
    }

    /** A gold bar on a row that answers nothing would be claiming that it does. */
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
