package dev.fullmoon.client.text;

import java.util.Random;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Halving the prefix length has to land where the one-code-point-at-a-time scan always did. */
final class TypesetFittingTest {
    /** The scan {@link Typeset#fittingPrefix} replaced. */
    private static String linear(ToIntFunction<String> measure, String text, int width) {
        int end = 0;
        while (end < text.length()) {
            int next = end + Character.charCount(text.codePointAt(end));
            if (measure.applyAsInt(text.substring(0, next)) > width) {
                break;
            }
            end = next;
        }
        return text.substring(0, end);
    }

    /** Wide Hangul, narrow Latin, a surrogate pair, and a zero-width format character. */
    private static final ToIntFunction<String> UNEVEN = text -> {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += cp >= 0xAC00 ? 9 : cp == '§' ? 0 : cp > 0xFFFF ? 12 : cp == ' ' ? 3 : 6;
            i += Character.charCount(cp);
        }
        return w;
    };

    @Test
    void agreesWithTheLinearScanForEveryWidth() {
        String[] samples = {"", "a", "던지기를 누르면 여기 나와요", "abc def 가나다 §e라마 😀 x", "😀😀😀😀", "                "};
        for (String sample : samples) {
            for (int width = -3; width <= UNEVEN.applyAsInt(sample) + 5; width++) {
                assertEquals(linear(UNEVEN, sample, width), Typeset.fittingPrefix(UNEVEN, sample, width),
                    "'" + sample + "' in " + width);
            }
        }
    }

    @Test
    void agreesOnRandomText() {
        Random random = new Random(7);
        String alphabet = "abc 가나다§😀·";
        int[] points = alphabet.codePoints().toArray();
        for (int round = 0; round < 300; round++) {
            StringBuilder text = new StringBuilder();
            for (int i = random.nextInt(40); i > 0; i--) {
                text.appendCodePoint(points[random.nextInt(points.length)]);
            }
            int width = random.nextInt(200) - 5;
            assertEquals(linear(UNEVEN, text.toString(), width),
                Typeset.fittingPrefix(UNEVEN, text.toString(), width));
        }
    }

    @Test
    void measuresOnlyAHandfulOfPrefixesOfALongLine() {
        int[] calls = {0};
        String text = "x".repeat(4096);
        String fit = Typeset.fittingPrefix(s -> {
            calls[0]++;
            return s.length();
        }, text, 1000);
        assertEquals(1000, fit.length());
        assertEquals(true, calls[0] <= 13, calls[0] + " measurements");
    }
}
