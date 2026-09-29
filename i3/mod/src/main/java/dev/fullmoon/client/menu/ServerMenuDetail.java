package dev.fullmoon.client.menu;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Dots;
import dev.fullmoon.client.ui.Glass;

/**
 * The detail of the chosen item: its name, its figures, its lore, and what a click does — the
 * server's footer conventions drawn as what they mean. A column beside a list, or a strip under
 * two columns of rows.
 */
final class ServerMenuDetail {
    private static final int ICON = 20;
    private static final int MOON_R = 14;
    private static final float SHIFT_ROW = 0.75f;
    private static final float DEAD_ROW = 0.35f;
    private static final String WAITING = "서버 응답을 기다려요";
    private static final String NOTHING = "고를 수 있는 항목이 없어요";

    private ServerMenuDetail() {}

    /**
     * The column beside a list or a grid. {@code extras} are lines about this item that other
     * items of the menu carry — the house's share of this game, from the 하우스 몫 fact — so the
     * column says something the row does not; the row's own description is repeated only when
     * there is nothing else to say.
     */
    static void column(Painter painter, Box d, ServerMenuEntry entry, List<MenuLore.Fact> extras, boolean busy) {
        if (entry == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, NOTHING, d.x(), d.y(), d.w(), 2,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        MenuLore.Parsed lore = entry.lore();
        float dim = entry.blocked() ? 0.6f : 1.0f;
        int y = d.y();
        entry.drawIcon(painter, d.x(), y, ICON);
        Typeset.draw(painter, Tokens.Type.TITLE,
            Typeset.ellipsized(Tokens.Type.TITLE, entry.label(), d.w() - ICON - Tokens.Space.COZY),
            d.x() + ICON + Tokens.Space.COZY, Typeset.centred(Tokens.Type.TITLE, y, ICON),
            Rgb.alpha(Tokens.Color.INK_PRIMARY, dim));
        y += ICON + Tokens.Space.LOOSE;

        List<MenuLore.Fact> figures = entry.figures();
        if (!figures.isEmpty()) {
            y = figures(painter, d.x(), y, figures, entry, d.w()) + Tokens.Space.LOOSE;
        }

        int footTop = foot(painter, d, entry, busy);
        int budget = Math.max(0, (footTop - Tokens.Space.COZY - y) / Tokens.Type.BODY.leading());
        List<String> prose = lore.prose();
        List<MenuLore.Fact> others = new java.util.ArrayList<>(entry.otherFacts());
        others.addAll(extras);
        boolean saysMore = !figures.isEmpty() || prose.size() > 1 || !others.isEmpty();
        if (saysMore && !prose.isEmpty() && prose.getFirst().equals(entry.description())) {
            prose = prose.subList(1, prose.size());
        }
        for (String line : prose) {
            if (budget <= 0) {
                break;
            }
            List<String> lines = Typeset.lines(Tokens.Type.BODY, line, d.w(), Math.min(budget, 3));
            for (String piece : lines) {
                Typeset.draw(painter, Tokens.Type.BODY, piece, d.x(), y, Tokens.Color.INK_SECONDARY);
                y += Tokens.Type.BODY.leading();
            }
            budget -= lines.size();
            y += Tokens.Space.TIGHT + 1;
        }
        for (MenuLore.Fact fact : others) {
            if (budget <= 0) {
                break;
            }
            Typeset.draw(painter, Tokens.Type.BODY,
                Typeset.ellipsized(Tokens.Type.BODY, fact.key() + MenuLore.SEPARATOR + fact.value(), d.w()),
                d.x(), y, Tokens.Color.INK_SECONDARY);
            y += Tokens.Type.BODY.leading() + Tokens.Space.TIGHT + 1;
            budget--;
        }
        for (MenuLore.Bar bar : lore.bars()) {
            if (budget <= 0) {
                break;
            }
            Glass.bar(painter, d.x(), y + Tokens.Space.SNUG, Math.min(96, d.w()), bar.fraction(),
                bar.fraction() >= 1.0f ? Tokens.Color.STATUS_WARN : Tokens.Color.ACCENT);
            y += Tokens.Type.BODY.leading();
            budget--;
        }
    }

    /**
     * The large figures: the moon with the win chance beside it when the server sent one, then
     * key over value for each figure. Returns the y under them.
     */
    private static int figures(Painter painter, int x, int y, List<MenuLore.Fact> figures,
            ServerMenuEntry entry, int width) {
        int cursor = x;
        int block = Tokens.Type.BODY.leading() + Tokens.Type.FIGURE.leading();
        int top = y;
        if (entry.item().chance().isPresent()) {
            painter.moon(x + MOON_R, y + block / 2.0f, MOON_R, (float) entry.item().chance().getAsDouble(), true,
                Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
            cursor += MOON_R * 2 + Tokens.Space.LOOSE;
        }
        boolean first = true;
        for (MenuLore.Fact figure : figures) {
            int keyW = Typeset.width(Tokens.Type.BODY, figure.key());
            int valueW = Typeset.width(Tokens.Type.FIGURE, figure.value());
            int w = Math.max(keyW, valueW);
            if (cursor + w > x + width && cursor > x) {
                break;
            }
            Typeset.draw(painter, Tokens.Type.BODY, figure.key(), cursor, top, Tokens.Color.INK_TERTIARY);
            int ink = first && entry.item().chance().isPresent() ? Tokens.Color.ACCENT : Tokens.Color.INK_PRIMARY;
            // A still figure keeps the face's own spacing; the tabular cell is for values that change.
            Typeset.draw(painter, Tokens.Type.FIGURE, figure.value(), cursor,
                top + Tokens.Type.BODY.leading(), ink);
            cursor += w + Tokens.Space.LOOSE;
            first = false;
        }
        return top + block;
    }

    /**
     * The foot of the column, bottom-aligned: the ✖ reason, then the action rows, then the ✔
     * state and the typed command. Returns the y the foot starts at, so the lore knows where to stop.
     */
    private static int foot(Painter painter, Box d, ServerMenuEntry entry, boolean busy) {
        MenuLore.Parsed lore = entry.lore();
        int bottom = d.bottom() - Tokens.Space.COZY;
        int y = bottom;
        if (busy) {
            y -= Tokens.Type.BODY.leading();
            Dots.draw(painter, d.x() + Dots.width() / 2.0f, y + Tokens.Type.BODY.leading() / 2.0f,
                Tokens.Color.INK_TERTIARY);
            Typeset.draw(painter, Tokens.Type.BODY, WAITING, d.x() + Dots.width() + Tokens.Space.COZY, y,
                Tokens.Color.INK_TERTIARY);
            return y - Tokens.Space.COZY;
        }
        for (int i = lore.typed().size() - 1; i >= 0; i--) {
            y -= Tokens.Type.BODY.leading();
            typed(painter, d.x(), y, d.w(), lore.typed().get(i));
            y -= Tokens.Space.SNUG;
        }
        for (int i = lore.done().size() - 1; i >= 0; i--) {
            y -= Tokens.Type.STRONG.leading();
            Glass.markedLine(painter, d.x(), y, lore.done().get(i), Tokens.Color.STATUS_LIVE, false);
            y -= Tokens.Space.SNUG;
        }
        List<MenuLore.Action> actions = lore.actions();
        for (int i = actions.size() - 1; i >= 0; i--) {
            MenuLore.Action action = actions.get(i);
            y -= Tokens.Size.ACTION_ROW;
            boolean shift = action.click() == dev.fullmoon.client.network.MenuProtocol.Click.SHIFT_LEFT;
            float alpha = entry.blocked() ? DEAD_ROW : (shift ? SHIFT_ROW : 1.0f);
            Glass.actionRow(painter, new Box(d.x(), y, d.w(), Tokens.Size.ACTION_ROW), shift, action.text(), alpha);
            y -= Tokens.Space.SNUG;
        }
        for (int i = lore.blocked().size() - 1; i >= 0; i--) {
            y -= Tokens.Type.STRONG.leading();
            Glass.markedLine(painter, d.x(), y, lore.blocked().get(i), Tokens.Color.STATUS_DANGER, true);
            y -= Tokens.Space.SNUG;
        }
        return y == bottom ? bottom : y;
    }

    /** {@code 채팅에 입력: [/길드 가입 <이름>]}: the command as a chip after the body words. */
    private static void typed(Painter painter, int x, int y, int width, String typed) {
        int cursor = x;
        cursor += Typeset.draw(painter, Tokens.Type.BODY, "채팅에 입력:", cursor, y, Tokens.Color.INK_SECONDARY)
            + Tokens.Space.SNUG + 1;
        String command = Typeset.ellipsized(Tokens.Type.STRONG, MenuLore.command(typed),
            Math.max(0, x + width - cursor - Tokens.Space.COZY));
        Glass.codeChip(painter, cursor, y, command);
    }

    /**
     * The strip under two columns of rows: the chosen item's name and figures on the left, the
     * limit bar of the menu's own clock under them, and on the right what a click does.
     */
    static void strip(Painter painter, Box d, ServerMenuEntry entry, ServerMenuEntry clock, boolean busy) {
        Glass.hair(painter, d.x(), d.y(), d.w());
        int left = d.x() + Tokens.Space.LOOSE;
        int leftW = Tokens.Size.DETAIL;
        int y = d.y() + Tokens.Space.COZY + Tokens.Space.TIGHT;
        int divider = left + leftW + Tokens.Space.GUTTER;
        Glass.vhair(painter, divider, y, d.h() - Tokens.Space.COZY * 2 - Tokens.Space.TIGHT);
        if (entry == null) {
            Typeset.draw(painter, Tokens.Type.BODY, NOTHING, left, y, Tokens.Color.INK_TERTIARY);
            return;
        }
        float dim = entry.blocked() ? 0.6f : 1.0f;
        entry.drawIcon(painter, left, y, ICON);
        Typeset.draw(painter, Tokens.Type.TITLE,
            Typeset.ellipsized(Tokens.Type.TITLE, entry.label(), leftW - ICON - Tokens.Space.COZY),
            left + ICON + Tokens.Space.COZY, Typeset.centred(Tokens.Type.TITLE, y, ICON),
            Rgb.alpha(Tokens.Color.INK_PRIMARY, dim));
        y += ICON + Tokens.Space.SNUG;
        List<MenuLore.Fact> figures = entry.figures();
        String held = entry.count();
        if (!held.isEmpty() && figures.size() < 2) {
            figures = new java.util.ArrayList<>(figures);
            figures.add(new MenuLore.Fact("가진 것", held));
        }
        if (!figures.isEmpty()) {
            y = figures(painter, left, y, figures, entry, leftW) + Tokens.Space.BASE;
        }
        if (clock != null && !clock.lore().bars().isEmpty()) {
            MenuLore.Bar bar = clock.lore().bars().getFirst();
            int barW = 96;
            Glass.bar(painter, left, y + Tokens.Space.SNUG, barW, bar.fraction(),
                bar.fraction() <= 0.0f ? Tokens.Color.STATUS_WARN : Tokens.Color.ACCENT);
            String note = clock.factValue();
            Typeset.draw(painter, Tokens.Type.STRONG, Typeset.ellipsized(Tokens.Type.STRONG, note, leftW - barW - Tokens.Space.COZY),
                left + barW + Tokens.Space.COZY, y, bar.fraction() <= 0.0f ? Tokens.Color.STATUS_WARN : Tokens.Color.INK_SECONDARY);
        }

        int x = divider + Tokens.Space.GUTTER;
        int w = d.right() - Tokens.Space.LOOSE - x;
        y = d.y() + Tokens.Space.COZY + Tokens.Space.TIGHT;
        MenuLore.Parsed lore = entry.lore();
        if (busy) {
            Dots.draw(painter, x + Dots.width() / 2.0f, y + Tokens.Type.BODY.leading() / 2.0f, Tokens.Color.INK_TERTIARY);
            Typeset.draw(painter, Tokens.Type.BODY, WAITING, x + Dots.width() + Tokens.Space.COZY, y,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        for (String reason : lore.blocked()) {
            Glass.markedLine(painter, x, y, reason, Tokens.Color.STATUS_DANGER, true);
            y += Tokens.Type.STRONG.leading() + Tokens.Space.SNUG;
        }
        for (MenuLore.Action action : lore.actions()) {
            boolean shift = action.click() == dev.fullmoon.client.network.MenuProtocol.Click.SHIFT_LEFT;
            float alpha = entry.blocked() ? DEAD_ROW : (shift ? SHIFT_ROW : 1.0f);
            Glass.actionRow(painter, new Box(x, y, w, Tokens.Size.ACTION_ROW), shift, action.text(), alpha);
            y += Tokens.Size.ACTION_ROW + Tokens.Space.SNUG;
        }
        for (String done : lore.done()) {
            Glass.markedLine(painter, x, y, done, Tokens.Color.STATUS_LIVE, false);
            y += Tokens.Type.STRONG.leading() + Tokens.Space.SNUG;
        }
        for (String typed : lore.typed()) {
            typed(painter, x, y, w, typed);
            y += Tokens.Type.BODY.leading() + Tokens.Space.SNUG;
        }
        if (lore.actions().isEmpty() && lore.blocked().isEmpty()) {
            for (String line : lore.prose()) {
                Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, line, w), x, y,
                    Tokens.Color.INK_SECONDARY);
                y += Tokens.Type.BODY.leading();
            }
        }
    }
}
