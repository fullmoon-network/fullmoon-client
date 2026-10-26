package dev.fullmoon.client.season;

import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.hud.ScoreboardSidebar;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.SeasonProtocol;
import dev.fullmoon.client.network.SeasonProtocol.Track;
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

import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The reward track: a column per level, the free cell over the level's node and the Plus cell
 * under it, scrolled sideways under a fixed gutter that names the two rows.
 *
 * <p>Gold on this page means one thing — a reward that can be taken now — so a cell that can be
 * claimed wears a gold edge and a gold point, and the cursor is a ring of ink instead. The rail
 * between the rows is the season told in moons: a level reached is a full moon, the next one
 * waxes as the points toward it come in, and the rest are dark. The current level's column is lit
 * from caption to Plus cell and named {@code 지금}.
 *
 * <p>One keyboard stop, arrows inside it: left and right walk the levels, up and down cross
 * between the free and Plus rows. The pointer lifts a cell; a click moves the cursor there and
 * acts, exactly as Enter does.
 */
final class TrackBoard extends Widget {
    interface Listener {
        void claim(TrackLayout.Cell cell, SeasonProtocol.Level level);

        /** A Plus cell was asked for without Plus: the page that sells it is where Enter goes. */
        void wantsPlus();

        void moved();

        void refused();
    }

    private static final int ICON = 32;
    private static final int NODE_R = 4;
    private static final int RING_R = 7;
    private static final int RING_GAP = Tokens.Space.TIGHT;
    private static final float CLAIMED_WASH = 0.55f;
    private static final float LOCKED_WASH = 0.4f;
    private static final int PAGE = 5;

    private final Listener listener;
    private final Glide ring = new Glide(Tokens.Spring.GLIDE);
    private final Spring scroll = new Spring(Tokens.Spring.SCROLL);

    private SeasonProtocol.Open pass;
    private TrackLayout layout;
    private ItemStack[] freeIcons = new ItemStack[0];
    private ItemStack[] plusIcons = new ItemStack[0];
    private TrackLayout.Cell cursor;
    private TrackLayout.Cell over;
    private TrackLayout.Cell busy;
    private boolean placed;

    TrackBoard(SeasonProtocol.Open pass, Listener listener) {
        super(Voice.QUIET, "track");
        this.listener = listener;
        update(pass);
    }

    /** A newer snapshot: the cursor stays on the level and row it was on, and the scroll where it was. */
    void update(SeasonProtocol.Open next) {
        int level = cursor == null || pass == null ? -1 : pass.levels().get(cursor.column()).level();
        Track row = cursor == null ? Track.FREE : cursor.track();
        pass = next;
        freeIcons = new ItemStack[next.levels().size()];
        plusIcons = new ItemStack[next.levels().size()];
        busy = null;
        int column = level < 0 ? -1 : next.indexOf(level);
        if (column >= 0 && reward(column, row).isPresent()) {
            cursor = new TrackLayout.Cell(column, row);
        } else if (column >= 0 && reward(column, other(row)).isPresent()) {
            cursor = new TrackLayout.Cell(column, other(row));
        } else {
            cursor = opening(next);
        }
        if (layout != null) {
            ring.to(contentBox(cursor));
        }
    }

    /** The screen laid itself out. The first time, the track opens on the level the player is at. */
    void layout(TrackLayout next) {
        layout = next;
        place(next.window());
        int columns = pass.levels().size();
        if (!placed) {
            placed = true;
            scroll.snap(next.opening(Math.max(0, currentColumn()), columns));
        } else {
            scroll.snap(Math.clamp(scroll.target(), 0, next.maxScroll(columns)));
        }
        if (cursor != null) {
            scroll.snap(next.reveal(cursor.column(), scroll.target(), columns));
            ring.snap(contentBox(cursor));
        }
    }

    /** The cursor's cell and what is in it, or null with an empty track. */
    TrackLayout.Cell current() {
        return cursor;
    }

    SeasonProtocol.Level currentLevel() {
        return cursor == null ? null : pass.levels().get(cursor.column());
    }

