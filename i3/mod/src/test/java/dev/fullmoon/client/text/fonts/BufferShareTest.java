package dev.fullmoon.client.text.fonts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class BufferShareTest {
    private final List<ByteBuffer> freed = new ArrayList<>();
    private final BufferShare<String> share = new BufferShare<>(freed::add);
    private int reads;

    private ByteBuffer read() {
        reads++;
        return ByteBuffer.allocate(8);
    }

    @Test
    void providersOfOneFileShareOneBufferReadOnce() throws IOException {
        ByteBuffer first = share.acquire("sans-bold", this::read);
        ByteBuffer second = share.acquire("sans-bold", this::read);
        assertSame(first, second);
        assertEquals(1, reads);
        assertEquals(1, share.size());
    }

    @Test
    void differentFilesGetTheirOwn() throws IOException {
        ByteBuffer bold = share.acquire("sans-bold", this::read);
        ByteBuffer regular = share.acquire("sans-regular", this::read);
        assertNotSame(bold, regular);
        assertEquals(2, reads);
    }

    @Test
    void theBufferIsFreedWithItsLastHolderOnly() throws IOException {
        ByteBuffer buffer = share.acquire("f", this::read);
        share.acquire("f", this::read);
        share.acquire("f", this::read);
        assertTrue(share.release(buffer));
        assertTrue(share.release(buffer));
        assertTrue(freed.isEmpty(), "two of three holders are still reading it");
        assertTrue(share.release(buffer));
        assertEquals(List.of(buffer), freed);
        assertEquals(0, share.size());
    }

    @Test
    void aFileIsReadAgainOnceNobodyHoldsIt() throws IOException {
        ByteBuffer first = share.acquire("f", this::read);
        share.release(first);
        ByteBuffer second = share.acquire("f", this::read);
        assertNotSame(first, second);
        assertEquals(2, reads);
    }

    @Test
    void aBufferThatIsNotTheSharesIsLeftToTheCaller() {
        ByteBuffer foreign = ByteBuffer.allocate(4);
        assertFalse(share.release(foreign));
        assertFalse(share.release(null));
        assertTrue(freed.isEmpty());
    }

    @Test
    void aFailedReadHoldsNothing() {
        assertThrows(IOException.class, () -> share.acquire("f", () -> {
            throw new IOException("unreadable");
        }));
        assertEquals(0, share.size());
    }
}
