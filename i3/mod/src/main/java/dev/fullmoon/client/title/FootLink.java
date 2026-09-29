package dev.fullmoon.client.title;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

/**
 * One word in the title's foot line that opens a screen: body face, tertiary ink, brightening
 * to primary under the pointer or the keyboard. Its box is the word; its reach is a little more,
 * because a nine-pixel word is a small thing to have to land on.
 */
final class FootLink extends Widget {
    private static final int REACH = Tokens.Space.SNUG;

    private final Runnable action;

    FootLink(String label, Runnable action) {
        super(Voice.QUIET, label);
        this.action = action;
    }

    /** The width the word takes in the foot line. */
    int width() {
        return Typeset.width(Tokens.Type.BODY, label());
    }

    @Override
    protected Box reach() {
        return bounds().inset(-REACH);
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        boolean lit = state == State.HOVER || state == State.ACTIVE
            || state == State.FOCUS || state == State.FOCUS_VISIBLE;
        Typeset.draw(painter, Tokens.Type.BODY, label(), b.x(), Typeset.centred(Tokens.Type.BODY, b.y(), b.h()),
            lit ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY);
        ring(painter, state, Tokens.Radius.NONE);
    }

    @Override
    protected void act() {
        UiSounds.play(UiSounds.Cue.CONFIRM);
        action.run();
    }
}
