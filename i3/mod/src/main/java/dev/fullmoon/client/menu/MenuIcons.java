package dev.fullmoon.client.menu;

import java.util.function.IntUnaryOperator;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.PixelArt;

/**
 * Hand-drawn pixel marks: 14×14 for the casino's games and the table's chips and facts, 10×10
 * for the slot reels' symbols — the same idiom as the vanilla HUD's own sprites, so they read as
 * the game's craft rather than chrome drawn on top of it. Every silhouette carries a dark outline
 * and a shaded edge the way the game's item art does; a flat blob is not a sprite. The server
 * names a mark through the menu item's icon id; anything it does not name falls back to the item
 * render in {@link ServerMenuEntry}.
 */
public final class MenuIcons {
    /** The id stem the slot reels' symbols are drawn under: {@code fullmoon.reel.<symbol>}. */
    public static final String REEL = "fullmoon.reel.";

    /** A gold coin: milled rim, top-left shine, crescent stamped in the face. */
    private static final String[] COIN = {
        "..............",
        ".....kkkk.....",
        "...kGGGGGGk...",
        "..kGWWGGGGgk..",
        ".kGWGGGddddgk.",
        ".kGWGGGddGGgk.",
        ".kGWGGGdGGGgk.",
        ".kGWGGGdGGGgk.",
        ".kGWGGGddGGgk.",
        ".kGWGGGddddgk.",
        "..kGGGGGGggk..",
        "...kGGGGggk...",
        ".....kkkk.....",
        "..............",
    };

    /** An ivory die showing five, shaded along its lower right. */
    private static final String[] DICE = {
        "..............",
        "..............",
        "..kkkkkkkkkk..",
        "..kWWWWWWWWk..",
        "..kWddWWddwk..",
        "..kWddWWddwk..",
        "..kWWWWddWwk..",
        "..kWWWWddWwk..",
        "..kWddWWddwk..",
        "..kWddWWddwk..",
        "..kwwwwwwwwk..",
        "..kkkkkkkkkk..",
        "..............",
        "..............",
    };

    /** A roulette wheel: gold rim, red and dark pockets, ivory hub, white ball. */
    private static final String[] ROULETTE = {
        "..............",
        ".....kkkk.....",
        "...kGGGGGGk...",
        "..kGdRWWRRdGk.",
        ".kGdRRGGRRdGk.",
        ".kGRGWWWWGRGk.",
        ".kRGGWGWWGGRk.",
        ".kRGGWGWWGGRk.",
        ".kRGGWGWWGGRk.",
        ".kGRGWWWWGRGk.",
        ".kGdRRGGRRdGk.",
        "..kGdRRRRdGk..",
        "...kGGGGGGk...",
        ".....kkkk.....",
    };

    /** Triple seven across the payline. */
    private static final String[] SLOTS = {
        "..............",
        "..............",
        "..............",
        "..............",
        "GGGG.GGGG.GGGG",
        "...G....G....G",
        "..G....G....G.",
        ".G....G....G..",
        "G....G....G...",
        "..............",
        "..............",
        "..............",
        "..............",
        "..............",
    };

    /** Pachinko: a moonlit bead dropping past the pins. The server's game id is still moonfall. */
    private static final String[] MOONFALL = {
        "..............",
        "...kkkk.......",
        "..kWWWWk..GG..",
        ".kWWWWWk..GG..",
        ".kWWWWk.......",
        "kWWWWk...GG...",
        "kWWWwk...GG...",
        "kWWWWk........",
        ".kWWWWk.GG....",
        ".kWWWWWkGG....",
        "...kkkk.......",
        "..............",
        "..............",
        "..............",
    };

    /** A jackpot: three stacked chips under a spark. */
    private static final String[] JACKPOT = {
        ".......G......",
        "......GGG.....",
        "..kRRRRRRRRk..",
        "..kWRRRRRRWk..",
        "..kRRRRRRRRk..",
        "..kGGGGGGGGk..",
        "..kdGGGGGGdk..",
        "..kGGGGGGGGk..",
        "..kWWWWWWWWk..",
        "..kdWWWWWWdk..",
        "..kWWWWWWWWk..",
        "...kkkkkkkk...",
        "..............",
        "..............",
    };

    /** The wallet: a gold purse with a clasp, for 내 잔액. */
    private static final String[] WALLET = {
        "..............",
        "..............",
        "..kkkkkkkkkk..",
        ".kGGGGGGGGGGk.",
        ".kGWGGGGGGGGk.",
        ".kGWGGGGGGGGk.",
        ".kGWGGGGkkkkk.",
        ".kGGGGGGkWdkk.",
        ".kGGGGGGkkkkk.",
        ".kGgGGGGGGGgk.",
        ".kggggggggggk.",
        "..kkkkkkkkkk..",
        "..............",
        "..............",
    };

