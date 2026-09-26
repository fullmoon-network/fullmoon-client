package dev.fullmoon.client.design;

/**
 * Generated from i3/design/tokens.json by i3/design/generate.mjs. Do not edit by hand,
 * and do not write a colour, radius, or duration literal anywhere else in the mod —
 * design/verify-tokens.mjs fails on any that appear outside this file.
 */
public final class Tokens {
    private Tokens() {}

    /** Packed 0xAARRGGBB, opaque. Only a scrim reopens the alpha, via Rgb#alpha. */
    public static final class Color {
        /** scrim behind a full-screen surface · oklch(0.145 0.018 275) */
        public static final int SURFACE_VOID = 0xFF080A12;
        /** inset wells, scroll troughs, empty states · oklch(0.17 0.02 275) */
        public static final int SURFACE_SUNKEN = 0xFF0D0F18;
        /** panel ground · oklch(0.195 0.022 275) */
        public static final int SURFACE_BASE = 0xFF12141F;
        /** hovered row, selected list item ground · oklch(0.235 0.026 275) */
        public static final int SURFACE_RAISED = 0xFF1A1D2A;
        /** popover, tooltip, dropdown · oklch(0.275 0.03 275) */
        public static final int SURFACE_OVERLAY = 0xFF232737;
        /** row separators inside a panel · oklch(0.3 0.02 275) */
        public static final int LINE_HAIRLINE = 0xFF2B2D38;
        /** section rule under a title · oklch(0.4 0.022 275) */
        public static final int LINE_STRONG = 0xFF444754;
        /** the palace frame's outer line, a tile's edge · oklch(0.47 0.045 85) */
        public static final int LINE_GILT = 0xFF66593D;
        /** the frame's second line, outside the first · oklch(0.31 0.022 80) */
        public static final int LINE_GILT_FAINT = 0xFF362F24;
        /** window-lattice strokes in a header band · oklch(0.24 0.02 80) */
        public static final int LINE_LATTICE = 0xFF241E14;
        /** titles, values, active labels · oklch(0.935 0.017 85) */
        public static final int INK_PRIMARY = 0xFFEFE9DD;
        /** body copy, inactive labels · oklch(0.765 0.02 88) */
        public static final int INK_SECONDARY = 0xFFB8B2A5;
        /** meta text, units, hints · oklch(0.605 0.02 88) */
        public static final int INK_TERTIARY = 0xFF878174;
        /** disabled label · oklch(0.42 0.012 275) */
        public static final int INK_DISABLED = 0xFF4B4D54;
        /** text on an accent fill · oklch(0.2 0.02 88) */
        public static final int INK_ON_ACCENT = 0xFF1A160B;
        /** corner brackets, selection, one primary action per surface, live values · oklch(0.835 0.115 88) */
        public static final int ACCENT = 0xFFE8C56C;
        /** accent fill while held · oklch(0.72 0.105 86) */
        public static final int ACCENT_PRESSED = 0xFFC2A052;
        /** selected-row tint behind ink · oklch(0.285 0.035 80) */
        public static final int ACCENT_WASH = 0xFF332815;
        /** server reachable, module enabled · oklch(0.78 0.105 168) */
        public static final int STATUS_LIVE = 0xFF6DCDAB;
        /** server unreachable, module off · oklch(0.545 0.015 275) */
        public static final int STATUS_IDLE = 0xFF6D7079;
        /** degraded, pending, unverified · oklch(0.805 0.128 66) */
        public static final int STATUS_WARN = 0xFFF7AE5F;
        /** destructive only — never a primary action fill · oklch(0.615 0.155 25) */
        public static final int STATUS_DANGER = 0xFFD25853;
        /** dancheong band: the green panel · oklch(0.62 0.08 170) */
        public static final int ORNAMENT_JADE = 0xFF50967F;
        /** dancheong band, the moon seal's ground · oklch(0.56 0.14 35) */
        public static final int ORNAMENT_CINNABAR = 0xFFB75037;
        /** dancheong band: the blue panel · oklch(0.36 0.09 268) */
        public static final int ORNAMENT_LAPIS = 0xFF29396C;
        /** the lit face of a moon phase · oklch(0.93 0.055 90) */
        public static final int MOON_LIT = 0xFFF6E7BF;
        /** the unlit face of a moon phase · oklch(0.235 0.025 260) */
        public static final int MOON_SHADOW = 0xFF171E2A;

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

    public static final class Radius {
        public static final int NONE = 0;
        public static final int SM = 0;
        public static final int MD = 0;
        public static final int LG = 0;
        public static final int ROUND = 999;

