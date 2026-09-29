package dev.fullmoon.client.ui;

import java.util.List;
import java.util.function.IntConsumer;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A well of {@link ListRow}s, as many as fit, with the rest a scroll away.
 *
 * <p>One keyboard stop for the whole list, not one per row: forty mods behind Tab is thirty-nine
 * presses to reach the button under them. So the panel takes the keyboard and the arrows move a
 * mark inside it, which is the same bargain {@link Select} strikes with its open list, and the same
 * one every listbox has struck since before either of us.
 *
 * <p>This is the first control to answer {@link #scroll}. The wheel and the arrows arrive at the
 * same place through different doors — the wheel moves the view and leaves the mark where it was,
 * the arrows move the mark and drag the view along behind it — so a player who scrolled with the
 * wheel and then pressed Down does not lose the row they were looking at.
 *
 * <p>Everything the thumb does is integer arithmetic over a row count, and all of it is static: the
 * rows are a fixed height, so where the thumb sits and which row a drag asks for can be settled
 * without a font, a window or a game. Only the names inside the rows need measuring.
 */
public final class ListPanel extends Widget {
    /** Rows per wheel notch. Three is what a list of names wants; one is a list that fights back. */
    private static final int NOTCH = 3;

    /** The scroll rail, and the shortest a thumb is allowed to get on a very long list. */
    private static final int RAIL_W = Tokens.Space.SNUG;
    private static final int THUMB_MIN = Tokens.Space.LOOSE;

    private final List<ListRow> rows;
    private final String empty;
    private final IntConsumer onPick;

    private int marked;
    private int selected = -1;
    private int first;
    private boolean dragging;

    public ListPanel(String label, List<ListRow> rows, String empty, IntConsumer onPick) {
        this(label, rows, empty, -1, onPick);
    }

    public ListPanel(String label, List<ListRow> rows, String empty, int selected,
            IntConsumer onPick) {
        super(Voice.QUIET, label);
        this.rows = List.copyOf(rows);
        this.empty = empty;
        this.onPick = onPick;
        this.selected = this.rows.isEmpty() ? -1 : Math.clamp(selected, -1, this.rows.size() - 1);
        this.marked = Math.max(0, this.selected);
        this.first = this.marked;
    }

    /** The height a well needs to show {@code rows} of them whole, borders included. */
    public static int heightFor(int rows) {
        return rows * ListRow.HEIGHT + Tokens.Stroke.HAIR * 2;
    }

    public int marked() {
        return marked;
    }

    public int selected() {
        return selected;
    }

    /** The index of the top row in view. */
    public int first() {
        return first;
    }

    /** How many rows the well has room for. At least one, so an empty box is not a divide by zero. */
    public int visible() {
        return Math.max(1, viewport().h() / ListRow.HEIGHT);
    }

    public boolean scrollable() {
        return rows.size() > visible();
    }

    @Override
    public void draw(Painter painter, State state) {
        first = boundedFirst(first, rows.size(), visible());
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        // The well is sunken in all eight: the states belong to the rows in it, and to the line
        // around it, which is where an error on the list as a whole has to show.
        painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.SM, Tokens.Stroke.HAIR,
            chrome.line());

        if (state == State.LOADING) {
            Dots.draw(painter, b.midX(), b.midY(), chrome.ink());
        } else if (rows.isEmpty()) {
            Typeset.drawCentered(painter, Tokens.Type.BODY, empty, b.midX(),
                Typeset.centred(Tokens.Type.BODY, b.y(), b.h()), Tokens.Color.INK_TERTIARY);
        } else {
            body(painter, state);
        }
        // The panel wears the ring and the marked row wears the tick: one says the list has the
        // keyboard, the other says where in the list it is. A ring on the row cannot do the first
        // job and could not be drawn anyway — it belongs outside a control's bounds, and a row's
        // outside is the next row.
        ring(painter, state, Tokens.Radius.SM);
    }

    private void body(Painter painter, State state) {
        Box view = viewport();
        int last = Math.min(rows.size(), first + visible());
        // Surface tells a widget when the pointer has left it; a widget made of parts has to pass
        // that on, or the row the pointer walked off stays lit for as long as the screen is up.
        if (!hovered()) {
            rows.forEach(row -> row.hovered(false));
        }
        for (int i = first; i < last; i++) {
            ListRow row = rows.get(i);
            row.place(rowBox(i));
            row.selected(i == selected);
            row.draw(painter, rowState(state, i));
        }
        // After the rows, not before: a resting row paints the well ground over anything under it.
        // The rules land on the boundaries between rows, so a lifted row keeps its band whole.
        for (int i = first + 1; i < last; i++) {
            painter.hRule(view.x() + Tokens.Space.COZY, rowBox(i).y(),
                view.w() - Tokens.Space.COZY * 2, Tokens.Color.LINE_HAIRLINE);
        }

        if (scrollable()) {
            Box rail = rail();
            int thumb = thumbH(rail.h(), rows.size(), visible());
            painter.fill(rail.x(), rail.y(), rail.w(), rail.h(), Tokens.Radius.SM,
                Tokens.Color.LINE_HAIRLINE);
            painter.fill(rail.x(), thumbY(rail.y(), rail.h(), thumb, rows.size(), visible(), first),
                rail.w(), thumb, Tokens.Radius.SM,
                dragging ? Tokens.Color.ACCENT : Tokens.Color.LINE_STRONG);
        }
    }

    /**
     * The state a row is drawn in. A list that is off or waiting has no live rows in it; a list in
     * error does, because the error is about the list and the line around it says so.
     */
    private State rowState(State own, int i) {
        if (!own.live()) {
            return own;
        }
        return rows.get(i).state(i == marked && holding(), i == marked && ringing());
    }

    @Override
    protected void hovering(double mx, double my) {
        int over = rowAt(mx, my);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).hovered(i == over);
        }
    }

    @Override
    protected boolean press(double mx, double my) {
        if (scrollable() && rail().holds(mx, my)) {
            dragging = true;
            drag(mx, my);
            return true;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            marked = row;
            pick(row);
        }
        // No capture for a row: the pick is done, and a list that held the pointer afterwards would
        // have to decide what dragging off one of its rows means. It means nothing.
        return false;
    }

    @Override
    protected void drag(double mx, double my) {
        if (dragging) {
            Box rail = rail();
            first = firstAt(my, rail.y(), rail.h(), thumbH(rail.h(), rows.size(), visible()),
                rows.size(), visible());
        }
    }

    /** Deliberately not {@link #act}: the only capture this panel takes is the thumb. */
    @Override
    protected void release(double mx, double my, boolean inside) {
        dragging = false;
    }

    @Override
    protected boolean scroll(double amount) {
        int was = first;
        first = Math.clamp(first - (int) Math.signum(amount) * NOTCH, 0, maxFirst());
        // A list already at its end has not answered for the wheel. Whatever is behind it can.
        return first != was;
    }

    @Override
    protected boolean key(Chord chord) {
        if (rows.isEmpty()) {
            return false;
        }
        if (chord.is(InputConstants.KEY_UP)) {
            return moveTo(marked - 1);
        }
        if (chord.is(InputConstants.KEY_DOWN)) {
            return moveTo(marked + 1);
        }
        if (chord.is(InputConstants.KEY_PAGEUP)) {
            return moveTo(marked - visible());
        }
        if (chord.is(InputConstants.KEY_PAGEDOWN)) {
            return moveTo(marked + visible());
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            return moveTo(0);
        }
        if (chord.is(InputConstants.KEY_END)) {
            return moveTo(rows.size() - 1);
        }
        return false;
    }

    @Override
    protected void act() {
        pick(marked);
    }

    /**
     * The mark moves through a dead row rather than around it. A list that renumbers its stops as
     * rows go dead moves the player's place while they are standing on it; refusing the pick is
     * enough, and it is the row itself that looks refused.
     */
    private boolean moveTo(int index) {
        int was = marked;
        marked = Math.clamp(index, 0, rows.size() - 1);
        if (marked != was) {
            UiSounds.play(UiSounds.Cue.FOCUS);
        }
        first = Math.clamp(first, marked - visible() + 1, marked);
        first = Math.clamp(first, 0, maxFirst());
        return true;
    }

    private void pick(int row) {
        if (row < 0 || row >= rows.size() || !rows.get(row).state(false, false).live()) {
            return;
        }
        // Two facts, two calls: what this row does when it is picked, and which row is now the
        // chosen one. A row on its own has the first and no list to tell about the second.
        selected = row;
        rows.get(row).act();
        onPick.accept(row);
    }

    private int maxFirst() {
        return Math.max(0, rows.size() - visible());
    }

    /** The row a pointer is on, or -1 for the rail, the border and past the last row. */
    private int rowAt(double mx, double my) {
        Box view = viewport();
        if (!view.holds(mx, my) || (scrollable() && rail().holds(mx, my))) {
            return -1;
        }
        int row = first + (int) ((my - view.y()) / ListRow.HEIGHT);
        return row < rows.size() ? row : -1;
    }

    private Box rowBox(int i) {
        Box view = viewport();
        int right = view.right() - (scrollable() ? RAIL_W + Tokens.Space.TIGHT : 0);
        int top = view.y() + (i - first) * ListRow.HEIGHT;
        return Box.between(view.x(), top, right, top + ListRow.HEIGHT);
    }

    private Box viewport() {
        return bounds().inset(Tokens.Stroke.HAIR);
    }

    private Box rail() {
        Box view = viewport();
        return Box.between(view.right() - RAIL_W, view.y(), view.right(), view.bottom());
    }

    /** As tall as the share of the list in view, and never so short it stops reading as a thumb. */
    static int thumbH(int trackH, int rows, int visible) {
        return Math.clamp(trackH * visible / Math.max(1, rows), THUMB_MIN, trackH);
    }

    static int thumbY(int top, int trackH, int thumbH, int rows, int visible, int first) {
        int over = rows - visible;
        return over <= 0 ? top : top + (trackH - thumbH) * first / over;
    }

    /** The top row a thumb dragged to {@code my} asks for, taking the thumb by its middle. */
    static int firstAt(double my, int top, int trackH, int thumbH, int rows, int visible) {
        int room = trackH - thumbH;
        int over = rows - visible;
        if (room <= 0 || over <= 0) {
            return 0;
        }
        return Math.clamp(Math.round((my - top - thumbH / 2.0) / room * over), 0, over);
    }

    static int boundedFirst(int first, int rows, int visible) {
        return Math.clamp(first, 0, Math.max(0, rows - visible));
    }
}
