package dev.fullmoon.client.title;

import java.util.function.Supplier;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Dots;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

/**
 * The one loud action on the title screen: straight into the lobby. A gilt plaque with a cut
 * inner line, the serif name on the left and the lobby's live answer on the right, so the player
 * sees whether anyone is there before pressing.
 */
final class LobbyButton extends Widget {
    /** The inner line sits this far inside the plaque's edge. */
    private static final int INSET = 3;

    private final Supplier<String> status;
    private final Supplier<Boolean> reachable;
    private final Runnable action;

    LobbyButton(String label, Supplier<String> status, Supplier<Boolean> reachable, Runnable action) {
        super(Voice.LOUD, label);
        this.status = status;
        this.reachable = reachable;
        this.action = action;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        int ground = state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT;
        painter.fill(b.x(), b.y(), b.w(), b.h(), ground);
        painter.border(b.x() + INSET, b.y() + INSET, b.w() - INSET * 2, b.h() - INSET * 2,
            Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.INK_ON_ACCENT);
        ring(painter, state, Tokens.Radius.NONE);

        if (state == State.LOADING) {
            Dots.draw(painter, b.midX(), b.midY(), Tokens.Color.INK_ON_ACCENT);
            return;
        }
        int pad = Tokens.Space.GUTTER;
        Typeset.draw(painter, Tokens.Type.HEADING, label(), b.x() + pad,
            Typeset.centred(Tokens.Type.HEADING, b.y(), b.h()), Tokens.Color.INK_ON_ACCENT);

        String text = status.get();
        int textY = Typeset.centred(Tokens.Type.LABEL, b.y(), b.h());
        int right = b.right() - pad;
        int textW = Typeset.width(Tokens.Type.LABEL, text);
        Typeset.draw(painter, Tokens.Type.LABEL, text, right - textW, textY, Tokens.Color.INK_ON_ACCENT);
        painter.dot(right - textW - Tokens.Space.COZY, b.midY(), Tokens.Space.TIGHT + 0.5f,
            reachable.get() ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
    }

    @Override
    protected void act() {
        action.run();
    }
}
