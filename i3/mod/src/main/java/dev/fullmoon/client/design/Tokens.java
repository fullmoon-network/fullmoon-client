package dev.fullmoon.client.design;

/**
 * Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand,
 * and do not write a colour, radius, or duration literal anywhere else in the mod —
 * design/verify-tokens.mjs fails on any that appear outside this file.
 */
public final class Tokens {
    private Tokens() {}

    /**
     * Packed 0xAARRGGBB. Inks are opaque; grounds carry the alpha the design gives them, because
     * the glass is a translucent pane over the game's own blur and a hover or a selection is a
     * tint of that pane. Rgb#alpha reopens the alpha of an opaque token for a scrim or a fade.
     */
    public static final class Color {
        /** scrim behind a full-screen surface; the glass's own colour · #0A0C13 */
        public static final int SURFACE_VOID = 0xFF0A0C13;
        /** menu panel ground over the game blur · #0A0C13 @ 0.86 */
        public static final int SURFACE_GLASS = 0xDB0A0C13;
        /** HUD chip ground, no border · #080A10 @ 0.56 */
        public static final int SURFACE_GLASS_HUD = 0x8F080A10;
        /** tooltip, popover, dropdown · #0A0C13 @ 0.94 */
        public static final int SURFACE_OVERLAY = 0xF00A0C13;
        /** opaque panel ground where there is no blur to sit on · #12141F */
        public static final int SURFACE_BASE = 0xFF12141F;
        /** inset wells, scroll troughs, empty states · #000000 @ 0.3 */
        public static final int SURFACE_SUNKEN = 0x4D000000;
        /** hovered row: the glass lifts five percent · #FFFFFF @ 0.05 */
        public static final int SURFACE_RAISED = 0x0DFFFFFF;
        /** quiet button and action-row ground · #FFFFFF @ 0.06 */
        public static final int SURFACE_CONTROL = 0x0FFFFFFF;
        /** quiet button ground under the pointer · #FFFFFF @ 0.1 */
        public static final int SURFACE_CONTROL_HOVER = 0x1AFFFFFF;
        /** quiet button ground while held: it sinks, never lifts · #000000 @ 0.1 */
        public static final int SURFACE_CONTROL_PRESSED = 0x1A000000;
        /** a dead control's ground, faint · #FFFFFF @ 0.03 */
        public static final int SURFACE_CONTROL_DISABLED = 0x08FFFFFF;
        /** the one-pixel light along a panel's top edge · #FFFFFF @ 0.07 */
        public static final int SURFACE_HIGHLIGHT = 0x12FFFFFF;
        /** the one-pixel dark line outside a panel · #000000 @ 0.45 */
        public static final int SURFACE_EDGE = 0x73000000;
        /** row separators, the rule under a header · #FFFFFF @ 0.08 */
        public static final int LINE_HAIRLINE = 0x14FFFFFF;
        /** button and keycap edges · #FFFFFF @ 0.16 */
        public static final int LINE_STRONG = 0x29FFFFFF;
        /** titles, values, row names · #F2EEE6 */
        public static final int INK_PRIMARY = 0xFFF2EEE6;
        /** body copy, descriptions · #B9B3A8 */
        public static final int INK_SECONDARY = 0xFFB9B3A8;
        /** meta text, units, hints, keycaps · #928C80 */
        public static final int INK_TERTIARY = 0xFF928C80;
        /** disabled label · #5E616A */
        public static final int INK_DISABLED = 0xFF5E616A;
        /** text on a gold fill · #1A160B */
        public static final int INK_ON_ACCENT = 0xFF1A160B;
        /** selection bar, the one primary action, live values · #E8C56C */
        public static final int ACCENT = 0xFFE8C56C;
        /** gold fill while held · #C9A44F */
        public static final int ACCENT_PRESSED = 0xFFC9A44F;
        /** selected-row ground behind the bar · #E8C56C @ 0.1 */
        public static final int ACCENT_WASH = 0x1AE8C56C;
        /** the chosen row under the pointer: its wash, lifted · #E8C56C @ 0.15 */
        public static final int ACCENT_WASH_LIFT = 0x26E8C56C;
        /** the soft light a focused row wears, faded in · #E8C56C @ 0.22 */
        public static final int ACCENT_GLOW = 0x38E8C56C;
        /** ✔ possible, connected, here · #6FCFA8 */
        public static final int STATUS_LIVE = 0xFF6FCFA8;
        /** server unreachable, module off · #5E616A */
        public static final int STATUS_IDLE = 0xFF5E616A;
        /** cooldown, a limit, waiting on the server · #F0A24A */
        public static final int STATUS_WARN = 0xFFF0A24A;
        /** ✖ reason, destructive action · #E0625C */
        public static final int STATUS_DANGER = 0xFFE0625C;
        /** a won bet: gold with a glow above it · #F5D06E */
        public static final int STATUS_WIN = 0xFFF5D06E;
        /** a lost bet: the moon's shadow, never red · #9AA3B5 */
        public static final int STATUS_ASH = 0xFF9AA3B5;
        /** the lit face of a moon phase · #F6E7BF */
        public static final int MOON_LIT = 0xFFF6E7BF;
        /** the unlit face of a moon phase · #262D3D */
        public static final int MOON_SHADOW = 0xFF262D3D;
        /** a red roulette pocket on the result card · #B8443F */
        public static final int WHEEL_RED = 0xFFB8443F;
        /** a black roulette pocket · #262A36 */
        public static final int WHEEL_BLACK = 0xFF262A36;
        /** the zero pocket · #2F7D62 */
        public static final int WHEEL_GREEN = 0xFF2F7D62;
        /** a server's yellow or gold text, softened for the glass · #F5C542 */
        public static final int CHAT_YELLOW = 0xFFF5C542;
        /** a server's aqua text · #7FD8E8 */
        public static final int CHAT_AQUA = 0xFF7FD8E8;
        /** a server's blue text · #8EA6F2 */
        public static final int CHAT_BLUE = 0xFF8EA6F2;
        /** a server's light purple text · #CFA0EA */
        public static final int CHAT_PURPLE = 0xFFCFA0EA;

