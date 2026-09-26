package dev.fullmoon.client.hud;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.menu.MenuIcons;
import dev.fullmoon.client.menu.ServerMenuSample;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.CasinoProtocol;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.render.Motion;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Palace;

/**
 * The settled-bet card: rises above the hotbar, plays the game's reveal, then states the result.
 * The money has already moved when the payload arrives, so nothing here waits on the player —
 * the reveal is presentation over a known outcome and the chat line stays the record.
 */
public final class CasinoResultOverlay {
    private static final int WIDTH = 232;
    private static final int HEIGHT = 84;
    private static final int CHIP = 22;
    private static final int STAGE = 20;
    private static final int POCKET = 18;
    private static final int COIN = 18;
    private static final int REEL_MAX = 48;
    private static final int BOTTOM_CLEARANCE = Tokens.Space.FIELD + Tokens.Space.LOOSE;
    private static final int COIN_HALF_TURNS = 6;
    private static final int WHEEL_LAPS = 2;

    static final int ENTER = Tokens.Duration.BASE;
    static final int SETTLE = ENTER + Tokens.Duration.REVEAL;
    static final long LEAVE = BridgeState.CASINO_CARD_MILLIS - Tokens.Duration.BASE;

    /** European single-zero wheel order, clockwise from zero. */
    private static final int[] WHEEL = {
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
    };
    private static final Set<Integer> RED = Set.of(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final List<String> REEL_ORDER =
        List.of("cherry", "lemon", "bell", "star", "diamond", "seven", "moon");
    private static final Map<String, String> SYMBOLS = Map.of(
        "cherry", "체리", "lemon", "레몬", "bell", "종", "star", "별",
        "diamond", "다이아", "seven", "세븐", "moon", "만월");

    private CasinoResultOverlay() {}

    public static void draw(Painter painter, long now) {
        FullmoonChannel.casino(now).or(() -> ServerMenuSample.casinoReveal(now)).ifPresent(reveal ->
            draw(painter, reveal.result(), now - reveal.receivedAt()));
    }

    private static void draw(Painter painter, CasinoProtocol.Result result, long age) {
        int width = Math.min(WIDTH, painter.width() - Tokens.Space.SECTION * 2);
        int x = (painter.width() - width) / 2;
        int y = painter.height() - HEIGHT - BOTTOM_CLEARANCE;
        int top = y + Math.round(HEIGHT * hidden(age));
        boolean settled = age >= SETTLE;
        if (top >= y + HEIGHT) {
            return;
        }

        // One pixel of slack all round: the verdict ticks sit just outside the card's edge.
        painter.pushClip(x - 1, y - 1, width + 2, HEIGHT + 2);
        painter.fill(x, top, width, HEIGHT, Tokens.Radius.NONE,
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.90f));
        painter.border(x, top, width, HEIGHT, Tokens.Radius.NONE,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT_FAINT);
        // The frame's brackets carry the verdict: gilt for a win, the plain gilt line otherwise.
        Palace.ticks(painter, x - 1, top - 1, width + 2, HEIGHT + 2, Tokens.Space.COZY,
            settled && result.won() ? Tokens.Color.ACCENT : Tokens.Color.LINE_GILT);

        int chipX = x + Tokens.Space.LOOSE + CHIP / 2;
        int chipY = top + Tokens.Space.COZY + CHIP / 2;
        painter.dot(chipX, chipY, CHIP / 2f, Tokens.Color.SURFACE_SUNKEN);
        painter.ring(chipX, chipY, CHIP / 2f, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT);
        MenuIcons.draw(painter, "fullmoon.casino." + result.game().wireName(),
            chipX, chipY, CHIP - Tokens.Space.SNUG);

        int textX = x + Tokens.Space.LOOSE + CHIP + Tokens.Space.COZY;
        int right = x + width - Tokens.Space.LOOSE;
        int eyebrowY = top + Tokens.Space.COZY;
        int titleY = eyebrowY + Tokens.Type.LABEL.leading();
        Typeset.draw(painter, Tokens.Type.LABEL, "풀문 카지노 · " + gameName(result.game()),
            textX, eyebrowY, Tokens.Color.INK_TERTIARY);
        int pill = settled && result.won() ? drawPill(painter, multiplier(result.payoutMultiplier()), right, eyebrowY) : 0;
        String title = Typeset.fittingPrefix(Tokens.Type.BODY_STRONG,
            title(result, settled), right - textX - pill);
        Typeset.draw(painter, Tokens.Type.BODY_STRONG, title, textX, titleY,
            settled && result.won() ? Tokens.Color.ACCENT : Tokens.Color.INK_PRIMARY);

        int stageX = x + Tokens.Space.LOOSE;
        int stageY = top + Tokens.Space.COZY + CHIP + Tokens.Space.BASE;
        int stageWidth = width - Tokens.Space.LOOSE * 2;
        switch (result.detail()) {
            case CasinoProtocol.Reels reels ->
                drawReels(painter, reels, result.won() && settled, stageX, stageY, stageWidth, age);
            case CasinoProtocol.Roll roll ->
                drawRoll(painter, roll, result.won(), settled, stageX, stageY, stageWidth, age);
            case CasinoProtocol.Spin spin ->
                drawWheel(painter, spin, result.won(), settled, stageX, stageY, stageWidth, age);
            case CasinoProtocol.Coin ignored ->
                drawCoin(painter, result.won(), stageX + stageWidth / 2, stageY + STAGE / 2, age);
        }

        String detail = Typeset.fittingPrefix(Tokens.Type.BODY, detail(result, settled), stageWidth);
        Typeset.draw(painter, Tokens.Type.BODY, detail, stageX,
            stageY + STAGE + Tokens.Space.BASE, Tokens.Color.INK_SECONDARY);
        painter.popClip();
    }

