package dev.fullmoon.client.text.fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The per-codepoint scans the game runs over a font set when it is built, and the part of each
 * that can wait.
 *
 * <p>For every codepoint any provider of a set supports, the game loads the glyph from the first
 * provider that has it, twice over: once when the reload is prepared, to warm it, and once when
 * the set is selected, to learn which providers contribute and to index codepoints by advance
 * width. A full Hangul face supports 11,000 codepoints and the client has 27 sets, so a start-up
 * rasterised some three hundred thousand glyph outlines that no screen ever draws. The warm-up
 * changes nothing a draw can see. The index is read only by obfuscated text, and the contributing
 * providers are decided by which codepoints a provider has, not by their glyphs. This is those two
 * decisions without the glyph loads.
 */
public final class FontScan {
    private FontScan() {}

    /**
     * Whether a selected provider list is one this client built: at least one provider that is
     * opened on demand, and every other provider either supports nothing (the game's all-missing
     * fallback) or is the vanilla space provider. Anything else is left to the game's own scan.
     */
    public static boolean applies(List<? extends GlyphProvider> selected) {
        boolean lazy = false;
        for (GlyphProvider provider : selected) {
            if (provider instanceof LazyGlyphProvider) {
                lazy = true;
            } else if (!(provider instanceof SpaceProvider) && !provider.getSupportedGlyphs().isEmpty()) {
                return false;
            }
        }
        return lazy;
    }

    /**
     * The providers the game keeps after its scan: those that are first to have some codepoint.
     * The client's own providers each are, and a space provider that is not has nothing a glyph
     * lookup can reach (see ShippedFontsTest), so the only ones dropped are the providers that
     * support nothing, which the game's scan drops too.
     */
    public static List<GlyphProvider> used(List<GlyphProvider> selected) {
        List<GlyphProvider> used = new ArrayList<>(selected.size());
        for (GlyphProvider provider : selected) {
            if (provider instanceof LazyGlyphProvider || !provider.getSupportedGlyphs().isEmpty()) {
                used.add(provider);
            }
        }
        return List.copyOf(used);
    }

    /**
     * Fills {@code out} with the codepoints of {@code providers} by the width of their glyph, in the
     * order the game's scan does: the union of supported codepoints walked once, each placed by
     * the first provider that has a glyph for it.
     *
     * @param counts whether a glyph takes part (the game leaves its missing glyph out)
     * @param width the bucket a glyph's advance falls in
     */
    public static void indexByWidth(
        List<? extends GlyphProvider> providers,
        Int2ObjectMap<IntList> out,
        Predicate<UnbakedGlyph> counts,
        ToIntFunction<UnbakedGlyph> width
    ) {
        IntFunction<IntList> newBucket = w -> new IntArrayList();
        IntSet supported = new IntOpenHashSet();
        for (GlyphProvider provider : providers) {
            supported.addAll(provider.getSupportedGlyphs());
        }
        supported.forEach(codepoint -> {
            for (GlyphProvider provider : providers) {
                UnbakedGlyph glyph = provider.getGlyph(codepoint);
                if (glyph != null) {
                    if (counts.test(glyph)) {
                        out.computeIfAbsent(width.applyAsInt(glyph), newBucket).add(codepoint);
                    }
                    break;
                }
            }
        });
    }
}
