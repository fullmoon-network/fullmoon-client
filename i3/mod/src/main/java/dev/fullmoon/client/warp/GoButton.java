package dev.fullmoon.client.warp;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chrome;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

/**
 * The one loud action on the route screen: {@code 이동} with the Enter keycap inside it, gold
 * while it can go and forty percent of itself while it cannot. Loading trades the label for
 * nothing: the status line under it already says the server is being waited on.
 */
final class GoButton extends Widget {
    private static final float DIMMED = 0.4f;
    private static final String KEY = "Enter";

    private final Runnable action;

    GoButton(String label, Runnable action) {
        super(Voice.LOUD, label);
        this.action = action;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        boolean dead = state == State.DISABLED || state == State.LOADING;
        Chrome chrome = voice().chrome(dead ? State.REST : state);
        Chrome was = voice().chrome(dead || before() == State.DISABLED || before() == State.LOADING ? State.REST : before());
        float t = settle();
        float opacity = painter.opacity();
        painter.opacity(opacity * (dead ? DIMMED : 1.0f));
        painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.NONE, Rgb.mix(was.fill(), chrome.fill(), t));
        int capW = Glass.keycapWidth(KEY);
        int labelW = Typeset.width(Tokens.Type.STRONG, label());
        int x = b.x() + (b.w() - labelW - Tokens.Space.BASE - capW) / 2 + Math.round(nudgeOffset());
        Typeset.draw(painter, Tokens.Type.STRONG, label(), x, Typeset.centred(Tokens.Type.STRONG, b.y(), b.h()),
            chrome.ink());
        Glass.keycap(painter, x + labelW + Tokens.Space.BASE, b.midY() - (Tokens.Size.KEYCAP - 1) / 2, KEY,
            chrome.ink(), Rgb.alpha(chrome.ink(), 0.45f), 0);
        painter.opacity(opacity);
        ring(painter, state, Tokens.Radius.NONE);
    }

    @Override
    protected void act() {
        action.run();
    }
}
