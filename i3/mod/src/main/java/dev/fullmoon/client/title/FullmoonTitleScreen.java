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
 * The first thing the game shows: the lobby's own panorama under a veil that deepens to the
 * left, the wordmark and its line, the vertical list with the way into the lobby first, tonight's
 * real moon in the sky with one line under it, and the foot with the player and the versions.
 * No panel, no plaque; the one gold on the screen is the selection.
 *
 * <p>It replaces the vanilla title screen whenever that one opens, including on the way back from
 * a disconnect, so every route the game takes to its menu lands here. Everything the vanilla
 * screen offers that a Fullmoon player needs is still reachable from it: worlds, the server list,
 * both settings screens, language, accessibility and quit.
 */
public final class FullmoonTitleScreen extends SurfaceScreen {
    /** Capture and rehearsal rigs point this at their own server so they never ping production. */
    private static final String LOBBY = System.getProperty("fullmoon.lobby", "play.fullmoon.ink");
    /** A lobby that has not answered by now is reported as not answering. */
    private static final long PING_PATIENCE_MILLIS = 8_000;
    /** The veil over the panorama: deepest on the left where the words are, and along the foot. */
    private static final float VEIL_LEFT = 0.82f;
    private static final float VEIL_MID = 0.55f;
    private static final float VEIL_RIGHT = 0.10f;
    private static final float VEIL_MID_AT = 0.38f;
    private static final float VEIL_RIGHT_AT = 0.70f;
    private static final float VEIL_FOOT = 0.70f;
    private static final float VEIL_FOOT_FROM = 0.78f;
    /** The moon's halo: four soft steps standing in for a ten-pixel blur. */
    private static final float[] HALO_REACH = {9.0f, 6.0f, 3.5f, 1.5f};
    private static final float[] HALO_ALPHA = {0.03f, 0.05f, 0.07f, 0.10f};
    private static final String SEPARATOR = " · ";

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final ServerData lobby;
    private final MoonPhase moon = MoonPhase.at(Instant.now());
    private final TitleMenu menu;
    private final FootLink language;
    private final FootLink accessibility;
    private final String versions;

    private boolean pinged;
    private long pingedAt;
    private TitleLayout layout;

