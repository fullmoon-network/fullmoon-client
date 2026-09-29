package dev.fullmoon.client.hud;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

/**
 * The settled-bet card: a 220×44 banner that rises above the hotbar, plays the game's reveal on
 * its first line, then states the verdict there in the display face — gold with a glow for a win,
 * ash for a loss — with the game and its detail under it. The money has already moved when the
 * payload arrives, so nothing here waits on the player; the reveal is presentation over a known
 * outcome and the chat line stays the record.
 */
public final class CasinoResultOverlay {
    private static final int WIDTH = Tokens.Size.CARD;
    private static final int HEIGHT = Tokens.Size.CARD_H;
    private static final int PAD_X = Tokens.Space.COZY + Tokens.Space.TIGHT;
    private static final int PAD_TOP = Tokens.Space.BASE;
    private static final int LINE_ONE = 20;
    private static final int REEL = Tokens.Size.REEL;
    private static final int REEL_GAP = Tokens.Space.TIGHT;
    private static final int SYMBOL = 10;
    private static final int TRACK_W = 56;
    private static final int TRACK_H = Tokens.Space.SNUG;
    private static final int POCKET = Tokens.Size.POCKET;
    private static final int WHEEL_W = 74;
    private static final int COIN = 14;
    private static final int COIN_EDGE = Tokens.Space.SNUG;
    private static final int GLOW = 18;
    private static final float WIN_WASH = 0.14f;
    private static final float WIN_LINE = 0.75f;
    private static final float WIN_GLOW = 0.18f;
    private static final float FLASH_PEAK = 0.40f;
    private static final float REEL_HIT = 0.12f;
    private static final float ZONE = 0.35f;
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

    /** The card whose cues have been played, and which of them. */
    private static long cuedFor = Long.MIN_VALUE;
    private static int cued;

    private CasinoResultOverlay() {}

    public static void draw(Painter painter, long now) {
        FullmoonChannel.casino(now).or(() -> ServerMenuSample.casinoReveal(now)).ifPresent(reveal ->
            draw(painter, reveal.result(), reveal.receivedAt(), now - reveal.receivedAt()));
    }

    private static void draw(Painter painter, CasinoProtocol.Result result, long key, long age) {
        long shown = Motion.reduced() ? Math.max(age, SETTLE) : age;
        cue(result, key, shown);
        int width = Math.min(WIDTH, painter.width() - Tokens.Space.SECTION * 2);
        int x = (painter.width() - width) / 2;
        int slot = painter.height() - Tokens.Size.HOTBAR - Tokens.Space.LOOSE - HEIGHT;
        int top = slot + Math.round((HEIGHT + Tokens.Space.LOOSE) * hidden(shown));
        if (top >= painter.height()) {
            return;
        }
        boolean settled = shown >= SETTLE;
        boolean won = settled && result.won();

        ground(painter, x, top, width, won, shown);

        int bandY = top + PAD_TOP;
        int left = x + PAD_X;
        int right = x + width - PAD_X;
        int stageW = switch (result.detail()) {
            case CasinoProtocol.Reels reels -> stage(painter, reels, result.won(), left, bandY, shown);
            case CasinoProtocol.Roll roll -> stage(painter, roll, result.won(), settled, left, bandY, shown);
            case CasinoProtocol.Spin spin -> stage(painter, spin, result.won(), settled, left, bandY, shown);
            case CasinoProtocol.Coin ignored -> coin(painter, result.won(), settled, left, bandY, shown);
        };
        int textX = left + stageW + PAD_X;

        String figure = figure(result, settled);
        int figureW = 0;
        if (!figure.isEmpty()) {
            figureW = Typeset.tabularRight(painter, Tokens.Type.FIGURE, figure, right,
                Typeset.centred(Tokens.Type.FIGURE, bandY, LINE_ONE),
                won ? Tokens.Color.STATUS_WIN : Tokens.Color.STATUS_ASH) + PAD_X;
        }
        verdict(painter, result, settled, shown, textX, bandY, right - figureW - textX);

        int lineTwo = bandY + LINE_ONE + Tokens.Space.TIGHT;
        int lineTwoY = Typeset.centred(Tokens.Type.BODY, lineTwo, Tokens.Type.BODY.leading());
        String meta = meta(result, settled);
        int metaW = meta.isEmpty() ? 0
            : Typeset.drawRight(painter, Tokens.Type.BODY, meta, right, lineTwoY, Tokens.Color.INK_TERTIARY) + PAD_X;
        Typeset.draw(painter, Tokens.Type.BODY,
            Typeset.ellipsized(Tokens.Type.BODY, detail(result, settled), right - metaW - left),
            left, lineTwoY, Tokens.Color.INK_SECONDARY);

        float flash = flash(shown, result.won());
        if (flash > 0.0f) {
            painter.fill(x, top, width, HEIGHT, Rgb.alpha(Tokens.Color.STATUS_WIN, flash));
        }
    }

