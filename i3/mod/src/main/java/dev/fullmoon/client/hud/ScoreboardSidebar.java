package dev.fullmoon.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.menu.ServerMenuSample;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Glass;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The server's sidebar scoreboard on HUD glass. The server owns every line, its colours and its
 * order; the client owns the ground and the type, so a lobby's sidebar reads as part of the same
 * HUD as the chips beside it. Which objective shows, the order, the fifteen-line cap and hidden
 * entries all follow vanilla.
 *
 * <p>A line is read as {@code label value}, split at its last space: the label in body ink, the
 * value in strong ink on the right in the colour the server gave it, translated to the glass
 * palette so a {@code §e} is the same gold the client uses and not the chat's. A line that is a
 * command is the sidebar's help and sits under a hairline; a line that is only dashes is a rule.
 */
public final class ScoreboardSidebar {
    private static final int MAX_LINES = 15;
    private static final int W = Tokens.Size.SIDEBAR;
    private static final int PAD_X = Tokens.Space.COZY;
    private static final int PAD_Y = Tokens.Space.BASE;
    private static final int LINE = Tokens.Space.LOOSE;
    private static final Pattern LEGACY_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Comparator<PlayerScoreEntry> ORDER =
        Comparator.comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    /** One line: the name the server gave it and its value, which a blank number format empties. */
    record Line(Component name, Component value) {}

    /** One coloured stretch of a server line, its colour already the glass palette's. */
    record Run(String text, int color) {}

    /** A line read as label and value; a line with no value is all label. */
    record Split(String label, String value, int valueColor) {}

    private ScoreboardSidebar() {}

