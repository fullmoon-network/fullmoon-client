package dev.fullmoon.client.keybinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.fullmoon.client.account.AccountScreen;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.mods.ModsScreen;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.settings.SettingsScreen;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.DevChrome;
import dev.fullmoon.client.ui.HubChrome;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.TabRail;
import dev.fullmoon.client.ui.TextField;
import dev.fullmoon.client.ui.Voice;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/** In-game keybind editor with search, conflict detection, and live persistence. */
public final class KeybindsScreen extends SurfaceScreen {
    private static final int MAX_CONTENT = 640;
    private static final int MASTER_MAX = 240;
    private static final int MAX_ROWS = 7;
    private static final int DETAIL_MIN_HEIGHT = 144;
    private static final int COMPACT_HEIGHT = 320;
    private static final int QUERY_LIMIT = 64;
    private static final int CARD_HEIGHT = 32;
    private static final Runnable INERT = () -> {};

    private record Item(KeybindEntry entry, KeyMapping mapping, List<KeybindEntry> conflicts) {}

    private final Screen parent;
    private final String query;
    private final List<Item> items;
    private final int selected;
    private final boolean listening;

    private final TabRail hub;
    private final TextField search;
    private final Button clear;
    private final ListPanel results;
    private final Button rebind;
    private final Button reset;

    private Box content = Box.EMPTY;
    private Box body = Box.EMPTY;
    private Box detail = Box.EMPTY;
    private int footerY;

    public KeybindsScreen(Screen parent) {
        this(parent, "", 0, false);
    }

