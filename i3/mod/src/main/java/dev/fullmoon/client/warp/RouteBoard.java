package dev.fullmoon.client.warp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.render.Glide;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Spring;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The destinations as one control: their groups as captions, each destination as a one-line
 * row with its distance, the keyboard's cursor among the rows, and the gold highlight that
 * glides to wherever the cursor goes. The pointer lifts a row without moving the cursor; a click
 * moves it. Enter, from anywhere on the screen, is the way to go.
 */
final class RouteBoard extends Widget {
    interface Listener {
        void moved(BridgeProtocol.Waypoint route);

        void go();
    }

    /** One line of the list: a group's caption, or the index of a destination. */
    record Slot(String caption, int route) {
        boolean isCaption() {
            return route < 0;
        }
    }

    private static final int PAD = Tokens.Space.LOOSE;

    private final List<BridgeProtocol.Waypoint> routes;
    private final List<Slot> slots;
    private final IntFunction<String> meta;
    private final IntPredicate here;
    private final Listener listener;
    private final Glide glide = new Glide(Tokens.Spring.GLIDE);
    private final Spring scroll = new Spring(Tokens.Spring.SCROLL);

    private WarpLayout layout;
    private int cursor;
    private int over = -1;

    RouteBoard(List<BridgeProtocol.Waypoint> routes, int cursor, IntFunction<String> meta, IntPredicate here,
            Listener listener) {
        super(Voice.QUIET, "routes");
        this.routes = List.copyOf(routes);
        this.slots = slots(this.routes);
        this.cursor = routes.isEmpty() ? -1 : Math.clamp(cursor, 0, routes.size() - 1);
        this.meta = meta;
        this.here = here;
        this.listener = listener;
    }

    /** The captions and rows in list order: a caption before the first destination of each group. */
    static List<Slot> slots(List<BridgeProtocol.Waypoint> routes) {
        List<Slot> out = new ArrayList<>();
        String group = null;
        for (int i = 0; i < routes.size(); i++) {
            String next = routes.get(i).group();
            if (!next.equals(group)) {
                out.add(new Slot(next, -1));
                group = next;
            }
            out.add(new Slot("", i));
        }
        return out;
    }

    void layout(WarpLayout next) {
        layout = next;
        place(next.list());
        scroll.snap(0);
        if (cursor >= 0) {
            keepVisible();
            scroll.snap(scroll.target());
            glide.snap(rowBox(cursor));
        }
    }

    BridgeProtocol.Waypoint current() {
        return cursor < 0 ? null : routes.get(cursor);
    }

    int cursor() {
        return cursor;
    }

    /** A press that cannot go: the board shakes its head. */
    void refuse() {
        nudge();
    }

    /** The y of the top of slot {@code index} before scrolling. */
    private int slotTop(int index) {
        int y = layout.list().y();
        for (int i = 0; i < index; i++) {
            y += slots.get(i).isCaption() ? WarpLayout.captionHeight() : layout.row();
        }
        return y;
    }

