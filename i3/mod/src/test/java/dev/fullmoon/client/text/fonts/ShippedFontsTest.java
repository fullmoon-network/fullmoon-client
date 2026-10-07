package dev.fullmoon.client.text.fonts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The facts about the shipped font files that the lazy font loading rests on. The game decides
 * which providers of a set to keep, and which glyph a codepoint draws, by scanning glyphs; the
 * loading here skips that scan, which is only the same thing if these hold.
 */
final class ShippedFontsTest {
    /** The game's fishy-advance limit, in GUI pixels, bold included. */
    private static final float LARGE_FORWARD_ADVANCE = 32f;

    /** What vanilla's include/space provider supports. */
    private static final int[] SPACE_CODEPOINTS = {' ', 0x200C};

    private static Path fontDir() throws URISyntaxException {
        return Path.of(ShippedFontsTest.class.getResource("/assets/fullmoon/font/sans-bold.ttf").toURI()).getParent();
    }

    private record Ttf(int unitsPerEm, Map<Integer, Integer> glyphOf, int[] advances) {
        float advanceEm(int codepoint) {
            return advances[glyphOf.get(codepoint)] / (float) unitsPerEm;
        }

        static Ttf read(Path path) throws IOException {
            ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(path));
            int tables = data.getShort(4) & 0xFFFF;
            Map<String, Integer> offsets = new HashMap<>();
            for (int i = 0; i < tables; i++) {
                int at = 12 + 16 * i;
                byte[] tag = new byte[4];
                data.get(at, tag);
                offsets.put(new String(tag, java.nio.charset.StandardCharsets.US_ASCII), data.getInt(at + 8));
            }
            int head = offsets.get("head");
            int unitsPerEm = data.getShort(head + 18) & 0xFFFF;
            int maxp = offsets.get("maxp");
            int glyphs = data.getShort(maxp + 4) & 0xFFFF;
            int hhea = offsets.get("hhea");
            int metrics = data.getShort(hhea + 34) & 0xFFFF;
            int hmtx = offsets.get("hmtx");
            int[] advances = new int[glyphs];
            for (int g = 0; g < glyphs; g++) {
                advances[g] = data.getShort(hmtx + 4 * Math.min(g, metrics - 1)) & 0xFFFF;
            }
            return new Ttf(unitsPerEm, cmap(data, offsets.get("cmap")), advances);
        }

