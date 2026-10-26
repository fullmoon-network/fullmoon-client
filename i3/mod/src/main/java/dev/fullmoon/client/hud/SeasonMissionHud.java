package dev.fullmoon.client.hud;

import java.util.Optional;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.network.SeasonProtocol;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.season.SeasonCopy;
import dev.fullmoon.client.season.SeasonItems;
import dev.fullmoon.client.season.SeasonSample;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Glass;

import net.minecraft.client.Minecraft;

/**
 * The season mission a player follows: the pass level as a moon filling toward the next one, the
 * tracked mission's count and bar, and for a moment under it a stage just finished or another
 * mission that just moved. The server drops its progress boss bar for a player who has this on
 * screen, so whether it is on screen is reported every time that changes.
 */
public final class SeasonMissionHud extends BaseHudElement {
    public static final String ID = "season";
    private static final int WIDTH = 168;
    private static final int HEADER = Tokens.Size.HUD_CHIP;
    private static final int LINE = Tokens.Size.HUD_CHIP;
    private static final int ROW = Tokens.Space.SECTION;
    private static final int ICON = 16;
    private static final int SMALL_ICON = 12;
    private static final float MOON_R = 4.0f;
    private static final BridgeState.SeasonFeed SAMPLE = new BridgeState.SeasonFeed(27, 95, 147,
        Optional.of(new SeasonProtocol.Goal("daily_coal", "석탄 캐기", "minecraft:coal", 13, 32, "개", false)),
        Optional.empty(), 0, Optional.empty(), 0);

    public SeasonMissionHud() {
        super(ID, "시즌 미션", "서버", true, Anchor.TOP_LEFT, Tokens.Space.COZY,
            Tokens.Space.COZY + (Tokens.Size.HUD_CHIP + Tokens.Space.SNUG) * 2);
    }

    /** Tells the server, once per change, whether the widget is on and has a season to show. */
    public static void report(Minecraft client) {
        HudElement element = HudElementRegistry.getInstance().get(ID);
        FullmoonChannel.seasonHud(element != null && element.enabled() && FullmoonChannel.season().isPresent());
    }

    @Override
    public int measureWidth(Minecraft client) {
        return measureWidth(client, false);
    }

    @Override
    public int measureWidth(Minecraft client, boolean isEditor) {
        return isEditor || feed(System.currentTimeMillis()).isPresent() ? WIDTH : 0;
    }

    /** The editor asks for the height without saying it is the editor; it is, while its screen is up. */
    @Override
    public int measureHeight(Minecraft client) {
        if (client.screen instanceof HudEditorScreen) {
            return height(SAMPLE, 0, true);
        }
        long now = System.currentTimeMillis();
        return feed(now).map(feed -> height(feed, now, false)).orElse(0);
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        long now = System.currentTimeMillis();
        Optional<BridgeState.SeasonFeed> shown = isEditor ? Optional.of(SAMPLE) : feed(now);
        if (shown.isEmpty() || bounds.empty()) {
            return;
        }
        BridgeState.SeasonFeed feed = shown.get();
        drawContainer(painter, bounds);
        int x = bounds.x() + PADDING_H;
        int right = bounds.right() - PADDING_H;
        int y = bounds.y();
        header(painter, feed, x, right, y);
        y += HEADER;
        y += tracked(painter, feed.tracked(), x, right, y);
        if (isEditor) {
            return;
        }
        Optional<SeasonProtocol.Flash> flash = feed.liveFlash(now);
        if (flash.isPresent()) {
            float was = painter.opacity();
            painter.opacity(was * fade(now - feed.flashAt(), BridgeState.SEASON_FLASH_MILLIS));
            finished(painter, flash.get(), x, right, y);
            painter.opacity(was);
            y += LINE;
        }
        Optional<SeasonProtocol.Goal> recent = feed.liveRecent(now);
        if (recent.isPresent()) {
            float was = painter.opacity();
            painter.opacity(was * fade(now - feed.recentAt(), BridgeState.SEASON_RECENT_MILLIS));
            moved(painter, recent.get(), x, right, y);
            painter.opacity(was);
        }
    }

    /** The live feed, or while a season fixture is up and no server has sent one, the fixture's. */
    private static Optional<BridgeState.SeasonFeed> feed(long now) {
        Optional<BridgeState.SeasonFeed> live = FullmoonChannel.season();
        return live.isPresent() ? live : SeasonSample.feed(now);
    }

    static int height(BridgeState.SeasonFeed feed, long now, boolean editor) {
        int h = HEADER + (feed.tracked().isPresent() ? ROW : LINE);
        if (!editor) {
            h += feed.liveFlash(now).isPresent() ? LINE : 0;
            h += feed.liveRecent(now).isPresent() ? LINE : 0;
        }
        return h + Tokens.Space.TIGHT;
    }