    private int slotOf(int route) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).route() == route) {
                return i;
            }
        }
        return -1;
    }

    /** The box of destination {@code route}'s row in content coordinates. */
    private Box rowBox(int route) {
        return new Box(layout.list().x(), slotTop(slotOf(route)), layout.list().w(), layout.row());
    }

    private int contentHeight() {
        return slotTop(slots.size()) - layout.list().y();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - layout.list().h());
    }

    @Override
    public void draw(Painter painter, State state) {
        if (layout == null) {
            return;
        }
        long now = System.nanoTime();
        scroll.advance(now);
        glide.advance(now);
        if (!hovered()) {
            over = -1;
        }
        Box list = layout.list();
        painter.pushClip(list.x(), list.y(), list.w(), list.h());
        int shift = Math.round(scroll.value());
        if (cursor >= 0) {
            float x = glide.x() + nudgeOffset();
            float y = glide.y() - shift;
            painter.fill(x, y, glide.w(), glide.h(), Tokens.Color.ACCENT_WASH);
            painter.fill(x, y, Tokens.Stroke.BAR, glide.h(),
                state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT);
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int top = slotTop(i) - shift;
            if (slot.isCaption()) {
                Typeset.draw(painter, Tokens.Type.STRONG, slot.caption(), list.x() + PAD,
                    top + Tokens.Space.COZY, Tokens.Color.INK_TERTIARY);
                continue;
            }
            Box b = new Box(list.x(), top, list.w(), layout.row());
            if (b.bottom() < list.y() || b.y() > list.bottom()) {
                continue;
            }
            row(painter, slot.route(), b);
        }
        painter.popClip();
    }

    private void row(Painter painter, int i, Box b) {
        BridgeProtocol.Waypoint route = routes.get(i);
        boolean atCursor = i == cursor;
        if (i == over) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
        }
        int textY = Typeset.centred(Tokens.Type.STRONG, b.y(), b.h());
        int right = b.right() - PAD;
        String value = meta.apply(i);
        int valueW = value.isEmpty() ? 0 : Typeset.tabularWidth(Tokens.Type.BODY, value);
        if (valueW > 0) {
            Typeset.tabular(painter, Tokens.Type.BODY, value, right - valueW,
                Typeset.centred(Tokens.Type.BODY, b.y(), b.h()),
                here.test(i) ? Tokens.Color.STATUS_LIVE : Tokens.Color.INK_TERTIARY);
        }
        int x = b.x() + PAD + (atCursor ? Math.round(nudgeOffset()) : 0);
        int room = right - valueW - Tokens.Space.COZY - x;
        Typeset.draw(painter, Tokens.Type.STRONG, Typeset.ellipsized(Tokens.Type.STRONG, route.name(), room),
            x, textY, Tokens.Color.INK_PRIMARY);
    }

    private int hit(double mx, double my) {
        if (layout == null || !layout.list().holds(mx, my)) {
            return -1;
        }
        int shift = Math.round(scroll.value());
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.isCaption()) {
                continue;
            }
            Box b = new Box(layout.list().x(), slotTop(i) - shift, layout.list().w(), layout.row());
            if (b.holds(mx, my)) {
                return slot.route();
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
            moveTo(index);
        }
        // No capture: choosing is done on the press, and dragging off a row means nothing.
        return false;
    }

    @Override
    protected boolean scroll(double amount) {
        int max = maxScroll();
        if (max <= 0) {
            return false;
        }
        float was = scroll.target();
        scroll.to(Math.clamp(was - (float) Math.signum(amount) * layout.row() * 2, 0, max));
        return scroll.target() != was;
    }

    @Override
    protected boolean key(Chord chord) {
        if (routes.isEmpty()) {
            return false;
        }
        if (chord.is(InputConstants.KEY_UP)) {
            moveTo(Math.max(0, cursor - 1));
            return true;
        }
        if (chord.is(InputConstants.KEY_DOWN)) {
            moveTo(Math.min(routes.size() - 1, cursor + 1));
            return true;
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            moveTo(0);
            return true;
        }
        if (chord.is(InputConstants.KEY_END)) {
            moveTo(routes.size() - 1);
            return true;
        }
        return false;
    }

    private void moveTo(int index) {
        if (index == cursor || index < 0 || index >= routes.size()) {
            return;
        }
        cursor = index;
        keepVisible();
        glide.to(rowBox(cursor));
        listener.moved(current());
    }

    /** Scrolls just far enough that the cursor's row is whole inside the list. */
    private void keepVisible() {
        if (cursor < 0) {
            return;
        }
        Box box = rowBox(cursor);
        Box list = layout.list();
        float target = scroll.target();
        if (box.y() - target < list.y()) {
            target = box.y() - list.y();
        } else if (box.bottom() - target > list.bottom()) {
            target = box.bottom() - list.bottom();
        }
        scroll.to(Math.clamp(target, 0, maxScroll()));
    }

    @Override
    protected void act() {
        listener.go();
    }
}