    private KeybindsScreen(Screen parent, String query, int selected, boolean listening) {
        super(Component.translatable("fullmoon.keybinds.title"));
        this.parent = parent;
        this.query = query;
        this.items = collect(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);
        this.listening = listening;

        hub = surface.add(new TabRail("허브", hubTabs(), 1, this::hubPicked));
        search = surface.add(new TextField(tr("search.label"), tr("search.placeholder"), query,
            QUERY_LIMIT, ignored -> true, this::searched));
        clear = surface.add(new Button(Voice.QUIET, tr("search.clear"), this::cleared));
        clear.enabled(!query.isBlank());

        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, this::picked));

        Item current = current();
        rebind = current == null ? null : surface.add(new Button(
            listening ? Voice.LOUD : Voice.QUIET,
            listening ? tr("action.listening") : tr("action.rebind"),
            this::startListening));

        reset = current == null ? null : surface.add(new Button(
            Voice.QUIET, tr("action.reset"), this::resetCurrent));
        if (reset != null && current != null) {
            reset.enabled(!current.entry().isDefault());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            Item current = current();
            if (current != null) {
                if (event.key() == InputConstants.KEY_ESCAPE) {
                    showNext(new KeybindsScreen(parent, query, selected, false));
                } else {
                    InputConstants.Key newKey = InputConstants.Type.KEYSYM.getOrCreate(event.key());
                    current.mapping().setKey(newKey);
                    KeyMapping.resetMapping();
                    Minecraft.getInstance().options.save();
                    showNext(new KeybindsScreen(parent, query, selected, false));
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void init() {
        boolean compact = height < COMPACT_HEIGHT;
        int edge = compact ? Tokens.Space.LOOSE : Tokens.Space.SECTION;
        int frame = Math.min(MAX_CONTENT, width - edge * 2);
        content = new Box((width - frame) / 2, edge, frame, height - edge * 2);
        footerY = content.bottom() - Tokens.Type.MICRO.leading() - Tokens.Space.COZY;

        int railY = content.y() + HubChrome.mastheadHeight(compact);
        hub.place(new Box(content.x(), railY, content.w(), TabRail.HEIGHT));

        int searchY = railY + TabRail.HEIGHT + Tokens.Space.COZY;
        int clearWidth = clear.measure();
        search.place(new Box(content.x(), searchY,
            content.w() - clearWidth - Tokens.Space.COZY, TextField.HEIGHT));
        clear.place(new Box(content.right() - clearWidth, searchY, clearWidth, Button.HEIGHT));

        int bodyY = searchY + TextField.HEIGHT + Tokens.Space.GUTTER;
        int availableBottom = footerY - Tokens.Space.GUTTER;
        int wantedHeight = Math.max(DETAIL_MIN_HEIGHT, DevChrome.sectionHeadHeight()
            + ListPanel.heightFor(Math.min(MAX_ROWS, Math.max(1, items.size()))));
        body = Box.between(content.x(), bodyY, content.right(),
            Math.min(availableBottom, bodyY + wantedHeight));
        int masterWidth = Math.min(MASTER_MAX, body.w() * 2 / 5);
        Box.Split columns = body.splitLeft(masterWidth, Tokens.Space.SECTION);
        int listY = body.y() + DevChrome.sectionHeadHeight();
        results.place(Box.between(columns.head().x(), listY, columns.head().right(), body.bottom()));
        detail = columns.rest();

        if (rebind != null && reset != null) {
            int cardY = body.y() + DevChrome.sectionHeadHeight()
                + Tokens.Type.ROW.leading() + Tokens.Space.COZY;
            int buttonY = cardY + CARD_HEIGHT + Tokens.Space.COZY
                + Tokens.Type.MICRO.leading() + Tokens.Space.LOOSE;
            int rebindW = rebind.measure();
            int resetW = reset.measure();
            rebind.place(new Box(detail.x(), buttonY, rebindW, Button.HEIGHT));
            reset.place(new Box(detail.x() + rebindW + Tokens.Space.COZY, buttonY, resetW, Button.HEIGHT));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.84f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        header(painter);
        DevChrome.sectionHead(painter, tr("results.count", items.size()), body.x(), body.y());
        details(painter);
        widgets(painter);
        footer(painter);
    }

    private void header(Painter painter) {
        boolean compact = height < COMPACT_HEIGHT;
        HubChrome.masthead(painter, content, compact);
    }

    private void details(Painter painter) {
        painter.vRule(detail.x() - Tokens.Space.LOOSE, body.y(), body.h(),
            Tokens.Color.LINE_HAIRLINE);
        Item item = current();
        if (item == null) {
            Typeset.draw(painter, Tokens.Type.BODY, tr("results.empty.detail"), detail.x(),
                body.y() + DevChrome.sectionHeadHeight(), Tokens.Color.INK_TERTIARY);
            return;
        }

        KeybindEntry entry = item.entry();
        int y = DevChrome.sectionHead(painter, entry.category(), detail.x(), body.y());
        Typeset.draw(painter, Tokens.Type.ROW, entry.label(), detail.x(), y,
            Tokens.Color.INK_PRIMARY);

        int cardY = y + Tokens.Type.ROW.leading() + Tokens.Space.COZY;
        boolean hasConflict = !item.conflicts().isEmpty();
        int cardBorder = listening ? Tokens.Color.ACCENT
            : (hasConflict ? Tokens.Color.STATUS_DANGER : Tokens.Color.LINE_HAIRLINE);

        painter.fill(detail.x(), cardY, detail.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(detail.x(), cardY, detail.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Stroke.HAIR,
            cardBorder);

        int textY = cardY + (CARD_HEIGHT - Tokens.Type.STRONG.leading()) / 2;
        Typeset.draw(painter, Tokens.Type.STRONG, tr("entry.id", entry.id()),
            detail.x() + Tokens.Space.COZY, textY, Tokens.Color.INK_SECONDARY);

        String boundText = listening ? tr("state.listening") : entry.boundKey();
        int boundColor = listening ? Tokens.Color.ACCENT
            : (hasConflict ? Tokens.Color.STATUS_DANGER : Tokens.Color.INK_PRIMARY);
        Typeset.drawRight(painter, Tokens.Type.STRONG, boundText,
            detail.right() - Tokens.Space.COZY, textY, boundColor);

        int conflictY = cardY + CARD_HEIGHT + Tokens.Space.COZY;
        if (hasConflict) {
            StringBuilder warning = new StringBuilder(tr("conflict.warning") + ": ");
            for (int i = 0; i < item.conflicts().size(); i++) {
                if (i > 0) warning.append(", ");
                warning.append(item.conflicts().get(i).label());
            }
            Typeset.drawWrapped(painter, Tokens.Type.MICRO, warning.toString(), detail.x(), conflictY,
                detail.w(), 2, Tokens.Color.STATUS_DANGER);
        } else {
            Typeset.draw(painter, Tokens.Type.MICRO, tr("conflict.none"), detail.x(), conflictY,
                Tokens.Color.INK_TERTIARY);
        }
    }

    private void widgets(Painter painter) {
        surface.draw(painter);
    }

    private void footer(Painter painter) {
        painter.hRule(content.x(), footerY, content.w(), Tokens.Color.LINE_HAIRLINE);
        int y = footerY + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.MICRO, tr("footer.keys"), content.x(), y,
            Tokens.Color.INK_TERTIARY);
        Typeset.drawRight(painter, Tokens.Type.MICRO, tr("footer.saved"), content.right(), y,
            Tokens.Color.INK_TERTIARY);
    }

    private void hubPicked(int tab) {
        if (tab == 1) return;
        Screen next = switch (tab) {
            case 0 -> new SettingsScreen(parent);
            case 2 -> new ModsScreen(parent);
            case 3 -> new AccountScreen(parent);
            default -> this;
        };
        showNext(next);
    }

    private void searched(String value) {
        showNext(new KeybindsScreen(parent, value, 0, false));
    }

    private void cleared() {
        showNext(new KeybindsScreen(parent, "", 0, false));
    }

    private void picked(int row) {
        showNext(new KeybindsScreen(parent, query, row, false));
    }

    private void startListening() {
        showNext(new KeybindsScreen(parent, query, selected, true));
    }

    private void resetCurrent() {
        Item item = current();
        if (item != null) {
            item.mapping().setKey(item.mapping().getDefaultKey());
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            showNext(new KeybindsScreen(parent, query, selected, false));
        }
    }

    private Item current() {
        return selected < 0 ? null : items.get(selected);
    }

    private static List<Item> collect(String query) {
        KeyMapping[] mappings = Minecraft.getInstance().options.keyMappings;
        List<KeybindEntry> rawEntries = new ArrayList<>();
        List<KeyMapping> validMappings = new ArrayList<>();

        for (KeyMapping km : mappings) {
            String id = km.getName();
            String cat = HubChrome.categoryLabel(km);
            String label = I18n.get(id);
            InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(km);
            String boundStr = boundKey.getDisplayName().getString();
            int code = boundKey.getValue();
            boolean isDefault = km.isDefault();
            boolean isUnbound = km.isUnbound();

            KeybindEntry entry = new KeybindEntry(id, cat, label, boundStr, code, isDefault, isUnbound);
            rawEntries.add(entry);
            validMappings.add(km);
        }

        Map<String, List<KeybindEntry>> conflicts = KeybindConflict.findConflicts(rawEntries);
        List<Item> allItems = new ArrayList<>(rawEntries.size());
        for (int i = 0; i < rawEntries.size(); i++) {
            KeybindEntry entry = rawEntries.get(i);
            KeyMapping km = validMappings.get(i);
            List<KeybindEntry> conf = conflicts.getOrDefault(entry.id(), List.of());
            allItems.add(new Item(entry, km, conf));
        }

        List<KeybindEntry> filtered = KeybindSearch.filter(rawEntries, query);
        return allItems.stream().filter(item -> filtered.contains(item.entry())).toList();
    }

    private static List<ListRow> rows(List<Item> items) {
        return items.stream().map(item -> {
            String label = item.entry().label();
            String meta = item.conflicts().isEmpty()
                ? item.entry().boundKey()
                : "! " + item.entry().boundKey();
            return new ListRow(label, () -> meta, INERT);
        }).toList();
    }

    private static List<String> hubTabs() {
        return List.of(
            I18n.get("fullmoon.hub.tab.settings"),
            I18n.get("fullmoon.hub.tab.keybinds"),
            I18n.get("fullmoon.hub.tab.mods"),
            I18n.get("fullmoon.hub.tab.account"));
    }

    private static void showNext(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(screen));
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.keybinds." + key, args);
    }
}
