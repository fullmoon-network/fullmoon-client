package dev.fullmoon.client.title;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

/**
 * One quiet line of the title menu. The row the pointer or the keyboard is on turns its diamond
 * cinnabar and its ink to ivory; nothing else about it moves, because a menu whose rows shift
 * under the pointer is a menu the player has to chase.
 */
final class TitleRow extends Widget {
    private final String key;
    private final Runnable action;

    TitleRow(String label, String key, Runnable action) {
        super(Voice.QUIET, label);
        this.key = key;
        this.action = action;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        boolean chosen = state == State.HOVER || state == State.ACTIVE
            || state == State.FOCUS || state == State.FOCUS_VISIBLE;
        if (chosen) {
            painter.fill(b.x(), b.y(), Tokens.Stroke.BAR, b.h(), Tokens.Color.ACCENT);
        }
        int textX = b.x() + Tokens.Space.GUTTER;
        Typeset.draw(painter, Tokens.Type.STRONG, label(), textX,
            Typeset.centred(Tokens.Type.STRONG, b.y(), b.h()),
            chosen ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY);
        if (!key.isEmpty()) {
            Typeset.drawRight(painter, Tokens.Type.MICRO, key, b.right(),
                Typeset.centred(Tokens.Type.MICRO, b.y(), b.h()), Tokens.Color.INK_TERTIARY);
        }
        ring(painter, state, Tokens.Radius.NONE);
    }

    @Override
    protected void act() {
        action.run();
    }
}