    private static int drawPill(Painter painter, String text, int right, int y) {
        int width = Typeset.tabularWidth(Tokens.Type.LABEL, text) + Tokens.Space.BASE * 2;
        int height = Tokens.Type.LABEL.leading() + Tokens.Space.TIGHT;
        painter.fill(right - width, y - Tokens.Space.HAIR, width, height, Tokens.Radius.ROUND,
            Tokens.Color.ACCENT);
        Typeset.tabularRight(painter, Tokens.Type.LABEL, text, right - Tokens.Space.BASE,
            Typeset.centred(Tokens.Type.LABEL, y - Tokens.Space.HAIR, height), Tokens.Color.INK_ON_ACCENT);
        return width + Tokens.Space.COZY;
    }

    private static void drawReels(Painter painter, CasinoProtocol.Reels reels, boolean won,
            int x, int y, int width, long age) {
        int count = reels.symbols().size();
        int cell = Math.min(REEL_MAX, (width - (count - 1) * Tokens.Space.SNUG) / count);
        int left = x + (width - (cell * count + (count - 1) * Tokens.Space.SNUG)) / 2;
        int leading = Tokens.Type.BODY_STRONG.leading();
        for (int i = 0; i < count; i++) {
            int cellX = left + i * (cell + Tokens.Space.SNUG);
            String symbol = reels.symbols().get(i);
            long stop = reelStop(i, count);
            boolean hit = won && reels.matched() >= 2
                && reels.symbols().stream().filter(symbol::equals).count() == reels.matched();
            painter.fill(cellX, y, cell, STAGE, Tokens.Radius.SM,
                hit ? Tokens.Color.ACCENT_WASH : Tokens.Color.SURFACE_SUNKEN);
            painter.border(cellX, y, cell, STAGE, Tokens.Radius.SM, Tokens.Stroke.HAIR,
                hit ? Tokens.Color.ACCENT : Tokens.Color.LINE_HAIRLINE);
            painter.pushClip(cellX, y, cell, STAGE);
            int centre = cellX + cell / 2;
            int baseline = Typeset.centred(Tokens.Type.BODY_STRONG, y, STAGE);
            if (age < stop) {
                long frame = Math.max(0, age) / Tokens.Duration.FAST + i * 3L;
                int scroll = Math.round(leading * Motion.progress(
                    Math.max(0, age) % Tokens.Duration.FAST, Tokens.Duration.FAST));
                String current = REEL_ORDER.get((int) (frame % REEL_ORDER.size()));
                String next = REEL_ORDER.get((int) ((frame + 1) % REEL_ORDER.size()));
                Typeset.drawCentered(painter, Tokens.Type.BODY_STRONG, symbolName(current),
                    centre, baseline - scroll, Tokens.Color.INK_TERTIARY);
                Typeset.drawCentered(painter, Tokens.Type.BODY_STRONG, symbolName(next),
                    centre, baseline - scroll + leading, Tokens.Color.INK_TERTIARY);
            } else {
                int drop = Math.round(Tokens.Space.SNUG * (1 - Motion.ease(Tokens.Easing.OUT,
                    Motion.progress(age - stop, Tokens.Duration.BASE))));
                Typeset.drawCentered(painter, Tokens.Type.BODY_STRONG, symbolName(symbol),
                    centre, baseline - drop, hit ? Tokens.Color.ACCENT : Tokens.Color.INK_PRIMARY);
            }
            painter.popClip();
        }
    }

