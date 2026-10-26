package dev.fullmoon.client.season;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.SeasonProtocol;
import dev.fullmoon.client.render.Glide;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.render.Spring;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Dots;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.State;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Today's missions, this week's and the season's, as one list the keyboard walks: each row is the
 * mission's item, its name, a bar and the count, and what finishing it is worth. The mission on
 * the HUD wears a small full moon, the same mark the widget leads with; Enter puts a mission there
 * or takes it off.
 */
final class MissionBoard extends Widget {
    interface Listener {
        void picked(SeasonProtocol.Mission mission);

        void moved();

        void refused();
    }

    private static final int PAD = Tokens.Space.LOOSE;
    private static final int ICON = Tokens.Size.ICON;
    private static final int BAR_W = 96;
    private static final int PIP = Tokens.Space.SNUG;
    private static final float DIMMED = 0.42f;
    private static final float MOON_R = 3.5f;
    private static final int PAGE = 5;

    private final Listener listener;
    private final Glide glide = new Glide(Tokens.Spring.GLIDE);
    private final Spring scroll = new Spring(Tokens.Spring.SCROLL);

    private SeasonProtocol.Open pass;
    private List<SeasonProtocol.Mission> missions = List.of();
    private SeasonLayout frame;
    private MissionLayout layout;
    private int cursor;
    private int over = -1;
    private int busy = -1;

    MissionBoard(SeasonProtocol.Open pass, Listener listener) {
        super(Voice.QUIET, "missions");
        this.listener = listener;
        update(pass);
    }

    /** A newer snapshot: the cursor stays on the mission it was on, wherever that mission now is. */
    void update(SeasonProtocol.Open next) {
        String was = current() == null ? "" : current().id();
        pass = next;
        List<SeasonProtocol.Mission> all = new ArrayList<>();
        all.addAll(next.missions().daily());
        all.addAll(next.missions().weekly());
        all.addAll(next.missions().season());
        missions = List.copyOf(all);
        busy = -1;
        cursor = 0;
        for (int i = 0; i < missions.size(); i++) {
            if (missions.get(i).id().equals(was)) {
                cursor = i;
            }
        }
        if (frame != null) {
            layout(frame);
        }
    }

    /** The screen laid itself out, or the snapshot changed how many missions each section holds. */
    void layout(SeasonLayout next) {
        frame = next;
        SeasonProtocol.Missions m = pass.missions();
        layout = next.missions(m.daily().size(), m.weekly().size(), m.season().size());
        place(layout.list());
        scroll.snap(Math.clamp(scroll.target(), 0, layout.maxScroll()));
        if (!missions.isEmpty()) {
            scroll.snap(layout.reveal(cursor, scroll.target()));
            glide.snap(rowBox(cursor));
        }
    }

    SeasonProtocol.Mission current() {
        return missions.isEmpty() ? null : missions.get(Math.clamp(cursor, 0, missions.size() - 1));
    }

    boolean empty() {
        return missions.isEmpty();
    }

    void busy(SeasonProtocol.Mission mission) {
        busy = missions.indexOf(mission);
    }

    private Box rowBox(int row) {
        MissionLayout.Line line = layout.row(row);
        return line == null ? Box.EMPTY : new Box(layout.list().x(), line.y(), layout.list().w(), line.h());
    }

    @Override
    public void draw(Painter painter, State state) {
        if (layout == null) {
            return;
        }
        long now = System.nanoTime();
        scroll.advance(now);
        glide.advance(now);
        if (!hovered()) {
            over = -1;
        }
        Box list = layout.list();
        if (missions.isEmpty()) {
            String why = pass.season().phase() == SeasonProtocol.Phase.UPCOMING
                ? "시즌이 시작되면 미션이 열려요" : "시즌이 끝나서 미션이 닫혔어요";
            Typeset.drawCentered(painter, Tokens.Type.BODY, why, list.midX(),
                Typeset.centred(Tokens.Type.BODY, list.y(), list.h()), Tokens.Color.INK_TERTIARY);
            return;
        }
        int offset = Math.round(scroll.value());
        painter.pushClip(list.x(), list.y(), list.w(), list.h());
        float y = glide.y() - offset;
        painter.fill(glide.x(), y, glide.w(), glide.h(), Tokens.Color.ACCENT_WASH);
        painter.fill(glide.x(), y, Tokens.Stroke.BAR, glide.h(),
            state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT);
        for (MissionLayout.Line line : layout.lines()) {
            int top = line.y() - offset;
            if (top + line.h() < list.y() || top > list.bottom()) {
                continue;
            }
            if (line.caption()) {
                caption(painter, line.section(), new Box(list.x(), top, list.w(), line.h()));
            } else {
                row(painter, line.row(), new Box(list.x(), top, list.w(), line.h()));
            }
        }
        painter.popClip();
        rail(painter);
    }

