package dev.fullmoon.client.season;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.hud.ScoreboardSidebar;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.network.SeasonProtocol;
import dev.fullmoon.client.network.SeasonProtocol.Tab;
import dev.fullmoon.client.render.Fade;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.IconButton;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.TabRail;
import dev.fullmoon.client.ui.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The season pass as one pane of glass: the season's name and the time left in the header, three
 * pages under a tab rail — the reward track, the missions, Plus — and a foot line that carries the
 * server's answer to the last thing asked of it.
 *
 * <p>Every action is the server's. The screen sends which cell, which mission or which button was
 * chosen against the snapshot it is showing, shows dots where the answer is awaited, and draws
 * whatever snapshot comes back. A snapshot older than the one on screen is not drawn.
 */
public final class SeasonScreen extends SurfaceScreen {
    private static final int RISE = Tokens.Space.COZY;
    private static final List<Tab> TABS = List.of(Tab.TRACK, Tab.MISSIONS, Tab.PLUS);
    private static final int ICON = 32;
    private static final int BAR_W = 120;
    private static final int PERK_H = 22;

    private final Screen parent;
    private final boolean sample;
    private final TabRail tabs;
    private final PassButton claimAll;
    private final TrackBoard track;
    private final MissionBoard missions;
    private final PassButton buy;
    private final IconButton closeButton;
    private final Fade fade;

    private SeasonProtocol.Open pass;
    private Optional<SeasonProtocol.Notice> notice;
    private final SeasonPending pending = new SeasonPending(System::currentTimeMillis);
    private SeasonLayout layout;
    private Tab tab;
    private boolean keyboard;

    public SeasonScreen(Screen parent, SeasonProtocol.Open pass) {
        this(parent, pass, false);
    }

    /** {@code sample} is a fixture's screen: nothing it is asked to do goes to a server. */
    SeasonScreen(Screen parent, SeasonProtocol.Open pass, boolean sample) {
        super(Component.literal(pass.season().name()));
        this.parent = parent;
        this.sample = sample;
        this.pass = pass;
        this.notice = pass.notice();
        this.tab = pass.tab();
        this.tabs = surface.add(new TabRail("season", TABS.stream().map(SeasonCopy::tab).toList(),
            TABS.indexOf(tab), index -> switchTo(TABS.get(index))));
        this.claimAll = surface.add(new PassButton("Claim all", "", this::claimAll));
        this.track = surface.add(new TrackBoard(pass, new TrackBoard.Listener() {
            @Override
            public void claim(TrackLayout.Cell cell, SeasonProtocol.Level level) {
                if (request(new SeasonProtocol.Claim(level.level(), cell.track()))) {
                    UiSounds.play(UiSounds.Cue.CONFIRM);
                    track.busy(cell);
                }
            }

            @Override
            public void wantsPlus() {
                tabs.turn(TABS.indexOf(Tab.PLUS) - tabs.index());
            }

            @Override
            public void moved() {
                UiSounds.play(UiSounds.Cue.FOCUS);
            }

            @Override
            public void refused() {
                UiSounds.play(UiSounds.Cue.ERROR);
            }
        }));
        this.missions = surface.add(new MissionBoard(pass, new MissionBoard.Listener() {
            @Override
            public void picked(SeasonProtocol.Mission mission) {
                SeasonProtocol.Action action = mission.tracked()
                    ? new SeasonProtocol.Untrack() : new SeasonProtocol.TrackMission(mission.id());
                if (request(action)) {
                    UiSounds.play(UiSounds.Cue.CONFIRM);
                    missions.busy(mission);
                }
            }

            @Override
            public void moved() {
                UiSounds.play(UiSounds.Cue.FOCUS);
            }

            @Override
            public void refused() {
                UiSounds.play(UiSounds.Cue.ERROR);
            }
        }));
        this.buy = surface.add(new PassButton("Buy Plus", "Enter", this::buyPlus));
        this.closeButton = surface.add(new IconButton(IconButton.Glyph.CLOSE, "", this::onClose));
        this.fade = Fade.opening();
        UiSounds.play(UiSounds.Cue.OPEN);
        refreshControls();
        surface.focus().point(main());
    }

