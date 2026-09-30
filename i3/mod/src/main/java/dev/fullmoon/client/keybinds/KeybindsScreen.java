package dev.fullmoon.client.keybinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.settings.Hub;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.HubLayout;
import dev.fullmoon.client.ui.HubScreen;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.TextField;
import dev.fullmoon.client.ui.Voice;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * 풀문 설정's 단축키 page: every key mapping the game has, searchable, with the chosen one's key
 * as a keycap, its conflicts, and the way to change or restore it. Listening for a new key is a
 * mode of the page, so it comes up as a fresh screen and Esc backs out of it.
 */
public final class KeybindsScreen extends HubScreen {
    private static final int QUERY_LIMIT = 64;
    private static final Runnable INERT = () -> {};

    private record Item(KeybindEntry entry, KeyMapping mapping, List<KeybindEntry> conflicts) {}

    private final String query;
    private final List<Item> items;
    private final boolean listening;
    private int selected;

    private final TextField search;
    private final ListPanel results;
    private final Button rebind;
    private final Button reset;

    public KeybindsScreen(Screen parent) {
        this(parent, "", 0, false, true, -1);
    }

    public KeybindsScreen(Screen parent, boolean opening, int from) {
        this(parent, "", 0, false, opening, from);
    }

    private KeybindsScreen(Screen parent, String query, int selected, boolean listening, boolean opening, int from) {
        super(parent, Tab.KEYBINDS, opening, from, Hub::open);
        this.query = query;
        this.items = collect(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);
        this.listening = listening;

        search = surface.add(new TextField("", tr("search.placeholder"), query, QUERY_LIMIT,
            ignored -> true, this::searched));
        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, this::picked).picksOnMove());
        rebind = surface.add(new Button(listening ? Voice.LOUD : Voice.QUIET,
            listening ? tr("action.listening") : tr("action.rebind"), this::startListening));
        reset = surface.add(new Button(Voice.QUIET, tr("action.reset"), this::resetCurrent));
        picked(this.selected);
        surface.focus().point(listening ? rebind : results);
    }

    @Override
    protected String count() {
        return tr("results.count", items.size());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening && current() != null) {
            if (event.key() != InputConstants.KEY_ESCAPE) {
                current().mapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
            show(new KeybindsScreen(parent, query, selected, false, false, -1));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void lay(HubLayout layout) {
        search.place(layout.search());
        results.place(layout.list());
        Box d = layout.detail();
        int y = d.bottom() - Button.HEIGHT;
        int rebindW = rebind.measure();
        rebind.place(new Box(d.x(), y, rebindW, Button.HEIGHT));
        reset.place(new Box(d.x() + rebindW + Tokens.Space.COZY, y, reset.measure(), Button.HEIGHT));
    }

    @Override
    protected void detail(Painter painter, HubLayout layout) {
        Box d = layout.detail();
        Item item = current();
        if (item == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("results.empty.detail"), d.x(), d.y(), d.w(), 3,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        KeybindEntry entry = item.entry();
        int y = d.y();
        Typeset.draw(painter, Tokens.Type.STRONG, entry.category(), d.x(), y, Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.ROW, entry.label(), d.x(), y, Tokens.Color.INK_PRIMARY);
        y += Tokens.Type.ROW.leading() + Tokens.Space.LOOSE;

        int keyX = d.x() + Typeset.draw(painter, Tokens.Type.BODY, tr("current.key"), d.x(), y, Tokens.Color.INK_TERTIARY)
            + Tokens.Space.COZY;
        int capY = y + (Tokens.Type.BODY.leading() - Tokens.Size.KEYCAP) / 2;
        if (listening) {
            Typeset.draw(painter, Tokens.Type.STRONG, tr("state.listening"), keyX, y, Tokens.Color.ACCENT);
        } else if (entry.isUnbound()) {
            Typeset.draw(painter, Tokens.Type.BODY, entry.boundKey(), keyX, y, Tokens.Color.INK_SECONDARY);
        } else {
            Glass.keycap(painter, keyX, capY, entry.boundKey(), Tokens.Color.INK_PRIMARY);
        }
        y += Tokens.Type.BODY.leading() + Tokens.Space.LOOSE;

        if (item.conflicts().isEmpty()) {
            Glass.markedLine(painter, d.x(), y, tr("conflict.none"), Tokens.Color.STATUS_LIVE, false);
        } else {
            List<String> names = item.conflicts().stream().map(KeybindEntry::label).toList();
            String warning = tr("conflict.warning") + " · " + String.join(", ", names);
            Glass.markedLine(painter, d.x(), y, Typeset.ellipsized(Tokens.Type.STRONG, warning, d.w() - Glass.MARK - Tokens.Space.COZY),
                Tokens.Color.STATUS_DANGER, true);
        }
        y += Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, tr("entry.id", entry.id()), d.w()),
            d.x(), y, Tokens.Color.INK_TERTIARY);
    }

    private void searched(String value) {
        show(new KeybindsScreen(parent, value, 0, false, false, -1));
    }

    private void picked(int row) {
        selected = row;
        Item item = current();
        rebind.enabled(item != null);
        reset.enabled(item != null && !item.entry().isDefault());
    }

    private void startListening() {
        if (!listening) {
            show(new KeybindsScreen(parent, query, selected, true, false, -1));
        }
    }

    private void resetCurrent() {
        Item item = current();
        if (item != null) {
            item.mapping().setKey(item.mapping().getDefaultKey());
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            show(new KeybindsScreen(parent, query, selected, false, false, -1));
        }
    }

    private Item current() {
        return selected < 0 ? null : items.get(selected);
    }

    private static List<Item> collect(String query) {
        KeyMapping[] mappings = Minecraft.getInstance().options.keyMappings;
        List<KeybindEntry> entries = new ArrayList<>();
        for (KeyMapping mapping : mappings) {
            InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(mapping);
            entries.add(new KeybindEntry(mapping.getName(), categoryLabel(mapping), I18n.get(mapping.getName()),
                bound.getDisplayName().getString(), bound.getValue(), mapping.isDefault(), mapping.isUnbound()));
        }
        Map<String, List<KeybindEntry>> conflicts = KeybindConflict.findConflicts(entries);
        List<Item> all = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            KeybindEntry entry = entries.get(i);
            all.add(new Item(entry, mappings[i], conflicts.getOrDefault(entry.id(), List.of())));
        }
        List<KeybindEntry> filtered = KeybindSearch.filter(entries, query);
        return all.stream().filter(item -> filtered.contains(item.entry())).toList();
    }

    private static String categoryLabel(KeyMapping mapping) {
        Identifier id = mapping.getCategory().id();
        String langKey = id.toLanguageKey("key.category");
        if (I18n.exists(langKey)) {
            return I18n.get(langKey);
        }
        String path = id.getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1);
    }

    private static List<ListRow> rows(List<Item> items) {
        return items.stream().map(item -> new ListRow(item.entry().label(),
            () -> (item.conflicts().isEmpty() ? "" : "! ") + item.entry().boundKey(), INERT)).toList();
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.keybinds." + key, args);
    }
}
