package dev.fullmoon.client.text.fonts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.font.AllMissingGlyphProvider;

final class FontScanTest {
    /**
     * The game's own scan, as it reads in FontSet.selectProviders: the union of what the providers
     * support, each codepoint placed by the first provider with a glyph for it.
     */
    private static Int2ObjectMap<IntList> vanillaIndex(List<? extends GlyphProvider> selected, float missingAdvance) {
        Int2ObjectMap<IntList> byWidth = new Int2ObjectOpenHashMap<>();
        IntSet supported = new IntOpenHashSet();
        for (GlyphProvider provider : selected) {
            supported.addAll(provider.getSupportedGlyphs());
        }
        supported.forEach(codepoint -> {
            for (GlyphProvider provider : selected) {
                UnbakedGlyph glyph = provider.getGlyph(codepoint);
                if (glyph != null) {
                    if (glyph.info().getAdvance() != missingAdvance) {
                        byWidth.computeIfAbsent((int) Math.ceil(glyph.info().getAdvance(false)), w -> new IntArrayList())
                            .add(codepoint);
                    }
                    break;
                }
            }
        });
        return byWidth;
    }

    private static FakeProvider serif() {
        FakeProvider serif = new FakeProvider();
        for (int cp = 0xAC00; cp < 0xAC00 + 300; cp++) {
            serif.with(cp, 10f + (cp % 3));
        }
        return serif.with('A', 6.5f).with('B', 7f);
    }

    private static FakeProvider sans() {
        FakeProvider sans = new FakeProvider();
        for (int cp = 0xAC00; cp < 0xAC00 + 1200; cp++) {
            sans.with(cp, 9f + (cp % 4) / 2f);
        }
        return sans.with('A', 5f).with('B', 5.5f).with('C', 5.25f).with(' ', 2.5f);
    }

    @Test
    void theWidthIndexIsTheGamesOwn() {
        List<FakeProvider> providers = List.of(serif(), sans());
        Int2ObjectMap<IntList> expected = vanillaIndex(providers, -1f);

        Int2ObjectMap<IntList> actual = new Int2ObjectOpenHashMap<>();
        FontScan.indexByWidth(providers, actual, glyph -> glyph.info().getAdvance() != -1f,
            glyph -> (int) Math.ceil(glyph.info().getAdvance(false)));

        assertEquals(expected, actual, "same buckets, same codepoints, same order: the random pick is unchanged");
        assertFalse(actual.isEmpty());
    }

    @Test
    void aGlyphTheGameLeavesOutOfTheIndexStaysOut() {
        FakeProvider provider = new FakeProvider().with('a', 5f).with('b', -1f);
        Int2ObjectMap<IntList> actual = new Int2ObjectOpenHashMap<>();
        FontScan.indexByWidth(List.of(provider), actual, glyph -> glyph.info().getAdvance() != -1f,
            glyph -> (int) Math.ceil(glyph.info().getAdvance(false)));
        assertEquals(Map.of(5, IntList.of('a')), Map.copyOf(actual));
    }

    @Test
    void theScanAppliesToTheSetsThisClientBuilds() throws Exception {
        LazyGlyphProvider lazy = LazyGlyphProvider.deferred(31, FakeProvider::new);
        SpaceProvider space = new SpaceProvider(Map.of((int) ' ', 4f));
        AllMissingGlyphProvider missing = new AllMissingGlyphProvider();

        assertTrue(FontScan.applies(List.of(lazy, space, missing)));
        assertTrue(FontScan.applies(List.of(lazy)));
        assertFalse(FontScan.applies(List.of(space, missing)), "a set without our providers is the game's");
        assertFalse(FontScan.applies(List.of(lazy, serif())), "an unknown provider with glyphs is the game's to scan");
        assertFalse(lazy.isOpen(), "deciding does not open anything");
        lazy.close();
    }

    @Test
    void onlyProvidersThatSupportNothingAreDropped() {
        LazyGlyphProvider lazy = LazyGlyphProvider.deferred(32, FakeProvider::new);
        SpaceProvider space = new SpaceProvider(Map.of((int) ' ', 4f));
        AllMissingGlyphProvider missing = new AllMissingGlyphProvider();

        List<GlyphProvider> used = FontScan.used(List.of(lazy, space, missing));
        assertEquals(List.of(lazy, space), used, "the order is kept; the all-missing fallback supports nothing");
        assertFalse(lazy.isOpen(), "choosing the providers does not open them");
        lazy.close();
    }
}
