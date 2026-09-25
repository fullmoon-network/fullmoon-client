package dev.fullmoon.client.hud;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;

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
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(ID, HudOverlay::render);
        // A bet is placed from a casino menu, so the result lands while that screen is still up;
        // the HUD layer is skipped under any screen and would hold the card until it expired.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
            ScreenEvents.afterExtract(screen).register((current, gfx, mouseX, mouseY, delta) ->
                CasinoResultOverlay.draw(new Painter(gfx), System.currentTimeMillis())));
    }

    private static void render(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.screen != null) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        Painter painter = new Painter(gfx);
        HudElementRegistry.getInstance().poll(System.currentTimeMillis());
        for (HudElement elem : HudElementRegistry.getInstance().elements()) {
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
