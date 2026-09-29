package dev.fullmoon.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A screen whose pointer, keyboard, text and wheel events all enter through one surface. */
public abstract class SurfaceScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Screen");

    protected final Surface surface = new Surface();

    protected SurfaceScreen(Component title) {
        super(title);
    }

    /** One line per opening, so a capture can be paired with the screen it shows. */
    @Override
    public void added() {
        super.added();
        LOG.info("Opened {} screen", getClass().getSimpleName());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        surface.pointer(mouseX, mouseY);
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        return surface.press(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        return surface.release(event.x(), event.y()) || super.mouseReleased(event);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (surface.captured() == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        surface.pointer(event.x(), event.y());
        return true;
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
            double scrollY) {
        return surface.scroll(mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return surface.key(Chord.from(event)) || super.keyPressed(event);
    }

    @Override
    public final boolean charTyped(CharacterEvent event) {
        return surface.type(event.codepoint()) || super.charTyped(event);
    }
}
