package dev.fullmoon.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.fullmoon.client.account.AccountScreen;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.keybinds.KeybindsScreen;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.mods.ModsScreen;
import dev.fullmoon.client.prefs.ClientPrefs;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.DevChrome;
import dev.fullmoon.client.ui.HubChrome;
import dev.fullmoon.client.ui.ListPanel;
import dev.fullmoon.client.ui.ListRow;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.TabRail;
import dev.fullmoon.client.ui.TextField;
import dev.fullmoon.client.ui.Toggle;
import dev.fullmoon.client.ui.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** A searchable master-detail ledger over real Minecraft options. */
public final class SettingsScreen extends SurfaceScreen {
    private static final int MAX_CONTENT = 640;
    private static final int MASTER_MAX = 224;
    private static final int MAX_ROWS = 7;
    private static final int DETAIL_MIN_HEIGHT = 144;
    private static final int COMPACT_HEIGHT = 320;
    private static final int QUERY_LIMIT = 64;
    private static final int CARD_HEIGHT = 32;
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

    private final Screen parent;
    private final String query;
    private final List<Item> items;
    private final int selected;

    private final TabRail hub;
    private final TextField search;
    private final Button clear;
    private final ListPanel results;
    private final Toggle toggle;

    private Box content = Box.EMPTY;
    private Box body = Box.EMPTY;
    private Box detail = Box.EMPTY;
    private int footerY;

    public SettingsScreen(Screen parent) {
        this(parent, "", 0);
    }

    private SettingsScreen(Screen parent, String query, int selected) {
        super(Component.translatable("fullmoon.settings.title"));
        this.parent = parent;
        this.query = query;
        this.items = filtered(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);

        hub = surface.add(new TabRail("허브", hubTabs(), 0, this::hubPicked));
        search = surface.add(new TextField(tr("search.label"), tr("search.placeholder"), query,
            QUERY_LIMIT, ignored -> true, this::searched));
        clear = surface.add(new Button(Voice.QUIET, tr("search.clear"), this::cleared));
        clear.enabled(!query.isBlank());
        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, this::picked));

        Item current = current();
        toggle = current == null ? null : surface.add(new Toggle("",
            current.option().get(), this::changed));
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

        if (toggle != null) {
            int cardY = body.y() + DevChrome.sectionHeadHeight()
                + Tokens.Type.ROW.leading() + Tokens.Space.COZY
                + Tokens.Type.BODY.leading() * 2 + Tokens.Space.GUTTER;
            int toggleW = Toggle.TRACK_W;
            toggle.place(new Box(detail.right() - toggleW - Tokens.Space.COZY,
                cardY + (CARD_HEIGHT - Toggle.TRACK_H) / 2, toggleW, Toggle.TRACK_H));
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

        SettingSearch.Entry copy = item.copy();
        int y = DevChrome.sectionHead(painter, copy.section(), detail.x(), body.y());
        Typeset.draw(painter, Tokens.Type.ROW, copy.label(), detail.x(), y,
            Tokens.Color.INK_PRIMARY);

        int descY = y + Tokens.Type.ROW.leading() + Tokens.Space.COZY;
        Typeset.drawWrapped(painter, Tokens.Type.BODY, copy.description(), detail.x(), descY,
            detail.w(), height < COMPACT_HEIGHT ? 2 : 3, Tokens.Color.INK_SECONDARY);

        int cardY = descY + Tokens.Type.BODY.leading() * 2 + Tokens.Space.GUTTER;
        painter.fill(detail.x(), cardY, detail.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(detail.x(), cardY, detail.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Stroke.HAIR,
            Tokens.Color.LINE_HAIRLINE);

        int textY = cardY + (CARD_HEIGHT - Tokens.Type.STRONG.leading()) / 2;
        Typeset.draw(painter, Tokens.Type.STRONG, tr("state.enabled"),
            detail.x() + Tokens.Space.COZY, textY, Tokens.Color.INK_PRIMARY);

        int noteY = cardY + CARD_HEIGHT + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.MICRO, tr("source.minecraft"), detail.x(), noteY,
            Tokens.Color.INK_TERTIARY);
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
        if (tab == 0) return;
        Screen next = switch (tab) {
            case 1 -> new KeybindsScreen(parent);
            case 2 -> new ModsScreen(parent);
            case 3 -> new AccountScreen(parent);
            default -> this;
        };
        showNext(next);
    }

    private void searched(String value) {
        showNext(new SettingsScreen(parent, value, 0));
    }

    private void cleared() {
        showNext(new SettingsScreen(parent));
    }

    private void picked(int row) {
        showNext(new SettingsScreen(parent, query, row));
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

    private static List<String> hubTabs() {
        return List.of(
            I18n.get("fullmoon.hub.tab.settings"),
            I18n.get("fullmoon.hub.tab.keybinds"),
            I18n.get("fullmoon.hub.tab.mods"),
            I18n.get("fullmoon.hub.tab.account"));
    }

    private static Spec spec(String id, String section,
            Function<Options, OptionInstance<Boolean>> option) {
        return new Spec(id, section, option);
    }

    private static void showNext(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(screen));
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.settings." + key, args);
    }
}