        private Color() {}
    }

    public static final class Space {
        public static final int HAIR = 1;
        public static final int TIGHT = 2;
        public static final int SNUG = 4;
        public static final int BASE = 6;
        public static final int COZY = 8;
        public static final int LOOSE = 12;
        public static final int GUTTER = 16;
        public static final int SECTION = 24;
        public static final int BAY = 32;
        public static final int FIELD = 48;

        private Space() {}
    }

    /** Fixed heights and widths of the glass components, GUI px. */
    public static final class Size {
        public static final int PANEL_W = 512;
        public static final int PANEL_H = 316;
        public static final int MOCK_PANEL_H = 300;
        public static final int EDGE = 24;
        public static final int HEADER = 28;
        public static final int ROW = 40;
        public static final int ROW_ONE = 28;
        public static final int ROW_MOCK = 36;
        public static final int ROW_ONE_MOCK = 24;
        public static final int FACTS = 36;
        public static final int FACTS_MOCK = 40;
        public static final int BUTTON = 20;
        public static final int ACTION_ROW = 20;
        public static final int KEYCAP = 11;
        public static final int ICON = 16;
        public static final int DETAIL = 176;
        public static final int HINT = 14;
        public static final int CELL = 20;
        public static final int CELL_GAP = 2;
        public static final int CARD = 220;
        public static final int CARD_H = 44;
        public static final int HOTBAR = 22;
        public static final int HUD_CHIP = 16;
        public static final int HUD_KEY = 14;
        public static final int SIDEBAR = 132;
        public static final int SIDEBAR_TOP = 100;
        public static final int ROUTE_ROW = 24;
        public static final int ROUTE_ROW_MOCK = 20;
        public static final int COMPASS = 44;
        public static final int REEL = 14;
        public static final int POCKET = 14;

        private Size() {}
    }

    public static final class Radius {
        public static final int NONE = 0;
        public static final int SM = 0;
        public static final int MD = 0;
        public static final int LG = 0;
        public static final int KEY = 1;
        public static final int MOUSE = 3;
        public static final int ROUND = 999;

