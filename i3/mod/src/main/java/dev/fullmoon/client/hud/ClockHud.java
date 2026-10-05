package dev.fullmoon.client.hud;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.title.MoonPhase;

import net.minecraft.client.Minecraft;

/** The real clock, with tonight's real moon beside it. */
public final class ClockHud extends BaseHudElement {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final float MOON_R = 5.0f;

    /** The chip and the moon for the wall-clock second last read; both move far slower than a frame. */
    private List<Part> live = List.of();
    private MoonPhase moon;
    private long liveSecond = Long.MIN_VALUE;

    public ClockHud() {
        super("clock", "시계", "일반", true, Anchor.TOP_RIGHT, Tokens.Space.COZY, Tokens.Space.COZY);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return chipWidth(moonWidth(), parts());
    }

    private static int moonWidth() {
        return Math.round(MOON_R * 2) + GAP;
    }

    @Override
    protected int drawLeadingMark(Painter painter, int x, float cy) {
        parts();
        painter.moon(x + MOON_R, cy, MOON_R, moon.lit(), moon.waxing(),
            Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        return moonWidth();
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        drawChip(painter, bounds, 0, parts());
    }

    private List<Part> parts() {
        long now = System.currentTimeMillis();
        long second = Math.floorDiv(now, 1000L);
        if (second != liveSecond) {
            liveSecond = second;
            Instant instant = Instant.ofEpochMilli(now);
            live = List.of(new Part(LocalTime.ofInstant(instant, ZoneId.systemDefault()).format(TIME_FMT), ""));
            moon = MoonPhase.at(instant);
        }
        return live;
    }
}
