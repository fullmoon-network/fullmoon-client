package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Glide;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.render.Spring;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Dots;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The choices of a server menu as one control: a list, two columns or a grid of them, the
 * keyboard's cursor among them, and the gold highlight that glides to wherever the cursor goes.
 *
 * <p>One keyboard stop for the whole board, arrows inside it — the same bargain every listbox
 * strikes. The cursor is a fact; the highlight is a spring aimed at the cursor's box, so a held
 * arrow moves the cursor at key-repeat speed and the highlight is never more than one response
 * behind it. The pointer lifts a row without moving the cursor; a click moves it and picks.
 */
final class MenuBoard extends Widget {
    interface Listener {
        void picked(ServerMenuEntry entry);

        void moved(ServerMenuEntry entry);

        void refused(ServerMenuEntry entry);
    }

    private static final int PAD = Tokens.Space.LOOSE;
    private static final int ICON = Tokens.Size.ICON;
    private static final int GAP = Tokens.Space.COZY;
    private static final float DIMMED = 0.42f;
    private static final float MOON = 5.0f;
    private static final int RAIL_W = Tokens.Space.TIGHT;

    private final List<ServerMenuEntry> choices;
    private final Listener listener;
    private final Glide glide = new Glide(Tokens.Spring.GLIDE);
    private final Spring lift = new Spring(Tokens.Spring.LIFT);
    private final Spring scroll = new Spring(Tokens.Spring.SCROLL);

    private ServerMenuLayout layout;
    private List<GridCursor.Cell> cells = List.of();
    private GridCursor cursor = new GridCursor(List.of(), -1);
    private int over = -1;
    private int busySlot = -1;
    private int wantedSlot;

    MenuBoard(List<ServerMenuEntry> choices, int wantedSlot, Listener listener) {
        super(Voice.QUIET, "menu");
        this.choices = List.copyOf(choices);
        this.wantedSlot = wantedSlot;
        this.listener = listener;
    }

    /** The screen laid itself out: the board takes the list area and lays its cells on it. */
    void layout(ServerMenuLayout next) {
        layout = next;
        place(next.list());
        List<Integer> slots = new ArrayList<>(choices.size());
        for (ServerMenuEntry entry : choices) {
            slots.add(entry.slot());
        }
        cells = next.cells(slots);
        int at = indexOfSlot(wantedSlot);
        cursor = new GridCursor(cells, at < 0 ? 0 : at);
        scroll.snap(0);
        if (cursor.at() >= 0) {
            keepVisible();
            scroll.snap(scroll.target());
            glide.snap(next.item(cells.get(cursor.at())));
        }
    }

    /** The choice the cursor is on, or null with none. */
    ServerMenuEntry current() {
        return cursor.at() < 0 ? null : choices.get(cursor.at());
    }

    /** The choice the pointer rests on, or null. */
    ServerMenuEntry hoveredEntry() {
        return over < 0 || over >= choices.size() ? null : choices.get(over);
    }

    int currentSlot() {
        ServerMenuEntry entry = current();
        return entry == null ? -1 : entry.slot();
    }

    /** A request went out for {@code slot}; its row shows dots until the server answers. */
    void busy(int slot) {
        busySlot = slot;
    }

    boolean isBusy() {
        return busySlot >= 0;
    }

    @Override
    public void draw(Painter painter, State state) {
        if (layout == null) {
            return;
        }
        long now = System.nanoTime();
        scroll.advance(now);
        glide.advance(now);
        lift.to(holding() ? 1.0f : 0.0f);
        lift.advance(now);
        if (!hovered()) {
            over = -1;
        }
        Box list = layout.list();
        painter.pushClip(list.x(), list.y(), list.w(), list.h());
        if (cursor.at() >= 0) {
            highlight(painter, state);
        }
        for (int i = 0; i < choices.size(); i++) {
            Box box = itemBox(i);
            if (box.bottom() < list.y() || box.y() > list.bottom()) {
                continue;
            }
            switch (layout.mode()) {
                case LIST -> listRow(painter, i, box, state);
                case COLUMNS -> columnRow(painter, i, box, state);
                case GRID -> cell(painter, i, box, state);
            }
        }
        painter.popClip();
        rail(painter);
        if (choices.isEmpty()) {
            Typeset.drawCentered(painter, Tokens.Type.BODY, "고를 수 있는 항목이 없어요", list.midX(),
                Typeset.centred(Tokens.Type.BODY, list.y(), list.h()), Tokens.Color.INK_TERTIARY);
        }
    }