        private Radius() {}
    }

    public static final class Stroke {
        public static final int HAIR = 1;
        public static final int FOCUS = 2;
        public static final int BAR = 2;
        public static final float GLYPH = 1.5f;

        private Stroke() {}
    }

    public static final class Duration {
        public static final int INSTANT = 0;
        public static final int FAST = 90;
        public static final int BASE = 140;
        public static final int SLOW = 220;
        public static final int OPEN = 160;
        public static final int CLOSE = 90;
        public static final int NUDGE = 220;
        public static final int REVEAL = 1200;
        public static final int REDUCED = 120;
        public static final int FLASH = 260;
        public static final int VERDICT = 120;

        private Duration() {}
    }

    public static final class Easing {
        /** Control points of a cubic Bézier from (0,0) to (1,1), as CSS cubic-bezier() takes them. */
        public record Curve(float x1, float y1, float x2, float y2) {}

        public static final Curve OUT = new Curve(0.16f, 1.00f, 0.30f, 1.00f);
        public static final Curve IN = new Curve(0.70f, 0.00f, 0.84f, 0.00f);
        public static final Curve IN_OUT = new Curve(0.83f, 0.00f, 0.17f, 1.00f);

        private Easing() {}
    }

    public static final class Spring {
        /**
         * {@code response} is the period, in seconds, of the undamped spring; {@code dampingFraction}
         * 1 is critically damped, which never overshoots.
         */
        public record Shape(float response, float dampingFraction) {}

        public static final Shape GLIDE = new Shape(0.16f, 1f);
        public static final Shape LIFT = new Shape(0.14f, 1f);
        public static final Shape SCROLL = new Shape(0.2f, 1f);

        private Spring() {}
    }

    public static final class Sound {
        public static final float VOLUME = 0.5f;
        public static final int FOCUS_INTERVAL_MS = 45;
        public static final float FOCUS_PITCH_JITTER = 0.04f;

        private Sound() {}
    }

    public static final class Layer {
        public static final int GROUND = 0;
        public static final int CONTENT = 100;
        public static final int RAIL = 200;
        public static final int OVERLAY = 300;
        public static final int POPOVER = 400;
        public static final int TOAST = 500;

        private Layer() {}
    }

    /**
     * One baked ttf provider per role and GUI scale. The game rasterises per provider, so a role
     * is a font id and not a scale factor — asking for title at 1.4x would resample the body
     * atlas and blur it. {@link Role#font} is the id stem; Typeset appends the scale suffix.
     */
    public static final class Type {
        /**
         * {@code font} is the provider id stem under assets/fullmoon/font; px and leading are GUI px.
         * {@code latinOnly} marks a role too small for Hangul: Typeset sets Hangul out of it.
         */
        public record Role(String font, int px, int leading, boolean latinOnly) {}

        /** Fullmoon Serif Bold 28/32 */
        public static final Role MARK = new Role("fullmoon:mark", 28, 32, false);
        /** Fullmoon Serif Bold 20/24 */
        public static final Role DISPLAY = new Role("fullmoon:display", 20, 24, false);
        /** Fullmoon Serif SemiBold 14/18 */
        public static final Role TITLE = new Role("fullmoon:title", 14, 18, false);
        /** Fullmoon Sans Bold 14/18 · tabular figures */
        public static final Role FIGURE = new Role("fullmoon:figure", 14, 18, false);
        /** Fullmoon Sans SemiBold 11/14 */
        public static final Role ROW = new Role("fullmoon:row", 11, 14, false);
        /** Fullmoon Sans 9/13 */
        public static final Role BODY = new Role("fullmoon:body", 9, 13, false);
        /** Fullmoon Sans SemiBold 9/13 */
        public static final Role STRONG = new Role("fullmoon:strong", 9, 13, false);
        /** Fullmoon Sans SemiBold 8/11 · Latin and digits only */
        public static final Role MICRO = new Role("fullmoon:micro", 8, 11, true);

