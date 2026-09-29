package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Painter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One item of a menu snapshot as the screen reads it: what kind of thing it is and what its lore says. */
final class ServerMenuEntry {
    /** What a slot is for. The server marks the two controls by slot, material and name. */
    enum Kind { CHOICE, FACT, BACK, CLOSE }

    static final String BACK_LABEL = "뒤로";
    static final String CLOSE_LABEL = "닫기";
    /** The fact whose value the header shows as the menu's context figure. */
    private static final String[] CONTEXT_KEYS = {"내 잔액", "잔액", "오늘 남은 획득 한도", "연습 잔액"};

    private final MenuProtocol.Item item;
    private final String label;
    private final MenuLore.Parsed lore;
    private final Kind kind;
    private final ItemStack icon;

    ServerMenuEntry(MenuProtocol.Item item) {
        this.item = item;
        this.label = ServerMenuCopy.label(item.label());
        this.lore = MenuLore.parse(item.details());
        this.kind = kindOf(item, label);
        this.icon = icon(item);
    }

    static Kind kindOf(MenuProtocol.Item item, String label) {
        boolean barrier = item.material().equals("minecraft:barrier");
        if (item.slot() == 0 && item.material().equals("minecraft:arrow") && label.equals(BACK_LABEL)) {
            return Kind.BACK;
        }
        if (barrier && label.equals(CLOSE_LABEL)) {
            return Kind.CLOSE;
        }
        return item.actions().isEmpty() ? Kind.FACT : Kind.CHOICE;
    }

    MenuProtocol.Item item() {
        return item;
    }

    int slot() {
        return item.slot();
    }

    String label() {
        return label;
    }

    MenuLore.Parsed lore() {
        return lore;
    }

    Kind kind() {
        return kind;
    }

    /** Whether a click on it is refused right now, by the server's own ✖ line. */
    boolean blocked() {
        return lore.isBlocked();
    }

    /** Whether it can be picked: a choice the server is not refusing. */
    boolean live() {
        return kind == Kind.CHOICE && !blocked();
    }

    /** The first line of prose: what the row says under its name. */
    String description() {
        return lore.prose().isEmpty() ? "" : lore.prose().getFirst();
    }

    /** How many the player holds, from the 보유 fact or the stack, or empty for one. */
    String count() {
        String held = lore.fact("보유");
        if (!held.isEmpty()) {
            return held;
        }
        return item.count() > 1 ? item.count() + "개" : "";
    }

    /** The figure a row shows on its right: a price, a payout, a cost. */
    String headline() {
        return lore.headline();
    }

    /**
     * The figures the detail column shows large, key over value: the win chance first when the
     * server sent one, then the first two facts whose values read as figures.
     */
    List<MenuLore.Fact> figures() {
        List<MenuLore.Fact> out = new ArrayList<>();
        if (item.chance().isPresent()) {
            out.add(new MenuLore.Fact("이길 확률", ServerMenuCopy.percent(item.chance().getAsDouble())));
        }
        for (MenuLore.Fact fact : lore.facts()) {
            if (out.size() >= 2) {
                break;
            }
            if (MenuLore.looksLikeFigure(fact.value()) && !fact.key().equals("보유")) {
                out.add(fact);
            }
        }
        if (out.size() < 2) {
            for (String figure : lore.figures()) {
                if (out.size() >= 2) {
                    break;
                }
                out.add(new MenuLore.Fact("", figure));
            }
        }
        return out;
    }

    /** The facts left over once {@link #figures()} took theirs: drawn as {@code key · value} body lines. */
    List<MenuLore.Fact> otherFacts() {
        List<MenuLore.Fact> shown = figures();
        List<MenuLore.Fact> out = new ArrayList<>();
        for (MenuLore.Fact fact : lore.facts()) {
            if (!shown.contains(fact) && !fact.key().equals("보유")) {
                out.add(fact);
            }
        }
        return out;
    }

    /**
     * The text the header shows beside the title, from a fact item: {@code 잔액 2억원} from the
     * 내 잔액 item, or the remaining daily limit. Empty when this fact is not one of those.
     */
    String contextLine() {
        if (kind != Kind.FACT) {
            return "";
        }
        for (String key : CONTEXT_KEYS) {
            if (label.equals(key)) {
                String value = headline();
                if (value.isEmpty()) {
                    return "";
                }
                String name = label.startsWith("내 ") ? label.substring(2) : label;
                return name + " " + value;
            }
        }
        return "";
    }

    /** The one line a fact shows under its name in the facts strip. */
    String factValue() {
        if (!lore.facts().isEmpty()) {
            MenuLore.Fact first = lore.facts().getFirst();
            return first.key() + MenuLore.SEPARATOR + first.value();
        }
        if (!lore.figures().isEmpty()) {
            return lore.figures().getFirst();
        }
        if (!lore.blocked().isEmpty()) {
            return lore.blocked().getFirst();
        }
        return description();
    }

    /** Whether the icon is the game's item art, and so cannot be drawn under a fade. */
    boolean itemIcon() {
        return !MenuIcons.knows(item.icon()) && !icon.isEmpty();
    }

    void drawIcon(Painter painter, int x, int y, int size) {
        if (MenuIcons.draw(painter, item.icon(), x + size / 2f, y + size / 2f, size)) {
            return;
        }
        if (icon.isEmpty()) {
            return;
        }
        if (size == 16) {
            painter.gfx().item(icon, x, y);
            return;
        }
        float scale = size / 16f;
        painter.gfx().pose().pushMatrix();
        painter.gfx().pose().translate(x, y);
        painter.gfx().pose().scale(scale, scale);
        painter.gfx().item(icon, 0, 0);
        painter.gfx().pose().popMatrix();
    }

    private static ItemStack icon(MenuProtocol.Item item) {
        Identifier id = Identifier.tryParse(item.material());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item resolved = BuiltInRegistries.ITEM.getValue(id);
        if (resolved == null) {
            return ItemStack.EMPTY;
        }
        try {
            return new ItemStack(resolved, item.count());
        } catch (NullPointerException unbound) {
            // Item components are bound with a world's registries; with none loaded a stack cannot
            // be built, and the row falls back to its pixel mark or no icon.
            return ItemStack.EMPTY;
        }
    }
}
