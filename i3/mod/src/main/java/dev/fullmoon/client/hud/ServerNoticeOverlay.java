package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

final class ServerNoticeOverlay {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 44;

    private ServerNoticeOverlay() {}

    static void draw(Painter painter, long now) {
        FullmoonChannel.notice(now).ifPresent(notice -> draw(painter, notice));
    }

    private static void draw(Painter painter, BridgeState.ActiveNotice notice) {
        int width = Math.min(WIDTH, painter.width() - Tokens.Space.SECTION * 2);
        int x = (painter.width() - width) / 2;
        int y = Tokens.Space.LOOSE;
        int rule = severityColor(notice.severity());

        painter.fill(x, y, width, HEIGHT, Tokens.Radius.SM,
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.90f));
        painter.border(x, y, width, HEIGHT, Tokens.Radius.SM,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
        painter.fill(x, y, Tokens.Stroke.FOCUS, HEIGHT, Tokens.Radius.NONE, rule);

        int textX = x + Tokens.Space.LOOSE;
        int textWidth = width - Tokens.Space.SECTION;
        String title = Typeset.fittingPrefix(Tokens.Type.STRONG, notice.title(), textWidth);
        String body = Typeset.fittingPrefix(Tokens.Type.BODY, notice.body(), textWidth);
        Typeset.draw(painter, Tokens.Type.STRONG, title,
            textX, y + Tokens.Space.COZY, Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.BODY, body,
            textX, y + Tokens.Space.SECTION, Tokens.Color.INK_SECONDARY);
    }

    private static int severityColor(BridgeProtocol.Severity severity) {
        return switch (severity) {
            case INFO -> Tokens.Color.ACCENT;
            case SUCCESS -> Tokens.Color.STATUS_LIVE;
            case WARNING -> Tokens.Color.STATUS_WARN;
            case ERROR -> Tokens.Color.STATUS_DANGER;
        };
    }
}
