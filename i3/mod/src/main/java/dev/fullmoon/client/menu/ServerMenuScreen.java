package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.FullmoonChannel;
import dev.fullmoon.client.network.MenuProtocol;
import dev.fullmoon.client.render.Motion;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.IconButton;
import dev.fullmoon.client.ui.SurfaceScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A server-owned menu as one pane of glass: a header with the way back and the way out, the
 * choices as rows the keyboard walks and the pointer lifts, the chosen one's detail beside them,
 * and the menu's facts along the foot. Every click is still the server's: the screen sends the
 * slot and the click the snapshot advertised and draws whatever comes back.
 */
public final class ServerMenuScreen extends SurfaceScreen {
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final int RISE = Tokens.Space.COZY;

    private final Screen parent;
    private final MenuProtocol.Open menu;
    private final String parentTitle;
    private final List<ServerMenuEntry> choices;
    private final List<ServerMenuEntry> facts;
    private final ServerMenuEntry back;
    private final ServerMenuEntry closeItem;
    private final MenuBoard board;
    private final IconButton backButton;
    private final IconButton closeButton;
    private final long openedAt;

    private ServerMenuLayout layout;
    private long requestedAt;
    private int busySlot = -1;
    private long closingAt;
    private boolean closingFromServer;
    private boolean keyboard;

    public ServerMenuScreen(Screen parent, MenuProtocol.Open menu) {
        this(parent, menu, "", -1, true);
    }

    private ServerMenuScreen(Screen parent, MenuProtocol.Open menu, String parentTitle, int wantedSlot,
            boolean opening) {
        super(Component.literal(menu.title()));
        this.parent = parent;
        this.menu = menu;
        this.parentTitle = parentTitle;
        List<ServerMenuEntry> entries = menu.items().stream()
            .sorted(Comparator.comparingInt(MenuProtocol.Item::slot))
            .map(ServerMenuEntry::new)
            .toList();
        this.choices = entries.stream().filter(e -> e.kind() == ServerMenuEntry.Kind.CHOICE).toList();
        this.facts = entries.stream().filter(e -> e.kind() == ServerMenuEntry.Kind.FACT).toList();
        this.back = entries.stream().filter(e -> e.kind() == ServerMenuEntry.Kind.BACK).findFirst().orElse(null);
        this.closeItem = entries.stream().filter(e -> e.kind() == ServerMenuEntry.Kind.CLOSE).findFirst().orElse(null);
        this.board = surface.add(new MenuBoard(choices, wantedSlot, new MenuBoard.Listener() {
            @Override
            public void picked(ServerMenuEntry entry) {
                UiSounds.play(UiSounds.Cue.CONFIRM);
                request(entry.item());
            }

            @Override
            public void moved(ServerMenuEntry entry) {
                UiSounds.play(UiSounds.Cue.FOCUS);
            }

            @Override
            public void refused(ServerMenuEntry entry) {
                UiSounds.play(UiSounds.Cue.ERROR);
            }
        }));
        String backLabel = parentTitle.isEmpty() ? I18n.get("fullmoon.menu.back") : parentTitle;
        this.backButton = back == null ? null
            : surface.add(new IconButton(IconButton.Glyph.BACK, backLabel, () -> request(back.item())));
        this.closeButton = surface.add(new IconButton(IconButton.Glyph.CLOSE, "", this::onClose));
        this.openedAt = opening ? System.nanoTime() : 0;
        if (opening) {
            UiSounds.play(UiSounds.Cue.OPEN);
        }
        surface.focus().point(board);
    }

    public String menuId() {
        return menu.id();
    }

    public String title() {
        return menu.title();
    }

    public Screen parentScreen() {
        return parent;
    }

    /** The same menu, a newer snapshot: the cursor stays on the slot it was on. */
    public ServerMenuScreen refreshed(MenuProtocol.Open next) {
        return new ServerMenuScreen(parent, next, parentTitle, board.currentSlot(), false);
    }

