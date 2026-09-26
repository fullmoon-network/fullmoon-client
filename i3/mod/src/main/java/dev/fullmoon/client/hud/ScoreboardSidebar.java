package dev.fullmoon.client.hud;

import java.util.Comparator;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.menu.ServerMenuSample;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Palace;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The server's sidebar scoreboard in the palace frame. The server owns every line, its colours and
 * its order; the client owns the frame and the type, so a lobby's sidebar reads as part of the same
 * HUD as the chips beside it. Which objective shows, the order, the fifteen-line cap and hidden
 * entries all follow vanilla.
 */
public final class ScoreboardSidebar {
    private static final int MAX_LINES = 15;
    private static final int PAD = Tokens.Space.COZY;
    private static final int TICK = 4;
    private static final Comparator<PlayerScoreEntry> ORDER =
        Comparator.comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    /** One line: the name the server gave it and its value, which a blank number format empties. */
    record Line(Component name, Component value) {}

    private ScoreboardSidebar() {}

    public static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> ScoreboardSidebar::extract);
    }

    private static void extract(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.options.hideGui) {
            return;
        }
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) {
                objective = scoreboard.getDisplayObjective(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return;
        }
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<Line> lines = scoreboard.listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .sorted(ORDER)
            .limit(MAX_LINES)
            .map(entry -> new Line(
                PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()),
                entry.formatValue(format)))
            .toList();
        gfx.nextStratum();
        draw(new Painter(gfx), objective.getDisplayName(), lines);
    }

    /** The {@code sidebar} fixture: the lobby's sidebar as the live rehearsal saw it. */
    public static void drawFixture(Painter painter) {
        if (!System.getProperty(ServerMenuSample.PROPERTY, "").equals("sidebar")) {
            return;
        }
        draw(painter, Component.literal("풀문").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
            line("소지금", "미연동", ChatFormatting.YELLOW),
            line("접속자", "1명", ChatFormatting.WHITE),
            line("위치", "로비", ChatFormatting.AQUA),
            line("플레이", "48초", ChatFormatting.WHITE)));
    }

    private static Line line(String label, String value, ChatFormatting colour) {
        return new Line(Component.literal(label + " ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(colour)), Component.empty());
    }

    private static void draw(Painter painter, Component title, List<Line> lines) {
        int titleW = Typeset.width(Tokens.Type.HEADING, title);
        int rowsW = 0;
        for (Line line : lines) {
            int valueW = Typeset.width(Tokens.Type.BODY_STRONG, line.value());
            rowsW = Math.max(rowsW, Typeset.width(Tokens.Type.BODY, line.name())
                + (valueW > 0 ? Tokens.Space.COZY + valueW : 0));
        }
        int w = Math.max(titleW, rowsW) + PAD * 2;
        int rule = Tokens.Space.SNUG * 2 + Tokens.Stroke.HAIR;
        int h = PAD + Tokens.Type.HEADING.leading() + rule + lines.size() * Tokens.Type.BODY.leading() + PAD;
        int x = painter.width() - w - Tokens.Space.COZY;
        int y = Math.max(Tokens.Space.COZY, painter.height() / 2 - h / 3);

        painter.fill(x, y, w, h, Tokens.Radius.NONE, Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.82f));
        painter.border(x, y, w, h, Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT_FAINT);
        Palace.ticks(painter, x - 1, y - 1, w + 2, h + 2, TICK);

        int cursor = y + PAD;
        Typeset.draw(painter, Tokens.Type.HEADING, title, x + (w - titleW) / 2, cursor, Tokens.Color.ACCENT);
        cursor += Tokens.Type.HEADING.leading() + Tokens.Space.SNUG;
        Palace.dashedRule(painter, x + PAD, cursor, w - PAD * 2);
        cursor += Tokens.Stroke.HAIR + Tokens.Space.SNUG;
        for (Line line : lines) {
            Typeset.draw(painter, Tokens.Type.BODY, line.name(), x + PAD, cursor, Tokens.Color.INK_SECONDARY);
            int valueW = Typeset.width(Tokens.Type.BODY_STRONG, line.value());
            if (valueW > 0) {
                Typeset.draw(painter, Tokens.Type.BODY_STRONG, line.value(), x + w - PAD - valueW, cursor,
                    Tokens.Color.INK_PRIMARY);
            }
            cursor += Tokens.Type.BODY.leading();
        }
    }
}
