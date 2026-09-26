package dev.fullmoon.client.ui;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/** Shared header, connection badge, and category localization for Fullmoon hub surfaces. */
public final class HubChrome {
    private HubChrome() {}

    public static int mastheadHeight(boolean compact) {
        return (compact ? Tokens.Type.TITLE.leading() : Tokens.Type.DISPLAY.leading())
            + Tokens.Space.COZY;
    }

    public static void masthead(Painter painter, Box content, boolean compact) {
        Tokens.Type.Role brand = compact ? Tokens.Type.TITLE : Tokens.Type.DISPLAY;
        int y = content.y();
        Palace.brand(painter, brand, content.x(), y);
        connection(painter, content, y + Tokens.Space.TIGHT);
    }

    public static void connection(Painter painter, Box content, int y) {
        Minecraft client = Minecraft.getInstance();
        ServerData server = client.getCurrentServer();
        boolean live = client.level != null && server != null;
        String status = live
            ? (content.w() < 480 ? I18n.get("fullmoon.settings.server.live")
                                 : I18n.get("fullmoon.settings.server.connected", server.ip))
            : I18n.get("fullmoon.settings.server.disconnected");

        int width = Typeset.width(Tokens.Type.LABEL, status);
        int textX = content.right() - width;
        painter.dot(textX - Tokens.Space.COZY, y + Typeset.capHeight(Tokens.Type.LABEL) / 2.0f,
            Tokens.Space.SNUG, live ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
        Typeset.draw(painter, Tokens.Type.LABEL, status, textX, y, Tokens.Color.INK_TERTIARY);
    }

    public static String categoryLabel(KeyMapping km) {
        Identifier id = km.getCategory().id();
        String langKey = id.toLanguageKey("key.category");
        if (I18n.exists(langKey)) {
            return I18n.get(langKey);
        }
        String path = id.getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1);
    }
}
