package dev.fullmoon.client.mixin;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;

import dev.fullmoon.client.text.fonts.FontLoading;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A ttf provider gives its buffer back to the share instead of freeing it under its siblings. */
@Mixin(TrueTypeGlyphProvider.class)
abstract class TrueTypeGlyphProviderMixin {
    @Redirect(
        method = "close",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/ByteBuffer;)V")
    )
    private void fullmoon$free(ByteBuffer buffer) {
        FontLoading.release(buffer);
    }
}
