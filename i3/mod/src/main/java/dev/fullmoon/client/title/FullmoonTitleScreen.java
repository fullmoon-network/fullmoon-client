package dev.fullmoon.client.title;

import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.settings.SettingsScreen;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Palace;
import dev.fullmoon.client.ui.SurfaceScreen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

/**
 * The first thing the game shows: a palace plaque over the night panorama, tonight's real moon,
 * and one loud way into the lobby.
 *
 * <p>It replaces the vanilla title screen whenever that one opens, including on the way back from
 * a disconnect, so every route the game takes to its menu lands here. Everything the vanilla
 * screen offers that a Fullmoon player needs is still reachable from it: worlds, the server list,
 * both settings screens, language, accessibility and quit.
 */
public final class FullmoonTitleScreen extends SurfaceScreen {
    private static final String LOBBY = "play.fullmoon.ink";
    private static final int WIDE = 800;

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final ServerData lobby;
    private final MoonPhase moon = MoonPhase.at(Instant.now());
    private final LobbyButton play;
    private final List<TitleRow> rows;
    private final TitleRow language;
    private final TitleRow accessibility;
    private final String version;

    private boolean pinged;
    private Box plaque = Box.EMPTY;
    private int band;

    public FullmoonTitleScreen() {
        super(Component.translatable("fullmoon.title.screen"));
        lobby = new ServerData(I18n.get("fullmoon.title.screen"), LOBBY, ServerData.Type.OTHER);
        play = surface.add(new LobbyButton(I18n.get("fullmoon.title.play"), this::status, this::reachable, this::join));
        Minecraft client = Minecraft.getInstance();
        rows = List.of(
            surface.add(new TitleRow(I18n.get("fullmoon.title.singleplayer"), "",
                () -> client.setScreen(new SelectWorldScreen(this)))),
            surface.add(new TitleRow(I18n.get("fullmoon.title.multiplayer"), "",
                () -> client.setScreen(new JoinMultiplayerScreen(this)))),
            surface.add(new TitleRow(I18n.get("fullmoon.title.fullmoon_settings"), "F9",
                () -> client.setScreen(new SettingsScreen(this)))),
            surface.add(new TitleRow(I18n.get("fullmoon.title.options"), "",
                () -> client.setScreen(new OptionsScreen(this, client.options, false)))),
            surface.add(new TitleRow(I18n.get("fullmoon.title.quit"), "", client::stop)));
        language = surface.add(new TitleRow(I18n.get("fullmoon.title.language"), "",
            () -> client.setScreen(new LanguageSelectScreen(this, client.options, client.getLanguageManager()))));
        accessibility = surface.add(new TitleRow(I18n.get("fullmoon.title.accessibility"), "",
            () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options))));
        version = FabricLoader.getInstance().getModContainer("fullmoon")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        boolean wide = width >= WIDE;
        int margin = wide ? Tokens.Space.FIELD + Tokens.Space.COZY : Tokens.Space.SECTION;
        int plaqueW = Math.min(320, width * 42 / 100);
        band = wide ? 86 : 60;
        int pad = wide ? Tokens.Space.GUTTER : Tokens.Space.LOOSE;
        int today = wide ? 30 : 24;
        int playH = wide ? 44 : 32;
        int rowH = wide ? 24 : 19;
        int plaqueH = band + Palace.DANCHEONG_HEIGHT + pad + today + Tokens.Space.LOOSE
            + playH + Tokens.Space.COZY + rowH * rows.size() + pad;
        plaque = new Box(margin, Math.max(Tokens.Space.SECTION, (height - plaqueH) / 2 - Tokens.Space.COZY),
            plaqueW, plaqueH);

        int x = plaque.x() + pad;
        int w = plaque.w() - pad * 2;
        int y = plaque.y() + band + Palace.DANCHEONG_HEIGHT + pad + today + Tokens.Space.LOOSE;
        play.place(new Box(x, y, w, playH));
        y += playH + Tokens.Space.COZY;
        for (TitleRow row : rows) {
            row.place(new Box(x, y, w, rowH));
            y += rowH;
        }

        int barY = height - Tokens.Space.SECTION;
        int small = Typeset.width(Tokens.Type.BODY_STRONG, language.label()) + Tokens.Space.GUTTER + Tokens.Space.SNUG;
        int smallA = Typeset.width(Tokens.Type.BODY_STRONG, accessibility.label()) + Tokens.Space.GUTTER + Tokens.Space.SNUG;
        accessibility.place(new Box(width - margin - smallA, barY - rowH / 2, smallA, rowH));
        language.place(new Box(width - margin - smallA - Tokens.Space.LOOSE - small, barY - rowH / 2, small, rowH));

        if (!pinged) {
            pinged = true;
            ping();
        }
    }

    private void ping() {
        lobby.setState(ServerData.State.PINGING);
        try {
            pinger.pingServer(lobby, () -> {}, () -> {},
                EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()));
        } catch (UnknownHostException | RuntimeException e) {
            lobby.setState(ServerData.State.UNREACHABLE);
        }
    }

    @Override
    public void tick() {
        pinger.tick();
    }

    @Override
    public void removed() {
        pinger.removeAll();
    }

    private void join() {
        ConnectScreen.startConnecting(this, Minecraft.getInstance(), ServerAddress.parseString(LOBBY),
            new ServerData(lobby.name, LOBBY, ServerData.Type.OTHER), false, null);
    }

    private boolean reachable() {
        return lobby.state() == ServerData.State.SUCCESSFUL;
    }

    private String status() {
        return switch (lobby.state()) {
            case SUCCESSFUL -> lobby.players != null
                ? I18n.get("fullmoon.title.lobby.live", lobby.players.online(), lobby.ping)
                : I18n.get("fullmoon.title.lobby.open", lobby.ping);
            case UNREACHABLE, INCOMPATIBLE -> I18n.get("fullmoon.title.lobby.down");
            default -> I18n.get("fullmoon.title.lobby.checking");
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        extractPanorama(gfx, partialTick);
        Painter painter = new Painter(gfx);
        painter.fillGradient(0, 0, width, height,
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.62f), Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.86f));
        skyMoon(painter);
    }

    /** Tonight's moon over the palace, ringed like the dial of an instrument. */
    private void skyMoon(Painter painter) {
        boolean wide = width >= WIDE;
        float r = wide ? 40.0f : 26.0f;
        float cx = width - (wide ? 150.0f : 90.0f);
        float cy = height * 0.27f;
        painter.moon(cx, cy, r, moon.lit(), moon.waxing(), Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        int dial = moon.full() ? Tokens.Color.ACCENT : Tokens.Color.LINE_GILT;
        painter.ring(cx, cy, r + 9.0f, Tokens.Stroke.HAIR, dial);
        for (int tick = 0; tick < 60; tick++) {
            double a = tick / 60.0 * Math.PI * 2.0;
            boolean major = tick % 5 == 0;
            float d = r + (major ? 15.0f : 13.0f);
            painter.dot(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d,
                major ? 1.0f : 0.5f, major ? dial : Tokens.Color.LINE_GILT_FAINT);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        plaque(painter);
        today(painter);
        bar(painter);
        surface.draw(painter);
    }

    private void plaque(Painter painter) {
        Box p = plaque;
        Palace.panel(painter, p.x(), p.y(), p.w(), p.h());
        painter.fill(p.x() + 1, p.y() + 1, p.w() - 2, band - 1, Tokens.Color.SURFACE_RAISED);
        Palace.lattice(painter, p.x() + 1, p.y() + 1, p.w() - 2, band - 1);
        Palace.dancheong(painter, p.x() + 1, p.y() + band, p.w() - 2);

        boolean wide = width >= WIDE;
        int pad = wide ? Tokens.Space.GUTTER : Tokens.Space.LOOSE;
        Typeset.draw(painter, Tokens.Type.LABEL, I18n.get("fullmoon.title.tagline"), p.x() + pad,
            p.y() + pad - Tokens.Space.TIGHT, Tokens.Color.ACCENT);
        Tokens.Type.Role mark = wide ? Tokens.Type.WORDMARK : Tokens.Type.DISPLAY;
        int sealSize = wide ? 34 : 24;
        int rowTop = p.y() + pad + Tokens.Type.LABEL.leading();
        int rowH = band - (rowTop - p.y()) - Tokens.Space.COZY;
        Palace.seal(painter, p.x() + pad, rowTop + (rowH - sealSize) / 2.0f, sealSize);
        Typeset.draw(painter, mark, "Fullmoon", p.x() + pad + sealSize + Tokens.Space.LOOSE,
            Typeset.centred(mark, rowTop, rowH), Tokens.Color.INK_PRIMARY);
    }

    private void today(Painter painter) {
        boolean wide = width >= WIDE;
        int pad = wide ? Tokens.Space.GUTTER : Tokens.Space.LOOSE;
        int top = plaque.y() + band + Palace.DANCHEONG_HEIGHT + pad;
        int h = wide ? 30 : 24;
        float r = wide ? 7.0f : 5.5f;
        float cx = plaque.x() + pad + r + 1.0f;
        float cy = top + h / 2.0f - 1.0f;
        painter.moon(cx, cy, r, moon.lit(), moon.waxing(), Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        painter.ring(cx, cy, r + 2.5f, Tokens.Stroke.HAIR, moon.full() ? Tokens.Color.ACCENT : Tokens.Color.LINE_GILT);

        int textX = (int) (cx + r + Tokens.Space.LOOSE);
        String headline = I18n.get("fullmoon.title.moon." + moon.name().name().toLowerCase(Locale.ROOT));
        String detail = moon.full()
            ? I18n.get("fullmoon.title.moon.next", moon.daysToNextFull())
            : I18n.get("fullmoon.title.moon.until", moon.daysToFull());
        Typeset.draw(painter, Tokens.Type.HEADING, headline, textX, top, Tokens.Color.ACCENT);
        Typeset.draw(painter, Tokens.Type.LABEL, detail, textX, top + Tokens.Type.HEADING.leading() - 1,
            Tokens.Color.INK_TERTIARY);
        Palace.dashedRule(painter, plaque.x() + pad, top + h + Tokens.Space.SNUG, plaque.w() - pad * 2);
    }

    private void bar(Painter painter) {
        boolean wide = width >= WIDE;
        int margin = wide ? Tokens.Space.FIELD + Tokens.Space.COZY : Tokens.Space.SECTION;
        int y = height - Tokens.Space.SECTION;
        String name = Minecraft.getInstance().getUser().getName();
        int nameY = Typeset.centred(Tokens.Type.BODY_STRONG, y - 9, 18);
        Typeset.draw(painter, Tokens.Type.BODY_STRONG, name, margin, nameY, Tokens.Color.INK_SECONDARY);
        String meta = (version.isEmpty() ? "Fullmoon" : "Fullmoon " + version)
            + "  ·  " + I18n.get("fullmoon.title.copyright");
        int metaRight = language.bounds().x() - Tokens.Space.GUTTER;
        Typeset.drawRight(painter, Tokens.Type.LABEL, meta, metaRight,
            Typeset.centred(Tokens.Type.LABEL, y - 9, 18), Tokens.Color.INK_TERTIARY);
    }
}
