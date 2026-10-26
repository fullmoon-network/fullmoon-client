package dev.fullmoon.client.season;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chrome;
import dev.fullmoon.client.ui.Dots;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

/**
 * A page's one loud action — take everything, buy Plus — whose words follow the snapshot: the
 * same button says {@code 플러스 사기} and then {@code 확인하고 사기} when the server asks for the
 * second press. Dead, it is forty percent of itself rather than grey, like the route screen's go.
 */
final class PassButton extends Widget {
    private static final float DIMMED = 0.4f;

    private final String key;
    private final Runnable action;
    private String text;

    PassButton(String name, String key, Runnable action) {
        super(Voice.LOUD, name);
        this.key = key;
        this.action = action;
        this.text = name;
    }

    void text(String value) {
        text = value;
    }

    String text() {
        return text;
    }

    int measure() {
        int w = Typeset.width(Tokens.Type.STRONG, text) + Tokens.Space.SECTION;
        return key.isEmpty() ? w : w + Glass.keycapWidth(key) + Tokens.Space.BASE;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        if (b.empty()) {
            return;
        }
        boolean dead = state == State.DISABLED;
        Chrome chrome = voice().chrome(dead ? State.REST : state);
        Chrome was = voice().chrome(dead || before() == State.DISABLED ? State.REST : before());
        float t = settle();
        float opacity = painter.opacity();
        painter.opacity(opacity * (dead ? DIMMED : 1.0f));
        painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.NONE, Rgb.mix(was.fill(), chrome.fill(), t));
        if (state == State.LOADING) {
            Dots.draw(painter, b.midX(), b.midY(), chrome.ink());
        } else {
            int capW = key.isEmpty() ? 0 : Glass.keycapWidth(key) + Tokens.Space.BASE;
            int labelW = Typeset.width(Tokens.Type.STRONG, text);
            int x = b.x() + (b.w() - labelW - capW) / 2 + Math.round(nudgeOffset());
            Typeset.draw(painter, Tokens.Type.STRONG, text, x, Typeset.centred(Tokens.Type.STRONG, b.y(), b.h()),
                chrome.ink());
            if (!key.isEmpty()) {
                Glass.keycap(painter, x + labelW + Tokens.Space.BASE, b.midY() - (Tokens.Size.KEYCAP - 1) / 2, key,
                    chrome.ink(), Rgb.alpha(chrome.ink(), 0.45f), 0);
            }
        }
        painter.opacity(opacity);
        ring(painter, state, Tokens.Radius.NONE);
    }

    @Override
    protected void act() {
        action.run();
    }
}
