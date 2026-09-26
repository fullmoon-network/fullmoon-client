package dev.fullmoon.client.menu;

import java.util.List;

import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Painter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class ServerMenuEntry {
    private final MenuProtocol.Item item;
    private final String label;
    private final List<String> details;
    private final ItemStack icon;

    ServerMenuEntry(MenuProtocol.Item item) {
        this.item = item;
        this.label = ServerMenuCopy.label(item.label());
        this.details = item.details().stream()
            .filter(line -> !line.stripLeading().startsWith("»"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
        this.icon = icon(item);
    }

    MenuProtocol.Item item() {
        return item;
    }

    String label() {
        return label;
    }

    List<String> details() {
        return details;
    }

    void drawIcon(Painter painter, int x, int y, int well) {
        if (MenuIcons.draw(painter, item.icon(), x + well / 2f, y + well / 2f, well)) {
            return;
        }
        if (well >= 24 && !icon.isEmpty()) {
            painter.gfx().item(icon, x + (well - 16) / 2, y + (well - 16) / 2);
        }
    }

    private static ItemStack icon(MenuProtocol.Item item) {
        Identifier id = Identifier.tryParse(item.material());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item resolved = BuiltInRegistries.ITEM.getValue(id);
        return resolved == null ? ItemStack.EMPTY : new ItemStack(resolved, item.count());
    }
}
