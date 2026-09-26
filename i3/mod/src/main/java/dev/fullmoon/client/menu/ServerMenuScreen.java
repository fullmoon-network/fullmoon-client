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
        painter.fill(frame.x() + Tokens.Space.SNUG, frame.y() + Tokens.Space.SNUG,
            frame.w(), frame.h(), Tokens.Radius.LG,
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.74f));
        painter.fill(frame.x(), frame.y(), frame.w(), frame.h(), Tokens.Radius.LG,
            Tokens.Color.SURFACE_BASE);
        painter.border(frame.x(), frame.y(), frame.w(), frame.h(), Tokens.Radius.LG,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
    }

    private void header(Painter painter) {
        Box header = layout.header();
        Typeset.draw(painter, Tokens.Type.LABEL, "FULLMOON  /  SERVER MENU", header.x(),
            header.y() + Tokens.Space.TIGHT, Tokens.Color.ACCENT);
        int titleBandTop = header.y() + Tokens.Type.LABEL.leading() + Tokens.Space.TIGHT;
        Typeset.draw(painter, Tokens.Type.DISPLAY, menu.title(), header.x(),
            Typeset.centred(Tokens.Type.DISPLAY, titleBandTop, header.bottom() - titleBandTop),
            Tokens.Color.INK_PRIMARY);
        painter.hRule(header.x(), header.bottom() - Tokens.Stroke.HAIR,
            header.w(), Tokens.Color.LINE_STRONG);
    }

    private void sectionHead(Painter painter) {
        int y = layout.sectionHeadY();
        Typeset.draw(painter, Tokens.Type.LABEL, "메뉴", layout.actions().x(), y,
            Tokens.Color.INK_TERTIARY);
        Typeset.tabularRight(painter, Tokens.Type.LABEL, Integer.toString(tiles.size()),
            layout.actions().right(), y, Tokens.Color.INK_TERTIARY);
    }

    private void context(Painter painter) {
        Box context = layout.context();
        painter.fill(context.x(), context.y(), context.w(), context.h(), Tokens.Radius.MD,
            Tokens.Color.SURFACE_SUNKEN);
        painter.border(context.x(), context.y(), context.w(), context.h(), Tokens.Radius.MD,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);

        ServerMenuEntry entry = currentEntry();
        int left = context.x() + Tokens.Space.LOOSE;
        int right = context.right() - Tokens.Space.LOOSE;
        int y = context.y() + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.LABEL, "선택한 항목", left, y,
            Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.LABEL.leading() + Tokens.Space.BASE;
        if (entry == null) {
            Typeset.drawWrapped(painter, Tokens.Type.BODY, "실행할 수 있는 항목이 없습니다.",
                left, y, right - left, 2, Tokens.Color.INK_TERTIARY);
            return;
        }

        painter.dot(left + 14f, y + 14f, 14f, Tokens.Color.SURFACE_RAISED);
        painter.ring(left + 14f, y + 14f, 14f - Tokens.Stroke.HAIR / 2f,
            Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
        entry.drawIcon(painter, left, y, 28);
        int copyX = left + 28 + Tokens.Space.COZY;
        int clipTop = Typeset.capTop(Tokens.Type.TITLE, y);
        painter.pushClip(copyX, clipTop, right - copyX, y + 30 - clipTop);
        Typeset.draw(painter, Tokens.Type.TITLE, entry.label(), copyX, y,
            Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.LABEL, actionHint(entry.item()), copyX,
            y + Tokens.Type.TITLE.leading(), Tokens.Color.ACCENT);
        painter.popClip();
        y += 28 + Tokens.Space.COZY;

        painter.pushClip(left, y, right - left, Tokens.Type.BODY.leading() * 3);
        for (String line : entry.details().stream().limit(3).toList()) {
            Typeset.draw(painter, Tokens.Type.BODY, line, left, y,
                Tokens.Color.INK_SECONDARY);
            y += Tokens.Type.BODY.leading();
        }
        painter.popClip();

        if (!facts.isEmpty()) {
            y += Tokens.Space.COZY;
            painter.hRule(left, y, right - left, Tokens.Color.LINE_HAIRLINE);
            y += Tokens.Space.COZY;
            Typeset.draw(painter, Tokens.Type.LABEL, "현재 정보", left, y,
                Tokens.Color.INK_TERTIARY);
            y += Tokens.Type.LABEL.leading() + Tokens.Space.SNUG;
            drawFacts(painter, left, right, y, context.bottom() - Tokens.Space.COZY);
        }
    }

    private void drawFacts(Painter painter, int left, int right, int top, int bottom) {
        int height = Math.max(Tokens.Type.BODY.leading(), (bottom - top) / facts.size());
        for (int index = 0; index < facts.size(); index++) {
            ServerMenuEntry fact = facts.get(index);
            int y = top + index * height;
            if (y + Tokens.Type.BODY.leading() > bottom) {
                return;
            }
            painter.pushClip(left, y, right - left, height);
            Typeset.draw(painter, Tokens.Type.BODY_STRONG, fact.label(), left, y,
                Tokens.Color.INK_PRIMARY);
            if (!fact.details().isEmpty() && height >= Tokens.Type.BODY.leading() * 2) {
                Typeset.draw(painter, Tokens.Type.LABEL, fact.details().getFirst(), left,
                    y + Tokens.Type.BODY.leading(), Tokens.Color.INK_TERTIARY);
            }
            painter.popClip();
        }
    }

    private void footer(Painter painter) {
        Box footer = layout.footer();
        painter.hRule(footer.x(), footer.y(), footer.w(), Tokens.Color.LINE_HAIRLINE);
        int y = footer.y() + Tokens.Space.SNUG;
        Typeset.draw(painter, Tokens.Type.LABEL, "Tab 이동  ·  Enter 실행  ·  Esc 닫기",
            footer.x(), y, Tokens.Color.INK_TERTIARY);
        String status = requestedAt > 0 ? "서버 응답 대기" : "서버 승인 후 반영";
        Typeset.drawRight(painter, Tokens.Type.LABEL, status, footer.right(), y,
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
