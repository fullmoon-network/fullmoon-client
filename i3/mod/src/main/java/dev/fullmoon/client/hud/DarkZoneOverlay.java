package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

/** Persistent strip while the server says the player is inside a dark zone. */
final class DarkZoneOverlay {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 36;

    private DarkZoneOverlay() {}

    static void draw(Painter painter, long now) {
        if (!FullmoonChannel.darkZone()) {
            return;
        }
        int width = Math.min(WIDTH, painter.width() - Tokens.Space.SECTION * 2);
        int x = (painter.width() - width) / 2;
        int y = Tokens.Space.LOOSE;
        if (FullmoonChannel.notice(now).isPresent()) {
            y += 44 + Tokens.Space.COZY;
        }

        painter.fill(x, y, width, HEIGHT, Tokens.Radius.SM,
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.90f));
        painter.border(x, y, width, HEIGHT, Tokens.Radius.SM,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
        painter.fill(x, y, Tokens.Stroke.FOCUS, HEIGHT, Tokens.Radius.NONE, Tokens.Color.ACCENT);

        int textX = x + Tokens.Space.LOOSE;
        int textWidth = width - Tokens.Space.SECTION;
        String title = Typeset.fittingPrefix(Tokens.Type.STRONG, "다크존", textWidth);
        String body = Typeset.fittingPrefix(
            Tokens.Type.BODY, "PVP가 열려 있고, 블록은 캘 수 없어요.", textWidth);
        Typeset.draw(painter, Tokens.Type.STRONG, title,
            textX, y + Tokens.Space.TIGHT, Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.BODY, body,
            textX, y + Tokens.Space.LOOSE, Tokens.Color.INK_SECONDARY);
    }
}