    public String sessionId() {
        return pass.id();
    }

    public Screen parentScreen() {
        return parent;
    }

    /**
     * The server's answer, or the snapshot after a change it made on its own. The page and the
     * cursor stay where the player left them — except when the snapshot is the answer to a tab
     * change or a purchase, which the server answers with the page it switched the session to.
     * A snapshot older than the one on screen is not drawn.
     */
    public void update(SeasonProtocol.Open next) {
        if (next.revision() < pass.revision()) {
            return;
        }
        SeasonProtocol.Action answering = pending.pending().orElse(null);
        boolean settled = pending.settle(next.revision());
        if (next.revision() > pass.revision() && !settled) {
            pending.abandon();
        }
        boolean answeringTab = settled && (answering instanceof SeasonProtocol.SwitchTab
            || answering instanceof SeasonProtocol.BuyPlus);
        pass = next;
        if (next.notice().isPresent() || !answeringTab) {
            notice = next.notice();
        }
        if (answeringTab) {
            tab = next.tab();
        }
        track.update(next);
        missions.update(next);
        claimAll.busy(false);
        buy.busy(false);
        refreshControls();
        if (answeringTab) {
            surface.focus().point(main());
        }
        if (layout != null) {
            place();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (fade.closing()) {
            return;
        }
        if (!sample) {
            FullmoonChannel.closeSeason(pass.id(), pass.revision());
        }
        UiSounds.play(UiSounds.Cue.CLOSE);
        fade.close();
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    /**
     * Dismisses this pass when its backend is gone. Unlike {@link #onClose()}, this sends no
     * season_close: the id and revision belong to the backend that is no longer connected.
     */
    public void dismissLocally() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    protected void init() {
        layout = SeasonLayout.fit(new Box(0, 0, width, height));
        place();
    }

    /** Lays out the page that is showing; the other pages' controls take no room and no focus. */
    private void place() {
        Box header = layout.header();
        int x = header.right() - Tokens.Space.LOOSE - IconButton.SIZE + Tokens.Space.BASE;
        closeButton.place(new Box(x, header.y() + (header.h() - IconButton.SIZE) / 2, IconButton.SIZE, IconButton.SIZE));
        tabs.place(layout.tabs());

        TrackLayout trackLayout = layout.track();
        track.layout(trackLayout);
        missions.layout(layout);
        Box summary = trackLayout.summary();
        int claimW = Math.max(claimAll.measure(), Tokens.Size.DETAIL / 2);
        claimAll.place(SeasonLayout.claimAll(summary, claimW).orElse(Box.EMPTY));
        Box side = layout.side();
        buy.place(new Box(side.x(), side.bottom() - Tokens.Space.SECTION, side.w(), Tokens.Space.SECTION));

        if (tab != Tab.TRACK) {
            claimAll.place(Box.EMPTY);
            track.place(Box.EMPTY);
        }
        if (tab != Tab.MISSIONS) {
            missions.place(Box.EMPTY);
        }
        if (tab != Tab.PLUS || pass.plus().owned()) {
            buy.place(Box.EMPTY);
        }
        refreshControls();
    }

    private void refreshControls() {
        SeasonProtocol.Plus plus = pass.plus();
        boolean open = SeasonGates.claimsOpen(pass);
        buy.text(plus.confirming() ? "Confirm purchase" : "Buy Plus");
        claimAll.enabled(tab == Tab.TRACK && open && pass.me().claimable() > 0);
        track.enabled(tab == Tab.TRACK);
        missions.enabled(tab == Tab.MISSIONS);
        buy.enabled(tab == Tab.PLUS && !plus.owned() && open && (plus.canBuy() || plus.confirming()));
    }

    private Widget main() {
        return switch (tab) {
            case TRACK -> track;
            case MISSIONS -> missions;
            case PLUS -> buy;
        };
    }

    private boolean shows(Widget widget) {
        if (widget == claimAll || widget == track) {
            return tab == Tab.TRACK;
        }
        if (widget == missions) {
            return tab == Tab.MISSIONS;
        }
        if (widget == buy) {
            return tab == Tab.PLUS && !pass.plus().owned();
        }
        return true;
    }

    /**
     * Moves to another page. The page itself is local, but its change is one of the pass's
     * requests, so while one is out the switch is refused with the reason — a page changed
     * silently against a pending request could drift from the server's own session page.
     */
    private void switchTo(Tab next) {
        if (pending.busy()) {
            pending.send(new SeasonProtocol.SwitchTab(next), pass.revision(), () -> false);
            UiSounds.play(UiSounds.Cue.ERROR);
            return;
        }
        tab = next;
        if (layout != null) {
            place();
        }
        surface.focus().point(main());
        if (!sample && !pending.send(new SeasonProtocol.SwitchTab(next), pass.revision(),
                () -> FullmoonChannel.requestSeasonAction(pass.id(), pass.revision(),
                    new SeasonProtocol.SwitchTab(next)))) {
            UiSounds.play(UiSounds.Cue.ERROR);
        }
    }

    /** Bulk-claims every ready cell, from the button or the keyboard; the phase gate is in {@link #request}. */
    private void claimAll() {
        if (request(new SeasonProtocol.ClaimAll())) {
            UiSounds.play(UiSounds.Cue.CONFIRM);
            claimAll.busy(true);
        }
    }

    private void buyPlus() {
        if (request(new SeasonProtocol.BuyPlus())) {
            UiSounds.play(UiSounds.Cue.CONFIRM);
            buy.busy(true);
        }
    }

    /** Sends one action against the snapshot on screen; one at a time, because each answer bumps the revision. */
    private boolean request(SeasonProtocol.Action action) {
        if (fade.closing() || sample) {
            UiSounds.play(UiSounds.Cue.ERROR);
            return false;
        }
        if (!SeasonGates.sends(pass, action)) {
            // A closed claim window answers with the standing row's own status; no request is made.
            UiSounds.play(UiSounds.Cue.ERROR);
            return false;
        }
        if (!pending.send(action, pass.revision(),
                () -> FullmoonChannel.requestSeasonAction(pass.id(), pass.revision(), action))) {
            UiSounds.play(UiSounds.Cue.ERROR);
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (pending.timedOut()) {
            pending.timeout();
            track.busy(null);
            missions.busy(null);
            claimAll.busy(false);
            buy.busy(false);
            UiSounds.play(UiSounds.Cue.ERROR);
        }
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (fade.closing()) {
            return true;
        }
        keyboard = true;
        Chord chord = Chord.from(event);
        if (chord.control() && chord.is(InputConstants.KEY_TAB)) {
            tabs.turn(chord.shift() ? -1 : 1);
            return true;
        }
        if (!chord.control() && chord.is(InputConstants.KEY_C) && tab == Tab.TRACK) {
            claimAll();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboard = false;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.52f * fade.appearance()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (!fade.closing()) {
            surface.hover(mouseX, mouseY);
        }
        Painter painter = new Painter(gfx);
        float t = fade.appearance();
        painter.opacity(t);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (1.0f - t) * RISE);
        Glass.panel(painter, layout.panel());
        header(painter);
        switch (tab) {
            case TRACK -> trackPage(painter);
            case MISSIONS -> missionsPage(painter);
            case PLUS -> plusPage(painter);
        }
        foot(painter);
        surface.drawNormal(painter, this::shows);
        surface.drawOverlays(painter);
        gfx.pose().popMatrix();
        hints(painter);
    }

    private void header(Painter painter) {
        Box header = layout.header();
        int x = header.x() + Tokens.Space.LOOSE;
        int right = closeButton.bounds().x() - Tokens.Space.COZY;
        int capW = Glass.keycapWidth("Esc");
        Glass.keycap(painter, right - capW, header.y() + (header.h() - Tokens.Size.KEYCAP) / 2, "Esc");
        right -= capW + Tokens.Space.COZY;
        String context = SeasonCopy.context(pass.season());
        if (!context.isEmpty()) {
            right -= Typeset.tabularRight(painter, Tokens.Type.BODY, context, right,
                Typeset.centred(Tokens.Type.BODY, header.y(), header.h()), Tokens.Color.INK_TERTIARY)
                + Tokens.Space.COZY;
        }
        Typeset.draw(painter, Tokens.Type.TITLE, Typeset.ellipsized(Tokens.Type.TITLE, pass.season().name(), right - x),
            x, Typeset.centred(Tokens.Type.TITLE, header.y(), header.h()), Tokens.Color.INK_PRIMARY);
        Glass.hair(painter, header.x(), header.bottom() - 1, header.w());
    }

    // ── the track ───────────────────────────────────────────────

    private void trackPage(Painter painter) {
        TrackLayout t = layout.track();
        summary(painter, t.summary());
        trackDetail(painter, t.detail());
    }

    /**
     * The standing over the track: the level, the way to the next one, and what is waiting to be
     * taken. On a small viewport the row keeps only the figures — the captions are the first to
     * go, and the bar yields to the "to next" count rather than crossing the claim button.
     */
    private void summary(Painter painter, Box s) {
        if (s.h() <= 0) {
            return;
        }
        SeasonProtocol.Me me = pass.me();
        boolean compact = s.h() < Tokens.Type.BODY.leading() + Tokens.Type.FIGURE.leading();
        int captionY = s.y();
        int valueTop = compact ? s.y() : captionY + Tokens.Type.BODY.leading();
        int valueH = s.bottom() - valueTop;
        int x = s.x();

        if (!compact) {
            Typeset.draw(painter, Tokens.Type.BODY, "Level", x, captionY, Tokens.Color.INK_TERTIARY);
        }
        int figureY = Typeset.centred(Tokens.Type.FIGURE, valueTop, valueH);
        int levelW = Typeset.tabular(painter, Tokens.Type.FIGURE, Integer.toString(me.level()), x, figureY,
            Tokens.Color.INK_PRIMARY);
        int maxW = Typeset.draw(painter, Tokens.Type.BODY, " / " + me.maxLevel(), x + levelW,
            Typeset.centred(Tokens.Type.BODY, valueTop, valueH), Tokens.Color.INK_TERTIARY);
        x += Math.max(compact ? 0 : Typeset.width(Tokens.Type.BODY, "Level"), levelW + maxW) + Tokens.Space.SECTION;

        if (!compact) {
            Typeset.draw(painter, Tokens.Type.BODY, "To next level", x, captionY, Tokens.Color.INK_TERTIARY);
        }
        int lineY = Typeset.centred(Tokens.Type.BODY, valueTop, valueH);
        int blockW;
        if (pass.season().phase() == SeasonProtocol.Phase.UPCOMING) {
            blockW = Typeset.draw(painter, Tokens.Type.BODY, "Starts when the season opens", x, lineY, Tokens.Color.INK_TERTIARY);
        } else if (pass.season().phase() == SeasonProtocol.Phase.ENDED) {
            blockW = Glass.markedLine(painter, x, Typeset.centred(Tokens.Type.STRONG, valueTop, valueH),
                "Season ended", Tokens.Color.STATUS_ASH, false);
        } else if (me.level() >= me.maxLevel()) {
            blockW = Glass.markedLine(painter, x, Typeset.centred(Tokens.Type.STRONG, valueTop, valueH),
                "Final level", Tokens.Color.STATUS_LIVE, false);
        } else {
            String toNext = SeasonCopy.toNext(me.into(), me.step(), pass.point());
            int barRoom = claimAll.bounds().x() - Tokens.Space.COZY - x
                - (Typeset.width(Tokens.Type.BODY, toNext) + Tokens.Space.BASE);
            int barW = Math.clamp(barRoom, 0, BAR_W);
            if (barW > 0) {
                Glass.bar(painter, x, valueTop + (valueH - Tokens.Space.SNUG) / 2, barW, me.fraction(),
                    Tokens.Color.MOON_LIT);
            }
            int textX = barW > 0 ? x + barW + Tokens.Space.BASE : x;
            blockW = Typeset.tabular(painter, Tokens.Type.BODY, toNext, textX, lineY, Tokens.Color.INK_SECONDARY)
                + (textX - x);
        }
        x += Math.max(compact ? 0 : Typeset.width(Tokens.Type.BODY, "To next level"), blockW) + Tokens.Space.SECTION;

        int claimCaptionW = compact ? 0 : Typeset.width(Tokens.Type.BODY, "To claim");
        if (x + claimCaptionW < claimAll.bounds().x() - Tokens.Space.COZY) {
            if (!compact) {
                Typeset.draw(painter, Tokens.Type.BODY, "To claim", x, captionY, Tokens.Color.INK_TERTIARY);
            }
            boolean closed = !SeasonGates.claimsOpen(pass);
            boolean some = me.claimable() > 0;
            String said = closed ? "Closed"
                : some ? me.claimable() + " ready" : "None";
            Typeset.tabular(painter, Tokens.Type.STRONG, said, x,
                Typeset.centred(Tokens.Type.STRONG, valueTop, valueH),
                !closed && some ? Tokens.Color.ACCENT : Tokens.Color.INK_TERTIARY);
        }
    }

    /** The cursor's cell said in words: which level and row, what the reward is, and where it stands. */
    private void trackDetail(Painter painter, Box d) {
        if (d.h() < Tokens.Type.ROW.leading()) {
            return;
        }
        SeasonProtocol.Level level = track.currentLevel();
        Optional<SeasonProtocol.Reward> found = track.currentReward();
        TrackLayout.Cell cell = track.current();
        if (level == null || found.isEmpty() || cell == null) {
            return;
        }
        SeasonProtocol.Reward reward = found.orElseThrow();
        int rowY = Typeset.centred(Tokens.Type.ROW, d.y(), d.h());
        int strongY = Typeset.centred(Tokens.Type.STRONG, d.y(), d.h());
        int bodyY = Typeset.centred(Tokens.Type.BODY, d.y(), d.h());

        int right = d.right();
        right -= statusLine(painter, reward, level, right, strongY);

        int x = d.x();
        String where = "Level " + level.level() + " · " + SeasonCopy.track(cell.track());
        x += Typeset.draw(painter, Tokens.Type.STRONG, where, x, strongY,
            cell.track() == SeasonProtocol.Track.PLUS ? Tokens.Color.ACCENT : Tokens.Color.INK_TERTIARY);
        x += Tokens.Space.COZY;
        int room = Math.max(0, right - Tokens.Space.GUTTER - x);
        String name = Typeset.ellipsized(Tokens.Type.ROW, reward.label(), room);
        x += Typeset.draw(painter, Tokens.Type.ROW, name, x, rowY, rewardInk(reward));
        String kind = reward.kind() == SeasonProtocol.Kind.WON ? "" : " · " + SeasonCopy.kind(reward);
        if (!kind.isEmpty() && x + Typeset.width(Tokens.Type.BODY, kind) < right - Tokens.Space.GUTTER) {
            Typeset.draw(painter, Tokens.Type.BODY, kind, x, bodyY, Tokens.Color.INK_TERTIARY);
        }
    }

    /** The right end of the detail line: where the cell stands and, if Enter does something, what. Returns its width. */
    private int statusLine(Painter painter, SeasonProtocol.Reward reward, SeasonProtocol.Level level, int right, int y) {
        long left = Math.max(0, level.at() - pass.me().xp());
        String words = SeasonCopy.status(reward, level.level(), left, pass.point(), pass.season().phase());
        int start = right;
        boolean closed = !SeasonGates.claimsOpen(pass);
        switch (reward.status()) {
            case CLAIMABLE -> {
                if (closed) {
                    // A stale claimable flag cannot advertise a claim the window cannot honour.
                    right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.INK_TERTIARY);
                } else {
                    right -= action(painter, I18n.get("fullmoon.season.hint.claim"), right, y, Tokens.Color.ACCENT);
                    right -= Tokens.Space.COZY;
                    right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.ACCENT);
                }
            }
            case CLAIMED -> {
                right -= Glass.markedWidth(words);
                Glass.markedLine(painter, right, y, words, Tokens.Color.STATUS_LIVE, false);
            }
            case PLUS_LOCKED -> {
                right -= action(painter, "View Plus", right, y, Tokens.Color.ACCENT);
                right -= Tokens.Space.COZY;
                right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.INK_SECONDARY);
            }
            case PENDING -> right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.STATUS_WARN);
            case LOCKED -> right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.INK_TERTIARY);
        }
        return start - right;
    }

    /** {@code [Enter] Claim}, right-aligned at {@code right}. Returns its width. */
    private static int action(Painter painter, String does, int right, int y, int ink) {
        int w = Typeset.drawRight(painter, Tokens.Type.STRONG, does, right, y, ink);
        int capW = Glass.keycapWidth("Enter");
        int capY = Typeset.capTop(Tokens.Type.STRONG, y) + Typeset.capHeight(Tokens.Type.STRONG) / 2 - Tokens.Size.KEYCAP / 2;
        Glass.keycap(painter, right - w - Tokens.Space.SNUG - capW, capY, "Enter", ink);
        return w + Tokens.Space.SNUG + capW;
    }

    private static int rewardInk(SeasonProtocol.Reward reward) {
        if (reward.kind() == SeasonProtocol.Kind.TITLE) {
            int named = ScoreboardSidebar.namedColor(reward.color());
            return named != 0 ? named : Tokens.Color.INK_PRIMARY;
        }
        return reward.kind() == SeasonProtocol.Kind.COSMETIC ? Tokens.Color.MOON_LIT : Tokens.Color.INK_PRIMARY;
    }

    // ── the missions ────────────────────────────────────────────

    private void missionsPage(Painter painter) {
        if (missions.empty()) {
            return;
        }
        Box side = layout.side();
        Glass.vhair(painter, layout.divider(), side.y(), side.h());
        SeasonProtocol.Mission mission = missions.current();
        if (mission == null) {
            return;
        }
        int y = side.y();
        SeasonItems.draw(painter, SeasonItems.stack(mission.material(), ""), side.x(), y, ICON);
        int nameX = side.x() + ICON + Tokens.Space.COZY;
        List<String> name = Typeset.lines(Tokens.Type.TITLE, mission.name(), side.right() - nameX, 2);
        int nameTop = y + (ICON - name.size() * Tokens.Type.TITLE.leading()) / 2;
        for (int i = 0; i < name.size(); i++) {
            Typeset.draw(painter, Tokens.Type.TITLE, name.get(i), nameX, nameTop + i * Tokens.Type.TITLE.leading(),
                Tokens.Color.INK_PRIMARY);
        }
        y += ICON + Tokens.Space.LOOSE;

        int half = side.w() / 2;
        Typeset.draw(painter, Tokens.Type.BODY, "Progress", side.x(), y, Tokens.Color.INK_TERTIARY);
        Typeset.draw(painter, Tokens.Type.BODY, "Reward", side.x() + half, y, Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.BODY.leading() - Tokens.Space.TIGHT;
        Typeset.tabular(painter, Tokens.Type.FIGURE, SeasonCopy.count(mission.progress(), mission.goal(), mission.unit()),
            side.x(), y, mission.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.INK_PRIMARY);
        Typeset.tabular(painter, Tokens.Type.FIGURE, SeasonCopy.reward(mission.xp(), pass.point()), side.x() + half, y,
            Tokens.Color.INK_PRIMARY);
        y += Tokens.Type.FIGURE.leading() + Tokens.Space.SNUG;
        Glass.bar(painter, side.x(), y, side.w(), mission.fraction(),
            mission.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.MOON_LIT);
        y += Tokens.Space.SNUG + Tokens.Space.LOOSE;

        List<String> notes = new ArrayList<>();
        if (mission.tiers() > 1) {
            notes.add(SeasonCopy.stage(mission.tier(), mission.tiers()));
        }
        if (mission.plusExtra()) {
            notes.add("Plus bonus mission");
        }
        for (String note : notes) {
            Typeset.draw(painter, Tokens.Type.BODY, note, side.x(), y,
                note.startsWith("Plus") ? Tokens.Color.ACCENT : Tokens.Color.INK_SECONDARY);
            y += Tokens.Type.BODY.leading();
        }

        Box row = new Box(side.x(), side.bottom() - Tokens.Size.ACTION_ROW, side.w(), Tokens.Size.ACTION_ROW);
        if (mission.done()) {
            Glass.markedLine(painter, row.x(), Typeset.centred(Tokens.Type.STRONG, row.y(), row.h()), "Completed",
                Tokens.Color.STATUS_LIVE, false);
        } else if (mission.trackable()) {
            Glass.actionRow(painter, row, false, mission.tracked() ? "Untrack" : "Track", 1.0f);
            if (mission.tracked()) {
                Typeset.drawWrapped(painter, Tokens.Type.BODY, "Shown in the season HUD", row.x(),
                    row.y() - Tokens.Space.COZY - Tokens.Type.BODY.leading(), row.w(), 1, Tokens.Color.MOON_LIT);
            }
        } else {
            String why = SeasonCopy.untrackable(mission, pass.me().plus());
            int lines = Typeset.lines(Tokens.Type.BODY, why, row.w(), 2).size();
            Typeset.drawWrapped(painter, Tokens.Type.BODY, why, row.x(),
                row.bottom() - lines * Tokens.Type.BODY.leading(), row.w(), 2,
                mission.plusExtra() && !pass.me().plus() ? Tokens.Color.ACCENT : Tokens.Color.INK_TERTIARY);
        }
    }

    // ── Plus ────────────────────────────────────────────────────

    private void plusPage(Painter painter) {
        SeasonProtocol.Plus plus = pass.plus();
        Box side = layout.side();
        Box body = layout.body();
        int left = body.x() + Tokens.Space.LOOSE;
        int width = layout.divider() - Tokens.Space.LOOSE - left;
        int y = body.y() + Tokens.Space.LOOSE;
        Glass.vhair(painter, layout.divider(), side.y(), side.h());

        Typeset.draw(painter, Tokens.Type.TITLE, "Season Pass Plus", left, y, Tokens.Color.ACCENT);
        y += Tokens.Type.TITLE.leading() + Tokens.Space.COZY;
        for (String perk : plus.perks()) {
            float cy = y + Tokens.Type.BODY.leading() / 2.0f;
            Glass.doneMark(painter, left + Glass.MARK / 2.0f, cy, Tokens.Color.ACCENT);
            int textX = left + Glass.MARK + Tokens.Space.COZY;
            int used = Typeset.drawWrapped(painter, Tokens.Type.BODY, perk, textX, y, width - (textX - left), 2,
                Tokens.Color.INK_PRIMARY);
            y += Math.max(PERK_H, used + Tokens.Space.COZY);
        }

        int sy = side.y();
        Typeset.draw(painter, Tokens.Type.BODY, "Price", side.x(), sy, Tokens.Color.INK_TERTIARY);
        sy += Tokens.Type.BODY.leading() - Tokens.Space.TIGHT;
        Typeset.tabular(painter, Tokens.Type.FIGURE, plus.price(), side.x(), sy, Tokens.Color.INK_PRIMARY);
        sy += Tokens.Type.FIGURE.leading() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, "Balance", side.x(), sy, Tokens.Color.INK_TERTIARY);
        sy += Tokens.Type.BODY.leading() - Tokens.Space.TIGHT;
        Optional<String> balance = pass.me().balance();
        Typeset.tabular(painter, Tokens.Type.STRONG, balance.orElse("Unavailable"), side.x(), sy,
            balance.isPresent() ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY);

        if (plus.owned()) {
            Glass.markedLine(painter, side.x(), side.bottom() - Tokens.Type.STRONG.leading(),
                "Plus is active this season", Tokens.Color.ACCENT, false);
            return;
        }
        int above = buy.bounds().y() - Tokens.Space.COZY;
        if (plus.confirming()) {
            Typeset.draw(painter, Tokens.Type.STRONG, "This purchase cannot be undone", side.x(),
                above - Tokens.Type.STRONG.leading(), Tokens.Color.STATUS_DANGER);
        } else if (!plus.canBuy()) {
            String why = plus.reason().orElse("Purchasing is unavailable");
            int lines = Typeset.lines(Tokens.Type.BODY, why, side.w() - Glass.MARK - Tokens.Space.COZY, 2).size();
            int top = above - lines * Tokens.Type.BODY.leading();
            Glass.blockedMark(painter, side.x() + Glass.MARK / 2.0f, top + Tokens.Type.BODY.leading() / 2.0f,
                Tokens.Color.STATUS_DANGER);
            Typeset.drawWrapped(painter, Tokens.Type.BODY, why, side.x() + Glass.MARK + Tokens.Space.COZY, top,
                side.w() - Glass.MARK - Tokens.Space.COZY, 2, Tokens.Color.INK_SECONDARY);
        }
    }

    // ── the foot ────────────────────────────────────────────────

    /**
     * The foot carries what was last asked of the server: a local problem about the request
     * itself, then the server's answer, and with neither, the wallet and the pass held.
     */
    private void foot(Painter painter) {
        Box f = layout.foot();
        Glass.hair(painter, f.x(), f.y(), f.w());
        int x = f.x() + Tokens.Space.LOOSE;
        int room = f.w() - Tokens.Space.LOOSE * 2;
        int strongY = Typeset.centred(Tokens.Type.STRONG, f.y(), f.h());
        Optional<String> problem = pending.problem();
        if (problem.isPresent()) {
            String text = Typeset.ellipsized(Tokens.Type.STRONG, problem.orElseThrow(),
                room - Glass.MARK - Tokens.Space.SNUG);
            Glass.markedLine(painter, x, strongY, text, Tokens.Color.STATUS_DANGER, true);
            return;
        }
        if (notice.isPresent()) {
            SeasonProtocol.Notice said = notice.orElseThrow();
            boolean ok = said.tone() == SeasonProtocol.Tone.OK;
            String text = Typeset.ellipsized(Tokens.Type.STRONG, said.text(), room - Glass.MARK - Tokens.Space.SNUG);
            Glass.markedLine(painter, x, strongY, text, ok ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_DANGER, !ok);
            return;
        }
        int bodyY = Typeset.centred(Tokens.Type.BODY, f.y(), f.h());
        x += Typeset.draw(painter, Tokens.Type.BODY, "Balance", x, bodyY, Tokens.Color.INK_TERTIARY) + Tokens.Space.BASE;
        Optional<String> balance = pass.me().balance();
        x += Typeset.tabular(painter, Tokens.Type.STRONG, balance.orElse("—"), x, strongY, Tokens.Color.INK_SECONDARY)
            + Tokens.Space.GUTTER;
        x += Typeset.draw(painter, Tokens.Type.BODY, "Plus", x, bodyY, Tokens.Color.INK_TERTIARY) + Tokens.Space.BASE;
        boolean owned = pass.me().plus();
        Typeset.draw(painter, Tokens.Type.STRONG, owned ? "Active" : "None", x, strongY,
            owned ? Tokens.Color.ACCENT : Tokens.Color.INK_TERTIARY);
    }

    private void hints(Painter painter) {
        List<Glass.Hint> hints = new ArrayList<>();
        boolean open = SeasonGates.claimsOpen(pass);
        switch (tab) {
            case TRACK -> {
                hints.add(new Glass.Hint("←→↑↓", I18n.get("fullmoon.menu.hint.move")));
                if (open) {
                    hints.add(new Glass.Hint("Enter", I18n.get("fullmoon.season.hint.claim")));
                    if (claimAll.bounds().empty()) {
                        hints.add(new Glass.Hint("C", I18n.get("fullmoon.season.hint.claim_all")));
                    }
                }
            }
            case MISSIONS -> {
                hints.add(new Glass.Hint("↑↓", I18n.get("fullmoon.menu.hint.move")));
                hints.add(new Glass.Hint("Enter", I18n.get("fullmoon.season.hint.track")));
            }
            case PLUS -> {
                if (buy.state(surface.focus()).live()) {
                    hints.add(new Glass.Hint("Enter", I18n.get("fullmoon.season.hint.buy")));
                }
            }
        }
        hints.add(new Glass.Hint("Ctrl+Tab", I18n.get("fullmoon.hub.hint.turn")));
        hints.add(new Glass.Hint("Esc", I18n.get("fullmoon.menu.hint.close")));
        // A hint bar is one line; on the narrowest panel the page-turn hint is the one that can
        // clip, and the tabs stay reachable by pointer and keyboard without it.
        if (layout.panel().w() < 400 && hints.size() > 3) {
            hints.remove(hints.size() - 2);
        }
        Glass.hints(painter, layout.panel().midX(), layout.hintY(), hints, keyboard);
    }
}
