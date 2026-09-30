package dev.fullmoon.client.account;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.settings.Hub;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Clipboard;
import dev.fullmoon.client.ui.HubLayout;
import dev.fullmoon.client.ui.HubScreen;
import dev.fullmoon.client.ui.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;

/** 풀문 설정's 계정 page: who the player is signed in as, and where they are connected. */
public final class AccountScreen extends HubScreen {
    private static final float DOT = 2.0f;
    private static final int SECTION_GAP = Tokens.Space.SECTION;

    private final Button copyUuid;
    private final Button copyServer;
    private String status = "";

    public AccountScreen(Screen parent) {
        this(parent, true, -1);
    }

    public AccountScreen(Screen parent, boolean opening, int from) {
        super(parent, Tab.ACCOUNT, opening, from, Hub::open);
        copyUuid = surface.add(new Button(Voice.QUIET, tr("action.copy_uuid"), this::copyUuidAction));
        copyServer = surface.add(new Button(Voice.QUIET, tr("action.copy_server"), this::copyServerAction));
        surface.focus().point(copyUuid);
    }

    @Override
    protected String count() {
        return tr("subtitle");
    }

    @Override
    protected boolean splits() {
        return false;
    }

    @Override
    protected boolean searches() {
        return false;
    }

    @Override
    protected void lay(HubLayout layout) {
        Box b = layout.body();
        int profileY = b.y() + captionHeight() + Tokens.Type.ROW.leading() + Tokens.Space.TIGHT
            + Tokens.Type.BODY.leading() + Tokens.Space.COZY;
        copyUuid.place(new Box(b.x(), profileY, copyUuid.measure(), Button.HEIGHT));
        int serverY = profileY + Button.HEIGHT + SECTION_GAP + captionHeight() + Tokens.Type.STRONG.leading()
            + Tokens.Space.TIGHT + Tokens.Type.BODY.leading() + Tokens.Space.COZY;
        copyServer.place(new Box(b.x(), serverY, copyServer.measure(), Button.HEIGHT));
    }

    @Override
    protected void detail(Painter painter, HubLayout layout) {
        Box b = layout.body();
        Minecraft client = Minecraft.getInstance();
        User user = client.getUser();
        ServerData server = client.getCurrentServer();
        boolean live = client.level != null && server != null;

        int y = b.y();
        Typeset.draw(painter, Tokens.Type.STRONG, tr("section.profile"), b.x(), y, Tokens.Color.INK_TERTIARY);
        y += captionHeight();
        int nameW = Typeset.draw(painter, Tokens.Type.ROW, user.getName(), b.x(), y, Tokens.Color.INK_PRIMARY);
        String type = user.getProfileId().version() == 4 ? "Mojang / Microsoft" : tr("type.offline");
        Typeset.draw(painter, Tokens.Type.BODY, tr("type", type), b.x() + nameW + Tokens.Space.COZY,
            y + Tokens.Type.ROW.leading() - Tokens.Type.BODY.leading(), Tokens.Color.INK_TERTIARY);
        y += Tokens.Type.ROW.leading() + Tokens.Space.TIGHT;
        Typeset.tabular(painter, Tokens.Type.BODY, "UUID " + user.getProfileId(), b.x(), y, Tokens.Color.INK_SECONDARY);

        y = copyUuid.bounds().bottom() + SECTION_GAP;
        Typeset.draw(painter, Tokens.Type.STRONG, tr("section.connection"), b.x(), y, Tokens.Color.INK_TERTIARY);
        y += captionHeight();
        float cy = y + Typeset.capHeight(Tokens.Type.STRONG) / 2.0f + 1;
        painter.dot(b.x() + DOT, cy, DOT, live ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
        Typeset.draw(painter, Tokens.Type.STRONG, live ? server.ip : tr("server.offline"),
            b.x() + Tokens.Space.LOOSE, y, live ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY);
        y += Tokens.Type.STRONG.leading() + Tokens.Space.TIGHT;
        Typeset.draw(painter, Tokens.Type.BODY, live ? tr("footer.info") : tr("server.none"), b.x() + Tokens.Space.LOOSE, y,
            Tokens.Color.INK_TERTIARY);

        if (!status.isEmpty()) {
            int statusY = b.bottom() - Tokens.Type.BODY.leading();
            painter.dot(b.x() + DOT, statusY + Typeset.capHeight(Tokens.Type.BODY) / 2.0f + 1, DOT, Tokens.Color.STATUS_LIVE);
            Typeset.draw(painter, Tokens.Type.BODY, status, b.x() + Tokens.Space.LOOSE, statusY, Tokens.Color.STATUS_LIVE);
        }
    }

    private static int captionHeight() {
        return Tokens.Type.STRONG.leading() + Tokens.Space.COZY;
    }

    private void copyUuidAction() {
        Clipboard.game().put(Minecraft.getInstance().getUser().getProfileId().toString());
        status = tr("status.uuid_copied");
    }

    private void copyServerAction() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server != null) {
            Clipboard.game().put(server.ip);
            status = tr("status.server_copied");
        } else {
            status = tr("status.no_server");
        }
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.account." + key, args);
    }
}