        /** The unicode charmap FreeType would select: UCS-4 (format 12) if there is one, else format 4. */
        private static Map<Integer, Integer> cmap(ByteBuffer data, int cmap) {
            int count = data.getShort(cmap + 2) & 0xFFFF;
            int format4 = -1;
            int format12 = -1;
            for (int i = 0; i < count; i++) {
                int platform = data.getShort(cmap + 4 + 8 * i) & 0xFFFF;
                int encoding = data.getShort(cmap + 6 + 8 * i) & 0xFFFF;
                int offset = cmap + data.getInt(cmap + 8 + 8 * i);
                int format = data.getShort(offset) & 0xFFFF;
                boolean unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10));
                if (unicode && format == 12) {
                    format12 = offset;
                } else if (unicode && format == 4) {
                    format4 = offset;
                }
            }
            Map<Integer, Integer> glyphOf = new HashMap<>();
            if (format12 >= 0) {
                int groups = data.getInt(format12 + 12);
                for (int i = 0; i < groups; i++) {
                    int at = format12 + 16 + 12 * i;
                    int start = data.getInt(at);
                    int end = data.getInt(at + 4);
                    int gid = data.getInt(at + 8);
                    for (int cp = start; cp <= end; cp++) {
                        glyphOf.put(cp, gid + cp - start);
                    }
                }
                return glyphOf;
            }
            int segments = (data.getShort(format4 + 6) & 0xFFFF) / 2;
            int ends = format4 + 14;
            int starts = ends + 2 * segments + 2;
            int deltas = starts + 2 * segments;
            int ranges = deltas + 2 * segments;
            for (int s = 0; s < segments; s++) {
                int end = data.getShort(ends + 2 * s) & 0xFFFF;
                int start = data.getShort(starts + 2 * s) & 0xFFFF;
                int delta = data.getShort(deltas + 2 * s);
                int range = data.getShort(ranges + 2 * s) & 0xFFFF;
                for (int cp = start; cp <= end && cp != 0xFFFF; cp++) {
                    int gid;
                    if (range == 0) {
                        gid = (cp + delta) & 0xFFFF;
                    } else {
                        gid = data.getShort(ranges + 2 * s + range + 2 * (cp - start)) & 0xFFFF;
                        gid = gid == 0 ? 0 : (gid + delta) & 0xFFFF;
                    }
                    if (gid != 0) {
                        glyphOf.put(cp, gid);
                    }
                }
            }
            return glyphOf;
        }
    }

    private record Definition(String file, List<JsonObject> ttf, JsonObject last) {}

    private static Definition definition(Path json) throws IOException {
        JsonArray providers = JsonParser.parseString(Files.readString(json)).getAsJsonObject().getAsJsonArray("providers");
        List<JsonObject> ttf = new ArrayList<>();
        for (JsonElement element : providers) {
            JsonObject provider = element.getAsJsonObject();
            if ("ttf".equals(provider.get("type").getAsString())) {
                ttf.add(provider);
            }
        }
        return new Definition(json.getFileName().toString(), ttf, providers.get(providers.size() - 1).getAsJsonObject());
    }

    private static List<Path> roleFiles() throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.list(fontDir())) {
            return files.filter(p -> p.getFileName().toString().matches("[a-z]+_x[234]\\.json")).sorted().toList();
        }
    }

    @Test
    void everyRoleIsBakedAtEveryScale() throws Exception {
        assertEquals(27, roleFiles().size(), "nine roles at three scales");
        for (Path json : roleFiles()) {
            Definition definition = definition(json);
            int scale = Character.digit(definition.file().charAt(definition.file().length() - 6), 10);
            for (JsonObject provider : definition.ttf()) {
                assertEquals(scale, provider.get("oversample").getAsInt(), definition.file());
                assertTrue(GuiScaleVariants.isVariant(scale), "the suffix is a baked scale");
                assertFalse(provider.has("skip") || provider.has("shift"), definition.file() + " has no skip or shift");
            }
        }
    }

    @Test
    void theSpaceProviderIsLastAndTheTtfsComeBeforeIt() throws Exception {
        for (Path json : roleFiles()) {
            Definition definition = definition(json);
            assertFalse(definition.ttf().isEmpty(), definition.file());
            assertTrue(definition.ttf().size() <= 2, definition.file());
            assertEquals("reference", definition.last().get("type").getAsString(), definition.file());
            assertEquals("minecraft:include/space", definition.last().get("id").getAsString(), definition.file());
            assertFalse(definition.last().has("filter"), definition.file());
        }
    }

    @Test
    void everyTtfProviderIsTheFirstToHaveSomeCodepoint() throws Exception {
        // The game drops a provider that is never first to have a codepoint. Keeping all of ours is
        // the same only if none is shadowed: the second face of a pair has Hangul the first lacks.
        for (Path json : roleFiles()) {
            Definition definition = definition(json);
            List<Ttf> fonts = new ArrayList<>();
            for (JsonObject provider : definition.ttf()) {
                fonts.add(Ttf.read(fontDir().resolve(provider.get("file").getAsString().substring("fullmoon:".length()))));
            }
            assertFalse(fonts.get(0).glyphOf().isEmpty(), definition.file());
            for (int i = 1; i < fonts.size(); i++) {
                boolean contributes = false;
                for (int codepoint : fonts.get(i).glyphOf().keySet()) {
                    if (!fonts.get(i - 1).glyphOf().containsKey(codepoint)) {
                        contributes = true;
                        break;
                    }
                }
                assertTrue(contributes, definition.file() + ": face " + i + " is shadowed by the one before it");
            }
        }
    }

    @Test
    void noFaceCanMakeTheSpaceCodepointsFishy() throws Exception {
        // If the space provider is itself dropped by the game, it only matters when the face before
        // it gives a space codepoint a fishy advance (< 0 or > 32 px, bold included).
        for (Path json : roleFiles()) {
            Definition definition = definition(json);
            for (JsonObject provider : definition.ttf()) {
                Ttf font = Ttf.read(fontDir().resolve(provider.get("file").getAsString().substring("fullmoon:".length())));
                float size = provider.get("size").getAsFloat();
                for (int codepoint : SPACE_CODEPOINTS) {
                    if (font.glyphOf().containsKey(codepoint)) {
                        // + 2: the game rounds the em to whole pixels at the oversample, and bold adds one.
                        float px = font.advanceEm(codepoint) * size + 2f;
                        assertTrue(px >= 0 && px <= LARGE_FORWARD_ADVANCE,
                            definition.file() + " U+" + Integer.toHexString(codepoint) + " advances " + px);
                    }
                }
            }
        }
    }
}
