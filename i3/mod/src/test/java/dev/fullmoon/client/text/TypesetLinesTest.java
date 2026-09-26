package dev.fullmoon.client.text;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Fitting server text into a fixed column. The strings are the ones the live casino menu sent on
 * 2026-09-27, when a clipped value read as "|요. 그 차이가 이만큼이에요."; a one-pixel-per-char
 * measure stands in for the font, so the rules are checked without one.
 */
final class TypesetLinesTest {
    private static final ToIntFunction<String> CHARS = String::length;

    @Test
    void textThatFitsIsLeftAlone() {
        assertEquals("하우스 몫", Typeset.ellipsized(CHARS, "하우스 몫", 10));
    }

    @Test
    void cutTextSaysSoAndStillFits() {
        String cut = Typeset.ellipsized(CHARS, "던지기를 누르면 여기 나와요", 8);
        assertEquals("던지기를 누르…", cut);
        assertTrue(cut.length() <= 8);
    }

    @Test
    void aSentenceBreaksAtSpacesAndItsLastLineEndsInAnEllipsis() {
        List<String> lines = Typeset.lines(CHARS, "길게 하면 하우스가 이겨요. 그 차이가 이만큼이에요.", 12, 2);
        assertEquals(List.of("길게 하면 하우스가", "이겨요. 그 차이가…"), lines);
        lines.forEach(line -> assertTrue(line.length() <= 12, line));
    }

    @Test
    void aWordWiderThanTheColumnBreaksInsideIt() {
        assertEquals(List.of("가나다", "라마바", "사"), Typeset.lines(CHARS, "가나다라마바사", 3, 5));
    }

    @Test
    void textThatFitsItsLinesLosesNothing() {
        List<String> lines = Typeset.lines(CHARS, "던지기를 누르면 여기 나와요", 9, 3);
        assertEquals("던지기를 누르면 여기 나와요", String.join(" ", lines));
    }

    @Test
    void aColumnTooNarrowForOneGlyphStillEnds() {
        assertEquals(List.of("가", "나"), Typeset.lines(CHARS, "가나", 0, 3));
    }
}
