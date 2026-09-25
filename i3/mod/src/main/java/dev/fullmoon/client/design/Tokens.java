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
        /** scrim behind a full-screen surface · oklch(0.145 0.008 89) */
        public static final int SURFACE_VOID = 0xFF0B0A07;
        /** inset wells, scroll troughs, empty states · oklch(0.185 0.009 89) */
        public static final int SURFACE_SUNKEN = 0xFF14130E;
        /** panel ground · oklch(0.215 0.01 89) */
        public static final int SURFACE_BASE = 0xFF1B1914;
        /** hovered row, selected list item ground · oklch(0.262 0.012 89) */
        public static final int SURFACE_RAISED = 0xFF27241E;
        /** popover, tooltip, dropdown · oklch(0.305 0.013 89) */
        public static final int SURFACE_OVERLAY = 0xFF322F28;
        /** row separators, panel edges · oklch(0.31 0.009 89) */
        public static final int LINE_HAIRLINE = 0xFF32302B;
        /** section rule under a title · oklch(0.42 0.011 89) */
        public static final int LINE_STRONG = 0xFF4F4D46;
        /** titles, values, active labels · oklch(0.945 0.01 89) */
        public static final int INK_PRIMARY = 0xFFEFEDE5;
        /** body copy, inactive labels · oklch(0.76 0.009 89) */
        public static final int INK_SECONDARY = 0xFFB3B1AB;
        /** meta text, units, hints · oklch(0.575 0.008 89) */
        public static final int INK_TERTIARY = 0xFF7B7974;
        /** disabled label · oklch(0.42 0.006 89) */
        public static final int INK_DISABLED = 0xFF4E4D49;
        /** text on an accent fill · oklch(0.18 0.014 89) */
        public static final int INK_ON_ACCENT = 0xFF14110A;
        /** selection bar, one primary action per surface, live values · oklch(0.87 0.124 89) */
        public static final int ACCENT = 0xFFF5D06E;
        /** accent fill while held · oklch(0.735 0.112 86) */
        public static final int ACCENT_PRESSED = 0xFFC9A44F;
        /** selected-row tint behind ink · oklch(0.3 0.042 89) */
        public static final int ACCENT_WASH = 0xFF362D13;
        /** server reachable, module enabled · oklch(0.78 0.105 168) */
        public static final int STATUS_LIVE = 0xFF6DCDAB;
        /** server unreachable, module off · oklch(0.545 0.012 89) */
        public static final int STATUS_IDLE = 0xFF737068;
        /** degraded, pending, unverified · oklch(0.805 0.128 66) */
        public static final int STATUS_WARN = 0xFFF7AE5F;
        /** destructive only — never a primary action fill · oklch(0.615 0.155 25) */
        public static final int STATUS_DANGER = 0xFFD25853;

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
        public static final int SM = 3;
        public static final int MD = 5;
        public static final int LG = 8;
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

        /** Fullmoon Serif 22/28 */
        public static final Role DISPLAY = new Role("fullmoon:display", 22, 28);
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
                java.util.Map.entry("display", DISPLAY),
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
            java.util.Map.entry("status.danger", Color.STATUS_DANGER)
        );
}