    /** The glass, its edge and top light; a won card also wears its wash, its gold line and a glow. */
    private static void ground(Painter painter, int x, int top, int width, boolean won, long age) {
        if (won) {
            float glow = WIN_GLOW * Motion.eased(age - SETTLE, Tokens.Duration.SLOW, Tokens.Easing.OUT);
            painter.fillGradient(x - Tokens.Space.SNUG, top - GLOW, width + Tokens.Space.COZY, GLOW,
                Rgb.alpha(Tokens.Color.STATUS_WIN, 0.0f), Rgb.alpha(Tokens.Color.STATUS_WIN, glow));
            painter.fillGradient(x - Tokens.Space.SNUG, top + HEIGHT, width + Tokens.Space.COZY, GLOW,
                Rgb.alpha(Tokens.Color.STATUS_WIN, glow), Rgb.alpha(Tokens.Color.STATUS_WIN, 0.0f));
        }
        painter.border(x - 1, top - 1, width + 2, HEIGHT + 2, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
            Tokens.Color.SURFACE_EDGE);
        painter.fill(x, top, width, HEIGHT, Tokens.Color.SURFACE_GLASS);
        if (won) {
            painter.fillGradient(x, top, width, HEIGHT,
                Rgb.alpha(Tokens.Color.STATUS_WIN, WIN_WASH), Rgb.alpha(Tokens.Color.STATUS_WIN, WIN_WASH / 3.5f));
            painter.hRule(x, top, width, Rgb.alpha(Tokens.Color.STATUS_WIN, WIN_LINE));
        } else {
            painter.hRule(x, top, width, Tokens.Color.SURFACE_HIGHLIGHT);
        }
    }

    /**
     * The first line's words: the game in motion while the reveal runs, then the verdict in the
     * display face, crossfaded over {@link Tokens.Duration#VERDICT}.
     */
    private static void verdict(Painter painter, CasinoProtocol.Result result, boolean settled, long age,
            int x, int bandY, int room) {
        float was = painter.opacity();
        float t = settled ? Motion.eased(age - SETTLE, Tokens.Duration.VERDICT, Tokens.Easing.OUT) : 0.0f;
        if (t < 1.0f) {
            painter.opacity(was * (1.0f - t));
            Typeset.draw(painter, Tokens.Type.ROW, Typeset.ellipsized(Tokens.Type.ROW, title(result, false), room),
                x, Typeset.centred(Tokens.Type.ROW, bandY, LINE_ONE), Tokens.Color.INK_SECONDARY);
        }
        if (t > 0.0f) {
            painter.opacity(was * t);
            Typeset.draw(painter, Tokens.Type.DISPLAY,
                Typeset.ellipsized(Tokens.Type.DISPLAY, title(result, true), room),
                x, Typeset.centred(Tokens.Type.DISPLAY, bandY, LINE_ONE),
                result.won() ? Tokens.Color.STATUS_WIN : Tokens.Color.STATUS_ASH);
        }
        painter.opacity(was);
    }

