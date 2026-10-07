package dev.fullmoon.client.text.fonts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class LazyGlyphProviderTest {
    // Each test uses a scale of its own: the registry is static, as the game's is.
    private static final int SCALE_OPEN = 21;
    private static final int SCALE_WARM = 22;
    private static final int SCALE_CLOSE = 23;

    @Test
    void aDeferredProviderOpensOnFirstUseAndOnlyOnce() {
        AtomicInteger opens = new AtomicInteger();
        FakeProvider real = new FakeProvider().with('a', 5f);
        LazyGlyphProvider lazy = LazyGlyphProvider.deferred(SCALE_OPEN, () -> {
            opens.incrementAndGet();
            return real;
        });
        assertEquals(0, opens.get());
        assertFalse(lazy.isOpen());

        assertNotNull(lazy.getGlyph('a'));
        assertNull(lazy.getGlyph('b'));
        assertTrue(lazy.getSupportedGlyphs().contains('a'));
        assertEquals(1, opens.get());
        assertTrue(lazy.isOpen());
    }

    @Test
    void anEagerProviderIsOpenAtOnce() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        LazyGlyphProvider lazy = LazyGlyphProvider.eager(SCALE_OPEN, () -> {
            opens.incrementAndGet();
            return new FakeProvider().with('a', 5f);
        });
        assertEquals(1, opens.get());
        assertTrue(lazy.isOpen());
        assertNotNull(lazy.getGlyph('a'));
        assertEquals(1, opens.get());
        lazy.close();
    }

    @Test
    void anEagerProviderThatFailsToOpenFailsTheLoadLikeTheGamesOwn() {
        try {
            LazyGlyphProvider.eager(SCALE_OPEN, () -> {
                throw new IOException("no such font");
            });
            throw new AssertionError("expected the load to fail");
        } catch (IOException expected) {
            assertEquals("no such font", expected.getMessage());
        }
    }

    @Test
    void aProviderThatCannotOpenAnswersNothingInsteadOfThrowing() {
        AtomicInteger opens = new AtomicInteger();
        LazyGlyphProvider lazy = LazyGlyphProvider.deferred(SCALE_OPEN, () -> {
            opens.incrementAndGet();
            throw new IOException("pack closed");
        });
        assertNull(lazy.getGlyph('a'));
        assertTrue(lazy.getSupportedGlyphs().isEmpty());
        assertEquals(1, opens.get(), "a failed open is not retried every frame");
        assertFalse(lazy.isOpen());
    }

    @Test
    void closingPassesThroughAndStopsLaterOpens() {
        FakeProvider real = new FakeProvider().with('a', 5f);
        LazyGlyphProvider open = LazyGlyphProvider.deferred(SCALE_CLOSE, () -> real);
        open.getGlyph('a');
        open.close();
        assertTrue(real.closed);
        open.close();

        AtomicInteger opens = new AtomicInteger();
        LazyGlyphProvider never = LazyGlyphProvider.deferred(SCALE_CLOSE, () -> {
            opens.incrementAndGet();
            return new FakeProvider();
        });
        never.close();
        assertNull(never.getGlyph('a'));
        assertEquals(0, opens.get(), "a closed provider never opens");
    }

    @Test
    void warmOpensTheClosedProvidersOfOneScaleOnTheExecutor() {
        AtomicInteger warmOpens = new AtomicInteger();
        AtomicInteger otherOpens = new AtomicInteger();
        LazyGlyphProvider a = LazyGlyphProvider.deferred(SCALE_WARM, () -> {
            warmOpens.incrementAndGet();
            return new FakeProvider();
        });
        LazyGlyphProvider b = LazyGlyphProvider.deferred(SCALE_WARM, () -> {
            warmOpens.incrementAndGet();
            return new FakeProvider();
        });
        LazyGlyphProvider other = LazyGlyphProvider.deferred(41, () -> {
            otherOpens.incrementAndGet();
            return new FakeProvider();
        });
        a.getGlyph('x'); // already open: warm must not open it again

        List<Runnable> queued = new ArrayList<>();
        LazyGlyphProvider.warm(SCALE_WARM, () -> queued::add);
        assertEquals(1, queued.size(), "only the provider that is still closed is queued");
        assertEquals(1, warmOpens.get());
        queued.forEach(Runnable::run);
        assertEquals(2, warmOpens.get());
        assertTrue(b.isOpen());
        assertEquals(0, otherOpens.get());

        List<Runnable> none = new ArrayList<>();
        LazyGlyphProvider.warm(SCALE_WARM, () -> {
            throw new AssertionError("nothing is pending, so no executor is needed");
        });
        LazyGlyphProvider.warm(99, () -> none::add);
        assertTrue(none.isEmpty());
        a.close();
        b.close();
        other.close();
    }
}
