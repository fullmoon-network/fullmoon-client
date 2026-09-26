package dev.fullmoon.client.menu;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.PixelArt;

/**
 * Hand-drawn 14×14 pixel marks for the casino's six games — the same idiom as
 * the vanilla HUD's own sprites, so they read as the game's craft rather than
 * chrome drawn on top of it. Every silhouette carries a dark outline and a
 * shaded edge the way the game's item art does; a flat blob is not a sprite.
 * The server names the mark through the menu item's icon id; anything it does
 * not name falls back to the item render in {@link ServerMenuEntry}.
 */
public final class MenuIcons {
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

    private MenuIcons() {}

    /** Draws the mark centred on {@code cx},{@code cy} in a {@code size} box; false if unknown. */
    public static boolean draw(Painter painter, String icon, float cx, float cy, float size) {
        String[] art = switch (icon) {
            case "fullmoon.casino.coinflip" -> COIN;
            case "fullmoon.casino.dice" -> DICE;
            case "fullmoon.casino.roulette" -> ROULETTE;
            case "fullmoon.casino.slots" -> SLOTS;
            case "fullmoon.casino.moonfall" -> MOONFALL;
            case "fullmoon.casino.jackpot" -> JACKPOT;
            default -> null;
        };
        if (art == null) {
            return false;
        }
        PixelArt.draw(painter, art, cx, cy, size, pixel -> color((char) pixel));
        return true;
    }

    private static int color(char pixel) {
        return switch (pixel) {
            case 'G' -> Tokens.Color.ACCENT;
            case 'g' -> Tokens.Color.ACCENT_PRESSED;
            case 'W' -> Tokens.Color.INK_PRIMARY;
            case 'w' -> Tokens.Color.INK_SECONDARY;
            case 'd' -> Tokens.Color.INK_ON_ACCENT;
            case 'R' -> Tokens.Color.STATUS_DANGER;
            case 'k' -> Tokens.Color.SURFACE_VOID;
            default -> 0;
        };
    }
}