    /** A passing line's opacity: whole, then out over the last slow beat of its life. */
    static float fade(long age, long life) {
        return Math.clamp((life - age) / (float) Tokens.Duration.SLOW, 0.0f, 1.0f);
    }

    private static void header(Painter painter, BridgeState.SeasonFeed feed, int x, int right, int y) {
        painter.moon(x + MOON_R, y + HEADER / 2.0f, MOON_R, feed.fraction(), true,
            Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        int textX = x + Math.round(MOON_R * 2) + GAP;
        int valueY = Typeset.centred(Tokens.Type.ROW, y, HEADER);
        int unitY = Typeset.centred(Tokens.Type.BODY, y, HEADER);
        textX += Typeset.tabular(painter, Tokens.Type.ROW, Integer.toString(feed.level()), textX, valueY,
            Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.BODY, "레벨", textX + GAP, unitY, Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.BODY,
            SeasonCopy.number(feed.into()) + " / " + SeasonCopy.number(feed.step()), right, unitY,
            Tokens.Color.INK_TERTIARY);
        int ruleY = y + HEADER - Tokens.Space.HAIR;
        painter.fill(x, ruleY, right - x, Tokens.Space.HAIR, Tokens.Color.LINE_HAIRLINE);
        int lit = Math.round((right - x) * feed.fraction());
        if (lit > 0) {
            painter.fill(x, ruleY, lit, Tokens.Space.HAIR, Tokens.Color.MOON_LIT);
        }
    }

    private static int tracked(Painter painter, Optional<SeasonProtocol.Goal> tracked, int x, int right, int y) {
        if (tracked.isEmpty()) {
            Typeset.draw(painter, Tokens.Type.BODY, "추적 중인 미션이 없어요", x,
                Typeset.centred(Tokens.Type.BODY, y, LINE), Tokens.Color.INK_TERTIARY);
            return LINE;
        }
        SeasonProtocol.Goal goal = tracked.get();
        SeasonItems.draw(painter, SeasonItems.stack(goal.material(), ""), x, y + (ROW - ICON) / 2, ICON);
        int textX = x + ICON + GAP;
        int bandY = y + Tokens.Space.TIGHT;
        int band = ROW - Tokens.Space.COZY - Tokens.Space.TIGHT;
        int ink = goal.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.INK_SECONDARY;
        int countW = Typeset.tabularRight(painter, Tokens.Type.BODY,
            SeasonCopy.count(goal.progress(), goal.goal(), goal.unit()), right,
            Typeset.centred(Tokens.Type.BODY, bandY, band), ink);
        Typeset.draw(painter, Tokens.Type.ROW,
            Typeset.ellipsized(Tokens.Type.ROW, goal.name(), Math.max(0, right - countW - GAP - textX)), textX,
            Typeset.centred(Tokens.Type.ROW, bandY, band), Tokens.Color.INK_PRIMARY);
        int barY = bandY + band + Tokens.Space.HAIR;
        int barW = right - textX;
        painter.fill(textX, barY, barW, Tokens.Space.TIGHT, Tokens.Color.LINE_HAIRLINE);
        int lit = Math.round(barW * goal.fraction());
        if (lit > 0) {
            painter.fill(textX, barY, lit, Tokens.Space.TIGHT, goal.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.MOON_LIT);
        }
        return ROW;
    }

    private static void finished(Painter painter, SeasonProtocol.Flash flash, int x, int right, int y) {
        int room = right - x - Glass.markedWidth("");
        Glass.markedLine(painter, x, Typeset.centred(Tokens.Type.STRONG, y, LINE),
            Typeset.ellipsized(Tokens.Type.STRONG, flash.label(), Math.max(0, room)), Tokens.Color.STATUS_LIVE, false);
    }

    private static void moved(Painter painter, SeasonProtocol.Goal goal, int x, int right, int y) {
        int inset = (ICON - SMALL_ICON) / 2;
        SeasonItems.draw(painter, SeasonItems.stack(goal.material(), ""), x + inset, y + (LINE - SMALL_ICON) / 2,
            SMALL_ICON);
        int textX = x + ICON + GAP;
        int textY = Typeset.centred(Tokens.Type.BODY, y, LINE);
        int countW = Typeset.tabularRight(painter, Tokens.Type.BODY,
            SeasonCopy.count(goal.progress(), goal.goal(), goal.unit()), right, textY,
            goal.done() ? Tokens.Color.STATUS_LIVE : Tokens.Color.INK_TERTIARY);
        Typeset.draw(painter, Tokens.Type.BODY,
            Typeset.ellipsized(Tokens.Type.BODY, goal.name(), Math.max(0, right - countW - GAP - textX)), textX, textY,
            Tokens.Color.INK_SECONDARY);
    }
}
