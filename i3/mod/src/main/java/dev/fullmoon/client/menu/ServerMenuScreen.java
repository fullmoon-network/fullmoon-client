package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Palace;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.Voice;
import dev.fullmoon.client.ui.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ServerMenuScreen extends SurfaceScreen {
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;

    private final Screen parent;
    private final MenuProtocol.Open menu;
    private final List<ServerMenuTile> tiles;
    private final List<ServerMenuEntry> facts;
    private final Button close;

    private ServerMenuLayout layout;
    private long requestedAt;
    private boolean closingFromServer;

    public ServerMenuScreen(Screen parent, MenuProtocol.Open menu) {
        super(Component.literal(menu.title()));
        this.parent = parent;
        this.menu = menu;
        this.tiles = createTiles(menu.items());
        this.facts = createFacts(menu.items());
        this.tiles.forEach(surface::add);
        this.close = surface.add(new Button(Voice.QUIET, "닫기", this::onClose));
    }

    public String menuId() {
        return menu.id();
    }

    public Screen parentScreen() {
        return parent;
    }

    public ServerMenuScreen refreshed(MenuProtocol.Open next) {
        return new ServerMenuScreen(parent, next);
    }

    public void closeFromServer() {
        closingFromServer = true;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closingFromServer) {
            FullmoonChannel.closeMenu(menu.id(), menu.revision());
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    protected void init() {
        layout = ServerMenuLayout.fit(new Box(0, 0, width, height), tiles.size());
        for (int index = 0; index < tiles.size(); index++) {
            tiles.get(index).place(layout.action(index));
        }
        int closeWidth = Math.max(close.measure(), 58);
        close.place(new Box(layout.header().right() - closeWidth,
            layout.header().y() + Tokens.Space.COZY, closeWidth, Button.HEIGHT));
    }

    @Override
    public void tick() {
        if (requestedAt > 0 && System.currentTimeMillis() - requestedAt >= REQUEST_TIMEOUT_MILLIS) {
            requestedAt = 0;
            tiles.forEach(tile -> tile.busy(false));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.62f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        panel(painter);
        header(painter);
        sectionHead(painter);
        context(painter);
        footer(painter);
        surface.draw(painter);
    }

    private void panel(Painter painter) {
        Box frame = layout.frame();
        Palace.panel(painter, frame.x(), frame.y(), frame.w(), frame.h());
        int band = bandBottom() - frame.y() - 1;
        painter.fill(frame.x() + 1, frame.y() + 1, frame.w() - 2, band, Tokens.Color.SURFACE_RAISED);
        Palace.lattice(painter, frame.x() + 1, frame.y() + 1, frame.w() - 2, band);
        Palace.dancheong(painter, frame.x() + 1, bandBottom(), frame.w() - 2);
    }

    /** The header band ends just under the title; the dancheong band runs along its foot. */
    private int bandBottom() {
        return layout.header().bottom() + Tokens.Space.TIGHT;
    }

    private void header(Painter painter) {
        Box header = layout.header();
        Typeset.draw(painter, Tokens.Type.LABEL, "FULLMOON  ·  서버 메뉴", header.x(),
            header.y() + Tokens.Space.TIGHT, Tokens.Color.ACCENT);
        int titleBandTop = header.y() + Tokens.Type.LABEL.leading() + Tokens.Space.TIGHT;
        int titleBand = header.bottom() - titleBandTop;
        int seal = Math.min(22, titleBand - Tokens.Space.SNUG);
        Palace.seal(painter, header.x(), titleBandTop + (titleBand - seal) / 2.0f, seal);
        Typeset.draw(painter, Tokens.Type.DISPLAY, menu.title(), header.x() + seal + Tokens.Space.COZY,
            Typeset.centred(Tokens.Type.DISPLAY, titleBandTop, titleBand), Tokens.Color.INK_PRIMARY);
    }

    private void sectionHead(Painter painter) {
        int y = layout.sectionHeadY();
        Typeset.draw(painter, Tokens.Type.LABEL, "메뉴", layout.actions().x(), y,
            Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.LABEL, Integer.toString(tiles.size()),
            layout.actions().right(), y, Tokens.Color.INK_TERTIARY);
    }

    /** The detail pane reads like an unrolled scroll: gilt rods along its top and foot. */
    private void context(Painter painter) {
        Box context = layout.context();
        painter.fill(context.x(), context.y(), context.w(), context.h(), Tokens.Color.SURFACE_SUNKEN);
        painter.vRule(context.x(), context.y(), context.h(), Tokens.Color.LINE_GILT_FAINT);
        painter.vRule(context.right() - 1, context.y(), context.h(), Tokens.Color.LINE_GILT_FAINT);
        painter.fill(context.x(), context.y(), context.w(), Tokens.Stroke.FOCUS, Tokens.Color.LINE_GILT);
        painter.fill(context.x(), context.bottom() - Tokens.Stroke.FOCUS, context.w(),
            Tokens.Stroke.FOCUS, Tokens.Color.LINE_GILT);

        ServerMenuEntry entry = currentEntry();
        int left = context.x() + Tokens.Space.LOOSE;
        int right = context.right() - Tokens.Space.LOOSE;
        int y = context.y() + Tokens.Space.LOOSE;
        if (entry == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, "실행할 수 있는 항목이 없어요.",
                left, y, right - left, 2, Tokens.Color.INK_TERTIARY);
            return;
        }

        painter.dot(left + 14f, y + 14f, 14f, Tokens.Color.SURFACE_RAISED);
        painter.ring(left + 14f, y + 14f, 14f - Tokens.Stroke.HAIR / 2f,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT);
        entry.drawIcon(painter, left, y, 28);
        int copyX = left + 28 + Tokens.Space.COZY;
        int clipTop = Typeset.capTop(Tokens.Type.HEADING, y);
        painter.pushClip(copyX, clipTop, right - copyX, y + 30 - clipTop);
        Typeset.draw(painter, Tokens.Type.HEADING, entry.label(), copyX, y,
            Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.LABEL, actionHint(entry.item()), copyX,
            y + Tokens.Type.HEADING.leading(), Tokens.Color.ACCENT);
        painter.popClip();
        y += 28 + Tokens.Space.COZY;

        if (entry.item().chance().isPresent()) {
            y = chance(painter, left, right, y, (float) entry.item().chance().getAsDouble());
        }

        painter.pushClip(left, y, right - left, Tokens.Type.BODY.leading() * 3);
        for (String line : entry.details().stream().limit(3).toList()) {
            Typeset.draw(painter, Tokens.Type.BODY, line, left, y,
                Tokens.Color.INK_SECONDARY);
            y += Tokens.Type.BODY.leading();
        }
        painter.popClip();

        if (!facts.isEmpty()) {
            y += Tokens.Space.COZY;
            Palace.dashedRule(painter, left, y, right - left);
            y += Tokens.Space.COZY;
            Typeset.draw(painter, Tokens.Type.LABEL, "현재 정보", left, y,
                Tokens.Color.INK_TERTIARY);
            y += Tokens.Type.LABEL.leading() + Tokens.Space.SNUG;
            drawFacts(painter, left, right, y, context.bottom() - Tokens.Space.COZY);
        }
    }

    /** The chosen game's odds as a moon filled that far, beside the figure itself. */
    private static int chance(Painter painter, int left, int right, int y, float chance) {
        Palace.dashedRule(painter, left, y, right - left);
        y += Tokens.Space.COZY;
        float r = 11.0f;
        painter.moon(left + r, y + r, r, chance, true, Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        painter.ring(left + r, y + r, r + 2.0f, Tokens.Stroke.HAIR, Tokens.Color.LINE_GILT);
        int textX = left + Math.round(r * 2) + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, "이길 확률", textX, y, Tokens.Color.INK_TERTIARY);
        Typeset.tabular(painter, Tokens.Type.TITLE, ServerMenuCopy.percent(chance), textX,
            y + Tokens.Type.LABEL.leading(), Tokens.Color.ACCENT);
        return y + Math.round(r * 2) + Tokens.Space.COZY;
    }

    /** One fact per line: its name on the left and its value, the first detail, on the right. */
    private void drawFacts(Painter painter, int left, int right, int top, int bottom) {
        int height = Tokens.Type.BODY.leading() + Tokens.Space.TIGHT;
        for (int index = 0; index < facts.size(); index++) {
            ServerMenuEntry fact = facts.get(index);
            int y = top + index * height;
            if (y + Tokens.Type.BODY.leading() > bottom) {
                return;
            }
            String value = fact.details().isEmpty() ? "" : fact.details().getFirst();
            int valueW = Math.min(Typeset.width(Tokens.Type.BODY_STRONG, value), (right - left) * 3 / 5);
            painter.pushClip(left, y, right - left - valueW - Tokens.Space.SNUG, height);
            Typeset.draw(painter, Tokens.Type.BODY, fact.label(), left, y, Tokens.Color.INK_SECONDARY);
            painter.popClip();
            painter.pushClip(right - valueW, y, valueW, height);
            Typeset.drawRight(painter, Tokens.Type.BODY_STRONG, value, right, y, Tokens.Color.INK_PRIMARY);
            painter.popClip();
        }
    }

    private void footer(Painter painter) {
        Box footer = layout.footer();
        painter.hRule(footer.x(), footer.y(), footer.w(), Tokens.Color.LINE_GILT_FAINT);
        int y = footer.y() + Tokens.Space.SNUG;
        int x = footer.x();
        String[][] keys = {{"Tab", "이동"}, {"Enter", "실행"}, {"Esc", "닫기"}};
        for (String[] key : keys) {
            x += Palace.key(painter, x, y - 1, key[0]) + Tokens.Space.SNUG;
            x += Typeset.draw(painter, Tokens.Type.LABEL, key[1], x, y + 1, Tokens.Color.INK_TERTIARY)
                + Tokens.Space.LOOSE;
        }
        String status = requestedAt > 0 ? "서버 응답 대기" : "서버가 결과를 확정해요";
        Typeset.drawRight(painter, Tokens.Type.LABEL, status, footer.right(), y + 1,
            requestedAt > 0 ? Tokens.Color.STATUS_WARN : Tokens.Color.STATUS_LIVE);
    }

    private List<ServerMenuTile> createTiles(List<MenuProtocol.Item> items) {
        List<ServerMenuTile> created = new ArrayList<>();
        items.stream()
            .filter(item -> !item.actions().isEmpty())
            .filter(item -> !isClose(item))
            .sorted(Comparator.comparingInt(MenuProtocol.Item::slot))
            .map(ServerMenuEntry::new)
            .forEach(entry -> created.add(new ServerMenuTile(entry,
                () -> request(entry.item()))));
        return List.copyOf(created);
    }

    private static List<ServerMenuEntry> createFacts(List<MenuProtocol.Item> items) {
        return items.stream()
            .filter(item -> item.actions().isEmpty())
            .sorted(Comparator.comparingInt(MenuProtocol.Item::slot))
            .map(ServerMenuEntry::new)
            .toList();
    }

    private ServerMenuEntry currentEntry() {
        Widget widget = surface.hovered() != null ? surface.hovered() : surface.held();
        if (widget instanceof ServerMenuTile tile) {
            return tile.entry();
        }
        return tiles.isEmpty() ? facts.stream().findFirst().orElse(null) : tiles.getFirst().entry();
    }

    private void request(MenuProtocol.Item item) {
        if (requestedAt > 0 || item.actions().isEmpty()) {
            return;
        }
        MenuProtocol.Click click = requestedClick(item);
        if (FullmoonChannel.requestMenuAction(menu.id(), menu.revision(), item.slot(), click)) {
            requestedAt = System.currentTimeMillis();
            tiles.forEach(tile -> tile.busy(true));
        }
    }

    private static MenuProtocol.Click requestedClick(MenuProtocol.Item item) {
        if (Minecraft.getInstance().hasShiftDown()
                && item.actions().contains(MenuProtocol.Click.SHIFT_LEFT)) {
            return MenuProtocol.Click.SHIFT_LEFT;
        }
        return item.actions().contains(MenuProtocol.Click.LEFT)
            ? MenuProtocol.Click.LEFT : item.actions().getFirst();
    }

    private static String actionHint(MenuProtocol.Item item) {
        boolean left = item.actions().contains(MenuProtocol.Click.LEFT);
        boolean shift = item.actions().contains(MenuProtocol.Click.SHIFT_LEFT);
        if (left && shift) {
            return "클릭  ·  Shift+클릭 보조 동작";
        }
        if (left) {
            return "클릭하여 실행";
        }
        if (shift) {
            return "Shift+클릭하여 실행";
        }
        return "읽기 전용";
    }

    private static boolean isClose(MenuProtocol.Item item) {
        return item.material().equals("minecraft:barrier")
            && ServerMenuCopy.label(item.label()).equals("닫기");
    }
}
