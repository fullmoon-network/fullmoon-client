package dev.fullmoon.client.mixin;

import java.util.List;

import com.mojang.blaze3d.font.GlyphProvider;

import dev.fullmoon.client.text.fonts.LazyGlyphProvider;

import net.minecraft.client.gui.font.FontManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game warms a font set while the reload is prepared by loading, for every codepoint any of
 * its providers supports, the glyph the set would draw. It changes no result, only what is already
 * in the glyph caches when the first frame draws; for this client's sets that is every outline of
 * a Hangul face that no screen asks for. The glyphs these sets draw are loaded as they are drawn.
 */
@Mixin(FontManager.class)
abstract class FontManagerMixin {
    @Inject(method = "finalizeProviderLoading", at = @At("HEAD"), cancellable = true)
    private void fullmoon$skipWarmup(
        List<GlyphProvider.Conditional> list, GlyphProvider.Conditional fallback, CallbackInfo ci
    ) {
        for (GlyphProvider.Conditional conditional : list) {
            if (conditional.provider() instanceof LazyGlyphProvider) {
                ci.cancel();
                return;
            }
        }
    }
}