    private void caption(Painter painter, MissionLayout.Section section, Box b) {
        String name = switch (section) {
            case DAILY -> "오늘";
            case WEEKLY -> "이번 주";
            case SEASON -> "시즌 도전";
        };
        String note = switch (section) {
            case DAILY -> pass.missions().resets().map(r -> r.daily().isEmpty() ? "" : r.daily() + " 뒤 바뀌어요")
                .orElse("");
            case WEEKLY -> pass.missions().resets().map(r -> r.weekly().isEmpty() ? "" : r.weekly() + " 뒤 바뀌어요")
                .orElse("");
            case SEASON -> "시즌 내내 이어져요";
        };
        int textY = Typeset.centred(Tokens.Type.STRONG, b.y() + Tokens.Space.SNUG, b.h() - Tokens.Space.SNUG);
        Typeset.draw(painter, Tokens.Type.STRONG, name, b.x() + PAD, textY, Tokens.Color.INK_SECONDARY);
        if (!note.isEmpty()) {
            Typeset.drawRight(painter, Tokens.Type.BODY, note, b.right() - PAD,
                Typeset.centred(Tokens.Type.BODY, b.y() + Tokens.Space.SNUG, b.h() - Tokens.Space.SNUG),
                Tokens.Color.INK_TERTIARY);
        }
        Glass.hair(painter, b.x() + PAD, b.bottom() - 1, b.w() - PAD * 2);
    }

    private void row(Painter painter, int index, Box b) {
        SeasonProtocol.Mission mission = missions.get(index);
        if (index == over && index != cursor) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
        }
        boolean shut = mission.plusExtra() && !pass.me().plus();
        float dim = shut ? DIMMED : 1.0f;
        int shift = index == cursor ? Math.round(nudgeOffset()) : 0;
        int x = b.x() + PAD + shift;
        int iconY = b.midY() - ICON / 2;
        SeasonItems.draw(painter, SeasonItems.stack(mission.material(), ""), x, iconY, ICON);
        if (shut) {
            painter.fill(x, iconY, ICON, ICON, Rgb.alpha(Tokens.Color.SURFACE_VOID, (1.0f - dim) * 0.86f));
        }
        int textX = x + ICON + Tokens.Space.COZY;
        int right = b.right() - PAD;
        int lineOne = b.y() + Tokens.Space.SNUG;
        int lineTwo = lineOne + Tokens.Type.ROW.leading() + Tokens.Space.TIGHT;

        if (index == busy) {
            Dots.draw(painter, right - Dots.width() / 2.0f, b.midY(), Tokens.Color.INK_TERTIARY);
            right -= Dots.width() + Tokens.Space.COZY;
        } else {
            right = trailing(painter, mission, right, lineOne, dim);
        }
        Typeset.draw(painter, Tokens.Type.ROW,
            Typeset.ellipsized(Tokens.Type.ROW, mission.name(), Math.max(0, right - Tokens.Space.COZY - textX)),
            textX, lineOne, Rgb.alpha(mission.done() ? Tokens.Color.INK_SECONDARY : Tokens.Color.INK_PRIMARY, dim));