    Optional<SeasonProtocol.Reward> currentReward() {
        return cursor == null ? Optional.empty() : reward(cursor.column(), cursor.track());
    }

    void busy(TrackLayout.Cell cell) {
        busy = cell;
    }

    /**
     * Where the cursor starts: on the reward that can be taken now nearest the player's level,
     * free row first, so Enter on opening does what the player most likely came for and the track
     * still opens where they are; with nothing to take, on the level they are at.
     */
    static TrackLayout.Cell opening(SeasonProtocol.Open pass) {
        List<SeasonProtocol.Level> levels = pass.levels();
        int here = Math.max(0, currentColumnOf(pass));
        TrackLayout.Cell best = null;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < levels.size(); i++) {
            for (Track track : Track.values()) {
                boolean claimable = levels.get(i).reward(track)
                    .filter(r -> r.status() == SeasonProtocol.Status.CLAIMABLE).isPresent();
                if (claimable && Math.abs(i - here) < distance) {
                    distance = Math.abs(i - here);
                    best = new TrackLayout.Cell(i, track);
                }
            }
        }
        if (best != null) {
            return best;
        }
        for (int i = here; i >= 0; i--) {
            for (Track track : Track.values()) {
                if (levels.get(i).reward(track).isPresent()) {
                    return new TrackLayout.Cell(i, track);
                }
            }
        }
        return levels.isEmpty() ? null : new TrackLayout.Cell(0, Track.FREE);
    }

    private int currentColumn() {
        return currentColumnOf(pass);
    }

    /** The column of the level the player is at, or -1 before the first. */
    private static int currentColumnOf(SeasonProtocol.Open pass) {
        int found = -1;
        for (int i = 0; i < pass.levels().size(); i++) {
            if (pass.levels().get(i).level() <= pass.me().level()) {
                found = i;
            }
        }
        return found;
    }

    private Optional<SeasonProtocol.Reward> reward(int column, Track track) {
        return pass.levels().get(column).reward(track);
    }

    private static Track other(Track track) {
        return track == Track.FREE ? Track.PLUS : Track.FREE;
    }

    private Box contentBox(TrackLayout.Cell cell) {
        return cell == null ? Box.EMPTY : layout.tile(cell.column(), cell.track(), 0);
    }

    @Override
    public void draw(Painter painter, State state) {
        if (layout == null) {
            return;
        }
        long now = System.nanoTime();
        scroll.advance(now);
        ring.advance(now);
        if (!hovered()) {
            over = null;
        }
        float at = scroll.value();
        Box window = layout.window();
        int columns = pass.levels().size();
        int here = currentColumn();

        gutter(painter);
        painter.pushClip(window.x(), window.y(), window.w(), window.h());
        if (here >= 0) {
            Box lit = layout.column(here, at);
            painter.fill(lit.x(), lit.y(), lit.w(), lit.h(), Tokens.Color.SURFACE_RAISED);
            Typeset.drawCentered(painter, Tokens.Type.STRONG, "지금", lit.midX(),
                Typeset.centred(Tokens.Type.STRONG, layout.caption().y(), layout.caption().h()), Tokens.Color.MOON_LIT);
        }
        rail(painter, at, here);
        int first = Math.max(0, (int) Math.floor(at / TrackLayout.PITCH));
        int last = Math.min(columns - 1, (int) Math.ceil((at + window.w()) / TrackLayout.PITCH));
        for (int i = first; i <= last; i++) {
            for (Track track : Track.values()) {
                int column = i;
                reward(i, track).ifPresent(reward -> tile(painter, column, track, reward, at));
            }
        }
        if (cursor != null) {
            float x = ring.x() - Math.round(at);
            painter.border(x - RING_GAP - Tokens.Stroke.HAIR, ring.y() - RING_GAP - Tokens.Stroke.HAIR,
                ring.w() + (RING_GAP + Tokens.Stroke.HAIR) * 2, ring.h() + (RING_GAP + Tokens.Stroke.HAIR) * 2,
                Tokens.Radius.NONE, Tokens.Stroke.FOCUS,
                state == State.ACTIVE ? Tokens.Color.INK_SECONDARY : Tokens.Color.INK_PRIMARY);
        }
        painter.popClip();
        scrollbar(painter, at, columns);
    }

    /** The names of the two rows, fixed while the columns move under them. */
    private void gutter(Painter painter) {
        int x = layout.free().x();
        Typeset.draw(painter, Tokens.Type.STRONG, SeasonCopy.track(Track.FREE), x,
            Typeset.centred(Tokens.Type.STRONG, layout.free().y(), layout.free().h()), Tokens.Color.INK_SECONDARY);
        Typeset.draw(painter, Tokens.Type.STRONG, SeasonCopy.track(Track.PLUS), x,
            Typeset.centred(Tokens.Type.STRONG, layout.plus().y(), layout.plus().h()), Tokens.Color.CHAT_PURPLE);
        if (!pass.me().plus()) {
            Glass.lockMark(painter, x + Typeset.width(Tokens.Type.STRONG, SeasonCopy.track(Track.PLUS))
                + Tokens.Space.SNUG + Glass.MARK / 2.0f, layout.plus().midY(), Tokens.Color.CHAT_PURPLE);
        }
    }

    /**
     * The rail: a hairline from the first node to the last, lit as far as the player has come —
     * through every level reached and into the next by the share of points toward it.
     */
    private void rail(Painter painter, float at, int here) {
        List<SeasonProtocol.Level> levels = pass.levels();
        if (levels.isEmpty()) {
            return;
        }
        float cy = layout.rail().y() + NODE_R + Tokens.Space.SNUG;
        float start = layout.centreX(0, at);
        float end = layout.centreX(levels.size() - 1, at);
        painter.fill(start, cy - 1, end - start, Tokens.Stroke.BAR, Tokens.Color.LINE_STRONG);
        float fraction = pass.me().fraction();
        if (here >= 0) {
            float lit = layout.centreX(here, at) + (here < levels.size() - 1 ? TrackLayout.PITCH * fraction : 0);
            painter.fill(start, cy - 1, Math.max(0, lit - start), Tokens.Stroke.BAR, Tokens.Color.MOON_LIT);
        }
        int numberY = layout.rail().y() + NODE_R * 2 + Tokens.Space.SNUG + Tokens.Space.TIGHT;
        int first = Math.max(0, (int) Math.floor(at / TrackLayout.PITCH));
        int last = Math.min(levels.size() - 1, (int) Math.ceil((at + layout.window().w()) / TrackLayout.PITCH));
        for (int i = first; i <= last; i++) {
            float cx = layout.centreX(i, at);
            float phase = i <= here ? 1.0f : i == here + 1 ? fraction : 0.0f;
            painter.dot(cx, cy, NODE_R + Tokens.Stroke.HAIR, Tokens.Color.SURFACE_VOID);
            painter.moon(cx, cy, NODE_R, phase, true, Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
            if (i == here) {
                painter.ring(cx, cy, RING_R, Tokens.Stroke.HAIR, Tokens.Color.MOON_LIT);
            }
            int ink = i == here ? Tokens.Color.INK_PRIMARY : i < here ? Tokens.Color.INK_SECONDARY : Tokens.Color.INK_TERTIARY;
            Typeset.drawCentered(painter, Tokens.Type.MICRO, Integer.toString(levels.get(i).level()), Math.round(cx),
                numberY, ink);
        }
    }

    private void tile(Painter painter, int column, Track track, SeasonProtocol.Reward reward, float at) {
        Box b = layout.tile(column, track, at);
        TrackLayout.Cell cell = new TrackLayout.Cell(column, track);
        SeasonProtocol.Status status = reward.status();
        boolean claimable = status == SeasonProtocol.Status.CLAIMABLE;
        int ground = switch (status) {
            case CLAIMABLE -> Tokens.Color.ACCENT_WASH;
            case CLAIMED -> Tokens.Color.SURFACE_SUNKEN;
            case PENDING -> Tokens.Color.SURFACE_CONTROL;
            case LOCKED, PLUS_LOCKED -> Tokens.Color.SURFACE_CONTROL_DISABLED;
        };
        painter.fill(b.x(), b.y(), b.w(), b.h(), ground);
        if (cell.equals(over)) {
            painter.fill(b.x(), b.y(), b.w(), b.h(), Tokens.Color.SURFACE_RAISED);
        }
        if (claimable) {
            painter.border(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.ACCENT);
        } else if (status == SeasonProtocol.Status.PENDING) {
            painter.border(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.STATUS_WARN);
        }

        int iconX = b.x() + (b.w() - ICON) / 2;
        int iconY = b.y() + Tokens.Space.SNUG;
        SeasonItems.draw(painter, icon(column, track, reward), iconX, iconY, ICON);
        float wash = switch (status) {
            case CLAIMED -> CLAIMED_WASH;
            case LOCKED, PLUS_LOCKED -> LOCKED_WASH;
            default -> 0.0f;
        };
        if (wash > 0) {
            // Item art is drawn at full strength whatever the painter's opacity; a cell that is
            // spent or shut is dimmed by a wash of glass over its art instead.
            painter.fill(iconX, iconY, ICON, ICON, Rgb.alpha(Tokens.Color.SURFACE_VOID, wash));
        }

        float markX = b.right() - Tokens.Space.SNUG - Glass.MARK / 2.0f;
        float markY = b.y() + Tokens.Space.SNUG + Glass.MARK / 2.0f;
        switch (status) {
            case CLAIMED -> Glass.doneMark(painter, markX, markY, Tokens.Color.STATUS_LIVE);
            case CLAIMABLE -> painter.dot(markX, markY, Tokens.Space.TIGHT, Tokens.Color.ACCENT);
            case PENDING -> painter.dot(markX, markY, Tokens.Space.TIGHT, Tokens.Color.STATUS_WARN);
            case LOCKED -> Glass.lockMark(painter, markX, markY, Tokens.Color.INK_TERTIARY);
            case PLUS_LOCKED -> Glass.lockMark(painter, markX, markY, Tokens.Color.CHAT_PURPLE);
        }

        int labelTop = iconY + ICON + Tokens.Space.TIGHT;
        int labelH = b.bottom() - labelTop;
        if (cell.equals(busy)) {
            Dots.draw(painter, b.midX(), labelTop + labelH / 2.0f, Tokens.Color.INK_TERTIARY);
            return;
        }
        int room = b.w() - Tokens.Space.SNUG;
        String label = Typeset.width(Tokens.Type.BODY, reward.label()) <= room ? reward.label() : SeasonCopy.shortKind(reward);
        Typeset.drawCentered(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, label, room), b.midX(),
            Typeset.centred(Tokens.Type.BODY, labelTop, labelH), labelInk(reward));
    }

    /** A title's name is in its own colour, the way the server shows it; everything spent or shut sits back. */
    private static int labelInk(SeasonProtocol.Reward reward) {
        boolean back = reward.status() != SeasonProtocol.Status.CLAIMABLE && reward.status() != SeasonProtocol.Status.PENDING;
        if (reward.kind() == SeasonProtocol.Kind.TITLE) {
            int named = ScoreboardSidebar.namedColor(reward.color());
            int ink = named != 0 ? named : Tokens.Color.INK_PRIMARY;
            return back ? Rgb.alpha(ink, 0.6f) : ink;
        }
        if (reward.kind() == SeasonProtocol.Kind.COSMETIC) {
            return back ? Rgb.alpha(Tokens.Color.MOON_LIT, 0.6f) : Tokens.Color.MOON_LIT;
        }
        return back ? Tokens.Color.INK_TERTIARY : Tokens.Color.INK_PRIMARY;
    }

    private ItemStack icon(int column, Track track, SeasonProtocol.Reward reward) {
        ItemStack[] icons = track == Track.FREE ? freeIcons : plusIcons;
        ItemStack stack = icons[column];
        if (stack == null || stack.isEmpty()) {
            stack = SeasonItems.stack(reward.material(), reward.model());
            icons[column] = stack;
        }
        return stack;
    }

    private void scrollbar(Painter painter, float at, int columns) {
        int[] thumb = layout.thumb(at, columns);
        if (thumb == null) {
            return;
        }
        Box window = layout.window();
        painter.fill(window.x(), layout.scrollbarY(), window.w(), Tokens.Stroke.BAR, Tokens.Color.LINE_HAIRLINE);
        painter.fill(thumb[0], layout.scrollbarY(), thumb[1], Tokens.Stroke.BAR, Tokens.Color.LINE_STRONG);
    }

    @Override
    protected void hovering(double mx, double my) {
        over = layout == null ? null : layout.hit(mx, my, scroll.value(), pass.levels().size())
            .filter(cell -> reward(cell.column(), cell.track()).isPresent())
            .orElse(null);
    }

    @Override
    protected boolean press(double mx, double my) {
        if (layout == null) {
            return false;
        }
        layout.hit(mx, my, scroll.value(), pass.levels().size())
            .filter(cell -> reward(cell.column(), cell.track()).isPresent())
            .ifPresent(cell -> {
                moveTo(cell);
                act();
            });
        return false;
    }

    @Override
    protected boolean scroll(double amount) {
        int max = layout == null ? 0 : layout.maxScroll(pass.levels().size());
        if (max <= 0) {
            return false;
        }
        float was = scroll.target();
        scroll.to(Math.clamp(was - (float) Math.signum(amount) * TrackLayout.PITCH * 2, 0, max));
        return scroll.target() != was;
    }

    @Override
    protected boolean key(Chord chord) {
        if (cursor == null) {
            return false;
        }
        if (chord.is(InputConstants.KEY_LEFT)) {
            return walk(-1, 1);
        }
        if (chord.is(InputConstants.KEY_RIGHT)) {
            return walk(1, 1);
        }
        if (chord.is(InputConstants.KEY_PAGEUP)) {
            return walk(-1, PAGE);
        }
        if (chord.is(InputConstants.KEY_PAGEDOWN)) {
            return walk(1, PAGE);
        }
        if (chord.is(InputConstants.KEY_UP) || chord.is(InputConstants.KEY_DOWN)) {
            Track want = chord.is(InputConstants.KEY_UP) ? Track.FREE : Track.PLUS;
            if (want != cursor.track() && reward(cursor.column(), want).isPresent()) {
                moveTo(new TrackLayout.Cell(cursor.column(), want));
            }
            return true;
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            return walk(-1, pass.levels().size());
        }
        if (chord.is(InputConstants.KEY_END)) {
            return walk(1, pass.levels().size());
        }
        return false;
    }

    /**
     * Jumps {@code count} columns that way, or to the end, and settles on the nearest column back
     * toward the cursor that has a cell — in the cursor's row when it has one there, else the other.
     */
    private boolean walk(int direction, int count) {
        int from = cursor.column();
        int to = Math.clamp((long) from + (long) direction * count, 0, pass.levels().size() - 1);
        for (int i = to; i != from; i -= direction) {
            if (reward(i, cursor.track()).isPresent()) {
                moveTo(new TrackLayout.Cell(i, cursor.track()));
                break;
            }
            if (reward(i, other(cursor.track())).isPresent()) {
                moveTo(new TrackLayout.Cell(i, other(cursor.track())));
                break;
            }
        }
        return true;
    }

    private void moveTo(TrackLayout.Cell cell) {
        if (cell.equals(cursor)) {
            return;
        }
        cursor = cell;
        ring.to(contentBox(cell));
        scroll.to(layout.reveal(cell.column(), scroll.target(), pass.levels().size()));
        listener.moved();
    }

    @Override
    protected void act() {
        if (cursor == null) {
            return;
        }
        SeasonProtocol.Status status = reward(cursor.column(), cursor.track())
            .map(SeasonProtocol.Reward::status).orElse(SeasonProtocol.Status.LOCKED);
        if (status == SeasonProtocol.Status.CLAIMABLE && busy == null) {
            listener.claim(cursor, pass.levels().get(cursor.column()));
        } else if (status == SeasonProtocol.Status.PLUS_LOCKED) {
            listener.wantsPlus();
        } else {
            nudge();
            listener.refused();
        }
    }
}