    /** Three reel tiles; a symbol scrolls through each until its reel lands, then drops two pixels home. */
    private static int stage(Painter painter, CasinoProtocol.Reels reels, boolean won, int x, int bandY, long age) {
        int count = reels.symbols().size();
        int y = bandY + (LINE_ONE - REEL) / 2;
        String winner = majority(reels);
        for (int i = 0; i < count; i++) {
            int tileX = x + i * (REEL + REEL_GAP);
            String symbol = reels.symbols().get(i);
            long stop = reelStop(i, count);
            boolean hit = won && age >= stop && reels.matched() >= 2 && symbol.equals(winner);
            painter.fill(tileX, y, REEL, REEL, hit ? Rgb.alpha(Tokens.Color.STATUS_WIN, REEL_HIT) : Tokens.Color.SURFACE_RAISED);
            painter.border(tileX, y, REEL, REEL, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
                hit ? Tokens.Color.STATUS_WIN : Tokens.Color.LINE_STRONG);
            float cx = tileX + REEL / 2.0f;
            float cy = y + REEL / 2.0f;
            painter.pushClip(tileX, y, REEL, REEL);
            if (age < stop) {
                long frame = Math.max(0, age) / Tokens.Duration.FAST + i * 3L;
                float scroll = REEL * Motion.progress(Math.max(0, age) % Tokens.Duration.FAST, Tokens.Duration.FAST);
                String current = REEL_ORDER.get((int) (frame % REEL_ORDER.size()));
                String next = REEL_ORDER.get((int) ((frame + 1) % REEL_ORDER.size()));
                MenuIcons.draw(painter, MenuIcons.REEL + current, cx, cy - scroll, SYMBOL);
                MenuIcons.draw(painter, MenuIcons.REEL + next, cx, cy - scroll + REEL, SYMBOL);
            } else {
                float drop = Tokens.Space.TIGHT * (1.0f - Motion.eased(age - stop, Tokens.Duration.BASE, Tokens.Easing.OUT));
                MenuIcons.draw(painter, MenuIcons.REEL + symbol, cx, cy - drop, SYMBOL);
            }
            painter.popClip();
        }
        return count * REEL + (count - 1) * REEL_GAP;
    }

    /** The percentile track: the winning zone lit, the marker sliding down onto the roll. */
    private static int stage(Painter painter, CasinoProtocol.Roll roll, boolean won, boolean settled,
            int x, int bandY, long age) {
        int trackY = bandY + (LINE_ONE - TRACK_H) / 2;
        painter.fill(x, trackY, TRACK_W, TRACK_H, Tokens.Color.LINE_HAIRLINE);
        painter.fill(x, trackY, TRACK_W * roll.target() / 100.0f, TRACK_H, Rgb.alpha(Tokens.Color.ACCENT, ZONE));
        float eased = Motion.eased(age - ENTER, Tokens.Duration.REVEAL, Tokens.Easing.OUT);
        float value = 99 + (roll.roll() - 99) * eased;
        float markerX = x + (TRACK_W - Tokens.Stroke.FOCUS) * value / 99.0f;
        int marker = !settled ? Tokens.Color.INK_PRIMARY : won ? Tokens.Color.STATUS_WIN : Tokens.Color.STATUS_ASH;
        painter.fill(markerX, trackY - (SYMBOL - TRACK_H) / 2.0f, Tokens.Stroke.FOCUS, SYMBOL, marker);
        return TRACK_W;
    }

