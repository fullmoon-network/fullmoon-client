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
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
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
    private SeasonLayout layout;
    private Tab tab;
    private long requestedAt;
    private boolean askedForTab;
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
        this.claimAll = surface.add(new PassButton("모두 받기", "", this::claimAll));
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
        this.buy = surface.add(new PassButton("플러스 사기", "Enter", this::buyPlus));
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
     * cursor stay where the player left them; the server's remembered page only chose the first one.
     */
    public void update(SeasonProtocol.Open next) {
        if (next.revision() < pass.revision()) {
            return;
        }
        boolean fromTab = askedForTab;
        askedForTab = false;
        pass = next;
        if (next.notice().isPresent() || !fromTab) {
            notice = next.notice();
        }
        requestedAt = 0;
        track.update(next);
        missions.update(next);
        claimAll.busy(false);
        buy.busy(false);
        refreshControls();
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
        claimAll.place(new Box(summary.right() - claimW, summary.midY() - Tokens.Size.BUTTON / 2, claimW,
            Tokens.Size.BUTTON));
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
        buy.text(plus.confirming() ? "확인하고 사기" : "플러스 사기");
        claimAll.enabled(tab == Tab.TRACK && pass.me().claimable() > 0);
        track.enabled(tab == Tab.TRACK);
        missions.enabled(tab == Tab.MISSIONS);
        buy.enabled(tab == Tab.PLUS && !plus.owned() && (plus.canBuy() || plus.confirming()));
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

    private void switchTo(Tab next) {
        tab = next;
        if (layout != null) {
            place();
        }
        surface.focus().point(main());
        if (!sample && requestedAt == 0
                && FullmoonChannel.requestSeasonAction(pass.id(), pass.revision(), new SeasonProtocol.SwitchTab(next))) {
            requestedAt = System.currentTimeMillis();
            askedForTab = true;
        }
    }

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
        if (requestedAt > 0 || fade.closing() || sample) {
            UiSounds.play(UiSounds.Cue.ERROR);
            return false;
        }
        if (!FullmoonChannel.requestSeasonAction(pass.id(), pass.revision(), action)) {
            UiSounds.play(UiSounds.Cue.ERROR);
            return false;
        }
        requestedAt = System.currentTimeMillis();
        askedForTab = false;
        return true;
    }

    @Override
    public void tick() {
        if (requestedAt > 0 && System.currentTimeMillis() - requestedAt >= REQUEST_TIMEOUT_MILLIS) {
            requestedAt = 0;
            askedForTab = false;
            track.busy(null);
            missions.busy(null);
            claimAll.busy(false);
            buy.busy(false);
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

    /** The standing over the track: the level, the way to the next one, and what is waiting to be taken. */
    private void summary(Painter painter, Box s) {
        SeasonProtocol.Me me = pass.me();
        int captionY = s.y();
        int valueTop = captionY + Tokens.Type.BODY.leading();
        int valueH = s.bottom() - valueTop;
        int x = s.x();

        Typeset.draw(painter, Tokens.Type.BODY, "레벨", x, captionY, Tokens.Color.INK_TERTIARY);
        int figureY = Typeset.centred(Tokens.Type.FIGURE, valueTop, valueH);
        int levelW = Typeset.tabular(painter, Tokens.Type.FIGURE, Integer.toString(me.level()), x, figureY,
            Tokens.Color.INK_PRIMARY);
        int maxW = Typeset.draw(painter, Tokens.Type.BODY, " / " + me.maxLevel(), x + levelW,
            Typeset.centred(Tokens.Type.BODY, valueTop, valueH), Tokens.Color.INK_TERTIARY);
        x += Math.max(Typeset.width(Tokens.Type.BODY, "레벨"), levelW + maxW) + Tokens.Space.SECTION;

        Typeset.draw(painter, Tokens.Type.BODY, "다음 레벨까지", x, captionY, Tokens.Color.INK_TERTIARY);
        int lineY = Typeset.centred(Tokens.Type.BODY, valueTop, valueH);
        int blockW;
        if (pass.season().phase() == SeasonProtocol.Phase.UPCOMING) {
            blockW = Typeset.draw(painter, Tokens.Type.BODY, "시즌이 시작되면 쌓여요", x, lineY, Tokens.Color.INK_TERTIARY);
        } else if (me.level() >= me.maxLevel()) {
            blockW = Glass.markedLine(painter, x, Typeset.centred(Tokens.Type.STRONG, valueTop, valueH),
                "마지막 레벨이에요", Tokens.Color.STATUS_LIVE, false);
        } else {
            Glass.bar(painter, x, valueTop + (valueH - Tokens.Space.SNUG) / 2, BAR_W, me.fraction(), Tokens.Color.MOON_LIT);
            blockW = BAR_W + Tokens.Space.BASE + Typeset.tabular(painter, Tokens.Type.BODY,
                SeasonCopy.toNext(me.into(), me.step(), pass.point()), x + BAR_W + Tokens.Space.BASE, lineY,
                Tokens.Color.INK_SECONDARY);
        }
        x += Math.max(Typeset.width(Tokens.Type.BODY, "다음 레벨까지"), blockW) + Tokens.Space.SECTION;

        if (x + Typeset.width(Tokens.Type.BODY, "받을 보상") < claimAll.bounds().x() - Tokens.Space.COZY) {
            Typeset.draw(painter, Tokens.Type.BODY, "받을 보상", x, captionY, Tokens.Color.INK_TERTIARY);
            boolean some = me.claimable() > 0;
            Typeset.tabular(painter, Tokens.Type.STRONG, some ? me.claimable() + "개" : "없어요", x,
                Typeset.centred(Tokens.Type.STRONG, valueTop, valueH), some ? Tokens.Color.ACCENT : Tokens.Color.INK_TERTIARY);
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
        String where = level.level() + "레벨 " + SeasonCopy.track(cell.track());
        x += Typeset.draw(painter, Tokens.Type.STRONG, where, x, strongY,
            cell.track() == SeasonProtocol.Track.PLUS ? Tokens.Color.CHAT_PURPLE : Tokens.Color.INK_TERTIARY);
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
        String words = SeasonCopy.status(reward, level.level(), left, pass.point());
        int start = right;
        switch (reward.status()) {
            case CLAIMABLE -> {
                right -= action(painter, I18n.get("fullmoon.season.hint.claim"), right, y, Tokens.Color.ACCENT);
                right -= Tokens.Space.COZY;
                right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.ACCENT);
            }
            case CLAIMED -> {
                right -= Glass.markedWidth(words);
                Glass.markedLine(painter, right, y, words, Tokens.Color.STATUS_LIVE, false);
            }
            case PLUS_LOCKED -> {
                right -= action(painter, "플러스 보기", right, y, Tokens.Color.CHAT_PURPLE);
                right -= Tokens.Space.COZY;
                right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.INK_SECONDARY);
            }
            case PENDING -> right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.STATUS_WARN);
            case LOCKED -> right -= Typeset.drawRight(painter, Tokens.Type.STRONG, words, right, y, Tokens.Color.INK_TERTIARY);
        }
        return start - right;
    }

    /** {@code [Enter] 받기}, right-aligned at {@code right}. Returns its width. */
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
        Typeset.draw(painter, Tokens.Type.BODY, "진행", side.x(), y, Tokens.Color.INK_TERTIARY);
        Typeset.draw(painter, Tokens.Type.BODY, "보상", side.x() + half, y, Tokens.Color.INK_TERTIARY);
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
            notes.add("플러스 추가 미션이에요");
        }
        for (String note : notes) {
            Typeset.draw(painter, Tokens.Type.BODY, note, side.x(), y,
                note.startsWith("플러스") ? Tokens.Color.CHAT_PURPLE : Tokens.Color.INK_SECONDARY);
            y += Tokens.Type.BODY.leading();
        }

        Box row = new Box(side.x(), side.bottom() - Tokens.Size.ACTION_ROW, side.w(), Tokens.Size.ACTION_ROW);
        if (mission.done()) {
            Glass.markedLine(painter, row.x(), Typeset.centred(Tokens.Type.STRONG, row.y(), row.h()), "완료했어요",
                Tokens.Color.STATUS_LIVE, false);
        } else if (mission.trackable()) {
            Glass.actionRow(painter, row, false, mission.tracked() ? "추적 풀기" : "추적하기", 1.0f);
            if (mission.tracked()) {
                Typeset.drawWrapped(painter, Tokens.Type.BODY, "HUD의 시즌 미션에 보여요", row.x(),
                    row.y() - Tokens.Space.COZY - Tokens.Type.BODY.leading(), row.w(), 1, Tokens.Color.MOON_LIT);
            }
        } else {
            String why = SeasonCopy.untrackable(mission, pass.me().plus());
            int lines = Typeset.lines(Tokens.Type.BODY, why, row.w(), 2).size();
            Typeset.drawWrapped(painter, Tokens.Type.BODY, why, row.x(),
                row.bottom() - lines * Tokens.Type.BODY.leading(), row.w(), 2,
                mission.plusExtra() && !pass.me().plus() ? Tokens.Color.CHAT_PURPLE : Tokens.Color.INK_TERTIARY);
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

        Typeset.draw(painter, Tokens.Type.TITLE, "시즌패스 플러스", left, y, Tokens.Color.CHAT_PURPLE);
        y += Tokens.Type.TITLE.leading() + Tokens.Space.COZY;
        for (String perk : plus.perks()) {
            float cy = y + Tokens.Type.BODY.leading() / 2.0f;
            Glass.doneMark(painter, left + Glass.MARK / 2.0f, cy, Tokens.Color.CHAT_PURPLE);
            int textX = left + Glass.MARK + Tokens.Space.COZY;
            int used = Typeset.drawWrapped(painter, Tokens.Type.BODY, perk, textX, y, width - (textX - left), 2,
                Tokens.Color.INK_PRIMARY);
            y += Math.max(PERK_H, used + Tokens.Space.COZY);
        }

        int sy = side.y();
        Typeset.draw(painter, Tokens.Type.BODY, "가격", side.x(), sy, Tokens.Color.INK_TERTIARY);
        sy += Tokens.Type.BODY.leading() - Tokens.Space.TIGHT;
        Typeset.tabular(painter, Tokens.Type.FIGURE, plus.price(), side.x(), sy, Tokens.Color.INK_PRIMARY);
        sy += Tokens.Type.FIGURE.leading() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, "내 잔액", side.x(), sy, Tokens.Color.INK_TERTIARY);
        sy += Tokens.Type.BODY.leading() - Tokens.Space.TIGHT;
        Optional<String> balance = pass.me().balance();
        Typeset.tabular(painter, Tokens.Type.STRONG, balance.orElse("불러오지 못했어요"), side.x(), sy,
            balance.isPresent() ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY);

        if (plus.owned()) {
            Glass.markedLine(painter, side.x(), side.bottom() - Tokens.Type.STRONG.leading(),
                "이번 시즌 플러스를 갖고 있어요", Tokens.Color.CHAT_PURPLE, false);
            return;
        }
        int above = buy.bounds().y() - Tokens.Space.COZY;
        if (plus.confirming()) {
            Typeset.draw(painter, Tokens.Type.STRONG, "산 뒤에는 되돌릴 수 없어요", side.x(),
                above - Tokens.Type.STRONG.leading(), Tokens.Color.STATUS_DANGER);
        } else if (!plus.canBuy()) {
            String why = plus.reason().orElse("지금은 살 수 없어요");
            int lines = Typeset.lines(Tokens.Type.BODY, why, side.w() - Glass.MARK - Tokens.Space.COZY, 2).size();
            int top = above - lines * Tokens.Type.BODY.leading();
            Glass.blockedMark(painter, side.x() + Glass.MARK / 2.0f, top + Tokens.Type.BODY.leading() / 2.0f,
                Tokens.Color.STATUS_DANGER);
            Typeset.drawWrapped(painter, Tokens.Type.BODY, why, side.x() + Glass.MARK + Tokens.Space.COZY, top,
                side.w() - Glass.MARK - Tokens.Space.COZY, 2, Tokens.Color.INK_SECONDARY);
        }
    }

    // ── the foot ────────────────────────────────────────────────

    /** The server's answer to the last action, or with none, the wallet and the pass the player holds. */
    private void foot(Painter painter) {
        Box f = layout.foot();
        Glass.hair(painter, f.x(), f.y(), f.w());
        int x = f.x() + Tokens.Space.LOOSE;
        int room = f.w() - Tokens.Space.LOOSE * 2;
        int strongY = Typeset.centred(Tokens.Type.STRONG, f.y(), f.h());
        if (notice.isPresent()) {
            SeasonProtocol.Notice said = notice.orElseThrow();
            boolean ok = said.tone() == SeasonProtocol.Tone.OK;
            String text = Typeset.ellipsized(Tokens.Type.STRONG, said.text(), room - Glass.MARK - Tokens.Space.SNUG);
            Glass.markedLine(painter, x, strongY, text, ok ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_DANGER, !ok);
            return;
        }
        int bodyY = Typeset.centred(Tokens.Type.BODY, f.y(), f.h());
        x += Typeset.draw(painter, Tokens.Type.BODY, "잔액", x, bodyY, Tokens.Color.INK_TERTIARY) + Tokens.Space.BASE;
        Optional<String> balance = pass.me().balance();
        x += Typeset.tabular(painter, Tokens.Type.STRONG, balance.orElse("—"), x, strongY, Tokens.Color.INK_SECONDARY)
            + Tokens.Space.GUTTER;
        x += Typeset.draw(painter, Tokens.Type.BODY, "플러스", x, bodyY, Tokens.Color.INK_TERTIARY) + Tokens.Space.BASE;
        boolean owned = pass.me().plus();
        Typeset.draw(painter, Tokens.Type.STRONG, owned ? "사용 중" : "없음", x, strongY,
            owned ? Tokens.Color.CHAT_PURPLE : Tokens.Color.INK_TERTIARY);
    }

    private void hints(Painter painter) {
        List<Glass.Hint> hints = new ArrayList<>();
        switch (tab) {
            case TRACK -> {
                hints.add(new Glass.Hint("←→↑↓", I18n.get("fullmoon.menu.hint.move")));
                hints.add(new Glass.Hint("Enter", I18n.get("fullmoon.season.hint.claim")));
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
        Glass.hints(painter, layout.panel().midX(), layout.hintY(), hints, keyboard);
    }
}