        private Radius() {}
    }

    public static final class Stroke {
        public static final int HAIR = 1;
        public static final int FOCUS = 2;

        private Stroke() {}
    }

    public static final class Duration {
        public static final int INSTANT = 0;
        public static final int FAST = 90;
        public static final int BASE = 140;
        public static final int SLOW = 220;
        public static final int REVEAL = 1200;
        public static final int REDUCED = 120;

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
     * One baked ttf provider per role. The game rasterises per provider, so a role is
     * a font id and not a scale factor — asking for title at 1.4x would resample the
     * body atlas and blur it.
     */
    public static final class Type {
        /** {@code font} is the provider id under assets/fullmoon/font; px and leading are GUI px. */
        public record Role(String font, int px, int leading) {}

        /** Fullmoon Serif 38/44 */
        public static final Role WORDMARK = new Role("fullmoon:wordmark", 38, 44);
        /** Fullmoon Serif 22/28 */
        public static final Role DISPLAY = new Role("fullmoon:display", 22, 28);
        /** Fullmoon Serif 13/18 */
        public static final Role HEADING = new Role("fullmoon:heading", 13, 18);
        /** Pretendard SemiBold 13/18 */
        public static final Role TITLE = new Role("fullmoon:title", 13, 18);
        /** Pretendard 9/13 */
        public static final Role BODY = new Role("fullmoon:body", 9, 13);
        /** Pretendard SemiBold 9/13 */
        public static final Role BODY_STRONG = new Role("fullmoon:body_strong", 9, 13);
        /** Pretendard SemiBold 8/11 */
        public static final Role LABEL = new Role("fullmoon:label", 8, 11);

        /** Declaration order, for the design specimen screen. */
        public static final java.util.List<java.util.Map.Entry<String, Role>> ROLL =
            java.util.List.of(
                java.util.Map.entry("wordmark", WORDMARK),
                java.util.Map.entry("display", DISPLAY),
                java.util.Map.entry("heading", HEADING),
                java.util.Map.entry("title", TITLE),
                java.util.Map.entry("body", BODY),
                java.util.Map.entry("bodyStrong", BODY_STRONG),
                java.util.Map.entry("label", LABEL)
            );

        private Type() {}
    }

    /** Token name to packed colour, in declaration order, for the design specimen screen. */
    public static final java.util.List<java.util.Map.Entry<String, Integer>> COLOR_ROLL =
        java.util.List.of(
            java.util.Map.entry("surface.void", Color.SURFACE_VOID),
            java.util.Map.entry("surface.sunken", Color.SURFACE_SUNKEN),
            java.util.Map.entry("surface.base", Color.SURFACE_BASE),
            java.util.Map.entry("surface.raised", Color.SURFACE_RAISED),
            java.util.Map.entry("surface.overlay", Color.SURFACE_OVERLAY),
            java.util.Map.entry("line.hairline", Color.LINE_HAIRLINE),
            java.util.Map.entry("line.strong", Color.LINE_STRONG),
            java.util.Map.entry("line.gilt", Color.LINE_GILT),
            java.util.Map.entry("line.giltFaint", Color.LINE_GILT_FAINT),
            java.util.Map.entry("line.lattice", Color.LINE_LATTICE),
            java.util.Map.entry("ink.primary", Color.INK_PRIMARY),
            java.util.Map.entry("ink.secondary", Color.INK_SECONDARY),
            java.util.Map.entry("ink.tertiary", Color.INK_TERTIARY),
            java.util.Map.entry("ink.disabled", Color.INK_DISABLED),
            java.util.Map.entry("ink.onAccent", Color.INK_ON_ACCENT),
            java.util.Map.entry("accent", Color.ACCENT),
            java.util.Map.entry("accent.pressed", Color.ACCENT_PRESSED),
            java.util.Map.entry("accent.wash", Color.ACCENT_WASH),
            java.util.Map.entry("status.live", Color.STATUS_LIVE),
            java.util.Map.entry("status.idle", Color.STATUS_IDLE),
            java.util.Map.entry("status.warn", Color.STATUS_WARN),
            java.util.Map.entry("status.danger", Color.STATUS_DANGER),
            java.util.Map.entry("ornament.jade", Color.ORNAMENT_JADE),
            java.util.Map.entry("ornament.cinnabar", Color.ORNAMENT_CINNABAR),
            java.util.Map.entry("ornament.lapis", Color.ORNAMENT_LAPIS),
            java.util.Map.entry("moon.lit", Color.MOON_LIT),
            java.util.Map.entry("moon.shadow", Color.MOON_SHADOW)
        );
}
