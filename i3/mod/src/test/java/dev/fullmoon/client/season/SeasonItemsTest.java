package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.item.component.CustomModelData;

import org.junit.jupiter.api.Test;

final class SeasonItemsTest {
    /**
     * The season pack's {@code items/iron_sword.json} selects on {@code minecraft:custom_model_data}
     * without an index, so the skin switches on the first string and on nothing else.
     */
    @Test
    void theModelStringIsTheFirstStringAndTheOnlyValue() {
        CustomModelData data = SeasonItems.modelData("fullmoon.skin.crescent_sword.1");

        assertEquals(List.of("fullmoon.skin.crescent_sword.1"), data.strings());
        assertTrue(data.floats().isEmpty());
        assertTrue(data.flags().isEmpty());
        assertTrue(data.colors().isEmpty());
    }
}
