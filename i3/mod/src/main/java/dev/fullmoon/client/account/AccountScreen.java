package dev.fullmoon.client.account;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.keybinds.KeybindsScreen;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.mods.ModsScreen;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.settings.SettingsScreen;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Clipboard;
import dev.fullmoon.client.ui.DevChrome;
import dev.fullmoon.client.ui.HubChrome;
import dev.fullmoon.client.ui.SurfaceScreen;
import dev.fullmoon.client.ui.TabRail;
import dev.fullmoon.client.ui.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** In-game player identity and network connection surface. */
public final class AccountScreen extends SurfaceScreen {
    private static final int MAX_CONTENT = 580;
    private static final int COMPACT_HEIGHT = 320;
    private static final int CARD_HEIGHT = 68;

    private final Screen parent;
    private final TabRail hub;
    private final Button copyUuid;
    private final Button copyServer;

    private Box content = Box.EMPTY;
    private Box body = Box.EMPTY;
    private int footerY;
    private String statusMessage = "";

    public AccountScreen(Screen parent) {
        super(Component.translatable("fullmoon.account.title"));
        this.parent = parent;

        hub = surface.add(new TabRail("허브", hubTabs(), 3, this::hubPicked));
        copyUuid = surface.add(new Button(Voice.QUIET, tr("action.copy_uuid"), this::copyUuidAction));
        copyServer = surface.add(new Button(Voice.QUIET, tr("action.copy_server"), this::copyServerAction));
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

        int bodyY = railY + TabRail.HEIGHT + Tokens.Space.GUTTER;
        body = Box.between(content.x(), bodyY, content.right(), footerY - Tokens.Space.GUTTER);

        int profileCardY = body.y() + DevChrome.sectionHeadHeight();
        int copyUuidW = copyUuid.measure();
        copyUuid.place(new Box(body.right() - copyUuidW - Tokens.Space.COZY,
            profileCardY + CARD_HEIGHT - Button.HEIGHT - Tokens.Space.COZY, copyUuidW, Button.HEIGHT));

        int serverCardY = profileCardY + CARD_HEIGHT + Tokens.Space.GUTTER + DevChrome.sectionHeadHeight();
        int copyServerW = copyServer.measure();
        copyServer.place(new Box(body.right() - copyServerW - Tokens.Space.COZY,
            serverCardY + CARD_HEIGHT - Button.HEIGHT - Tokens.Space.COZY, copyServerW, Button.HEIGHT));
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
        details(painter);
        widgets(painter);
        footer(painter);
    }

    private void header(Painter painter) {
        boolean compact = height < COMPACT_HEIGHT;
        HubChrome.masthead(painter, content, compact);
    }

    private void details(Painter painter) {
        User user = Minecraft.getInstance().getUser();
        String username = user.getName();
        String uuid = user.getProfileId().toString();
        String userType = user.getProfileId().version() == 4 ? "Mojang / Microsoft" : "Offline";

        Minecraft client = Minecraft.getInstance();
        ServerData server = client.getCurrentServer();
        boolean live = client.level != null && server != null;
        String serverStatus = live ? server.ip : tr("server.offline");

        // Profile Section
        int y = body.y();
        y = DevChrome.sectionHead(painter, tr("section.profile"), body.x(), y);

        painter.fill(body.x(), y, body.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(body.x(), y, body.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Stroke.HAIR,
            Tokens.Color.LINE_HAIRLINE);

        int textX = body.x() + Tokens.Space.COZY;
        int row1Y = y + Tokens.Space.COZY;
        Typeset.draw(painter, Tokens.Type.ROW, username, textX, row1Y, Tokens.Color.INK_PRIMARY);
        Typeset.drawRight(painter, Tokens.Type.MICRO, tr("type", userType),
            body.right() - Tokens.Space.COZY, row1Y + (Tokens.Type.ROW.leading() - Tokens.Type.MICRO.leading()) / 2,
            Tokens.Color.INK_TERTIARY);

        int row2Y = row1Y + Tokens.Type.ROW.leading() + Tokens.Space.SNUG;
        Typeset.draw(painter, Tokens.Type.BODY, "UUID: " + uuid, textX, row2Y, Tokens.Color.INK_SECONDARY);

        // Connection Section
        y += CARD_HEIGHT + Tokens.Space.GUTTER;
        y = DevChrome.sectionHead(painter, tr("section.connection"), body.x(), y);

        painter.fill(body.x(), y, body.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Color.SURFACE_SUNKEN);
        painter.border(body.x(), y, body.w(), CARD_HEIGHT, Tokens.Radius.SM, Tokens.Stroke.HAIR,
            Tokens.Color.LINE_HAIRLINE);

        int sRow1Y = y + Tokens.Space.COZY;
        int dotCenterY = sRow1Y + Typeset.capHeight(Tokens.Type.STRONG) / 2;
        painter.dot(textX + Tokens.Space.SNUG, dotCenterY, Tokens.Space.SNUG,
            live ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
        Typeset.draw(painter, Tokens.Type.STRONG, serverStatus,
            textX + Tokens.Space.SECTION, sRow1Y,
            live ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY);

        int sRow2Y = sRow1Y + Tokens.Type.STRONG.leading() + Tokens.Space.SNUG;
        String authText = live ? tr("footer.info") : tr("server.offline");
        Typeset.draw(painter, Tokens.Type.MICRO, authText, textX + Tokens.Space.SECTION, sRow2Y,
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
        String rightText = statusMessage.isEmpty() ? tr("footer.info") : statusMessage;
        Typeset.drawRight(painter, Tokens.Type.MICRO, rightText, content.right(), y,
            Tokens.Color.ACCENT);
    }

    private void hubPicked(int tab) {
        if (tab == 3) return;
        Screen next = switch (tab) {
            case 0 -> new SettingsScreen(parent);
            case 1 -> new KeybindsScreen(parent);
            case 2 -> new ModsScreen(parent);
            default -> this;
        };
        showNext(next);
    }

    private void copyUuidAction() {
        User user = Minecraft.getInstance().getUser();
        Clipboard.game().put(user.getProfileId().toString());
        statusMessage = tr("status.uuid_copied");
    }

    private void copyServerAction() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server != null) {
            Clipboard.game().put(server.ip);
            statusMessage = tr("status.server_copied");
        } else {
            statusMessage = tr("status.no_server");
        }
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
        return I18n.get("fullmoon.account." + key, args);
    }
}
