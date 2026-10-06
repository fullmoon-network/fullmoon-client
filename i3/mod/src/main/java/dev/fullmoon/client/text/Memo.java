package dev.fullmoon.client.text;

import java.util.HashMap;
import java.util.Map;

/**
 * A string-keyed memo that cannot grow without bound: once {@code limit} entries are held the
 * table is dropped whole and refilled from what is asked for next, which on a screen of live
 * text is the same few hundred strings again. {@link #find} and {@link #remember} are separate
 * so a hit costs no lambda.
 */
final class Memo<V> {
    private final Map<String, V> entries = new HashMap<>();
    private final int limit;

    Memo(int limit) {
        this.limit = limit;
    }

    /** The remembered value, or null. */
    V find(String key) {
        return entries.get(key);
    }

    V remember(String key, V value) {
        if (entries.size() >= limit) {
            entries.clear();
        }
        entries.put(key, value);
        return value;
    }

    int size() {
        return entries.size();
    }
}
