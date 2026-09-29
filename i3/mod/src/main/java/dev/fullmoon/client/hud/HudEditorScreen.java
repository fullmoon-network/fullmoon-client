package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Glide;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Chord;
import dev.fullmoon.client.ui.Glass;
import dev.fullmoon.client.ui.IconButton;
import dev.fullmoon.client.ui.Surface;
import dev.fullmoon.client.ui.Toggle;
import dev.fullmoon.client.ui.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HUD 편집기: the HUD's elements where they really are, on the world behind a light scrim, each
 * one draggable to a snapped place; the chosen one outlined in gold with its anchor and offset
 * over it. A glass strip across the top holds the chosen element's anchor grid and switch, a strip
 * near the foot holds one chip per element with the gold bar gliding to the chosen one, and the
 * hint bar names the three things a hand can do here.
 *
 * <p>Not a {@link dev.fullmoon.client.ui.SurfaceScreen}: dragging an element is a capture the
 * surface does not own, so the pointer entry points are wired by hand.
 */
public final class HudEditorScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Screen");
    private static final int GRID_STEP = 16;
    private static final int CELL = 6;
    private static final int CELL_GAP = 1;
    private static final int GRID = CELL * 3 + CELL_GAP * 2;
    private static final int NOTE_MIN_WIDTH = 520;
    private static final float DOT = 2.0f;
    private static final float SCRIM = 0.52f;

    private final Screen parent;
    private final Surface surface = new Surface();
    private final List<HudElement> elements;
    private final Glide glide = new Glide(Tokens.Spring.GLIDE);
    private String selectedId;

    private boolean dragging;
    private int dragStartX;
    private int dragStartY;
    private int dragInitialOffsetX;
    private int dragInitialOffsetY;
    private boolean keyboard;

    private final IconButton closeButton;
    private final Button resetButton;
    private final Toggle enabled;
    private HudEditorLayout layout;
    private Box anchorGrid = Box.EMPTY;

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("fullmoon.hud.editor.title"));
        this.parent = parent;
        this.elements = HudElementRegistry.getInstance().elements();
        if (!elements.isEmpty()) {
            this.selectedId = elements.get(0).id();
        }
        closeButton = surface.add(new IconButton(IconButton.Glyph.CLOSE, "", this::onClose));
        resetButton = surface.add(new Button(Voice.QUIET, tr("action.reset"), this::resetDefaults));
        HudElement current = selectedElement();
        enabled = surface.add(new Toggle(tr("action.enabled"), current != null && current.enabled(), this::switched));
        enabled.enabled(current != null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        LOG.info("Opened {} screen", getClass().getSimpleName());
    }

    @Override
    public void onClose() {
        HudElementRegistry.getInstance().save();
        UiSounds.play(UiSounds.Cue.CLOSE);
        Minecraft.getInstance().setScreen(parent);
    }

    private void resetDefaults() {
        for (HudElement elem : elements) {
            boolean defaultEnabled = elem.id().equals("coords") || elem.id().equals("fps")
                || elem.id().equals("ping") || elem.id().equals("clock") || elem.id().equals("keystrokes");
            elem.setEnabled(defaultEnabled);
            elem.setOffsetX(16);
            if (elem.id().equals("coords") || elem.id().equals("ping")) {
                elem.setOffsetY(56);
            } else if (elem.id().equals("fps") || elem.id().equals("clock")) {
                elem.setOffsetY(82);
            } else if (elem.id().equals("tps")) {
                elem.setOffsetY(108);
            } else if (elem.id().equals("effects")) {
                elem.setOffsetY(134);
            } else {
                elem.setOffsetY(56);
            }
        }
        HudElementRegistry.getInstance().save();
        HudElement current = selectedElement();
        enabled.on(current != null && current.enabled());
    }

    private void switched(boolean on) {
        HudElement current = selectedElement();
        if (current != null) {
            current.setEnabled(on);
            HudElementRegistry.getInstance().save();
        }
    }

    @Override
    protected void init() {
        layout = HudEditorLayout.fit(width, height);
        Box header = layout.header();
        int right = header.right() - Tokens.Space.LOOSE;
        closeButton.place(new Box(right - IconButton.SIZE + Tokens.Space.BASE, header.y() + (header.h() - IconButton.SIZE) / 2,
            IconButton.SIZE, IconButton.SIZE));
        right -= IconButton.SIZE + Tokens.Space.COZY + Glass.keycapWidth("Esc") + Tokens.Space.LOOSE;
        int resetW = resetButton.measure();
        resetButton.place(new Box(right - resetW, header.y() + (header.h() - Button.HEIGHT) / 2, resetW, Button.HEIGHT));

        int x = header.midX() - Tokens.Size.SIDEBAR;
        HudElement current = selectedElement();
        int labelW = current == null ? 0 : Typeset.width(Tokens.Type.ROW, current.label());
        anchorGrid = new Box(x + labelW + Tokens.Space.LOOSE, header.y() + (header.h() - GRID) / 2, GRID, GRID);
        int anchorLabelW = current == null ? 0 : Typeset.width(Tokens.Type.BODY, current.anchor().label());
        enabled.place(new Box(anchorGrid.right() + Tokens.Space.COZY + anchorLabelW + Tokens.Space.LOOSE,
            header.y() + (header.h() - Toggle.HEIGHT) / 2, enabled.measure(), Toggle.HEIGHT));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboard = false;
        surface.pointer(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (surface.press(event.x(), event.y())) {
                return true;
            }
            int mx = (int) event.x();
            int my = (int) event.y();
            if (handleAnchorGridClick(mx, my) || handleDockClick(mx, my)) {
                return true;
            }
            Minecraft client = Minecraft.getInstance();
            for (HudElement elem : elements) {
                if (!elem.enabled()) {
                    continue;
                }
                Box b = elem.computeBounds(width, height, client);
                if (b.holds(mx, my)) {
                    select(elem.id());
                    dragging = true;
                    dragStartX = mx;
                    dragStartY = my;
                    dragInitialOffsetX = elem.offsetX();
                    dragInitialOffsetY = elem.offsetY();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleAnchorGridClick(int mx, int my) {
        HudElement elem = selectedElement();
        if (elem == null || !anchorGrid.holds(mx, my)) {
            return false;
        }
        int col = (mx - anchorGrid.x()) / (CELL + CELL_GAP);
        int row = (my - anchorGrid.y()) / (CELL + CELL_GAP);
        Anchor chosen = Anchor.fromGrid(Math.min(col, 2), Math.min(row, 2));

        Minecraft client = Minecraft.getInstance();
        int elemW = elem.measureWidth(client);
        int elemH = elem.measureHeight(client);
        int currScreenX = elem.anchor().computeX(width, elemW, elem.offsetX());
        int currScreenY = elem.anchor().computeY(height, elemH, elem.offsetY());

        elem.setAnchor(chosen);
        elem.setOffsetX(Math.max(0, chosen.computeOffsetX(width, elemW, currScreenX)));
        elem.setOffsetY(Math.max(0, chosen.computeOffsetY(height, elemH, currScreenY)));
        HudElementRegistry.getInstance().save();
        UiSounds.play(UiSounds.Cue.CONFIRM);
        init();
        return true;
    }

    private boolean handleDockClick(int mx, int my) {
        int[] widths = chipWidths();
        Box dock = layout.dock(HudEditorLayout.chipsWidth(widths), width);
        if (!dock.holds(mx, my)) {
            return false;
        }
        for (int i = 0; i < elements.size(); i++) {
            if (HudEditorLayout.chip(dock, widths, i).holds(mx, my)) {
                HudElement elem = elements.get(i);
                select(elem.id());
                elem.setEnabled(!elem.enabled());
                enabled.on(elem.enabled());
                HudElementRegistry.getInstance().save();
                UiSounds.play(UiSounds.Cue.CONFIRM);
                return true;
            }
        }
        return true;
    }

    /** Chooses an element: the chip's bar glides to it and the header takes its anchor and switch. */
    private void select(String id) {
        if (!id.equals(selectedId)) {
            UiSounds.play(UiSounds.Cue.FOCUS);
        }
        selectedId = id;
        HudElement current = selectedElement();
        enabled.enabled(current != null);
        enabled.on(current != null && current.enabled());
        init();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            HudElementRegistry.getInstance().save();
            return true;
        }
        return surface.release(event.x(), event.y()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            HudElement elem = selectedElement();
            if (elem != null) {
                Minecraft client = Minecraft.getInstance();
                int elemW = elem.measureWidth(client);
                int elemH = elem.measureHeight(client);
                int dx = (int) (event.x() - dragStartX);
                int dy = (int) (event.y() - dragStartY);
                int rawX = elem.anchor().computeX(width, elemW, dragInitialOffsetX) + dx;
                int rawY = elem.anchor().computeY(height, elemH, dragInitialOffsetY) + dy;
                int step = HudElementRegistry.getInstance().gridSnap();
                int snappedX = HudGrid.snap(rawX, step);
                int snappedY = HudGrid.snap(rawY, step);
                Anchor nearest = Anchor.nearest(width, height, elemW, elemH, snappedX, snappedY);
                elem.setAnchor(nearest);
                elem.setOffsetX(Math.max(0, nearest.computeOffsetX(width, elemW, snappedX)));
                elem.setOffsetY(Math.max(0, nearest.computeOffsetY(height, elemH, snappedY)));
                return true;
            }
        }
        if (surface.captured() != null) {
            surface.pointer(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return surface.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        keyboard = true;
        if (surface.key(Chord.from(event))) {
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return surface.type(event.codepoint()) || super.charTyped(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(), Rgb.alpha(Tokens.Color.SURFACE_VOID, SCRIM));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);
        dots(painter);
        if (dragging) {
            painter.vRule(width / 2, 0, height, Tokens.Color.LINE_HAIRLINE);
            painter.hRule(0, height / 2, width, Tokens.Color.LINE_HAIRLINE);
        }
        elements(painter);
        outline(painter);
        header(painter);
        dock(painter, mouseX, mouseY);
        surface.draw(painter);
        Glass.hints(painter, width / 2, layout.hintY(), List.of(
            new Glass.Hint(tr("hint.drag.key"), tr("hint.drag")),
            new Glass.Hint(tr("hint.click.key"), tr("hint.click")),
            new Glass.Hint("Esc", tr("hint.done"))), keyboard);
    }

    /** The snap grid, as one dim dot per sixteen pixels of the canvas. */
    private void dots(Painter painter) {
        Box canvas = layout.canvas(width);
        int color = Rgb.alpha(Tokens.Color.LINE_HAIRLINE, 0.35f);
        for (int y = canvas.y() + GRID_STEP - canvas.y() % GRID_STEP; y < canvas.bottom(); y += GRID_STEP) {
            for (int x = GRID_STEP; x < width; x += GRID_STEP) {
                painter.fill(x, y, 1, 1, color);
            }
        }
    }

    private void elements(Painter painter) {
        Minecraft client = Minecraft.getInstance();
        for (HudElement elem : elements) {
            if (elem.enabled()) {
                elem.draw(painter, elem.computeBounds(width, height, client), client, true);
            }
        }
    }

    /** The chosen element: a hairline of gold around it, and its anchor and offset on a chip above. */
    private void outline(Painter painter) {
        HudElement elem = selectedElement();
        if (elem == null || !elem.enabled()) {
            return;
        }
        Box b = elem.computeBounds(width, height, Minecraft.getInstance()).inset(-Tokens.Space.TIGHT);
        painter.border(b.x(), b.y(), b.w(), b.h(), Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.ACCENT);
        String badge = elem.anchor().label() + " · " + elem.offsetX() + ", " + elem.offsetY();
        int w = Typeset.tabularWidth(Tokens.Type.BODY, badge) + Tokens.Space.COZY * 2;
        int h = Tokens.Size.HUD_CHIP;
        int y = b.y() - Tokens.Space.SNUG - h;
        if (y < layout.header().bottom()) {
            y = b.bottom() + Tokens.Space.SNUG;
        }
        int x = Math.clamp(b.x(), 0, Math.max(0, width - w));
        painter.fill(x, y, w, h, Tokens.Color.SURFACE_GLASS_HUD);
        Typeset.tabular(painter, Tokens.Type.BODY, badge, x + Tokens.Space.COZY, Typeset.centred(Tokens.Type.BODY, y, h),
            Tokens.Color.ACCENT);
    }

    /** The strip across the top: the editor's name, the chosen element's anchor and switch, the way out. */
    private void header(Painter painter) {
        Box header = layout.header();
        painter.fill(header.x(), header.y(), header.w(), header.h(), Tokens.Color.SURFACE_GLASS);
        Glass.hair(painter, header.x(), header.bottom() - 1, header.w());
        int x = header.x() + Tokens.Space.LOOSE;
        x += Typeset.draw(painter, Tokens.Type.TITLE, tr("title"), x, Typeset.centred(Tokens.Type.TITLE, header.y(), header.h()),
            Tokens.Color.INK_PRIMARY);
        int bodyY = Typeset.centred(Tokens.Type.BODY, header.y(), header.h());
        if (width >= NOTE_MIN_WIDTH) {
            Typeset.draw(painter, Tokens.Type.BODY, tr("snap", HudElementRegistry.getInstance().gridSnap()),
                x + Tokens.Space.COZY, bodyY, Tokens.Color.INK_TERTIARY);
        }
        HudElement elem = selectedElement();
        if (elem != null) {
            Typeset.draw(painter, Tokens.Type.ROW, elem.label(), header.midX() - Tokens.Size.SIDEBAR,
                Typeset.centred(Tokens.Type.ROW, header.y(), header.h()), Tokens.Color.INK_PRIMARY);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int cx = anchorGrid.x() + c * (CELL + CELL_GAP);
                    int cy = anchorGrid.y() + r * (CELL + CELL_GAP);
                    boolean active = elem.anchor().col() == c && elem.anchor().row() == r;
                    painter.fill(cx, cy, CELL, CELL, active ? Tokens.Color.ACCENT : Tokens.Color.SURFACE_CONTROL);
                    if (!active) {
                        painter.border(cx, cy, CELL, CELL, Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
                    }
                }
            }
            Typeset.draw(painter, Tokens.Type.BODY, elem.anchor().label(), anchorGrid.right() + Tokens.Space.COZY, bodyY,
                Tokens.Color.INK_SECONDARY);
        }
        int capX = closeButton.bounds().x() - Tokens.Space.COZY - Glass.keycapWidth("Esc");
        Glass.keycap(painter, capX, header.y() + (header.h() - Tokens.Size.KEYCAP) / 2, "Esc");
    }

    /** One chip per element on a glass strip; the gold bar glides to the chosen one. */
    private void dock(Painter painter, int mx, int my) {
        int[] widths = chipWidths();
        Box dock = layout.dock(HudEditorLayout.chipsWidth(widths), width);
        painter.fill(dock.x(), dock.y(), dock.w(), dock.h(), Tokens.Color.SURFACE_GLASS);
        painter.hRule(dock.x(), dock.y(), dock.w(), Tokens.Color.SURFACE_HIGHLIGHT);
        for (int i = 0; i < elements.size(); i++) {
            HudElement elem = elements.get(i);
            Box chip = HudEditorLayout.chip(dock, widths, i);
            boolean chosen = elem.id().equals(selectedId);
            if (chosen) {
                if (!glide.placed()) {
                    glide.snap(chip);
                } else if (!chip.equals(glide.target())) {
                    glide.to(chip);
                }
            } else if (chip.holds(mx, my)) {
                painter.fill(chip.x(), chip.y(), chip.w(), chip.h(), Tokens.Color.SURFACE_RAISED);
            }
        }
        if (glide.placed()) {
            glide.advance(System.nanoTime());
            painter.fill(glide.x(), glide.y(), glide.w(), glide.h(), Tokens.Color.ACCENT_WASH);
            painter.fill(glide.x(), glide.y(), Tokens.Stroke.BAR, glide.h(), Tokens.Color.ACCENT);
        }
        for (int i = 0; i < elements.size(); i++) {
            HudElement elem = elements.get(i);
            Box chip = HudEditorLayout.chip(dock, widths, i);
            float dotX = chip.x() + Tokens.Space.COZY + DOT;
            painter.dot(dotX, chip.midY(), DOT, elem.enabled() ? Tokens.Color.STATUS_LIVE : Tokens.Color.STATUS_IDLE);
            Typeset.draw(painter, Tokens.Type.BODY, elem.label(), Math.round(dotX + DOT) + Tokens.Space.BASE,
                Typeset.centred(Tokens.Type.BODY, chip.y(), chip.h()),
                elem.enabled() ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY);
        }
    }

    /** A chip is its dot, its label and eight pixels either side. */
    private int[] chipWidths() {
        int[] widths = new int[elements.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Tokens.Space.COZY + Math.round(DOT * 2) + Tokens.Space.BASE
                + Typeset.width(Tokens.Type.BODY, elements.get(i).label()) + Tokens.Space.COZY;
        }
        return widths;
    }

    private HudElement selectedElement() {
        return selectedId == null ? null : HudElementRegistry.getInstance().get(selectedId);
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.hud.editor." + key, args);
    }
}
