package dev.fullmoon.client.mods;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.account.AccountScreen;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.keybinds.KeybindsScreen;
import dev.fullmoon.client.layout.Box;
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

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** In-game mod browser showing active Fabric mods and metadata. */
public final class ModsScreen extends SurfaceScreen {
    private static final int MAX_CONTENT = 640;
    private static final int MASTER_MAX = 240;
    private static final int MAX_ROWS = 7;
    private static final int DETAIL_MIN_HEIGHT = 144;
    private static final int COMPACT_HEIGHT = 320;
    private static final int QUERY_LIMIT = 64;
    private static final Runnable INERT = () -> {};

    private final Screen parent;
    private final String query;
    private final List<ModEntry> items;
    private final int selected;

    private final TabRail hub;
    private final TextField search;
    private final Button clear;
    private final ListPanel results;

    private Box content = Box.EMPTY;
    private Box body = Box.EMPTY;
    private Box detail = Box.EMPTY;
    private int footerY;

    public ModsScreen(Screen parent) {
        this(parent, "", 0);
    }

    private ModsScreen(Screen parent, String query, int selected) {
        super(Component.translatable("fullmoon.mods.title"));
        this.parent = parent;
        this.query = query;
        this.items = collect(query);
        this.selected = items.isEmpty() ? -1 : Math.clamp(selected, 0, items.size() - 1);

        hub = surface.add(new TabRail("허브", hubTabs(), 2, this::hubPicked));
        search = surface.add(new TextField(tr("search.label"), tr("search.placeholder"), query,
            QUERY_LIMIT, ignored -> true, this::searched));
        clear = surface.add(new Button(Voice.QUIET, tr("search.clear"), this::cleared));
        clear.enabled(!query.isBlank());

        results = surface.add(new ListPanel(tr("results.label"), rows(items), tr("results.empty"),
            this.selected, this::picked));
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

        int railY = content.y() + (compact ? Tokens.Type.ROW.leading() : Tokens.Type.DISPLAY.leading()) + Tokens.Space.COZY;
        hub.place(new Box(content.x(), railY, content.w(), TabRail.HEIGHT));

        int searchY = railY + TabRail.HEIGHT + Tokens.Space.LOOSE;
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
        ModEntry item = current();
        if (item == null) {
            Typeset.draw(painter, Tokens.Type.BODY, tr("results.empty.detail"), detail.x(),
                body.y() + DevChrome.sectionHeadHeight(), Tokens.Color.INK_TERTIARY);
            return;
        }

        int y = DevChrome.sectionHead(painter, item.id(), detail.x(), body.y());
        Typeset.draw(painter, Tokens.Type.ROW, item.name(), detail.x(), y,
            Tokens.Color.INK_PRIMARY);

        int versionY = y + Tokens.Type.ROW.leading() + Tokens.Space.SNUG;
        Typeset.draw(painter, Tokens.Type.MICRO, "v" + item.version() + " · " + item.environment(),
            detail.x(), versionY, Tokens.Color.INK_TERTIARY);

        int authorY = versionY + Tokens.Type.MICRO.leading() + Tokens.Space.COZY;
        String authors = item.authors().isEmpty() ? "Unknown" : String.join(", ", item.authors());
        Typeset.draw(painter, Tokens.Type.MICRO, tr("authors", authors), detail.x(), authorY,
            Tokens.Color.INK_SECONDARY);

        int descY = authorY + Tokens.Type.MICRO.leading() + Tokens.Space.COZY;
        String desc = item.description().isBlank() ? tr("no.description") : item.description();
        Typeset.drawWrapped(painter, Tokens.Type.BODY, desc, detail.x(), descY,
            detail.w(), height < COMPACT_HEIGHT ? 3 : 4, Tokens.Color.INK_SECONDARY);

        int cardY = descY + Tokens.Type.BODY.leading() * 3 + Tokens.Space.GUTTER;
        painter.fill(detail.x(), cardY, detail.w(), 32, Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(detail.x(), cardY, detail.w(), 32, Tokens.Radius.SM, Tokens.Stroke.HAIR,
            Tokens.Color.LINE_HAIRLINE);

        int textY = cardY + (32 - Tokens.Type.STRONG.leading()) / 2;
        Typeset.draw(painter, Tokens.Type.STRONG, item.id(),
            detail.x() + Tokens.Space.COZY, textY, Tokens.Color.INK_PRIMARY);

        int dotX = detail.right() - Typeset.width(Tokens.Type.MICRO, "활성") - Tokens.Space.SECTION;
        painter.dot(dotX, cardY + 16, Tokens.Space.SNUG, Tokens.Color.STATUS_LIVE);
        Typeset.drawRight(painter, Tokens.Type.MICRO, "활성",
            detail.right() - Tokens.Space.COZY, cardY + (32 - Tokens.Type.MICRO.leading()) / 2,
            Tokens.Color.INK_SECONDARY);
    }

    private void widgets(Painter painter) {
        surface.draw(painter);
    }

    private void footer(Painter painter) {
        painter.hRule(content.x(), footerY, content.w(), Tokens.Color.LINE_HAIRLINE);
        int y = footerY + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.MICRO, tr("footer.keys"), content.x(), y,
            Tokens.Color.INK_TERTIARY);
        Typeset.drawRight(painter, Tokens.Type.MICRO, tr("footer.info"), content.right(), y,
            Tokens.Color.INK_TERTIARY);
    }

    private void hubPicked(int tab) {
        if (tab == 2) return;
        Screen next = switch (tab) {
            case 0 -> new SettingsScreen(parent);
            case 1 -> new KeybindsScreen(parent);
            case 3 -> new AccountScreen(parent);
            default -> this;
        };
        showNext(next);
    }

    private void searched(String value) {
        showNext(new ModsScreen(parent, value, 0));
    }

    private void cleared() {
        showNext(new ModsScreen(parent, "", 0));
    }

    private void picked(int row) {
        showNext(new ModsScreen(parent, query, row));
    }

    private ModEntry current() {
        return selected < 0 ? null : items.get(selected);
    }

    private static List<ModEntry> collect(String query) {
        List<ModEntry> all = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            String id = meta.getId();
            String name = meta.getName();
            String version = meta.getVersion().getFriendlyString();
            String desc = meta.getDescription();
            List<String> authors = meta.getAuthors().stream().map(Person::getName).toList();
            String env = meta.getEnvironment().name();
            all.add(new ModEntry(id, name, version, desc, authors, env));
        }
        return ModSearch.filter(all, query);
    }

    private static List<ListRow> rows(List<ModEntry> items) {
        return items.stream().map(item ->
            new ListRow(item.name(), () -> "v" + item.version(), INERT)
        ).toList();
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
        return I18n.get("fullmoon.mods." + key, args);
    }
}