    private static void drawRoll(Painter painter, CasinoProtocol.Roll roll, boolean won,
            boolean settled, int x, int y, int width, long age) {
        int trackHeight = Tokens.Space.SNUG;
        int trackY = y + STAGE - trackHeight - Tokens.Space.TIGHT;
        painter.fill(x, trackY, width, trackHeight, Tokens.Radius.ROUND, Tokens.Color.SURFACE_SUNKEN);
        float zone = width * roll.target() / 100f;
        painter.fill(x, trackY, zone, trackHeight, Tokens.Radius.ROUND, Tokens.Color.ACCENT_WASH);
        painter.vRule(x + zone, trackY - Tokens.Space.TIGHT, trackHeight + Tokens.Space.SNUG,
            Tokens.Color.ACCENT_PRESSED);

        float eased = Motion.ease(Tokens.Easing.OUT, Motion.progress(age - ENTER, Tokens.Duration.REVEAL));
        float value = 99 + (roll.roll() - 99) * eased;
        float markerX = x + width * (value + 0.5f) / 100f;
        int marker = !settled ? Tokens.Color.INK_PRIMARY
            : won ? Tokens.Color.ACCENT : Tokens.Color.STATUS_DANGER;
        painter.fill(markerX - Tokens.Stroke.FOCUS / 2f, trackY - Tokens.Space.TIGHT,
            Tokens.Stroke.FOCUS, trackHeight + Tokens.Space.SNUG, Tokens.Radius.NONE, marker);
        String number = Integer.toString(Math.round(value));
        int half = Typeset.tabularWidth(Tokens.Type.LABEL, number) / 2;
        int labelX = Math.clamp(Math.round(markerX) - half, x, x + width - half * 2);
        Typeset.tabular(painter, Tokens.Type.LABEL, number, labelX, y, marker);
    }

    private static void drawWheel(Painter painter, CasinoProtocol.Spin spin, boolean won,
            boolean settled, int x, int y, int width, long age) {
        int index = 0;
        while (WHEEL[index] != spin.pocket()) {
            index++;
        }
        float travel = WHEEL_LAPS * WHEEL.length + index;
        float position = travel * Motion.ease(Tokens.Easing.OUT,
            Motion.progress(age - ENTER, Tokens.Duration.REVEAL));
        int pitch = POCKET + Tokens.Space.HAIR;
        int centre = x + width / 2;
        int reach = width / pitch / 2 + 2;

        painter.pushClip(x, y, width, STAGE);
        int nearest = Math.round(position);
        for (int k = nearest - reach; k <= nearest + reach; k++) {
            int pocket = WHEEL[Math.floorMod(k, WHEEL.length)];
            float cellX = centre + (k - position) * pitch - POCKET / 2f;
            painter.fill(cellX, y, POCKET, STAGE, Tokens.Radius.NONE, pocketColor(pocket));
            Typeset.drawCentered(painter, Tokens.Type.LABEL, Integer.toString(pocket),
                Math.round(cellX + POCKET / 2f), Typeset.centred(Tokens.Type.LABEL, y, STAGE),
                pocket == 0 ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_PRIMARY);
        }
        painter.popClip();
        painter.border(centre - POCKET / 2f - Tokens.Stroke.FOCUS, y - Tokens.Stroke.FOCUS,
            POCKET + Tokens.Stroke.FOCUS * 2, STAGE + Tokens.Stroke.FOCUS * 2, Tokens.Radius.NONE,
            Tokens.Stroke.FOCUS, settled && !won ? Tokens.Color.INK_SECONDARY : Tokens.Color.ACCENT);
    }