    /** A clock face at a quarter past, for 오늘의 나. */
    private static final String[] TODAY = {
        "..............",
        ".....kkkk.....",
        "...kkwwwwkk...",
        "..kwwwwwwwwk..",
        ".kwwwwwkwwwwk.",
        ".kwwwwwkwwwwk.",
        ".kwwwwwkwwwwk.",
        ".kwwwwwkkkwwk.",
        ".kwwwwwwwwwwk.",
        ".kwwwwwwwwwwk.",
        "..kwwwwwwwwk..",
        "...kkwwwwkk...",
        ".....kkkk.....",
        "..............",
    };

    /** A half moon: the house's share is the dark half, for 하우스 몫. */
    private static final String[] ODDS = {
        "..............",
        ".....kkkk.....",
        "...kkMMmmkk...",
        "..kMMMMmmmmk..",
        ".kMMMMMmmmmmk.",
        ".kMMMMMmmmmmk.",
        ".kMMMMMmmmmmk.",
        ".kMMMMMmmmmmk.",
        ".kMMMMMmmmmmk.",
        ".kMMMMMmmmmmk.",
        "..kMMMMmmmmk..",
        "...kkMMmmkk...",
        ".....kkkk.....",
        "..............",
    };

    /** A casino chip: a ring with four ivory notches; {@code C} is the denomination's colour. */
    private static final String[] CHIP = {
        "..............",
        "..............",
        ".....kkkk.....",
        "...kkCWWCkk...",
        "..kCCWWWWCCk..",
        ".kCCCWkkWCCCk.",
        ".kWWWkCCkWWWk.",
        ".kWWWkCCkWWWk.",
        ".kCCCWkkWCCCk.",
        "..kCCWWWWCCk..",
        "...kkCWWCkk...",
        ".....kkkk.....",
        "..............",
        "..............",
    };

    /** A gold arrow chasing its tail, for 돌리기. */
    private static final String[] SPIN = {
        "..............",
        "....kkkkkk....",
        "...kGGGGGGk...",
        "..kGGkkkkGGk..",
        "..kGk....kGkk.",
        "..kGk...kGGGk.",
        "..kGk..kGGGGk.",
        "..kGk...kGGGk.",
        "..kGk....kGk..",
        "..kGGkkkkGGk..",
        "...kGGGGGGk...",
        "....kkkkkk....",
        "..............",
        "..............",
    };

    /** A pot with coins over its rim, for 만월 팟. */
    private static final String[] POT = {
        "..............",
        "..............",
        "....kGGkGGk...",
        "...kGGGGGGGk..",
        "..kkkkkkkkkkk.",
        "..kwwwwwwwwwk.",
        "..kwkkkkkkkwk.",
        "..kwwwwwwwwwk.",
        "..kwwwwwwwwwk.",
        "..kwwwwwwwwwk.",
        "...kwwwwwwwk..",
        "....kkkkkkk...",
        "..............",
        "..............",
    };

    /** A ticket with its stub perforated, for 티켓. */
    private static final String[] TICKET = {
        "..............",
        "..............",
        "..............",
        ".kkkkkkkkkkkk.",
        ".kGGGGGkGGGGk.",
        ".kGWWGGkGGWGk.",
        ".kGGGGGkGGGGk.",
        ".kGWWGGkGGWGk.",
        ".kGGGGGkGGGGk.",
        ".kkkkkkkkkkkk.",
        "..............",
        "..............",
        "..............",
        "..............",
    };

    /** A check in live green, for the chosen chip. */
    private static final String[] CHOSEN = {
        "..............",
        "..............",
        "..............",
        "..........kk..",
        ".........kLLk.",
        "........kLLk..",
        "..kk...kLLk...",
        ".kLLk.kLLk....",
        ".kLLLkLLk.....",
        "..kLLLLk......",
        "...kLLk.......",
        "....kk........",
        "..............",
        "..............",
    };

    private static final String[] REEL_MOON = {
        "...MMMM...",
        "..MMMMMM..",
        ".MMMMMMMM.",
        "MMMMMMMMMM",
        "MMMMMMMMMM",
        "MMMMMMMMMM",
        "MMMMMMMMMM",
        ".MMMMMMMM.",
        "..MMMMMM..",
        "...MMMM...",
    };

    private static final String[] REEL_CHERRY = {
        ".....L....",
        "....LL....",
        "...L.L....",
        "..L..L....",
        ".RR...RR..",
        "RRRR.RRRR.",
        "RRRR.RRRR.",
        "RWRR.RWRR.",
        ".RR...RR..",
        "..........",
    };

