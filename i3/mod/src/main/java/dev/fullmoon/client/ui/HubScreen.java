package dev.fullmoon.client.ui;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Fade;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * 풀문 설정 and the pages on its rail, on one glass pane laid out by {@link HubLayout}: the
 * header with the hub's name, what the page holds and where the client is connected, the rail
 * of pages with its gliding underline, the page's own list and detail, and the hint bar under
 * the pane. Ctrl+Tab turns the rail from anywhere on the page.
 *
 * <p>A page is one screen per tab. Turning the rail shows the next page's screen, which starts
 * its underline from the tab it came from, so the glide crosses the page change.
 */
public abstract class HubScreen extends SurfaceScreen {
    /** The pages, in rail order. */
    public enum Tab {
        SETTINGS("settings"),
        KEYBINDS("keybinds"),
        MODS("mods"),
        ACCOUNT("account");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        public String label() {
            return I18n.get("fullmoon.hub.tab." + key);
        }
    }

    /** How a page names the screen for another page; a static factory, so no page is half-built when it runs. */
    @FunctionalInterface
    public interface Pages {
        Screen open(Tab tab, Screen parent, int from);
    }

    private static final int RISE = Tokens.Space.COZY;
    private static final float SCRIM = 0.52f;
    private static final float DOT = 2.0f;
    private static final int DOT_GAP = Tokens.Space.SNUG + 1;

    protected final Screen parent;
    private final Tab tab;
    private final Pages pages;
    private final TabRail rail;
    private final IconButton closeButton;
    private final Fade fade;
    private HubLayout layout;
    private boolean keyboard;

    /**
     * @param opening whether the pane fades in — false for a page shown in place of another
     * @param from the tab the previous page was on, or -1, so the underline glides from it
     */
    protected HubScreen(Screen parent, Tab tab, boolean opening, int from, Pages pages) {
        super(Component.translatable("fullmoon.hub.title"));
        this.parent = parent;
        this.tab = tab;
        this.pages = pages;
        List<String> labels = new ArrayList<>();
        for (Tab each : Tab.values()) {
            labels.add(each.label());
        }
        rail = surface.add(new TabRail(I18n.get("fullmoon.hub.tabs"), labels, tab.ordinal(), this::turned).from(from));
        closeButton = surface.add(new IconButton(IconButton.Glyph.CLOSE, "", this::onClose));
        fade = opening ? Fade.opening() : Fade.settled();
        if (opening) {
            UiSounds.play(UiSounds.Cue.OPEN);
        }
    }

    /** Where the page's own controls go. Called from {@code init} with the pane divided. */
    protected abstract void lay(HubLayout layout);

    /** What the page holds, beside the hub's name in the header: 설정 9개. */
    protected abstract String count();

    /** The page's own drawing, inside {@code detail} — or the whole body when the page has no list. */
    protected abstract void detail(Painter painter, HubLayout layout);

    /** Whether the page has a list on the left of a divider. */
    protected boolean splits() {
        return true;
    }

    /** Whether the page has a search field under the rail. */
    protected boolean searches() {
        return true;
    }

    /** The keys under the pane. A page with a list adds ↑↓ before these. */
    protected List<Glass.Hint> hints() {
        List<Glass.Hint> hints = new ArrayList<>();
        if (splits()) {
            hints.add(new Glass.Hint("↑↓", I18n.get("fullmoon.menu.hint.move")));
        }
        hints.add(new Glass.Hint("Ctrl+Tab", I18n.get("fullmoon.hub.hint.turn")));
        hints.add(new Glass.Hint("Esc", I18n.get("fullmoon.menu.hint.close")));
        return hints;
    }

    public final Tab tab() {
        return tab;
    }

    protected final HubLayout layout() {
        return layout;
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }

    @Override
    public final void onClose() {
        if (fade.closing()) {
            return;
        }
        UiSounds.play(UiSounds.Cue.CLOSE);
        fade.close();
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (fade.gone()) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    protected final void init() {
        layout = HubLayout.fit(new Box(0, 0, width, height), searches());
        rail.place(layout.tabs());
        Box header = layout.header();
        closeButton.place(new Box(header.right() - Tokens.Space.LOOSE - IconButton.SIZE + Tokens.Space.BASE,
            header.y() + (header.h() - IconButton.SIZE) / 2, IconButton.SIZE, IconButton.SIZE));
        lay(layout);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (fade.closing()) {
            return true;
        }
        keyboard = true;
        Chord chord = Chord.from(event);
        if (chord.control() && chord.is(InputConstants.KEY_TAB)) {
            rail.turn(chord.shift() ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboard = false;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, SCRIM * fade.appearance()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (!fade.closing()) {
            surface.hover(mouseX, mouseY);
        }
        Painter painter = new Painter(gfx);
        float t = fade.appearance();
        painter.opacity(t);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (1.0f - t) * RISE);
        Glass.panel(painter, layout.panel());
        header(painter);
        if (splits()) {
            Glass.vhair(painter, layout.divider(), layout.list().y(), layout.list().h());
        }
        detail(painter, layout);
        surface.draw(painter);
        gfx.pose().popMatrix();
        Glass.hints(painter, layout.panel().midX(), layout.hintY(), hints(), keyboard);
    }

    /** The hub's name, the page's count, and on the right where the client is, then Esc and ✕. */
    private void header(Painter painter) {
        Box header = layout.header();
        int x = header.x() + Tokens.Space.LOOSE;
        int titleY = Typeset.centred(Tokens.Type.TITLE, header.y(), header.h());
        int bodyY = Typeset.centred(Tokens.Type.BODY, header.y(), header.h());
        x += Typeset.draw(painter, Tokens.Type.TITLE, I18n.get("fullmoon.hub.title"), x, titleY, Tokens.Color.INK_PRIMARY)
            + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.BODY, count(), x, bodyY, Tokens.Color.INK_TERTIARY);

        int right = closeButton.bounds().x() - Tokens.Space.COZY;
        right -= Glass.keycapWidth("Esc");
        Glass.keycap(painter, right, header.y() + (header.h() - Tokens.Size.KEYCAP) / 2, "Esc");
        right -= Tokens.Space.LOOSE;
        String status = connection();
        int statusW = Typeset.width(Tokens.Type.BODY, status);
        Typeset.draw(painter, Tokens.Type.BODY, status, right - statusW, bodyY, Tokens.Color.INK_TERTIARY);
        painter.dot(right - statusW - DOT_GAP - DOT, header.midY(), DOT,
            connected() ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
        Glass.hair(painter, header.x(), header.bottom() - 1, header.w());
    }

    private static boolean connected() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.getCurrentServer() != null;
    }

    private String connection() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (!connected()) {
            return I18n.get("fullmoon.settings.server.disconnected");
        }
        return layout.panel().w() < Tokens.Size.PANEL_W
            ? I18n.get("fullmoon.settings.server.live")
            : I18n.get("fullmoon.settings.server.connected", server.ip);
    }

    private void turned(int index) {
        Tab next = Tab.values()[index];
        if (next != tab) {
            show(pages.open(next, parent, tab.ordinal()));
        }
    }

    /** Shows a screen once the current event has finished with this one. */
    protected static void show(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(screen));
    }
}
