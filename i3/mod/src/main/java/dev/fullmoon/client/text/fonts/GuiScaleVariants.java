package dev.fullmoon.client.text.fonts;

/**
 * The GUI scales the font providers are baked for, and which of them a window at a given scale
 * draws through. A ttf provider is rasterised once at its oversample and sampled with nearest
 * filtering, so a glyph is only pixel-exact where the oversample equals the GUI scale; every role
 * therefore ships one provider per scale in {@link #SCALES}, and any other scale takes the nearest
 * (scale 1 shares the ×2 atlas, 5 and up the ×4).
 */
public final class GuiScaleVariants {
    /** The GUI scales a provider set is baked for; any other scale takes the nearest of these. */
    public static final int[] SCALES = {2, 3, 4};

    private GuiScaleVariants() {}

    /** Index into {@link #SCALES} of the variant a window at {@code guiScale} draws through. */
    public static int indexOf(int guiScale) {
        int best = 0;
        for (int i = 1; i < SCALES.length; i++) {
            if (Math.abs(SCALES[i] - guiScale) < Math.abs(SCALES[best] - guiScale)) {
                best = i;
            }
        }
        return best;
    }

    /** The baked scale a window at {@code guiScale} draws through. */
    public static int scaleOf(int guiScale) {
        return SCALES[indexOf(guiScale)];
    }

    /** Whether {@code oversample} is one of the baked scales (a provider that has a sibling per scale). */
    public static boolean isVariant(float oversample) {
        for (int scale : SCALES) {
            if (oversample == scale) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a provider baked at {@code oversample} is the one a window at {@code guiScale} draws
     * through. A provider that is not a per-scale variant is always wanted.
     */
    public static boolean isWanted(float oversample, int guiScale) {
        return !isVariant(oversample) || oversample == scaleOf(guiScale);
    }
}