    private static final String[] REEL_LEMON = {
        "..........",
        "......YY..",
        "..YYYYYY..",
        ".YYYYYYYY.",
        "YYYYYYYYYY",
        "YYWYYYYYYY",
        "YYYYYYYYYY",
        ".YYYYYYYY.",
        "..YYYYYY..",
        "..........",
    };

    private static final String[] REEL_BELL = {
        "....GG....",
        "...GGGG...",
        "..GGGGGG..",
        "..GGGGGG..",
        "..GGGGGG..",
        ".GGGGGGGG.",
        "GGGGGGGGGG",
        "GGGGGGGGGG",
        "....kk....",
        "..........",
    };

    private static final String[] REEL_STAR = {
        "....MM....",
        "....MM....",
        "...MMMM...",
        "MMMMMMMMMM",
        ".MMMMMMMM.",
        "..MMMMMM..",
        "..MMMMMM..",
        ".MMM..MMM.",
        "MM......MM",
        "..........",
    };

    private static final String[] REEL_DIAMOND = {
        "..........",
        "..AAAAAA..",
        ".AAWAAAAA.",
        "AAAAAAAAAA",
        ".AAAAAAAA.",
        "..AAAAAA..",
        "...AAAA...",
        "....AA....",
        "..........",
        "..........",
    };

    private static final String[] REEL_SEVEN = {
        "..........",
        "RRRRRRRRR.",
        "RRRRRRRRR.",
        ".......RR.",
        "......RR..",
        ".....RR...",
        "....RR....",
        "...RR.....",
        "...RR.....",
        "..........",
    };

    private MenuIcons() {}

    /** Whether {@code icon} names a mark drawn here rather than an item texture. */
    public static boolean knows(String icon) {
        return art(icon) != null;
    }

    /** Draws the mark centred on {@code cx},{@code cy} in a {@code size} box; false if unknown. */
    public static boolean draw(Painter painter, String icon, float cx, float cy, float size) {
        String[] art = art(icon);
        if (art == null) {
            return false;
        }
        PixelArt.draw(painter, art, cx, cy, size, palette(icon));
        return true;
    }

    private static String[] art(String icon) {
        return switch (icon) {
            case "fullmoon.casino.coinflip" -> COIN;
            case "fullmoon.casino.dice" -> DICE;
            case "fullmoon.casino.roulette" -> ROULETTE;
            case "fullmoon.casino.slots" -> SLOTS;
            case "fullmoon.casino.moonfall" -> MOONFALL;
            case "fullmoon.casino.jackpot" -> JACKPOT;
            case "fullmoon.casino.wallet" -> WALLET;
            case "fullmoon.casino.today" -> TODAY;
            case "fullmoon.casino.odds" -> ODDS;
            case "fullmoon.casino.chip.small", "fullmoon.casino.chip.mid", "fullmoon.casino.chip.large" -> CHIP;
            case "fullmoon.casino.spin" -> SPIN;
            case "fullmoon.casino.pot" -> POT;
            case "fullmoon.casino.ticket" -> TICKET;
            case "fullmoon.casino.chosen" -> CHOSEN;
            case REEL + "moon" -> REEL_MOON;
            case REEL + "cherry" -> REEL_CHERRY;
            case REEL + "lemon" -> REEL_LEMON;
            case REEL + "bell" -> REEL_BELL;
            case REEL + "star" -> REEL_STAR;
            case REEL + "diamond" -> REEL_DIAMOND;
            case REEL + "seven" -> REEL_SEVEN;
            default -> null;
        };
    }

    /** The chip's body follows its denomination: ivory, gold, then red, as a casino's do. */
    private static IntUnaryOperator palette(String icon) {
        int chip = switch (icon) {
            case "fullmoon.casino.chip.mid" -> Tokens.Color.ACCENT;
            case "fullmoon.casino.chip.large" -> Tokens.Color.STATUS_DANGER;
            default -> Tokens.Color.INK_SECONDARY;
        };
        return pixel -> pixel == 'C' ? chip : color((char) pixel);
    }

    private static int color(char pixel) {
        return switch (pixel) {
            case 'G' -> Tokens.Color.ACCENT;
            case 'g' -> Tokens.Color.ACCENT_PRESSED;
            case 'W' -> Tokens.Color.INK_PRIMARY;
            case 'w' -> Tokens.Color.INK_SECONDARY;
            case 'd' -> Tokens.Color.INK_ON_ACCENT;
            case 'R' -> Tokens.Color.STATUS_DANGER;
            case 'L' -> Tokens.Color.STATUS_LIVE;
            case 'Y' -> Tokens.Color.CHAT_YELLOW;
            case 'A' -> Tokens.Color.CHAT_AQUA;
            case 'M' -> Tokens.Color.MOON_LIT;
            case 'm' -> Tokens.Color.MOON_SHADOW;
            case 'k' -> Tokens.Color.SURFACE_VOID;
            default -> 0;
        };
    }
}