    /**
     * The wash and the bar under the cursor's row — or in the grid, the gold edge of its cell. The
     * glide runs in content coordinates and the scroll is taken off at draw time, so the highlight
     * sits on its row however far either spring has got.
     */
    private void highlight(Painter painter, State state) {
        float x = glide.x() + nudgeOffset();
        float y = glide.y() - scroll.value();
        float w = glide.w();
        float h = glide.h();
        if (layout.mode() == ServerMenuLayout.Mode.GRID) {
            painter.fill(x, y, w, h, Tokens.Color.ACCENT_WASH);
            painter.border(x, y, w, h, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
                state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT);
            return;
        }
        painter.fill(x, y, w, h, Tokens.Color.ACCENT_WASH);
        float glow = lift.value();
        if (glow > 0.01f) {
            painter.fillGradient(x, y, w, h, Rgb.scaleAlpha(Tokens.Color.ACCENT_GLOW, glow * 0.6f),
                Rgb.alpha(Tokens.Color.ACCENT_GLOW, 0.0f));
        }
        painter.fill(x, y, Tokens.Stroke.BAR, h,
            state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT);
    }

    private void listRow(Painter painter, int i, Box b, State state) {
        ServerMenuEntry entry = choices.get(i);
        boolean atCursor = i == cursor.at();
        hoverLift(painter, i, b, atCursor);
        float dim = entry.blocked() ? DIMMED : 1.0f;
        int shift = atCursor ? Math.round(nudgeOffset()) : 0;
        int x = b.x() + PAD + shift;
        int iconY = b.midY() - ICON / 2;
        drawIcon(painter, entry, x, iconY, ICON, dim);
        int textX = x + ICON + GAP;
        int right = valueColumn(painter, entry, b, state, atCursor);
        int room = Math.max(0, right - GAP - textX);
        int block = Tokens.Type.ROW.leading() + Tokens.Type.BODY.leading();
        String description = entry.description();
        int top = description.isEmpty()
            ? Typeset.centred(Tokens.Type.ROW, b.y(), b.h())
            : b.y() + (b.h() - block) / 2;
        Typeset.draw(painter, Tokens.Type.ROW, Typeset.ellipsized(Tokens.Type.ROW, entry.label(), room),
            textX, top, Rgb.alpha(Tokens.Color.INK_PRIMARY, dim));
        if (!description.isEmpty()) {
            Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, description, room),
                textX, top + Tokens.Type.ROW.leading(), Rgb.alpha(Tokens.Color.INK_SECONDARY, dim));
        }
    }

    private void columnRow(Painter painter, int i, Box b, State state) {
        ServerMenuEntry entry = choices.get(i);
        boolean atCursor = i == cursor.at();
        hoverLift(painter, i, b, atCursor);
        float dim = entry.blocked() ? DIMMED : 1.0f;
        int shift = atCursor ? Math.round(nudgeOffset()) : 0;
        int x = b.x() + PAD + shift;
        drawIcon(painter, entry, x, b.midY() - ICON / 2, ICON, dim);
        int textX = x + ICON + GAP;
        int right = valueColumn(painter, entry, b, state, atCursor);
        int room = Math.max(0, right - GAP - textX);
        Typeset.draw(painter, Tokens.Type.ROW, Typeset.ellipsized(Tokens.Type.ROW, entry.label(), room),
            textX, Typeset.centred(Tokens.Type.ROW, b.y(), b.h()), Rgb.alpha(Tokens.Color.INK_PRIMARY, dim));
    }

    private void cell(Painter painter, int i, Box b, State state) {
        ServerMenuEntry entry = choices.get(i);
        boolean atCursor = i == cursor.at();
        if (i == over && !atCursor) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
        } else if (!atCursor) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_SUNKEN);
        }
        float dim = entry.blocked() ? DIMMED : 1.0f;
        int shift = atCursor ? Math.round(nudgeOffset()) : 0;
        drawIcon(painter, entry, b.x() + (b.w() - ICON) / 2 + shift, b.y() + (b.h() - ICON) / 2, ICON, dim);
        if (entry.slot() == busySlot) {
            Dots.draw(painter, b.midX(), b.midY(), Tokens.Color.INK_TERTIARY);
        } else if (entry.item().count() > 1) {
            Typeset.tabularRight(painter, Tokens.Type.MICRO, Integer.toString(entry.item().count()),
                b.right() - 1, b.bottom() - Tokens.Type.MICRO.leading() + 1, Tokens.Color.INK_PRIMARY);
        }
    }

    /** The hover lift: five percent of white on the row the pointer is on, over the wash if it is the cursor's. */
    private void hoverLift(Painter painter, int i, Box b, boolean atCursor) {
        if (i == over) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
        }
    }

    /**
     * The right-hand column of a row: dots while its request is out, else a count, a ✖ when
     * refused, and the figure — a moon with the win chance, or the price. Returns the x it starts
     * at, which is where the name has to stop.
     */
    private int valueColumn(Painter painter, ServerMenuEntry entry, Box b, State state, boolean atCursor) {
        int right = b.right() - PAD;
        if (entry.slot() == busySlot) {
            Dots.draw(painter, right - Dots.width() / 2.0f, b.midY(), Tokens.Color.INK_TERTIARY);
            return right - Dots.width() - GAP;
        }
        int textY = Typeset.centred(Tokens.Type.STRONG, b.y(), b.h());
        if (entry.item().chance().isPresent()) {
            String percent = ServerMenuCopy.percent(entry.item().chance().getAsDouble());
            int w = Typeset.tabularWidth(Tokens.Type.STRONG, percent);
            Typeset.tabular(painter, Tokens.Type.STRONG, percent, right - w, textY, Tokens.Color.INK_SECONDARY);
            float cx = right - w - Tokens.Space.SNUG - 1 - MOON;
            painter.moon(cx, b.midY(), MOON, (float) entry.item().chance().getAsDouble(), true,
                Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
            return (int) (cx - MOON) - GAP;
        }
        String headline = entry.headline();
        int x = right;
        if (!headline.isEmpty()) {
            int w = Typeset.tabularWidth(Tokens.Type.STRONG, headline);
            x -= w;
            Typeset.tabular(painter, Tokens.Type.STRONG, headline, x, textY, Tokens.Color.INK_SECONDARY);
        }
        if (entry.blocked()) {
            x -= Tokens.Space.SNUG + 1 + Glass.MARK;
            Glass.blockedMark(painter, x + Glass.MARK / 2.0f, b.midY(), Tokens.Color.STATUS_DANGER);
        }
        String count = entry.count();
        if (!count.isEmpty()) {
            x -= GAP + Typeset.tabularWidth(Tokens.Type.BODY, count);
            Typeset.tabular(painter, Tokens.Type.BODY, count, x, Typeset.centred(Tokens.Type.BODY, b.y(), b.h()),
                Tokens.Color.INK_TERTIARY);
        }
        return x - GAP;
    }

    private void drawIcon(Painter painter, ServerMenuEntry entry, int x, int y, int size, float dim) {
        // The game draws item art at full strength whatever the painter's opacity; a dimmed row's
        // icon is dimmed by a wash of the glass over it instead.
        entry.drawIcon(painter, x, y, size);
        if (dim < 1.0f && entry.itemIcon()) {
            painter.fill(x, y, size, size, Rgb.alpha(Tokens.Color.SURFACE_VOID, (1.0f - dim) * 0.86f));
        }
    }

    /** A two-pixel rail on the list's right edge when there is more than fits. */
    private void rail(Painter painter) {
        int max = layout.maxScroll(cells);
        if (max <= 0) {
            return;
        }
        Box list = layout.list();
        int trackH = list.h() - Tokens.Space.COZY * 2;
        int thumb = Math.max(Tokens.Space.LOOSE, trackH * list.h() / layout.contentHeight(cells));
        float at = scroll.value() / max;
        int y = list.y() + Tokens.Space.COZY + Math.round((trackH - thumb) * at);
        painter.fill(list.right() - RAIL_W - Tokens.Space.TIGHT, list.y() + Tokens.Space.COZY, RAIL_W, trackH,
            Tokens.Color.LINE_HAIRLINE);
        painter.fill(list.right() - RAIL_W - Tokens.Space.TIGHT, y, RAIL_W, thumb, Tokens.Color.LINE_STRONG);
    }

    private Box itemBox(int index) {
        Box box = layout.item(cells.get(index));
        return box.at(box.x(), box.y() - Math.round(scroll.value()));
    }

    private int indexOfSlot(int slot) {
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).slot() == slot) {
                return i;
            }
        }
        return -1;
    }

    private int hit(double mx, double my) {
        if (layout == null || !layout.list().holds(mx, my)) {
            return -1;
        }
        for (int i = 0; i < choices.size(); i++) {
            if (itemBox(i).holds(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void hovering(double mx, double my) {
        over = hit(mx, my);
    }

    @Override
    protected boolean press(double mx, double my) {
        int index = hit(mx, my);
        if (index >= 0) {
            moveCursorTo(index);
            pick();
        }
        // No capture: the pick is done, and dragging off a row means nothing.
        return false;
    }

    @Override
    protected boolean scroll(double amount) {
        int max = layout == null ? 0 : layout.maxScroll(cells);
        if (max <= 0) {
            return false;
        }
        float was = scroll.target();
        scroll.to(Math.clamp(was - (float) Math.signum(amount) * layout.pitch() * 2, 0, max));
        return scroll.target() != was;
    }

    @Override
    protected boolean key(Chord chord) {
        if (choices.isEmpty()) {
            return false;
        }
        if (chord.is(InputConstants.KEY_UP)) {
            return step(GridCursor.Direction.UP);
        }
        if (chord.is(InputConstants.KEY_DOWN)) {
            return step(GridCursor.Direction.DOWN);
        }
        if (chord.is(InputConstants.KEY_LEFT)) {
            return step(GridCursor.Direction.LEFT);
        }
        if (chord.is(InputConstants.KEY_RIGHT)) {
            return step(GridCursor.Direction.RIGHT);
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            moveCursorTo(0);
            return true;
        }
        if (chord.is(InputConstants.KEY_END)) {
            moveCursorTo(choices.size() - 1);
            return true;
        }
        return false;
    }

    /** An arrow that finds nothing is still an arrow the board answered: the cursor stays put. */
    private boolean step(GridCursor.Direction direction) {
        int was = cursor.at();
        if (cursor.move(direction) && cursor.at() != was) {
            afterMove();
        }
        return true;
    }

    private void moveCursorTo(int index) {
        if (cursor.set(index)) {
            afterMove();
        }
    }

    private void afterMove() {
        keepVisible();
        glide.to(layout.item(cells.get(cursor.at())));
        listener.moved(current());
    }

    /** Scrolls just far enough that the cursor's box is whole inside the list. */
    private void keepVisible() {
        if (cursor.at() < 0) {
            return;
        }
        Box box = layout.item(cells.get(cursor.at()));
        Box list = layout.list();
        int inset = layout.listInset();
        float target = scroll.target();
        if (box.y() - target < list.y() + inset) {
            target = box.y() - list.y() - inset;
        } else if (box.bottom() - target > list.bottom() - inset) {
            target = box.bottom() - list.bottom() + inset;
        }
        scroll.to(Math.clamp(target, 0, layout.maxScroll(cells)));
    }

    @Override
    protected void act() {
        pick();
    }

    private void pick() {
        ServerMenuEntry entry = current();
        if (entry == null) {
            return;
        }
        if (!entry.live() || busySlot >= 0) {
            nudge();
            listener.refused(entry);
            return;
        }
        listener.picked(entry);
    }

}
