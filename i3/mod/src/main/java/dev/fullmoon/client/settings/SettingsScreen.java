package dev.fullmoon.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.prefs.ClientPrefs;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.HubLayout;
import dev.fullmoon.client.ui.HubScreen;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.TextField;
import dev.fullmoon.client.ui.Toggle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/**
 * 풀문 설정's first page: a searchable list of the options the client cares about, real
 * Minecraft options and the client's own two, with the chosen one's description and switch on
 * the right. The list's bar is the cursor: an arrow chooses, and the detail follows it.
 */
public final class SettingsScreen extends HubScreen {
    private static final int QUERY_LIMIT = 64;
    private static final int DESCRIPTION_LINES = 3;
    private static final Runnable INERT = () -> {};

    private static final List<Spec> SPECS = List.of(
        spec("auto_jump", "gameplay", Options::autoJump),
        spec("view_bobbing", "gameplay", Options::bobView),
        spec("toggle_sprint", "gameplay", Options::toggleSprint),
        spec("subtitles", "accessibility", Options::showSubtitles),
        spec("high_contrast", "accessibility", Options::highContrast),
        spec("autosave_indicator", "interface", Options::showAutosaveIndicator),
        spec("chat_drafts", "interface", Options::saveChatDrafts),
        spec("reduce_motion", "interface", options -> ClientPrefs.REDUCE_MOTION),
        spec("ui_sounds", "interface", options -> ClientPrefs.UI_SOUNDS));

    private record Spec(String id, String section, Function<Options, OptionInstance<Boolean>> option) {}

    private record Item(SettingSearch.Entry copy, OptionInstance<Boolean> option) {}

    private final String query;
    private final List<Item> items;
    private int selected;

    private final TextField search;
    private final ListPanel results;
    private final Toggle toggle;

    public SettingsScreen(Screen parent) {
        this(parent, "", 0, true, -1);
    }

    SettingsScreen(Screen parent, boolean opening, int from) {
        this(parent, "", 0, opening, from);
    }

    private SettingsScreen(Screen parent, String query, int selected, boolean opening, int from) {
        super(parent, Tab.SETTINGS, opening, from, Hub::open);
        this.query = query;
        this.items = filtered(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);

        search = surface.add(new TextField("", tr("search.placeholder"), query, QUERY_LIMIT,
            ignored -> true, this::searched));
        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, this::picked).picksOnMove());
        Item current = current();
        toggle = surface.add(new Toggle(tr("state.enabled"), current != null && current.option().get(), this::changed));
        toggle.enabled(current != null);
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
        Box d = layout.detail();
        int y = d.y() + captionHeight() + Tokens.Type.ROW.leading() + Tokens.Space.COZY
            + Tokens.Type.BODY.leading() * DESCRIPTION_LINES + Tokens.Space.COZY;
        toggle.place(new Box(d.x(), y, toggle.measure(), Toggle.HEIGHT));
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
        SettingSearch.Entry copy = item.copy();
        int y = d.y();
        Typeset.draw(painter, Tokens.Type.STRONG, copy.section(), d.x(), y, Tokens.Color.INK_TERTIARY);
        y += captionHeight();
        Typeset.draw(painter, Tokens.Type.ROW, copy.label(), d.x(), y, Tokens.Color.INK_PRIMARY);
        y += Tokens.Type.ROW.leading() + Tokens.Space.COZY;
        Typeset.drawWrapped(painter, Tokens.Type.BODY, copy.description(), d.x(), y, d.w(), DESCRIPTION_LINES,
            Tokens.Color.INK_SECONDARY);
        int noteY = toggle.bounds().bottom() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, tr("source.minecraft"), d.x(), noteY, Tokens.Color.INK_TERTIARY);
    }

    private static int captionHeight() {
        return Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
    }

    private void searched(String value) {
        show(new SettingsScreen(parent, value, 0, false, -1));
    }

    private void picked(int row) {
        selected = row;
        Item item = current();
        toggle.enabled(item != null);
        if (item != null) {
            toggle.on(item.option().get());
        }
    }

    private void changed(boolean value) {
        Item item = current();
        if (item == null) {
            return;
        }
        item.option().set(value);
        Minecraft.getInstance().options.save();
    }

    private Item current() {
        return selected < 0 ? null : items.get(selected);
    }

    private static List<Item> filtered(String query) {
        Options options = Minecraft.getInstance().options;
        List<Item> catalog = new ArrayList<>(SPECS.size());
        for (Spec spec : SPECS) {
            String root = "fullmoon.settings.option." + spec.id();
            String section = tr("section." + spec.section());
            List<String> aliases = List.of(I18n.get(root + ".aliases").split("\\|"));
            SettingSearch.Entry copy = new SettingSearch.Entry(spec.id(), section,
                I18n.get(root + ".label"), I18n.get(root + ".description"), aliases);
            catalog.add(new Item(copy, spec.option().apply(options)));
        }
        List<SettingSearch.Entry> found = SettingSearch.filter(
            catalog.stream().map(Item::copy).toList(), query);
        return catalog.stream().filter(item -> found.contains(item.copy())).toList();
    }

    private static List<ListRow> rows(List<Item> items) {
        return items.stream().map(item -> new ListRow(item.copy().label(),
            () -> item.option().get() ? tr("state.on") : tr("state.off"), INERT)).toList();
    }

    private static Spec spec(String id, String section,
            Function<Options, OptionInstance<Boolean>> option) {
        return new Spec(id, section, option);
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.settings." + key, args);
    }
}