    public static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> ScoreboardSidebar::extract);
    }

    private static void extract(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.options.hideGui || HudOverlay.underGlass()) {
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
        Painter painter = new Painter(gfx);
        List<Box> occupied = new ArrayList<>();
        for (HudElement element : dev.fullmoon.client.hud.HudElementRegistry.getInstance().elements()) {
            if (element.enabled()) {
                occupied.add(element.computeBounds(painter.width(), painter.height(), client));
            }
        }
        draw(painter, objective.getDisplayName(), lines, occupied);
    }

    /** The {@code sidebar} fixture: the lobby's sidebar as the live rehearsal saw it. */
    public static void drawFixture(Painter painter) {
        if (!System.getProperty(ServerMenuSample.PROPERTY, "").equals("sidebar")) {
            return;
        }
        draw(painter, Component.literal("풀문").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
            new Line(Component.literal("────────").withStyle(ChatFormatting.DARK_GRAY), Component.empty()),
            line("소지금", "2억원", ChatFormatting.YELLOW),
            line("접속자", "1명", ChatFormatting.WHITE),
            line("위치", "로비", ChatFormatting.AQUA),
            line("플레이", "39초", ChatFormatting.WHITE),
            new Line(Component.literal("/텔레포트 ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("로비 곳곳으로 이동").withStyle(ChatFormatting.GRAY)), Component.empty())),
            List.of());
    }

    private static Line line(String label, String value, ChatFormatting colour) {
        return new Line(Component.literal(label + " ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(colour)), Component.empty());
    }

    /**
     * The mockup's spot at the right edge, a hundred pixels down, lifted clear of any HUD element
     * in the way and moved left of one when lifting runs out of screen.
     */
    static Box place(int screenW, int screenH, int w, int h, List<Box> occupied) {
        Box box = new Box(screenW - w - Tokens.Space.COZY,
            Math.max(Tokens.Space.COZY, Math.min(Tokens.Size.SIDEBAR_TOP, screenH - h - Tokens.Space.COZY)), w, h);
        for (int pass = 0; pass <= occupied.size(); pass++) {
            Box hit = null;
            for (Box other : occupied) {
                if (overlaps(box, other)) {
                    hit = other;
                    break;
                }
            }
            if (hit == null) {
                return box;
            }
            int up = hit.y() - Tokens.Space.SNUG - h;
            box = up >= Tokens.Space.COZY
                ? new Box(box.x(), up, w, h)
                : new Box(hit.x() - Tokens.Space.SNUG - w, box.y(), w, h);
        }
        return box;
    }

    private static boolean overlaps(Box a, Box b) {
        int gap = Tokens.Space.SNUG;
        return a.x() < b.right() + gap && b.x() < a.right() + gap
            && a.y() < b.bottom() + gap && b.y() < a.bottom() + gap;
    }

    /**
     * A line that is only a drawn rule. Servers draw them with box-drawing dashes, which the
     * faces do not carry, so the sidebar draws its own rule instead. A line's text carries the
     * team entry's {@code §} code after the prefix, so those codes are not part of what it says.
     */
    static boolean isRule(String text) {
        String stripped = LEGACY_CODE.matcher(text).replaceAll("").strip();
        return !stripped.isEmpty() && stripped.chars().allMatch(c ->
            c == '-' || c == '_' || c == '=' || c == '—' || c == '―' || (c >= 0x2500 && c <= 0x257F));
    }

    /** A line that names a command is the sidebar's help, and sits apart under a hairline. */
    static boolean isHelp(String text) {
        return LEGACY_CODE.matcher(text).replaceAll("").strip().startsWith("/");
    }

    /** The coloured stretches of a component, flattened, with the server's colours translated. */
    static List<Run> runs(Component component) {
        List<Run> runs = new ArrayList<>();
        component.visit((style, text) -> {
            String clean = LEGACY_CODE.matcher(text).replaceAll("");
            if (!clean.isEmpty()) {
                runs.add(new Run(clean, chatColor(style.getColor())));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    /**
     * A server colour on the glass. The named chat colours are the game's own palette, tuned for
     * black text shadows on a world; here they are mapped onto the tokens they mean. A colour the
     * server picked by hex is its own and passes through.
     */
    static int chatColor(TextColor color) {
        if (color == null) {
            return 0;
        }
        return switch (color.serialize()) {
            case "yellow", "gold" -> Tokens.Color.CHAT_YELLOW;
            case "aqua", "dark_aqua" -> Tokens.Color.CHAT_AQUA;
            case "green", "dark_green" -> Tokens.Color.STATUS_LIVE;
            case "red", "dark_red" -> Tokens.Color.STATUS_DANGER;
            case "blue", "dark_blue" -> Tokens.Color.CHAT_BLUE;
            case "light_purple", "dark_purple" -> Tokens.Color.CHAT_PURPLE;
            case "gray" -> Tokens.Color.INK_SECONDARY;
            case "dark_gray" -> Tokens.Color.INK_TERTIARY;
            case "white", "black" -> Tokens.Color.INK_PRIMARY;
            default -> 0xFF000000 | color.getValue();
        };
    }

    /**
     * {@code 소지금 2억원} is a label and a value, split at the last space. The value takes the
     * colour of the run it ends in when the server gave it one; a line without a space, or with
     * nothing after it, is all label.
     */
    static Split split(List<Run> runs) {
        StringBuilder all = new StringBuilder();
        for (Run run : runs) {
            all.append(run.text());
        }
        String text = all.toString().strip();
        int space = text.lastIndexOf(' ');
        if (space < 0 || space == text.length() - 1) {
            return new Split(text, "", 0);
        }
        String value = text.substring(space + 1);
        int color = 0;
        int seen = 0;
        int valueStart = all.indexOf(value, space);
        for (Run run : runs) {
            if (seen + run.text().length() > valueStart) {
                color = run.color();
            }
            seen += run.text().length();
        }
        return new Split(text.substring(0, space).strip(), value, color);
    }

    private static void draw(Painter painter, Component title, List<Line> all, List<Box> occupied) {
        // The title is ruled off by its own spacing; a server's rule straight under it would double it.
        List<Line> lines = !all.isEmpty() && isRule(all.getFirst().name().getString()) ? all.subList(1, all.size()) : all;
        int rows = 0;
        int help = 0;
        for (Line line : lines) {
            if (isHelp(line.name().getString())) {
                help++;
            } else {
                rows++;
            }
        }
        int h = PAD_Y + Tokens.Type.ROW.leading() + Tokens.Space.SNUG + rows * LINE
            + (help > 0 ? Tokens.Space.SNUG * 2 + Tokens.Stroke.HAIR + help * LINE : 0) + PAD_Y;
        Box at = place(painter.width(), painter.height(), W, h, occupied);
        int x = at.x();
        int y = at.y();
        int inner = W - PAD_X * 2;
        painter.fill(x, y, W, h, Tokens.Radius.NONE, Tokens.Color.SURFACE_GLASS_HUD);

        int cursor = y + PAD_Y;
        List<Run> titleRuns = runs(title);
        int titleColor = Tokens.Color.ACCENT;
        for (Run run : titleRuns) {
            if (run.color() != 0) {
                titleColor = run.color();
                break;
            }
        }
        Typeset.draw(painter, Tokens.Type.ROW, Typeset.ellipsized(Tokens.Type.ROW, title.getString().strip(), inner),
            x + PAD_X, cursor, titleColor);
        cursor += Tokens.Type.ROW.leading() + Tokens.Space.SNUG;

        boolean ruled = false;
        for (Line line : lines) {
            String raw = line.name().getString();
            if (isHelp(raw)) {
                if (!ruled) {
                    cursor += Tokens.Space.SNUG;
                    Glass.hair(painter, x + PAD_X, cursor, inner);
                    cursor += Tokens.Stroke.HAIR + Tokens.Space.SNUG;
                    ruled = true;
                }
                helpLine(painter, x + PAD_X, cursor, inner, LEGACY_CODE.matcher(raw).replaceAll("").strip());
                cursor += LINE;
                continue;
            }
            if (isRule(raw) && line.value().getString().isBlank()) {
                Glass.hair(painter, x + PAD_X, cursor + LINE / 2, inner);
                cursor += LINE;
                continue;
            }
            Split split = split(runs(line.name()));
            String value = split.value();
            int valueColor = split.valueColor();
            String label = split.label();
            if (!line.value().getString().isBlank()) {
                value = line.value().getString().strip();
                valueColor = chatColor(line.value().getStyle().getColor());
                label = raw.strip();
            }
            int textY = Typeset.centred(Tokens.Type.BODY, cursor, LINE);
            int valueW = value.isEmpty() ? 0 : Typeset.tabularWidth(Tokens.Type.STRONG, value);
            if (valueW > 0) {
                Typeset.tabular(painter, Tokens.Type.STRONG, value, x + W - PAD_X - valueW, textY,
                    valueColor == 0 ? Tokens.Color.INK_PRIMARY : valueColor);
            }
            int room = inner - (valueW > 0 ? valueW + Tokens.Space.COZY : 0);
            Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, label, room),
                x + PAD_X, textY, Tokens.Color.INK_SECONDARY);
            cursor += LINE;
        }
    }

    /** {@code /텔레포트 로비 곳곳으로 이동}: the command in strong ink, what it does in body. */
    private static void helpLine(Painter painter, int x, int y, int w, String text) {
        int space = text.indexOf(' ');
        String command = space < 0 ? text : text.substring(0, space);
        String rest = space < 0 ? "" : text.substring(space + 1).strip();
        int textY = Typeset.centred(Tokens.Type.BODY, y, LINE);
        int used = Typeset.draw(painter, Tokens.Type.STRONG, Typeset.ellipsized(Tokens.Type.STRONG, command, w),
            x, textY, Tokens.Color.INK_PRIMARY);
        if (!rest.isEmpty()) {
            int restX = x + used + Tokens.Space.SNUG;
            Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, rest, x + w - restX),
                restX, textY, Tokens.Color.INK_SECONDARY);
        }
    }
}
