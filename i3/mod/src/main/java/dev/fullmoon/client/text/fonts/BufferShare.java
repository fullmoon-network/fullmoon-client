package dev.fullmoon.client.text.fonts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reference-counted native buffers shared between the providers that read the same file.
 *
 * <p>The game reads a ttf into a fresh native buffer for every provider that names it, and keeps
 * the buffer for as long as the provider lives, so the client's 2 MB Hangul faces were held once
 * per role and scale. FreeType only reads a memory face's bytes, so any number of faces can sit
 * over one buffer; this hands the same buffer to every provider of a key and frees it when the
 * last of them lets go.
 *
 * @param <K> identifies a file as one reload sees it
 */
public final class BufferShare<K> {
    private static final class Entry<K> {
        final K key;
        final ByteBuffer buffer;
        int holders;

        Entry(K key, ByteBuffer buffer) {
            this.key = key;
            this.buffer = buffer;
        }
    }

    /** Reads the file when no live holder has it. */
    @FunctionalInterface
    public interface Reader {
        ByteBuffer read() throws IOException;
    }

    private final Consumer<ByteBuffer> free;
    private final Map<K, Entry<K>> byKey = new HashMap<>();
    private final Map<ByteBuffer, Entry<K>> byBuffer = new IdentityHashMap<>();

    public BufferShare(Consumer<ByteBuffer> free) {
        this.free = free;
    }

    /** The buffer for {@code key}, read by {@code reader} if no one holds it; one more holder. */
    public synchronized ByteBuffer acquire(K key, Reader reader) throws IOException {
        Entry<K> entry = byKey.get(key);
        if (entry == null) {
            entry = new Entry<>(key, reader.read());
            byKey.put(key, entry);
            byBuffer.put(entry.buffer, entry);
        }
        entry.holders++;
        return entry.buffer;
    }

    /**
     * Lets go of {@code buffer}. Returns true when it was one of this share's, which is freed
     * with the last holder; false when it is not and the caller still owns it.
     */
    public synchronized boolean release(ByteBuffer buffer) {
        Entry<K> entry = buffer == null ? null : byBuffer.get(buffer);
        if (entry == null) {
            return false;
        }
        if (--entry.holders == 0) {
            byKey.remove(entry.key);
            byBuffer.remove(entry.buffer);
            free.accept(entry.buffer);
        }
        return true;
    }

    /** Buffers currently held, for tests. */
    public synchronized int size() {
        return byKey.size();
    }
}
