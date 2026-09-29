package dev.fullmoon.client.mods;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.settings.Hub;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.HubLayout;
import dev.fullmoon.client.ui.HubScreen;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.TextField;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/** 풀문 설정's 모드 page: the Fabric mods that are loaded, searchable, with the chosen one's metadata. */
public final class ModsScreen extends HubScreen {
    private static final int QUERY_LIMIT = 64;
    private static final int DESCRIPTION_LINES = 4;
    private static final Runnable INERT = () -> {};

    private final String query;
    private final List<ModEntry> items;
    private int selected;

    private final TextField search;
    private final ListPanel results;

    public ModsScreen(Screen parent) {
        this(parent, "", 0, true, -1);
    }

    public ModsScreen(Screen parent, boolean opening, int from) {
        this(parent, "", 0, opening, from);
    }

    private ModsScreen(Screen parent, String query, int selected, boolean opening, int from) {
        super(parent, Tab.MODS, opening, from, Hub::open);
        this.query = query;
        this.items = collect(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);

        search = surface.add(new TextField("", tr("search.placeholder"), query, QUERY_LIMIT,
            ignored -> true, this::searched));
        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, row -> selected = row).picksOnMove());
        surface.focus().point(results);
    }

    @Override
    protected String count() {
        return tr("results.count", items.size());
    }

    @Override
    protected void lay(HubLayout layout) {
        search.place(layout.search());
        results.place(layout.list());
    }

    @Override
    protected void detail(Painter painter, HubLayout layout) {
        Box d = layout.detail();
        ModEntry item = selected < 0 ? null : items.get(selected);
        if (item == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, tr("results.empty.detail"), d.x(), d.y(), d.w(), 3,
                Tokens.Color.INK_TERTIARY);
            return;
        }
        int y = d.y();
        Typeset.draw(painter, Tokens.Type.STRONG, Typeset.ellipsized(Tokens.Type.STRONG, item.id(), d.w()), d.x(), y,
            Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.ROW, Typeset.ellipsized(Tokens.Type.ROW, item.name(), d.w()), d.x(), y,
            Tokens.Color.INK_PRIMARY);
        y += Tokens.Type.ROW.leading() + Tokens.Space.TIGHT;
        Typeset.tabular(painter, Tokens.Type.BODY, "v" + item.version() + " · " + item.environment(), d.x(), y,
            Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.BODY.leading() + Tokens.Space.COZY;
        String authors = item.authors().isEmpty() ? tr("authors.unknown") : String.join(", ", item.authors());
        Typeset.draw(painter, Tokens.Type.BODY, Typeset.ellipsized(Tokens.Type.BODY, tr("authors", authors), d.w()), d.x(), y,
            Tokens.Color.INK_SECONDARY);
        y += Tokens.Type.BODY.leading() + Tokens.Space.COZY;
        String description = item.description().isBlank() ? tr("no.description") : item.description();
        Typeset.drawWrapped(painter, Tokens.Type.BODY, description, d.x(), y, d.w(), DESCRIPTION_LINES,
            Tokens.Color.INK_SECONDARY);
        Glass.markedLine(painter, d.x(), d.bottom() - Tokens.Type.STRONG.leading(), tr("state.active"),
            Tokens.Color.STATUS_LIVE, false);
    }

    private void searched(String value) {
        show(new ModsScreen(parent, value, 0, false, -1));
    }

    private static List<ModEntry> collect(String query) {
        List<ModEntry> all = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            List<String> authors = meta.getAuthors().stream().map(Person::getName).toList();
            all.add(new ModEntry(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(),
                meta.getDescription(), authors, meta.getEnvironment().name()));
        }
        return ModSearch.filter(all, query);
    }

    private static List<ListRow> rows(List<ModEntry> items) {
        return items.stream().map(item -> new ListRow(item.name(), () -> "v" + item.version(), INERT)).toList();
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.mods." + key, args);
    }
}
