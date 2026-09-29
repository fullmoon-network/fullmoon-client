package dev.fullmoon.client.title;

import java.util.List;
import java.util.function.Supplier;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Glide;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The title's vertical list as one control: the way into the lobby first, with the lobby's live
 * answer beside it, then the quiet rows. The keyboard's cursor starts on the way in and the gold
 * bar with its wash glides to wherever it goes; the pointer lifts a row without moving the
 * cursor, and a click moves it there and fires it.
 */
final class TitleMenu extends Widget {
    record Entry(String label, String key, Runnable action) {}

    /** What the lobby answered, and whether that answer means someone is home. */
    interface Status {
        String text();

        boolean live();
    }

    private static final int PAD = Tokens.Space.COZY + Tokens.Space.TIGHT;
    private static final float DOT = 2.0f;
    private static final int DOT_GAP = Tokens.Space.SNUG + 1;

    private final List<Entry> entries;
    private final Status status;
    private final Supplier<Boolean> primaryReady;
    private final Glide glide = new Glide(Tokens.Spring.GLIDE);

    private TitleLayout layout;
    private int cursor;
    private int over = -1;
    private int pressed = -1;

    TitleMenu(List<Entry> entries, Status status, Supplier<Boolean> primaryReady) {
        super(Voice.QUIET, entries.getFirst().label());
        this.entries = List.copyOf(entries);
        this.status = status;
        this.primaryReady = primaryReady;
    }

    void layout(TitleLayout next) {
        layout = next;
        place(next.menu());
        glide.snap(next.item(cursor));
    }

    int cursor() {
        return cursor;
    }

    @Override
    public void draw(Painter painter, State state) {
        if (layout == null) {
            return;
        }
        glide.advance(System.nanoTime());
        if (!hovered()) {
            over = -1;
        }
        float shake = nudgeOffset();
        painter.fill(glide.x() + shake, glide.y(), glide.w(), glide.h(), Tokens.Color.ACCENT_WASH);
        painter.fill(glide.x() + shake, glide.y(), Tokens.Stroke.BAR, glide.h(),
            state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT);
        for (int i = 0; i < entries.size(); i++) {
            Box b = layout.item(i);
            boolean atCursor = i == cursor;
            if (i == over && !atCursor) {
                painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
            }
            int ink = atCursor || i == over ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY;
            int x = b.x() + PAD + (atCursor ? Math.round(shake) : 0);
            Entry entry = entries.get(i);
            Typeset.draw(painter, Tokens.Type.ROW, entry.label(), x, Typeset.centred(Tokens.Type.ROW, b.y(), b.h()), ink);
            if (i == 0) {
                answer(painter, b);
            } else if (!entry.key().isEmpty()) {
                Glass.keycap(painter, b.right() - PAD - Glass.keycapWidth(entry.key()),
                    b.y() + (b.h() - Tokens.Size.KEYCAP) / 2, entry.key());
            }
        }
        ring(painter, state, Tokens.Radius.NONE);
    }

    /** The lobby's answer on the right of the way in: a live dot and the words, in the body face. */
    private void answer(Painter painter, Box b) {
        String text = status.text();
        int right = b.right() - PAD;
        int textX = right - Typeset.tabularWidth(Tokens.Type.BODY, text);
        Typeset.tabular(painter, Tokens.Type.BODY, text, textX, Typeset.centred(Tokens.Type.BODY, b.y(), b.h()),
            Tokens.Color.INK_SECONDARY);
        painter.dot(textX - DOT_GAP - DOT, b.midY(), DOT,
            status.live() ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
    }

    private int hit(double mx, double my) {
        if (layout == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (layout.item(i).holds(mx, my)) {
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
        pressed = hit(mx, my);
        if (pressed < 0) {
            return false;
        }
        moveTo(pressed);
        return true;
    }

    @Override
    protected void release(double mx, double my, boolean inside) {
        int index = pressed;
        pressed = -1;
        if (inside && index >= 0 && hit(mx, my) == index) {
            act();
        }
    }

    @Override
    protected boolean key(Chord chord) {
        if (chord.is(InputConstants.KEY_UP)) {
            moveTo(Math.max(0, cursor - 1));
            return true;
        }
        if (chord.is(InputConstants.KEY_DOWN)) {
            moveTo(Math.min(entries.size() - 1, cursor + 1));
            return true;
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            moveTo(0);
            return true;
        }
        if (chord.is(InputConstants.KEY_END)) {
            moveTo(entries.size() - 1);
            return true;
        }
        return false;
    }

    private void moveTo(int index) {
        if (index == cursor || index < 0 || index >= entries.size()) {
            return;
        }
        cursor = index;
        glide.to(layout.item(cursor));
        UiSounds.play(UiSounds.Cue.FOCUS);
    }

    @Override
    protected void act() {
        if (cursor == 0 && !primaryReady.get()) {
            nudge();
            UiSounds.play(UiSounds.Cue.ERROR);
            return;
        }
        UiSounds.play(UiSounds.Cue.CONFIRM);
        entries.get(cursor).action().run();
    }
}
