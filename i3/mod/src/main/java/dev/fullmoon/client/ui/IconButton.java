package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

/**
 * A header control: the way back or the way out, as a drawn glyph with no box around it until
 * the pointer arrives. Back carries the name of where it goes; close carries nothing.
 */
public final class IconButton extends Widget {
    public enum Glyph { BACK, CLOSE }

    public static final int SIZE = 20;
    private static final float GLYPH = 8.0f;

    private final Glyph glyph;
    private final Runnable action;

    public IconButton(Glyph glyph, String label, Runnable action) {
        super(Voice.QUIET, label);
        this.glyph = glyph;
        this.action = action;
    }

    /** The width the control wants: the glyph, and for back the name after it. */
    public int measure() {
        if (glyph == Glyph.CLOSE || label().isEmpty()) {
            return SIZE;
        }
        return SIZE + Typeset.width(Tokens.Type.BODY, label()) + Tokens.Space.SNUG;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        Chrome was = voice().chrome(before());
        float t = settle();
        int ground = Rgb.mix(groundOf(before()), groundOf(state), t);
        if (ground >>> 24 != 0) {
            painter.fill(b.x(), b.y(), SIZE, b.h(), ground);
        }
        int ink = state == State.REST || state == State.FOCUS
            ? Rgb.mix(inkOf(before(), was), Tokens.Color.INK_TERTIARY, t)
            : Rgb.mix(inkOf(before(), was), inkOf(state, chrome), t);
        float cx = b.x() + SIZE / 2.0f + nudgeOffset();
        float cy = b.midY();
        if (glyph == Glyph.CLOSE) {
            Glass.close(painter, cx, cy, GLYPH - 1, ink);
        } else {
            Glass.back(painter, cx - 2, cy, GLYPH, ink);
            if (!label().isEmpty()) {
                Typeset.draw(painter, Tokens.Type.BODY, label(), b.x() + SIZE - Tokens.Space.TIGHT,
                    Typeset.centred(Tokens.Type.BODY, b.y(), b.h()), ink);
            }
        }
        ring(painter, state, Tokens.Radius.NONE);
    }

    private static int groundOf(State state) {
        return switch (state) {
            case HOVER -> Tokens.Color.SURFACE_RAISED;
            case ACTIVE -> Tokens.Color.SURFACE_CONTROL_PRESSED;
            default -> 0;
        };
    }

    private static int inkOf(State state, Chrome chrome) {
        return switch (state) {
            case REST, FOCUS -> Tokens.Color.INK_TERTIARY;
            case HOVER, ACTIVE, FOCUS_VISIBLE -> Tokens.Color.INK_PRIMARY;
            default -> chrome.ink();
        };
    }

    @Override
    protected void act() {
        UiSounds.play(glyph == Glyph.BACK ? UiSounds.Cue.BACK : UiSounds.Cue.CLOSE);
        action.run();
    }
}
