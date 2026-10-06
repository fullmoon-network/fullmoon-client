package dev.fullmoon.client.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

final class MemoTest {
    @Test
    void aRememberedValueComesBackAsTheSameObject() {
        Memo<Object> memo = new Memo<>(4);
        Object value = new Object();
        assertNull(memo.find("a"));
        memo.remember("a", value);
        assertSame(value, memo.find("a"));
    }

    @Test
    void theTableIsDroppedWholeOnceItIsFull() {
        Memo<Integer> memo = new Memo<>(3);
        memo.remember("a", 1);
        memo.remember("b", 2);
        memo.remember("c", 3);
        assertEquals(3, memo.size());

        memo.remember("d", 4);

        assertEquals(1, memo.size(), "full means start again, not evict one");
        assertNull(memo.find("a"));
        assertEquals(4, memo.find("d"));
    }

    @Test
    void neverHoldsMoreThanTheLimit() {
        Memo<Integer> memo = new Memo<>(8);
        for (int i = 0; i < 1000; i++) {
            memo.remember("k" + i, i);
            assertEquals(true, memo.size() <= 8);
        }
    }
}
