package dev.fullmoon.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;

import dev.fullmoon.client.text.fonts.FontScan;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Selecting a font set's providers walks every codepoint any of them supports, loading its glyph, to
 * learn which providers contribute and to index codepoints by advance width for obfuscated text.
 * For this client's sets the providers that contribute are known without it (see
 * {@link FontScan#used}), and the width index is only read by {@code getRandomGlyph}, so it is
 * built there, the first time a set is asked for a random glyph.
 */
@Mixin(FontSet.class)
abstract class FontSetMixin {
    @Shadow
    @Final
    private Int2ObjectMap<IntList> glyphsByWidth;

    /** The providers the width index is built from, while it has not been built; else null. */
    @Unique
    private List<GlyphProvider> fullmoon$indexFrom;

    @Inject(method = "selectProviders", at = @At("HEAD"), cancellable = true)
    private void fullmoon$select(
        List<GlyphProvider.Conditional> providers,
        Set<FontOption> options,
        CallbackInfoReturnable<List<GlyphProvider>> cir
    ) {
        fullmoon$indexFrom = null;
        List<GlyphProvider> selected = new ArrayList<>(providers.size());
        for (GlyphProvider.Conditional conditional : providers) {
            if (conditional.filter().apply(options)) {
                selected.add(conditional.provider());
            }
        }
        if (FontScan.applies(selected)) {
            fullmoon$indexFrom = selected;
            cir.setReturnValue(FontScan.used(selected));
        }
    }

    @Inject(method = "getRandomGlyph", at = @At("HEAD"))
    private void fullmoon$index(RandomSource random, int width, CallbackInfoReturnable<net.minecraft.client.gui.font.glyphs.BakedGlyph> cir) {
        List<GlyphProvider> from = fullmoon$indexFrom;
        if (from != null) {
            fullmoon$indexFrom = null;
            FontScan.indexByWidth(
                from,
                glyphsByWidth,
                (UnbakedGlyph glyph) -> glyph.info() != SpecialGlyphs.MISSING,
                (UnbakedGlyph glyph) -> Mth.ceil(glyph.info().getAdvance(false)));
        }
    }
}