    public FullmoonTitleScreen() {
        super(Component.translatable("fullmoon.title.screen"));
        lobby = new ServerData(I18n.get("fullmoon.title.screen"), LOBBY, ServerData.Type.OTHER);
        Minecraft client = Minecraft.getInstance();
        menu = surface.add(new TitleMenu(List.of(
            new TitleMenu.Entry(I18n.get("fullmoon.title.play"), "", this::join),
            new TitleMenu.Entry(I18n.get("fullmoon.title.singleplayer"), "",
                () -> client.setScreen(new SelectWorldScreen(this))),
            new TitleMenu.Entry(I18n.get("fullmoon.title.multiplayer"), "",
                () -> client.setScreen(new JoinMultiplayerScreen(this))),
            new TitleMenu.Entry(I18n.get("fullmoon.title.fullmoon_settings"), "F9",
                () -> client.setScreen(new SettingsScreen(this))),
            new TitleMenu.Entry(I18n.get("fullmoon.title.options"), "",
                () -> client.setScreen(new OptionsScreen(this, client.options, false))),
            new TitleMenu.Entry(I18n.get("fullmoon.title.quit"), "", client::stop)),
            new TitleMenu.Status() {
                @Override
                public String text() {
                    return status();
                }

                @Override
                public boolean live() {
                    return reachable();
                }
            }, () -> true));
        language = surface.add(new FootLink(I18n.get("fullmoon.title.language"),
            () -> client.setScreen(new LanguageSelectScreen(this, client.options, client.getLanguageManager()))));
        accessibility = surface.add(new FootLink(I18n.get("fullmoon.title.accessibility"),
            () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options))));
        versions = "Fullmoon " + version("fullmoon") + SEPARATOR + "Minecraft " + version("minecraft");
        surface.focus().point(menu);
    }

    private static String version(String mod) {
        return FabricLoader.getInstance().getModContainer(mod)
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("");
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
        layout = TitleLayout.fit(new Box(0, 0, width, height));
        menu.layout(layout);
        int h = Tokens.Type.BODY.leading();
        int right = width - layout.margin();
        int accessibilityW = accessibility.width();
        int languageW = language.width();
        int gap = Typeset.width(Tokens.Type.BODY, SEPARATOR);
        accessibility.place(new Box(right - accessibilityW, layout.footY(), accessibilityW, h));
        language.place(new Box(right - accessibilityW - gap - languageW, layout.footY(), languageW, h));
        if (!pinged) {
            pinged = true;
            ping();
        }
    }

    /**
     * The pinger fills in the players and the round trip but leaves the state to its caller, as
     * the vanilla server list does: the response callback is what makes the lobby reachable.
     */
    private void ping() {
        Minecraft client = Minecraft.getInstance();
        lobby.setState(ServerData.State.PINGING);
        pingedAt = System.currentTimeMillis();
        try {
            pinger.pingServer(lobby, () -> {},
                () -> client.execute(() -> lobby.setState(ServerData.State.SUCCESSFUL)),
                EventLoopGroupHolder.remote(client.options.useNativeTransport()));
        } catch (UnknownHostException | RuntimeException e) {
            lobby.setState(ServerData.State.UNREACHABLE);
        }
    }

    @Override
    public void tick() {
        pinger.tick();
        if (lobby.state() == ServerData.State.PINGING
                && System.currentTimeMillis() - pingedAt >= PING_PATIENCE_MILLIS) {
            lobby.setState(ServerData.State.UNREACHABLE);
        }
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
        veil(painter);
        sky(painter);
    }

    /** Two gradients, as the mockup lays them: across from the words, and up from the foot. */
    private void veil(Painter painter) {
        int ground = Tokens.Color.SURFACE_VOID;
        float footFrom = height * VEIL_FOOT_FROM;
        painter.fillGradient(0, footFrom, width, height - footFrom, Rgb.alpha(ground, 0.0f), Rgb.alpha(ground, VEIL_FOOT));
        float mid = width * VEIL_MID_AT;
        float far = width * VEIL_RIGHT_AT;
        painter.fillGradientAcross(0, 0, mid, height, Rgb.alpha(ground, VEIL_LEFT), Rgb.alpha(ground, VEIL_MID));
        painter.fillGradientAcross(mid, 0, far - mid, height, Rgb.alpha(ground, VEIL_MID), Rgb.alpha(ground, VEIL_RIGHT));
        painter.fill(far, 0, width - far, height, Rgb.alpha(ground, VEIL_RIGHT));
    }

    /** Tonight's moon in the sky, haloed, with its name and the days to the full under it. */
    private void sky(Painter painter) {
        String line = skyLine();
        int textW = Typeset.width(Tokens.Type.BODY, line);
        int column = Math.max(TitleLayout.MOON_R * 2, textW);
        float cx = layout.skyRight() - column / 2.0f;
        float cy = layout.skyTop() + TitleLayout.MOON_R;
        for (int i = 0; i < HALO_REACH.length; i++) {
            painter.dot(cx, cy, TitleLayout.MOON_R + HALO_REACH[i], Rgb.alpha(Tokens.Color.MOON_LIT, HALO_ALPHA[i]));
        }
        painter.moon(cx, cy, TitleLayout.MOON_R, moon.lit(), moon.waxing(), Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
        int textTop = layout.skyTop() + TitleLayout.MOON_R * 2 + Tokens.Space.BASE;
        Typeset.drawCentered(painter, Tokens.Type.BODY, line, Math.round(cx),
            Typeset.centred(Tokens.Type.BODY, textTop, Tokens.Type.BODY.leading()), Tokens.Color.INK_SECONDARY);
    }

    private String skyLine() {
        String name = I18n.get("fullmoon.title.moon." + moon.name().name().toLowerCase(Locale.ROOT));
        String until = moon.full()
            ? I18n.get("fullmoon.title.moon.next", moon.daysToNextFull())
            : I18n.get("fullmoon.title.moon.until", moon.daysToFull());
        return name + SEPARATOR + until;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        brand(painter);
        foot(painter);
        surface.draw(painter);
    }

    private void brand(Painter painter) {
        int x = layout.margin();
        Typeset.draw(painter, Tokens.Type.MARK, "Fullmoon", x, Typeset.originFor(layout.markBaseline()),
            Tokens.Color.INK_PRIMARY);
        Typeset.draw(painter, Tokens.Type.BODY, I18n.get("fullmoon.title.tagline"), x,
            Typeset.centred(Tokens.Type.BODY, layout.taglineY(), Tokens.Type.BODY.leading()), Tokens.Color.INK_SECONDARY);
    }

    /** The player on the left; on the right the versions, then the two words that open screens. */
    private void foot(Painter painter) {
        int y = Typeset.centred(Tokens.Type.BODY, layout.footY(), Tokens.Type.BODY.leading());
        Typeset.draw(painter, Tokens.Type.BODY, Minecraft.getInstance().getUser().getName(), layout.margin(), y,
            Tokens.Color.INK_SECONDARY);
        int gap = Typeset.width(Tokens.Type.BODY, SEPARATOR);
        int x = language.bounds().x() - gap;
        Typeset.draw(painter, Tokens.Type.BODY, SEPARATOR, x, y, Tokens.Color.INK_DISABLED);
        Typeset.draw(painter, Tokens.Type.BODY, SEPARATOR, accessibility.bounds().x() - gap, y, Tokens.Color.INK_DISABLED);
        Typeset.tabularRight(painter, Tokens.Type.BODY, versions, x, y, Tokens.Color.INK_TERTIARY);
    }
}