        int barY = lineTwo + (Tokens.Type.BODY.leading() - Tokens.Space.SNUG) / 2;
        int barColor = mission.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.MOON_LIT;
        Glass.bar(painter, textX, barY, BAR_W, mission.fraction(), Rgb.alpha(barColor, dim));
        int countX = textX + BAR_W + Tokens.Space.BASE;
        String count = SeasonCopy.count(mission.progress(), mission.goal(), mission.unit());
        countX += Typeset.tabular(painter, Tokens.Type.BODY, count, countX, lineTwo,
            Rgb.alpha(Tokens.Color.INK_TERTIARY, dim));
        if (mission.tiers() > 1) {
            pips(painter, mission, countX + Tokens.Space.BASE, lineTwo + Tokens.Type.BODY.leading() / 2, dim);
        }
    }

    /**
     * The right end of a mission's first line, drawn right to left: the points it is worth, then
     * whichever of done, tracked, Plus and Discord it is. Returns where the name has to stop.
     */
    private int trailing(Painter painter, SeasonProtocol.Mission mission, int right, int y, float dim) {
        String worth = SeasonCopy.reward(mission.xp(), pass.point());
        right -= Typeset.drawRight(painter, Tokens.Type.STRONG, worth, right, y,
            Rgb.alpha(mission.done() ? Tokens.Color.INK_TERTIARY : Tokens.Color.INK_SECONDARY, dim));
        float cy = Typeset.capTop(Tokens.Type.STRONG, y) + Typeset.capHeight(Tokens.Type.STRONG) / 2.0f;
        if (mission.done()) {
            right -= Tokens.Space.COZY;
            right -= Typeset.drawRight(painter, Tokens.Type.STRONG, "완료", right, y, Tokens.Color.STATUS_LIVE);
            right -= Tokens.Space.SNUG + Glass.MARK;
            Glass.doneMark(painter, right + Glass.MARK / 2.0f, cy, Tokens.Color.STATUS_LIVE);
        } else if (mission.tracked()) {
            right -= Tokens.Space.COZY;
            right -= Typeset.drawRight(painter, Tokens.Type.STRONG, "추적 중", right, y, Tokens.Color.MOON_LIT);
            right -= Tokens.Space.SNUG + Math.round(MOON_R * 2);
            painter.moon(right + MOON_R, cy, MOON_R, 1.0f, true, Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        }
        if (mission.plusExtra()) {
            right -= Tokens.Space.COZY;
            right -= Typeset.drawRight(painter, Tokens.Type.BODY, "플러스", right, y, Tokens.Color.CHAT_PURPLE);
            if (!pass.me().plus()) {
                right -= Tokens.Space.SNUG + Glass.MARK;
                Glass.lockMark(painter, right + Glass.MARK / 2.0f, cy, Tokens.Color.CHAT_PURPLE);
            }
        }
        if (mission.discord()) {
            right -= Tokens.Space.COZY;
            right -= Typeset.drawRight(painter, Tokens.Type.BODY, "디스코드", right, y, Tokens.Color.CHAT_BLUE);
        }
        return right;
    }

    /** A season mission's stages as small squares: lit for each one done. */
    private static void pips(Painter painter, SeasonProtocol.Mission mission, int x, int cy, float dim) {
        for (int i = 0; i < mission.tiers(); i++) {
            int px = x + i * (PIP + Tokens.Space.TIGHT);
            if (i < mission.tier()) {
                painter.fill(px, cy - PIP / 2, PIP, PIP, Rgb.alpha(Tokens.Color.MOON_LIT, dim));
            } else {
                painter.border(px, cy - PIP / 2, PIP, PIP, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
                    Rgb.alpha(Tokens.Color.LINE_STRONG, dim));
            }
        }
    }

    private void rail(Painter painter) {
        int max = layout.maxScroll();
        if (max <= 0) {
            return;
        }
        Box list = layout.list();
        int trackH = list.h() - Tokens.Space.COZY * 2;
        int thumb = Math.max(Tokens.Space.LOOSE, trackH * list.h() / layout.contentHeight());
        int y = list.y() + Tokens.Space.COZY + Math.round((trackH - thumb) * (scroll.value() / max));
        int x = list.right() - Tokens.Stroke.BAR - Tokens.Space.TIGHT;
        painter.fill(x, list.y() + Tokens.Space.COZY, Tokens.Stroke.BAR, trackH, Tokens.Color.LINE_HAIRLINE);
        painter.fill(x, y, Tokens.Stroke.BAR, thumb, Tokens.Color.LINE_STRONG);
    }

    @Override
    protected void hovering(double mx, double my) {
        over = layout == null ? -1 : layout.hit(mx, my, scroll.value());
    }

    @Override
    protected boolean press(double mx, double my) {
        int row = layout == null ? -1 : layout.hit(mx, my, scroll.value());
        if (row >= 0) {
            moveTo(row);
            act();
        }
        return false;
    }

    @Override
    protected boolean scroll(double amount) {
        int max = layout == null ? 0 : layout.maxScroll();
        if (max <= 0) {
            return false;
        }
        float was = scroll.target();
        scroll.to(Math.clamp(was - (float) Math.signum(amount) * MissionLayout.ROW_H * 2, 0, max));
        return scroll.target() != was;
    }

    @Override
    protected boolean key(Chord chord) {
        if (missions.isEmpty()) {
            return false;
        }
        if (chord.is(InputConstants.KEY_UP)) {
            moveTo(cursor - 1);
            return true;
        }
        if (chord.is(InputConstants.KEY_DOWN)) {
            moveTo(cursor + 1);
            return true;
        }
        if (chord.is(InputConstants.KEY_PAGEUP)) {
            moveTo(cursor - PAGE);
            return true;
        }
        if (chord.is(InputConstants.KEY_PAGEDOWN)) {
            moveTo(cursor + PAGE);
            return true;
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            moveTo(0);
            return true;
        }
        if (chord.is(InputConstants.KEY_END)) {
            moveTo(missions.size() - 1);
            return true;
        }
        return false;
    }

    private void moveTo(int row) {
        int next = Math.clamp(row, 0, missions.size() - 1);
        if (next == cursor) {
            return;
        }
        cursor = next;
        glide.to(rowBox(cursor));
        scroll.to(layout.reveal(cursor, scroll.target()));
        listener.moved();
    }

    @Override
    protected void act() {
        SeasonProtocol.Mission mission = current();
        if (mission == null) {
            return;
        }
        if (busy >= 0 || mission.done() || !mission.trackable()) {
            nudge();
            listener.refused();
            return;
        }
        listener.picked(mission);
    }
}
