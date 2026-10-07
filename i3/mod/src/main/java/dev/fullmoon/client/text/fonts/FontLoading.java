package dev.fullmoon.client.text.fonts;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;

import org.lwjgl.system.MemoryUtil;

/**
 * Where the font loading hooks meet the game: whether they are on, which scale the window is at,
 * and the one buffer share.
 *
 * <p>{@code -Dfullmoon.fonts.eager=true} turns every hook into the game's own behaviour, for a
 * side-by-side comparison or if a game update moves the code the hooks stand on.
 */
public final class FontLoading {
    /** The namespace whose font files are loaded this way; nothing else is touched. */
    public static final String NAMESPACE = "fullmoon";

    private static final boolean ENABLED = !Boolean.getBoolean("fullmoon.fonts.eager");

    /** A font file as one reload sees it: two reloads never share a buffer, a pack may differ. */
    public record FileKey(ResourceManager manager, Identifier file) {
        @Override
        public boolean equals(Object other) {
            return other instanceof FileKey key && key.manager == manager && key.file.equals(file);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(manager) * 31 + file.hashCode();
        }
    }

    public static final BufferShare<FileKey> BUFFERS = new BufferShare<>(MemoryUtil::memFree);

    /** The resource manager of the font load in progress on this thread, for {@link FileKey}. */
    private static final ThreadLocal<ResourceManager> LOADING = new ThreadLocal<>();

    private FontLoading() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean ours(Identifier file) {
        return ENABLED && NAMESPACE.equals(file.getNamespace());
    }

    public static void loading(ResourceManager manager) {
        LOADING.set(manager);
    }

    /** The key for a file read by the load in progress on this thread, or null outside one. */
    public static FileKey keyFor(Identifier file) {
        ResourceManager manager = LOADING.get();
        return manager == null ? null : new FileKey(manager, file);
    }

    /** Gives a font buffer back: to the share if it is the share's, else freed as the game would. */
    public static void release(Buffer buffer) {
        if (buffer instanceof ByteBuffer bytes && BUFFERS.release(bytes)) {
            return;
        }
        MemoryUtil.memFree(buffer);
    }

    /** The GUI scale the window is at, or 0 before there is a window. */
    public static int currentGuiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 0;
        }
        return minecraft.getWindow().getGuiScale();
    }

    /** Opens the providers of {@code guiScale}'s variant that are still closed, off the render thread. */
    public static void warm(int guiScale) {
        if (!ENABLED) {
            return;
        }
        LazyGlyphProvider.warm(GuiScaleVariants.scaleOf(guiScale), Util::backgroundExecutor);
    }
}
