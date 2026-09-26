package dev.fullmoon.client.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Everything a player reads in Korean is friendly 해요체. A formal (합쇼체) ending slipped into the
 * route screen after the last sweep because nothing here checked, so the language file and the
 * string literals in the source are both read.
 */
final class KoreanToneTest {
    private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    /** A formal sentence ending, closing the string or followed by punctuation or a space. */
    private static final Pattern FORMAL =
        Pattern.compile("(니다|습니까|입니까|합니까|됩니까|십시오)(?=[.!?…\\s]|$)");

    @Test
    void theLanguageFileIsFriendly() throws IOException {
        String json;
        try (InputStream in = KoreanToneTest.class.getResourceAsStream("/assets/fullmoon/lang/ko_kr.json")) {
            assertNotNull(in, "ko_kr.json is on the test classpath");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> formal = new ArrayList<>();
        int entries = 0;
        Matcher entry = ENTRY.matcher(json);
        while (entry.find()) {
            entries++;
            if (FORMAL.matcher(entry.group(2)).find()) {
                formal.add(entry.group(1) + " = " + entry.group(2));
            }
        }
        assertTrue(entries > 100, "read the whole file, got " + entries + " entries");
        assertEquals(List.of(), formal);
    }

    @Test
    void theSourceLiteralsAreFriendly() throws IOException {
        List<String> formal = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher literal = LITERAL.matcher(source);
                while (literal.find()) {
                    if (FORMAL.matcher(literal.group(1)).find()) {
                        formal.add(file.getFileName() + ": " + literal.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), formal);
    }

    @Test
    void theRuleCatchesFormalEndingsAndLeavesFriendlyOnesAlone() {
        assertTrue(FORMAL.matcher("확인한 뒤 이동시킵니다.").find());
        assertTrue(FORMAL.matcher("다시 시도하십시오").find());
        assertTrue(FORMAL.matcher("준비됐습니까?").find());
        assertTrue(!FORMAL.matcher("확인한 뒤 이동시켜 줘요.").find());
        assertTrue(!FORMAL.matcher("없으니까 괜찮아요").find());
    }
}
