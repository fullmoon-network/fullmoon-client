package dev.fullmoon.client.menu;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

final class ServerMenuTile extends Widget {
    private static final int ICON_WELL = 28;

    private final ServerMenuEntry entry;
    private final Runnable action;

    ServerMenuTile(MenuProtocol.Item item, Runnable action) {
        this(new ServerMenuEntry(item), action);
    }

    ServerMenuTile(ServerMenuEntry entry, Runnable action) {
        super(Voice.QUIET, entry.label());
        this.entry = entry;
        this.action = action;
    }

    MenuProtocol.Item item() {
        return entry.item();
    }

    ServerMenuEntry entry() {
        return entry;
    }

    @Override
    public void draw(Painter painter, State state) {
        Box box = bounds();
        int ground = switch (state) {
            case HOVER, FOCUS_VISIBLE -> Tokens.Color.ACCENT_WASH;
            case ACTIVE -> Tokens.Color.SURFACE_OVERLAY;
            case LOADING -> Tokens.Color.SURFACE_SUNKEN;
            default -> Tokens.Color.SURFACE_RAISED;
        };
        int line = switch (state) {
            case HOVER, ACTIVE, FOCUS_VISIBLE -> Tokens.Color.ACCENT;
            case LOADING -> Tokens.Color.STATUS_WARN;
            default -> Tokens.Color.LINE_HAIRLINE;
        };

        painter.fill(box.x(), box.y(), box.w(), box.h(), Tokens.Radius.MD, ground);
        painter.border(box.x(), box.y(), box.w(), box.h(), Tokens.Radius.MD,
            Tokens.Stroke.HAIR, line);
        ring(painter, state, Tokens.Radius.MD);

        int wellX = box.x() + Tokens.Space.COZY;
        int well = Math.min(ICON_WELL, box.h() - Tokens.Space.TIGHT);
        int left;
        if (well >= 12) {
            // A chip disc, not a square well: the mark sits on the table.
            float cx = wellX + well / 2f;
            float cy = box.midY();
            painter.dot(cx, cy, well / 2f, Tokens.Color.SURFACE_SUNKEN);
            painter.ring(cx, cy, well / 2f - Tokens.Stroke.HAIR / 2f,
                Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
            entry.drawIcon(painter, wellX, (int) (cy - well / 2f), well);
            left = wellX + well + Tokens.Space.COZY;
        } else {
            left = wellX;
        }
        int countSpace = item().count() > 1 ? Tokens.Space.SECTION : 0;
        int textWidth = Math.max(0, box.right() - Tokens.Space.COZY - countSpace - left);
        painter.pushClip(left, box.y(), textWidth, box.h());
        int labelY = entry.details().isEmpty() || box.h() < 42
            ? Typeset.centred(Tokens.Type.BODY_STRONG, box.y(), box.h())
            : box.midY() - Tokens.Type.BODY_STRONG.leading();
        Typeset.draw(painter, Tokens.Type.BODY_STRONG, entry.label(), left, labelY,
            Tokens.Color.INK_PRIMARY);
        if (!entry.details().isEmpty() && box.h() >= 42) {
            Typeset.draw(painter, Tokens.Type.LABEL, entry.details().getFirst(), left,
                labelY + Tokens.Type.BODY_STRONG.leading() + Tokens.Space.TIGHT,
                Tokens.Color.INK_TERTIARY);
        }
        painter.popClip();

        if (item().count() > 1) {
            Typeset.tabularRight(painter, Tokens.Type.LABEL, Integer.toString(item().count()),
                box.right() - Tokens.Space.COZY,
                box.bottom() - Tokens.Type.LABEL.leading() - Tokens.Space.SNUG,
                Tokens.Color.INK_TERTIARY);
        }
    }

    @Override
    protected void act() {
        action.run();
    }
}
