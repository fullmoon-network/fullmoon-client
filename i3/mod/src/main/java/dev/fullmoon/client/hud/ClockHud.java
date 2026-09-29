package dev.fullmoon.client.hud;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.title.MoonPhase;

import net.minecraft.client.Minecraft;

/** Real-world clock chip, with tonight's real moon beside the time. */
public final class ClockHud extends BaseHudElement {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final float MOON_R = 4.0f;

    public ClockHud() {
        super("clock", "시계", "일반", true, Anchor.TOP_RIGHT, 16, 82);
    }

    @Override
    public int measureWidth(Minecraft client) {
        String text = formatText(client, false);
        return PADDING_H * 2 + moonWidth() + Typeset.width(Tokens.Type.MICRO, "TIME") + Tokens.Space.SNUG
            + Typeset.width(Tokens.Type.STRONG, text);
    }

    private static int moonWidth() {
        return Math.round(MOON_R * 2) + Tokens.Space.SNUG;
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
        String text = formatText(client, isEditor);
        drawChip(painter, bounds, "TIME", text, 0);
    }

    private String formatText(Minecraft client, boolean isEditor) {
        return LocalTime.now().format(TIME_FMT);
    }
}
