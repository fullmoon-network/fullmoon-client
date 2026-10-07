package dev.fullmoon.client.text.fonts;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;

/** A provider over a codepoint-to-advance table that counts how many glyphs were loaded from it. */
final class FakeProvider implements GlyphProvider {
    final Map<Integer, Float> advances = new HashMap<>();
    int loads;
    boolean closed;

    FakeProvider with(int codepoint, float advance) {
        advances.put(codepoint, advance);
        return this;
    }

    @Override
    public UnbakedGlyph getGlyph(int codepoint) {
        Float advance = advances.get(codepoint);
        if (advance == null) {
            return null;
        }
        loads++;
        GlyphInfo info = GlyphInfo.simple(advance);
        return new UnbakedGlyph() {
            @Override
            public GlyphInfo info() {
                return info;
            }

            @Override
            public BakedGlyph bake(Stitcher stitcher) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public IntSet getSupportedGlyphs() {
        IntSet set = new IntOpenHashSet();
        for (int codepoint : advances.keySet()) {
            set.add(codepoint);
        }
        return set;
    }

    @Override
    public void close() {
        closed = true;
    }
}
