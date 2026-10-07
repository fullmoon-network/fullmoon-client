package dev.fullmoon.client.text.fonts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import org.slf4j.Logger;

/**
 * A glyph provider that is opened the first time a glyph is asked of it.
 *
 * <p>The game opens every provider of every font file at every resource reload: it reads the
 * whole ttf into a native buffer, makes a FreeType face over it and walks the face's cmap. The
 * client ships each role once per GUI scale, three times over, though a window is only ever at one
 * scale; so two thirds of that work, and of the memory it leaves behind, is for text the window is
 * not drawing. The providers of the scale the window is at are opened in the reload as before;
 * the others are wrapped in this and cost nothing until the window moves to their scale, when
 * {@link #warm} opens them off the render thread.
 *
 * <p>What the wrapped provider answers is exactly what the unwrapped one would: every call is
 * delegated.
 */
public final class LazyGlyphProvider implements GlyphProvider {
    /** Opens the real provider: the loader the game built for the font definition. */
    @FunctionalInterface
    public interface Source {
        GlyphProvider open() throws IOException;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Live wrappers by the scale they are baked for, so a scale change can open its set. */
    private static final java.util.Map<Integer, Set<LazyGlyphProvider>> BY_SCALE = new ConcurrentHashMap<>();

    private final int scale;
    private final Source source;
    private volatile GlyphProvider real;
    private boolean closed;
    private boolean failed;

    private LazyGlyphProvider(int scale, Source source) {
        this.scale = scale;
        this.source = source;
    }

    /** A wrapper over {@code source} that opens on first use. */
    public static LazyGlyphProvider deferred(int scale, Source source) {
        LazyGlyphProvider provider = new LazyGlyphProvider(scale, source);
        BY_SCALE.computeIfAbsent(scale, s -> ConcurrentHashMap.newKeySet()).add(provider);
        return provider;
    }

    /** A wrapper over {@code source} that is opened now, on the calling thread, like the game's own. */
    public static LazyGlyphProvider eager(int scale, Source source) throws IOException {
        LazyGlyphProvider provider = deferred(scale, source);
        GlyphProvider opened = source.open();
        synchronized (provider) {
            provider.real = opened;
        }
        return provider;
    }

    /**
     * Opens, on the executor {@code executors} supplies, every live wrapper baked for {@code scale} that is not open yet.
     * Whatever the render thread asks for before that finishes it opens itself.
     */
    public static void warm(int scale, Supplier<Executor> executors) {
        Set<LazyGlyphProvider> set = BY_SCALE.get(scale);
        if (set == null) {
            return;
        }
        List<LazyGlyphProvider> pending = new ArrayList<>();
        for (LazyGlyphProvider provider : set) {
            if (provider.real == null) {
                pending.add(provider);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        Executor executor = executors.get();
        for (LazyGlyphProvider provider : pending) {
            executor.execute(provider::resolve);
        }
    }

    /** Whether the real provider has been opened. */
    public boolean isOpen() {
        return real != null;
    }

    private GlyphProvider resolve() {
        GlyphProvider opened = real;
        return opened != null ? opened : open();
    }

    private synchronized GlyphProvider open() {
        if (real != null || closed || failed) {
            return real;
        }
        try {
            real = source.open();
        } catch (IOException | RuntimeException e) {
            failed = true;
            LOGGER.warn("Failed to open the x{} font provider on demand; its glyphs are missing", scale, e);
        }
        return real;
    }

    @Override
    public UnbakedGlyph getGlyph(int codepoint) {
        GlyphProvider opened = resolve();
        return opened == null ? null : opened.getGlyph(codepoint);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        GlyphProvider opened = resolve();
        return opened == null ? IntSets.EMPTY_SET : opened.getSupportedGlyphs();
    }

    @Override
    public void close() {
        GlyphProvider opened;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            opened = real;
        }
        Set<LazyGlyphProvider> set = BY_SCALE.get(scale);
        if (set != null) {
            set.remove(this);
        }
        if (opened != null) {
            opened.close();
        }
    }
}
