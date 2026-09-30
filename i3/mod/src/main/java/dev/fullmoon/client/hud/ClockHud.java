package dev.fullmoon.client.hud;

import java.time.Instant;
import java.time.LocalTime;
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
        MoonPhase moon = MoonPhase.at(Instant.now());
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

    private static List<Part> parts() {
        return List.of(new Part(LocalTime.now().format(TIME_FMT), ""));
    }
}