        /** Declaration order, for the design specimen screen. */
        public static final java.util.List<java.util.Map.Entry<String, Role>> ROLL =
            java.util.List.of(
                java.util.Map.entry("mark", MARK),
                java.util.Map.entry("display", DISPLAY),
                java.util.Map.entry("title", TITLE),
                java.util.Map.entry("figure", FIGURE),
                java.util.Map.entry("row", ROW),
                java.util.Map.entry("body", BODY),
                java.util.Map.entry("strong", STRONG),
                java.util.Map.entry("micro", MICRO)
            );

        private Type() {}
    }

    /** Token name to packed colour, in declaration order, for the design specimen screen. */
    public static final java.util.List<java.util.Map.Entry<String, Integer>> COLOR_ROLL =
        java.util.List.of(
            java.util.Map.entry("surface.void", Color.SURFACE_VOID),
            java.util.Map.entry("surface.glass", Color.SURFACE_GLASS),
            java.util.Map.entry("surface.glassHud", Color.SURFACE_GLASS_HUD),
            java.util.Map.entry("surface.overlay", Color.SURFACE_OVERLAY),
            java.util.Map.entry("surface.base", Color.SURFACE_BASE),
            java.util.Map.entry("surface.sunken", Color.SURFACE_SUNKEN),
            java.util.Map.entry("surface.raised", Color.SURFACE_RAISED),
            java.util.Map.entry("surface.control", Color.SURFACE_CONTROL),
            java.util.Map.entry("surface.controlHover", Color.SURFACE_CONTROL_HOVER),
            java.util.Map.entry("surface.controlPressed", Color.SURFACE_CONTROL_PRESSED),
            java.util.Map.entry("surface.controlDisabled", Color.SURFACE_CONTROL_DISABLED),
            java.util.Map.entry("surface.highlight", Color.SURFACE_HIGHLIGHT),
            java.util.Map.entry("surface.edge", Color.SURFACE_EDGE),
            java.util.Map.entry("line.hairline", Color.LINE_HAIRLINE),
            java.util.Map.entry("line.strong", Color.LINE_STRONG),
            java.util.Map.entry("ink.primary", Color.INK_PRIMARY),
            java.util.Map.entry("ink.secondary", Color.INK_SECONDARY),
            java.util.Map.entry("ink.tertiary", Color.INK_TERTIARY),
            java.util.Map.entry("ink.disabled", Color.INK_DISABLED),
            java.util.Map.entry("ink.onAccent", Color.INK_ON_ACCENT),
            java.util.Map.entry("accent", Color.ACCENT),
            java.util.Map.entry("accent.pressed", Color.ACCENT_PRESSED),
            java.util.Map.entry("accent.wash", Color.ACCENT_WASH),
            java.util.Map.entry("accent.washLift", Color.ACCENT_WASH_LIFT),
            java.util.Map.entry("accent.glow", Color.ACCENT_GLOW),
            java.util.Map.entry("status.live", Color.STATUS_LIVE),
            java.util.Map.entry("status.idle", Color.STATUS_IDLE),
            java.util.Map.entry("status.warn", Color.STATUS_WARN),
            java.util.Map.entry("status.danger", Color.STATUS_DANGER),
            java.util.Map.entry("status.win", Color.STATUS_WIN),
            java.util.Map.entry("status.ash", Color.STATUS_ASH),
            java.util.Map.entry("moon.lit", Color.MOON_LIT),
            java.util.Map.entry("moon.shadow", Color.MOON_SHADOW),
            java.util.Map.entry("wheel.red", Color.WHEEL_RED),
            java.util.Map.entry("wheel.black", Color.WHEEL_BLACK),
            java.util.Map.entry("wheel.green", Color.WHEEL_GREEN),
            java.util.Map.entry("chat.yellow", Color.CHAT_YELLOW),
            java.util.Map.entry("chat.aqua", Color.CHAT_AQUA),
            java.util.Map.entry("chat.blue", Color.CHAT_BLUE),
            java.util.Map.entry("chat.purple", Color.CHAT_PURPLE)
        );
}