    /** A window onto the wheel: the pockets stream past until the winning one stops in the middle. */
    private static int stage(Painter painter, CasinoProtocol.Spin spin, boolean won, boolean settled,
            int x, int bandY, long age) {
        int index = 0;
        while (WHEEL[index] != spin.pocket()) {
            index++;
        }
        float travel = WHEEL_LAPS * WHEEL.length + index;
        float position = travel * Motion.eased(age - ENTER, Tokens.Duration.REVEAL, Tokens.Easing.OUT);
        int pitch = POCKET + Tokens.Space.HAIR;
        int y = bandY + (LINE_ONE - POCKET) / 2;
        int centre = x + WHEEL_W / 2;
        int reach = WHEEL_W / pitch / 2 + 2;
        painter.pushClip(x, y, WHEEL_W, POCKET);
        int nearest = Math.round(position);
        for (int k = nearest - reach; k <= nearest + reach; k++) {
            int pocket = WHEEL[Math.floorMod(k, WHEEL.length)];
            float cellX = centre + (k - position) * pitch - POCKET / 2.0f;
            painter.fill(cellX, y, POCKET, POCKET, pocketColor(pocket));
            Typeset.drawCentered(painter, Tokens.Type.MICRO, Integer.toString(pocket),
                Math.round(cellX + POCKET / 2.0f), Typeset.centred(Tokens.Type.MICRO, y, POCKET), Tokens.Color.INK_PRIMARY);
        }
        painter.border(centre - POCKET / 2.0f, y, POCKET, POCKET, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
            settled && !won ? Tokens.Color.STATUS_ASH : Tokens.Color.ACCENT);
        painter.popClip();
        return WHEEL_W;
    }

    /** A coin turning over: the gold face, and the ash edge between turns; it lands face or edge up. */
    private static int coin(Painter painter, boolean won, boolean settled, int x, int bandY, long age) {
        float cx = x + COIN / 2.0f;
        float cy = bandY + LINE_ONE / 2.0f;
        float turns = (won ? COIN_HALF_TURNS : COIN_HALF_TURNS + 1)
            * Motion.eased(age - ENTER, Tokens.Duration.REVEAL, Tokens.Easing.OUT);
        float face = Math.abs((float) Math.cos(turns * Math.PI));
        boolean gold = Math.floorMod((int) Math.floor(turns + 0.5f), 2) == 0;
        if (settled) {
            face = won ? 1.0f : 0.0f;
            gold = won;
        }
        float width = Math.max(COIN_EDGE, COIN * face);
        painter.fill(cx - width / 2, cy - COIN / 2.0f, width, COIN, Tokens.Radius.ROUND,
            gold ? Tokens.Color.ACCENT : Tokens.Color.STATUS_ASH);
        if (gold) {
            painter.border(cx - width / 2, cy - COIN / 2.0f, width, COIN, Tokens.Radius.ROUND,
                Tokens.Stroke.HAIR, Tokens.Color.ACCENT_PRESSED);
        }
        return COIN;
    }

    /** Plays each cue of this card once, as its moment passes. */
    private static void cue(CasinoProtocol.Result result, long key, long age) {
        if (key != cuedFor) {
            cuedFor = key;
            cued = 0;
        }
        List<UiSounds.Cue> due = cuesDue(result, age);
        for (int i = 0; i < due.size(); i++) {
            if ((cued & (1 << i)) == 0) {
                cued |= 1 << i;
                UiSounds.play(due.get(i));
            }
        }
    }

    /**
     * The cues whose moment has passed by {@code age}, in order: a reel tick as each reel of a
     * slots result lands, then the verdict. Pure, so the timing is a test and not a recording.
     */
    static List<UiSounds.Cue> cuesDue(CasinoProtocol.Result result, long age) {
        List<UiSounds.Cue> due = new ArrayList<>();
        if (result.detail() instanceof CasinoProtocol.Reels reels) {
            int count = reels.symbols().size();
            for (int i = 0; i < count; i++) {
                if (age >= reelStop(i, count)) {
                    due.add(UiSounds.Cue.REEL);
                }
            }
        }
        if (age >= SETTLE) {
            due.add(result.won() ? UiSounds.Cue.WIN : UiSounds.Cue.LOSE);
        }
        return due;
    }

