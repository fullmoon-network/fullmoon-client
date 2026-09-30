package dev.fullmoon.client.render;

/** Packed-colour arithmetic. Hue and lightness always come from a token; only alpha and blends vary. */
public final class Rgb {
    private Rgb() {}

    /**
     * Sets a colour's alpha. For a scrim over the game's blur, a fade in or out, and the 42% a
     * disabled row's icon and name drop to; hue and lightness stay the token's.
     */
    public static int alpha(int argb, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Multiplies the alpha a colour already carries, so a translucent ground can fade as one. */
    public static int scaleAlpha(int argb, float factor) {
        return alpha(argb, alphaOf(argb) * factor);
    }

    public static float alphaOf(int argb) {
        return (argb >>> 24) / 255.0f;
    }

    /** Linear blend of two packed colours, alpha included; {@code t} 0 is {@code a}, 1 is {@code b}. */
    public static int mix(int a, int b, float t) {
        float k = Math.clamp(t, 0.0f, 1.0f);
        int alpha = lerp(a >>> 24, b >>> 24, k);
        int red = lerp((a >>> 16) & 0xFF, (b >>> 16) & 0xFF, k);
        int green = lerp((a >>> 8) & 0xFF, (b >>> 8) & 0xFF, k);
        int blue = lerp(a & 0xFF, b & 0xFF, k);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** {@code top} composited over an opaque {@code bottom}: the colour a translucent ground is on screen. */
    public static int over(int top, int bottom) {
        float a = alphaOf(top);
        int red = lerp((bottom >>> 16) & 0xFF, (top >>> 16) & 0xFF, a);
        int green = lerp((bottom >>> 8) & 0xFF, (top >>> 8) & 0xFF, a);
        int blue = lerp(bottom & 0xFF, top & 0xFF, a);
        return alpha((red << 16) | (green << 8) | blue, 1.0f);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
