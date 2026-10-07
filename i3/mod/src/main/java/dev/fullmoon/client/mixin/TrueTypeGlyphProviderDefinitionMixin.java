package dev.fullmoon.client.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.datafixers.util.Either;

import dev.fullmoon.client.text.fonts.FontLoading;
import dev.fullmoon.client.text.fonts.GuiScaleVariants;
import dev.fullmoon.client.text.fonts.LazyGlyphProvider;

import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Loads this client's ttf providers cheaply: one provider per GUI scale is opened in the reload and
 * the rest wait to be asked for, and providers that read the same file share one native buffer.
 * See {@link LazyGlyphProvider} and {@link dev.fullmoon.client.text.fonts.BufferShare}.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
abstract class TrueTypeGlyphProviderDefinitionMixin {
    @Inject(method = "unpack", at = @At("RETURN"), cancellable = true)
    private void fullmoon$wrap(
        CallbackInfoReturnable<Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference>> cir
    ) {
        TrueTypeGlyphProviderDefinition self = (TrueTypeGlyphProviderDefinition) (Object) this;
        if (!FontLoading.ours(self.location()) || !GuiScaleVariants.isVariant(self.oversample())) {
            return;
        }
        GlyphProviderDefinition.Loader vanilla = cir.getReturnValue().left().orElse(null);
        if (vanilla == null) {
            return;
        }
        int scale = Math.round(self.oversample());
        // The scale is read when the reload builds its loaders, which is after the window has
        // sized the GUI. With no window yet, nothing is deferred.
        int guiScale = FontLoading.currentGuiScale();
        boolean wanted = guiScale <= 0 || GuiScaleVariants.isWanted(self.oversample(), guiScale);
        GlyphProviderDefinition.Loader wrapped = manager -> {
            LazyGlyphProvider.Source source = () -> vanilla.load(manager);
            return wanted ? LazyGlyphProvider.eager(scale, source) : LazyGlyphProvider.deferred(scale, source);
        };
        cir.setReturnValue(Either.<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference>left(wrapped));
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void fullmoon$loading(ResourceManager manager, CallbackInfoReturnable<GlyphProvider> cir) {
        FontLoading.loading(manager);
    }

    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/TextureUtil;readResource(Ljava/io/InputStream;)Ljava/nio/ByteBuffer;"
        )
    )
    private ByteBuffer fullmoon$read(InputStream stream) throws IOException {
        TrueTypeGlyphProviderDefinition self = (TrueTypeGlyphProviderDefinition) (Object) this;
        FontLoading.FileKey key = FontLoading.ours(self.location()) ? FontLoading.keyFor(self.location()) : null;
        if (key == null) {
            return TextureUtil.readResource(stream);
        }
        return FontLoading.BUFFERS.acquire(key, () -> TextureUtil.readResource(stream));
    }

    @Redirect(
        method = "load",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/ByteBuffer;)V")
    )
    private void fullmoon$free(ByteBuffer buffer) {
        FontLoading.release(buffer);
    }
}
