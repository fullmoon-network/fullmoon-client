package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

/**
 * A labelled action.
 *
 * <p>Loading trades the label for {@link Dots} rather than dimming it, because dimmed ink is what
 * disabled already looks like and a still frame has to be able to tell the two apart.
 */
public final class Button extends Widget {
    public static final int HEIGHT = Tokens.Space.SECTION;

    private final Runnable action;

    public Button(Voice voice, String label, Runnable action) {
        super(voice, label);
        this.action = action;
    }

    /** The narrowest this button reads at. Layout may give it more, never less. */
    public int measure() {
        return Math.max(HEIGHT * 2,
            Typeset.width(Tokens.Type.STRONG, label()) + Tokens.Space.SECTION);
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        Chrome was = voice().chrome(before());
        float t = settle();
        painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.MD, Rgb.mix(was.fill(), chrome.fill(), t));
        painter.border(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.MD, Tokens.Stroke.HAIR,
            Rgb.mix(was.line(), chrome.line(), t));
        ring(painter, state, Tokens.Radius.MD);

        if (state == State.LOADING) {
            Dots.draw(painter, b.midX(), b.midY(), chrome.ink());
            return;
        }
        Typeset.drawCentered(painter, Tokens.Type.STRONG, label(), b.midX(),
            Typeset.centred(Tokens.Type.STRONG, b.y(), b.h()), chrome.ink());
    }

    @Override
    protected void act() {
        UiSounds.play(UiSounds.Cue.CONFIRM);
        action.run();
    }
}