    /** How far below its slot the card sits, 0..1: rises on arrival, sinks before it expires. */
    static float hidden(long age) {
        if (age >= LEAVE) {
            return Motion.ease(Tokens.Easing.IN, Motion.progress(age - LEAVE, Tokens.Duration.BASE));
        }
        return 1 - Motion.ease(Tokens.Easing.OUT, Motion.progress(age, ENTER));
    }

    /** The win flash over the card: forty percent at the verdict, gone in {@link Tokens.Duration#FLASH}. */
    static float flash(long age, boolean won) {
        if (!won || age < SETTLE || age >= SETTLE + Tokens.Duration.FLASH) {
            return 0.0f;
        }
        return FLASH_PEAK * (1.0f - Motion.eased(age - SETTLE, Tokens.Duration.FLASH, Tokens.Easing.OUT));
    }

    /** When reel {@code index} of {@code count} lands; the last one lands as the reveal settles. */
    static long reelStop(int index, int count) {
        return SETTLE - (long) (count - 1 - index) * Tokens.Duration.SLOW;
    }

    /** The first line: the game in motion, then the verdict. */
    static String title(CasinoProtocol.Result result, boolean settled) {
        if (settled) {
            return result.won() ? "당첨" : "아쉬워요";
        }
        return switch (result.game()) {
            case COINFLIP -> "동전이 돌아요";
            case DICE -> "주사위가 굴러요";
            case ROULETTE -> "휠이 돌아가요";
            case SLOTS -> "릴이 돌아가요";
        };
    }

    /** The large figure on the right: the multiplier of a win; the roll or the pocket of a loss. */
    static String figure(CasinoProtocol.Result result, boolean settled) {
        if (!settled) {
            return "";
        }
        if (result.won()) {
            return multiplier(result.payoutMultiplier());
        }
        return switch (result.detail()) {
            case CasinoProtocol.Roll roll -> Integer.toString(roll.roll());
            case CasinoProtocol.Spin spin -> Integer.toString(spin.pocket());
            default -> "";
        };
    }

    /** The second line: the game and the bet as the player placed it, then what came up. */
    static String detail(CasinoProtocol.Result result, boolean settled) {
        return switch (result.detail()) {
            case CasinoProtocol.Roll roll -> "주사위 · 목표 " + roll.target() + " 미만";
            case CasinoProtocol.Spin spin -> "룰렛 · " + betName(spin.bet()) + "에 걸었어요";
            case CasinoProtocol.Reels reels -> !settled ? "슬롯" : "슬롯 · " + String.join(" · ",
                reels.symbols().stream().map(CasinoResultOverlay::symbolName).toList());
            case CasinoProtocol.Coin ignored -> "동전 던지기";
        };
    }

    /** The second line's right end: what the reveal showed, once it has. */
    static String meta(CasinoProtocol.Result result, boolean settled) {
        if (!settled) {
            return "";
        }
        return switch (result.detail()) {
            case CasinoProtocol.Roll roll -> "나온 수 " + roll.roll();
            case CasinoProtocol.Spin spin -> "포켓 " + spin.pocket();
            case CasinoProtocol.Reels reels -> reels.matched() < 2 ? "일치 없음" : reels.matched() + "개 일치";
            case CasinoProtocol.Coin ignored -> result.won() ? "고른 면" : "반대 면";
        };
    }

    /** Two decimals, cut rather than rounded: the server sends dice payouts as raw quotients such as 1.9607843137254901. */
    static String multiplier(double value) {
        BigDecimal exact = BigDecimal.valueOf(value);
        BigDecimal shown = exact.setScale(2, RoundingMode.DOWN);
        return (shown.signum() == 0 ? exact : shown).stripTrailingZeros().toPlainString() + "배";
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
            return Tokens.Color.WHEEL_GREEN;
        }
        return RED.contains(pocket) ? Tokens.Color.WHEEL_RED : Tokens.Color.WHEEL_BLACK;
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