    private static void drawCoin(Painter painter, boolean won, int cx, int cy, long age) {
        float turns = (won ? COIN_HALF_TURNS : COIN_HALF_TURNS + 1) * Motion.ease(Tokens.Easing.OUT,
            Motion.progress(age - ENTER, Tokens.Duration.REVEAL));
        float face = Math.abs((float) Math.cos(turns * Math.PI));
        boolean gold = Math.floorMod((int) Math.floor(turns + 0.5f), 2) == 0;
        float width = Math.max(Tokens.Stroke.FOCUS, COIN * face);
        painter.fill(cx - width / 2, cy - COIN / 2f, width, COIN, Tokens.Radius.ROUND,
            gold ? Tokens.Color.ACCENT : Tokens.Color.SURFACE_RAISED);
        painter.border(cx - width / 2, cy - COIN / 2f, width, COIN, Tokens.Radius.ROUND,
            Tokens.Stroke.HAIR, gold ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.LINE_STRONG);
    }

    /** How far below its slot the card sits, 0..1: rises on arrival, sinks before it expires. */
    static float hidden(long age) {
        if (age >= LEAVE) {
            return Motion.ease(Tokens.Easing.IN, Motion.progress(age - LEAVE, Tokens.Duration.BASE));
        }
        return 1 - Motion.ease(Tokens.Easing.OUT, Motion.progress(age, ENTER));
    }

    /** When reel {@code index} of {@code count} lands; the last one lands as the reveal settles. */
    static long reelStop(int index, int count) {
        return SETTLE - (long) (count - 1 - index) * Tokens.Duration.SLOW;
    }

    static String title(CasinoProtocol.Result result, boolean settled) {
        if (settled) {
            return result.won() ? "당첨이에요" : "아쉽지만 다음 기회예요";
        }
        return switch (result.game()) {
            case COINFLIP -> "동전이 돌고 있어요";
            case DICE -> "주사위를 굴리고 있어요";
            case ROULETTE -> "휠이 돌아가고 있어요";
            case SLOTS -> "릴이 돌아가고 있어요";
        };
    }

    static String detail(CasinoProtocol.Result result, boolean settled) {
        return switch (result.detail()) {
            case CasinoProtocol.Roll roll -> settled
                ? "굴림 " + roll.roll() + " · 목표 " + roll.target() + " 미만"
                : "목표 " + roll.target() + " 미만이 나오면 이겨요";
            case CasinoProtocol.Spin spin -> settled
                ? "포켓 " + spin.pocket() + " · " + betName(spin.bet()) + "에 걸었어요"
                : betName(spin.bet()) + "에 걸었어요";
            case CasinoProtocol.Reels reels -> !settled ? ""
                : reels.matched() < 2 ? "같은 그림이 없어요"
                : symbolName(majority(reels)) + " " + reels.matched() + "개가 맞았어요";
            case CasinoProtocol.Coin ignored -> !settled ? ""
                : result.won() ? "동전이 고른 면으로 떨어졌어요" : "동전이 반대 면으로 떨어졌어요";
        };
    }

    static String multiplier(double value) {
        return "×" + BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String gameName(CasinoProtocol.Game game) {
        return switch (game) {
            case COINFLIP -> "동전 던지기";
            case DICE -> "주사위";
            case ROULETTE -> "룰렛";
            case SLOTS -> "슬롯";
        };
    }

    static String symbolName(String id) {
        return SYMBOLS.getOrDefault(id, id);
    }

    /** The server's bet ids, as a player would name the bet. Unknown ids pass through. */
    static String betName(String bet) {
        int colon = bet.indexOf(':');
        String kind = colon < 0 ? bet : bet.substring(0, colon);
        String value = colon < 0 ? "" : bet.substring(colon + 1);
        return switch (kind) {
            case "red" -> "빨강";
            case "black" -> "검정";
            case "even" -> "짝수";
            case "odd" -> "홀수";
            case "low" -> "1-18";
            case "high" -> "19-36";
            case "straight" -> value.isEmpty() ? bet : "숫자 " + value;
            case "column" -> value.isEmpty() ? bet : value + "열";
            case "dozen" -> value.isEmpty() ? bet : value + "번째 12";
            default -> bet;
        };
    }

    static int pocketColor(int pocket) {
        if (pocket == 0) {
            return Tokens.Color.STATUS_LIVE;
        }
        return RED.contains(pocket) ? Tokens.Color.STATUS_DANGER : Tokens.Color.SURFACE_RAISED;
    }

    private static String majority(CasinoProtocol.Reels reels) {
        for (String symbol : reels.symbols()) {
            if (reels.symbols().stream().filter(symbol::equals).count() == reels.matched()) {
                return symbol;
            }
        }
        return reels.symbols().getFirst();
    }
}