    /** Another menu opened over this one: it remembers this one's name for its way back. */
    public ServerMenuScreen replacedBy(MenuProtocol.Open next) {
        return new ServerMenuScreen(parent, next, menu.title(), -1, false);
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
        if (closingAt != 0) {
            return;
        }
        if (!closingFromServer) {
            FullmoonChannel.closeMenu(menu.id(), menu.revision());
            UiSounds.play(UiSounds.Cue.CLOSE);
        }
        if (Motion.reduced()) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        closingAt = System.nanoTime();
    }

    @Override
    protected void init() {
        layout = ServerMenuLayout.fit(new Box(0, 0, width, height), choices.size(), !facts.isEmpty());
        board.layout(layout);
        Box header = layout.header();
        int x = header.right() - Tokens.Space.LOOSE - IconButton.SIZE + Tokens.Space.BASE;
        closeButton.place(new Box(x, header.y() + (header.h() - IconButton.SIZE) / 2, IconButton.SIZE, IconButton.SIZE));
        if (backButton != null) {
            backButton.place(new Box(header.x() + Tokens.Space.BASE,
                header.y() + (header.h() - IconButton.SIZE) / 2, backButton.measure(), IconButton.SIZE));
        }
    }

    @Override
    public void tick() {
        if (requestedAt > 0 && System.currentTimeMillis() - requestedAt >= REQUEST_TIMEOUT_MILLIS) {
            requestedAt = 0;
            board.busy(-1);
        }
        if (closingAt != 0 && (System.nanoTime() - closingAt) / 1_000_000L >= Tokens.Duration.CLOSE) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closingAt != 0) {
            return true;
        }
        keyboard = true;
        if (event.key() == InputConstants.KEY_BACKSPACE && back != null && requestedAt == 0) {
            UiSounds.play(UiSounds.Cue.BACK);
            request(back.item());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.52f * appearance()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (closingAt == 0) {
            surface.hover(mouseX, mouseY);
        }
        Painter painter = new Painter(gfx);
        float t = appearance();
        painter.opacity(t);
        float rise = (1.0f - t) * RISE;
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, rise);
        Glass.panel(painter, layout.panel());
        header(painter);
        divider(painter);
        detail(painter);
        factsStrip(painter);
        surface.draw(painter);
        gfx.pose().popMatrix();
        hints(painter);
    }

    /** 0 to 1: how far the pane has come in, or how much of it is left on the way out. */
    private float appearance() {
        if (closingAt != 0) {
            long elapsed = (System.nanoTime() - closingAt) / 1_000_000L;
            return 1.0f - Motion.eased(elapsed, Tokens.Duration.CLOSE, Tokens.Easing.IN);
        }
        if (openedAt == 0) {
            return 1.0f;
        }
        long elapsed = (System.nanoTime() - openedAt) / 1_000_000L;
        return Motion.eased(elapsed, Tokens.Duration.OPEN, Tokens.Easing.OUT);
    }

    private void header(Painter painter) {
        Box header = layout.header();
        int x = header.x() + Tokens.Space.LOOSE;
        if (backButton != null) {
            x = backButton.bounds().right() + Tokens.Space.COZY;
        }
        int right = closeButton.bounds().x() - Tokens.Space.COZY;
        int capW = Glass.keycapWidth("Esc");
        Glass.keycap(painter, right - capW, header.y() + (header.h() - Tokens.Size.KEYCAP) / 2, "Esc");
        right -= capW + Tokens.Space.COZY;
        String context = contextLine();
        if (!context.isEmpty()) {
            right -= Typeset.tabularRight(painter, Tokens.Type.BODY, context, right,
                Typeset.centred(Tokens.Type.BODY, header.y(), header.h()), Tokens.Color.INK_TERTIARY)
                + Tokens.Space.COZY;
        }
        Typeset.draw(painter, Tokens.Type.TITLE, Typeset.ellipsized(Tokens.Type.TITLE, menu.title(), right - x),
            x, Typeset.centred(Tokens.Type.TITLE, header.y(), header.h()), Tokens.Color.INK_PRIMARY);
        Glass.hair(painter, header.x(), header.bottom() - 1, header.w());
    }

    /** The menu's context figure: the wallet balance, the remaining daily limit. */
    private String contextLine() {
        for (ServerMenuEntry fact : facts) {
            String line = fact.contextLine();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return "";
    }

    private void divider(Painter painter) {
        int x = layout.divider();
        if (x >= 0) {
            Glass.vhair(painter, x, layout.detail().y(), layout.detail().h());
        }
    }

    private void detail(Painter painter) {
        ServerMenuEntry current = board.current();
        boolean busy = requestedAt > 0 && current != null && current.slot() == busySlot;
        if (layout.mode() == ServerMenuLayout.Mode.COLUMNS) {
            ServerMenuDetail.strip(painter, layout.detail(), current, clock(), busy);
        } else {
            ServerMenuDetail.column(painter, layout.detail(), current, busy);
        }
    }

    /** The fact item that carries a bar, if any: the sell menu's limit clock. */
    private ServerMenuEntry clock() {
        for (ServerMenuEntry fact : facts) {
            if (!fact.lore().bars().isEmpty()) {
                return fact;
            }
        }
        return null;
    }

    /** The facts strip along the foot of a list: each fact's name over its first line. */
    private void factsStrip(Painter painter) {
        Box strip = layout.facts();
        if (strip.empty()) {
            return;
        }
        Glass.hair(painter, strip.x(), strip.y(), strip.w());
        int x = strip.x() + Tokens.Space.LOOSE;
        int block = Tokens.Type.BODY.leading() + Tokens.Type.STRONG.leading() - Tokens.Space.TIGHT;
        int top = strip.y() + (strip.h() - block) / 2;
        int right = strip.right() - Tokens.Space.LOOSE;
        for (ServerMenuEntry fact : facts) {
            if (!fact.contextLine().isEmpty()) {
                continue;
            }
            String value = fact.factValue();
            int w = Math.max(Typeset.width(Tokens.Type.BODY, fact.label()), Typeset.tabularWidth(Tokens.Type.STRONG, value));
            if (x + w > right) {
                break;
            }
            Typeset.draw(painter, Tokens.Type.BODY, fact.label(), x, top, Tokens.Color.INK_TERTIARY);
            int ink = fact.blocked() ? Tokens.Color.STATUS_DANGER : Tokens.Color.INK_PRIMARY;
            Typeset.tabular(painter, Tokens.Type.STRONG, value, x, top + Tokens.Type.BODY.leading() - Tokens.Space.TIGHT, ink);
            x += w + Tokens.Space.GUTTER;
        }
    }

    private void hints(Painter painter) {
        List<Glass.Hint> hints = new ArrayList<>();
        hints.add(new Glass.Hint(layout.mode() == ServerMenuLayout.Mode.LIST ? "↑↓" : "←→↑↓",
            I18n.get("fullmoon.menu.hint.move")));
        hints.add(new Glass.Hint("Enter", I18n.get("fullmoon.menu.hint.select")));
        if (back != null) {
            hints.add(new Glass.Hint("Backspace", I18n.get("fullmoon.menu.hint.back")));
        }
        hints.add(new Glass.Hint("Esc", I18n.get("fullmoon.menu.hint.close")));
        Glass.hints(painter, layout.panel().midX(), layout.hintY(), hints, keyboard);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboard = false;
        super.mouseMoved(mouseX, mouseY);
    }

    private void request(MenuProtocol.Item item) {
        if (requestedAt > 0 || item.actions().isEmpty() || closingAt != 0) {
            return;
        }
        MenuProtocol.Click click = requestedClick(item);
        if (FullmoonChannel.requestMenuAction(menu.id(), menu.revision(), item.slot(), click)) {
            requestedAt = System.currentTimeMillis();
            busySlot = item.slot();
            board.busy(item.slot());
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
}
