package dev.fullmoon.client.season;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.fullmoon.client.render.Painter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The item art a reward or a mission is drawn with. A cosmetic is its kind's vanilla item wearing
 * the model string the server named, so a client with the season pack switched on draws the skin
 * and one without draws the plain sword — the same rule the game itself follows for that string.
 */
public final class SeasonItems {
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private SeasonItems() {}

    /** The stack for {@code material} wearing {@code model}, or an empty stack while there is no world to build one in. */
    public static ItemStack stack(String material, String model) {
        String key = model.isEmpty() ? material : material + "#" + model;
        ItemStack cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ItemStack built = build(material, model);
        if (!built.isEmpty()) {
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
            }
            CACHE.put(key, built);
        }
        return built;
    }

    public static void draw(Painter painter, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        if (size == 16) {
            painter.gfx().item(stack, x, y);
            return;
        }
        float scale = size / 16f;
        painter.gfx().pose().pushMatrix();
        painter.gfx().pose().translate(x, y);
        painter.gfx().pose().scale(scale, scale);
        painter.gfx().item(stack, 0, 0);
        painter.gfx().pose().popMatrix();
    }

    /**
     * The model string as custom model data: the first string, which is the one a pack's
     * {@code minecraft:select} on {@code minecraft:custom_model_data} reads when it names no index.
     */
    static CustomModelData modelData(String model) {
        return new CustomModelData(List.of(), List.of(), List.of(model), List.of());
    }

    private static ItemStack build(String material, String model) {
        Identifier id = Identifier.tryParse(material);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = new ItemStack(item);
            if (!model.isEmpty()) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, modelData(model));
            }
            return stack;
        } catch (NullPointerException unbound) {
            // Item components are bound with a world's registries; with none loaded a stack cannot
            // be built, and the cell is drawn without its art until there is one.
            return ItemStack.EMPTY;
        }
    }
}
