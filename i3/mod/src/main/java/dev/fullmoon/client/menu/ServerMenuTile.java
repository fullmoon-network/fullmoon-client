package dev.fullmoon.client.menu;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Palace;
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
        boolean chosen = state == State.HOVER || state == State.ACTIVE || state == State.FOCUS_VISIBLE;
        int top = switch (state) {
            case HOVER, FOCUS_VISIBLE -> Tokens.Color.ACCENT_WASH;
            case ACTIVE -> Tokens.Color.SURFACE_OVERLAY;
            case LOADING -> Tokens.Color.SURFACE_SUNKEN;
            default -> Tokens.Color.SURFACE_RAISED;
        };
        int bottom = state == State.LOADING ? Tokens.Color.SURFACE_SUNKEN : Tokens.Color.SURFACE_BASE;
        int line = switch (state) {
            case HOVER, ACTIVE, FOCUS_VISIBLE -> Tokens.Color.ACCENT;
            case LOADING -> Tokens.Color.STATUS_WARN;
            default -> Tokens.Color.LINE_GILT_FAINT;
        };

        painter.fillGradient(box.x(), box.y(), box.w(), box.h(), top, bottom);
        painter.border(box.x(), box.y(), box.w(), box.h(), Tokens.Radius.NONE, Tokens.Stroke.HAIR, line);
        if (chosen) {
            Palace.marker(painter, box.x(), box.y(), true);
        }
        ring(painter, state, Tokens.Radius.NONE);

        int wellX = box.x() + Tokens.Space.COZY;
        int well = Math.min(ICON_WELL, box.h() - Tokens.Space.TIGHT);
        int left;
        if (well >= 12) {
            // A chip disc, not a square well: the mark sits on the table.
            float cx = wellX + well / 2f;
            float cy = box.midY();
            painter.dot(cx, cy, well / 2f, Tokens.Color.SURFACE_SUNKEN);
            painter.ring(cx, cy, well / 2f - Tokens.Stroke.HAIR / 2f,
                Tokens.Stroke.HAIR, chosen ? Tokens.Color.ACCENT : Tokens.Color.LINE_GILT);
            entry.drawIcon(painter, wellX, (int) (cy - well / 2f), well);
            left = wellX + well + Tokens.Space.COZY;
        } else {
            left = wellX;
        }
        int right = box.right() - Tokens.Space.COZY;
        if (item().chance().isPresent()) {
            right = chance(painter, right, box, (float) item().chance().getAsDouble());
        }
        int countSpace = item().count() > 1 ? Tokens.Space.SECTION : 0;
        int textWidth = Math.max(0, right - countSpace - left);
        painter.pushClip(left, box.y(), textWidth, box.h());
        int labelY = entry.details().isEmpty() || box.h() < 42
            ? Typeset.centred(Tokens.Type.BODY_STRONG, box.y(), box.h())
            : box.midY() - Tokens.Type.BODY_STRONG.leading();
        Typeset.draw(painter, Tokens.Type.BODY_STRONG,
            Typeset.ellipsized(Tokens.Type.BODY_STRONG, entry.label(), textWidth), left, labelY,
            Tokens.Color.INK_PRIMARY);
        if (!entry.details().isEmpty() && box.h() >= 42) {
            Typeset.draw(painter, Tokens.Type.LABEL,
                Typeset.ellipsized(Tokens.Type.LABEL, entry.details().getFirst(), textWidth), left,
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

    /**
     * The game's win chance as a moon filled that far, with the figure under it. Returns the
     * left edge it took, so the label clips short of it.
     */
    private static int chance(Painter painter, int right, Box box, float chance) {
        float r = Math.min(7.0f, box.h() / 5.0f);
        String figure = ServerMenuCopy.percent(chance);
        int figureW = Typeset.tabularWidth(Tokens.Type.LABEL, figure);
        int column = Math.max(figureW, Math.round(r * 2));
        float cx = right - column / 2.0f;
        float cy = box.midY() - Tokens.Type.LABEL.leading() / 2.0f;
        painter.moon(cx, cy, r, chance, true, Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        // A sliver of a long shot would vanish into the tile without its rim.
        painter.ring(cx, cy, r + 1.5f, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT_FAINT);
        Typeset.tabular(painter, Tokens.Type.LABEL, figure, Math.round(cx - figureW / 2.0f),
            Math.round(cy + r + Tokens.Space.TIGHT), Tokens.Color.INK_SECONDARY);
        return right - column - Tokens.Space.COZY;
    }

    @Override
    protected void act() {
        action.run();
    }
}
