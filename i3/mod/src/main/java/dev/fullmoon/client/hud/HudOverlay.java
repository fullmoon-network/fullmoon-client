package dev.fullmoon.client.hud;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.menu.ServerMenuScreen;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.warp.WarpScreen;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Renders active HUD modules onto the in-game HUD layer. */
public final class HudOverlay {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FullmoonClient.NAMESPACE, "hud_overlay");

    private HudOverlay() {}

    public static void init() {
        HudElementRegistry.addLast(ID, HudOverlay::render);
        // A pane of glass covers the whole world; the hotbar showing through under the hint bar
        // reads as a bug, not as depth, so the hotbar waits with the rest of the HUD.
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (gfx, delta) -> {
            if (!underGlass()) {
                vanilla.render(gfx, delta);
            }
        });
        ScoreboardSidebar.init();
        // A bet is placed from a casino menu, so the result lands while that screen is still up;
        // the HUD layer is skipped under any screen and would hold the card until it expired.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
            ScreenEvents.afterExtract(screen).register((current, gfx, mouseX, mouseY, delta) -> {
                Painter painter = new Painter(gfx);
                ScoreboardSidebar.drawFixture(painter);
                CasinoResultOverlay.draw(painter, System.currentTimeMillis());
            }));
    }

    /**
     * Whether one of the client's full-screen panes is over the world. The client's own chips
     * and sidebar, and the game's hotbar, would bleed through it as dark blocks, so they wait.
     */
    public static boolean underGlass() {
        return Minecraft.getInstance().screen instanceof ServerMenuScreen
            || Minecraft.getInstance().screen instanceof WarpScreen;
    }

    private static void render(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.screen != null) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        Painter painter = new Painter(gfx);
        dev.fullmoon.client.hud.HudElementRegistry.getInstance().poll(System.currentTimeMillis());
        for (HudElement elem : dev.fullmoon.client.hud.HudElementRegistry.getInstance().elements()) {
            if (elem.enabled()) {
                Box bounds = elem.computeBounds(width, height, client);
                elem.draw(painter, bounds, client, false);
            }
        }
        long now = System.currentTimeMillis();
        ServerNoticeOverlay.draw(painter, now);
        CasinoResultOverlay.draw(painter, now);
    }
}
