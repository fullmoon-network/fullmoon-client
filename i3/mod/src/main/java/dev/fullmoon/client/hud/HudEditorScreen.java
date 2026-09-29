package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;
import dev.fullmoon.client.ui.Button;
import dev.fullmoon.client.ui.Chord;
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

/** Premier in-game HUD studio with clean top header, centered inspector pill, and spacious bottom dock. */
public final class HudEditorScreen extends Screen {
    private static final int GRID_STEP = 16;
    private static final int CORNER_LEN = 6;
    private static final int CORNER_THICK = Tokens.Stroke.FOCUS;

    private static final int INSPECTOR_W = 340;
    private static final int INSPECTOR_H = 34;
    private static final int DOCK_H = 38;
    private static final int DOCK_PILL_H = 26;
    private static final int DOCK_PILL_GAP = 6;
    private static final int ANCHOR_CELL_SIZE = 9;
    private static final int ANCHOR_CELL_GAP = 1;

    private final Screen parent;
    private final Surface surface = new Surface();
    private final List<HudElement> elements;
    private String selectedId = null;

    private boolean dragging = false;
    private int dragStartX;
    private int dragStartY;
    private int dragInitialOffsetX;
    private int dragInitialOffsetY;

    private final Button closeBtn;
    private final Button resetBtn;
    private Toggle enableToggle;

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("fullmoon.hud.editor.title"));
        this.parent = parent;
        this.elements = HudElementRegistry.getInstance().elements();
        if (!elements.isEmpty()) {
            this.selectedId = elements.get(0).id();
        }

        closeBtn = surface.add(new Button(Voice.QUIET, tr("action.close"), this::onClose));
        resetBtn = surface.add(new Button(Voice.QUIET, tr("action.reset"), this::resetDefaults));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        HudElementRegistry.getInstance().save();
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
    }

    @Override
    protected void init() {
        int barY = 12;
        int closeW = closeBtn.measure();
        int resetW = resetBtn.measure();

        closeBtn.place(new Box(width - 24 - closeW, barY + (INSPECTOR_H - Button.HEIGHT) / 2, closeW, Button.HEIGHT));
        resetBtn.place(new Box(width - 24 - closeW - Tokens.Space.COZY - resetW, barY + (INSPECTOR_H - Button.HEIGHT) / 2, resetW, Button.HEIGHT));

        HudElement current = selectedElement();
        if (current != null) {
            int inspX = (width - INSPECTOR_W) / 2;
            enableToggle = surface.add(new Toggle("사용", current.enabled(), current::setEnabled));
            int toggleW = enableToggle.measure();
            enableToggle.place(new Box(inspX + INSPECTOR_W - Tokens.Space.COZY - toggleW,
                barY + (INSPECTOR_H - Toggle.HEIGHT) / 2, toggleW, Toggle.HEIGHT));
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
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

            // Check 3x3 Anchor grid click in inspector pill
            if (handleAnchorGridClick(mx, my)) {
                return true;
            }

            // Check Bottom Dock click
            if (handleDockClick(mx, my)) {
                return true;
            }

            // Check canvas elements click
            Minecraft client = Minecraft.getInstance();
            for (HudElement elem : elements) {
                if (!elem.enabled()) continue;
                Box b = elem.computeBounds(width, height, client);
                if (b.holds(mx, my)) {
                    selectedId = elem.id();
                    dragging = true;
                    dragStartX = mx;
                    dragStartY = my;
                    dragInitialOffsetX = elem.offsetX();
                    dragInitialOffsetY = elem.offsetY();
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleAnchorGridClick(int mx, int my) {
        HudElement elem = selectedElement();
        if (elem == null) return false;

        int inspX = (width - INSPECTOR_W) / 2;
        int inspY = 12;
        int anchorBoxX = inspX + 135;
        int anchorBoxY = inspY + (INSPECTOR_H - (ANCHOR_CELL_SIZE * 3 + ANCHOR_CELL_GAP * 2)) / 2;

        int totalGridW = ANCHOR_CELL_SIZE * 3 + ANCHOR_CELL_GAP * 2;
        int totalGridH = ANCHOR_CELL_SIZE * 3 + ANCHOR_CELL_GAP * 2;
        Box gridBounds = new Box(anchorBoxX, anchorBoxY, totalGridW, totalGridH);

        if (gridBounds.holds(mx, my)) {
            int col = (mx - anchorBoxX) / (ANCHOR_CELL_SIZE + ANCHOR_CELL_GAP);
            int row = (my - anchorBoxY) / (ANCHOR_CELL_SIZE + ANCHOR_CELL_GAP);
            Anchor chosen = Anchor.fromGrid(col, row);

            int elemW = elem.measureWidth(Minecraft.getInstance());
            int elemH = elem.measureHeight(Minecraft.getInstance());
            int currScreenX = elem.anchor().computeX(width, elemW, elem.offsetX());
            int currScreenY = elem.anchor().computeY(height, elemH, elem.offsetY());

            elem.setAnchor(chosen);
            elem.setOffsetX(Math.max(0, chosen.computeOffsetX(width, elemW, currScreenX)));
            elem.setOffsetY(Math.max(0, chosen.computeOffsetY(height, elemH, currScreenY)));

            HudElementRegistry.getInstance().save();
            return true;
        }
        return false;
    }

    private boolean handleDockClick(int mx, int my) {
        int dockW = computeDockWidth();
        int dockX = (width - dockW) / 2;
        int dockY = height - DOCK_H - Tokens.Space.COZY;

        Box dockBox = new Box(dockX, dockY, dockW, DOCK_H);
        if (!dockBox.holds(mx, my)) {
            return false;
        }

        int currX = dockX + Tokens.Space.LOOSE;
        int pillY = dockY + (DOCK_H - DOCK_PILL_H) / 2;

        for (HudElement elem : elements) {
            int pillW = measureDockPillWidth(elem);
            Box pillBox = new Box(currX, pillY, pillW, DOCK_PILL_H);
            if (pillBox.holds(mx, my)) {
                selectedId = elem.id();
                elem.setEnabled(!elem.enabled());
                HudElementRegistry.getInstance().save();
                init();
                return true;
            }
            currX += pillW + DOCK_PILL_GAP;
        }
        return true;
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
                int dx = (int) (event.x() - dragStartX);
                int dy = (int) (event.y() - dragStartY);

                int rawX = elem.anchor().computeX(width, elem.measureWidth(Minecraft.getInstance()), dragInitialOffsetX) + dx;
                int rawY = elem.anchor().computeY(height, elem.measureHeight(Minecraft.getInstance()), dragInitialOffsetY) + dy;

                int step = HudElementRegistry.getInstance().gridSnap();
                int snappedX = HudGrid.snap(rawX, step);
                int snappedY = HudGrid.snap(rawY, step);

                Anchor nearest = Anchor.nearest(width, height, elem.measureWidth(Minecraft.getInstance()),
                    elem.measureHeight(Minecraft.getInstance()), snappedX, snappedY);
                elem.setAnchor(nearest);

                int offX = nearest.computeOffsetX(width, elem.measureWidth(Minecraft.getInstance()), snappedX);
                int offY = nearest.computeOffsetY(height, elem.measureHeight(Minecraft.getInstance()), snappedY);

                elem.setOffsetX(Math.max(0, offX));
                elem.setOffsetY(Math.max(0, offY));
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
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.76f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        surface.hover(mouseX, mouseY);
        Painter painter = new Painter(gfx);

        drawSpatialDotGrid(painter);
        drawAnchorZoneGuides(painter);
        drawDynamicSnapGuides(painter);
        drawElements(painter);
        drawSelectedCornerTicks(painter);
        drawTopHeader(painter);
        drawModuleDock(painter, mouseX, mouseY);
        surface.draw(painter);
    }

    /** Subtle 16px spatial dot grid across the canvas. */
    private void drawSpatialDotGrid(Painter painter) {
        int dotColor = Rgb.alpha(Tokens.Color.LINE_HAIRLINE, 0.35f);
        for (int y = GRID_STEP; y < height; y += GRID_STEP) {
            for (int x = GRID_STEP; x < width; x += GRID_STEP) {
                painter.fill(x, y, 1, 1, 0, dotColor);
            }
        }
    }

    /** Subtle corner brackets for the 9 anchor regions. */
    private void drawAnchorZoneGuides(Painter painter) {
        int guideColor = Rgb.alpha(Tokens.Color.LINE_HAIRLINE, 0.45f);
        int m = Tokens.Space.COZY;
        int len = 8;

        // Top-Left
        painter.fill(m, m, len, 1, 0, guideColor);
        painter.fill(m, m, 1, len, 0, guideColor);

        // Top-Right
        painter.fill(width - m - len, m, len, 1, 0, guideColor);
        painter.fill(width - m, m, 1, len, 0, guideColor);

        // Bottom-Left
        painter.fill(m, height - m, len, 1, 0, guideColor);
        painter.fill(m, height - m - len, 1, len, 0, guideColor);

        // Bottom-Right
        painter.fill(width - m - len, height - m, len, 1, 0, guideColor);
        painter.fill(width - m, height - m - len, 1, len, 0, guideColor);
    }

    private void drawDynamicSnapGuides(Painter painter) {
        if (dragging) {
            int midX = width / 2;
            int midY = height / 2;
            painter.vRule(midX, 0, height, Tokens.Color.LINE_HAIRLINE);
            painter.hRule(0, midY, width, Tokens.Color.LINE_HAIRLINE);
        }
    }

    private void drawElements(Painter painter) {
        Minecraft client = Minecraft.getInstance();
        for (HudElement elem : elements) {
            if (!elem.enabled()) continue;

            Box b = elem.computeBounds(width, height, client);
            elem.draw(painter, b, client, true);
        }
    }

    private void drawSelectedCornerTicks(Painter painter) {
        HudElement elem = selectedElement();
        if (elem == null || !elem.enabled()) return;

        Minecraft client = Minecraft.getInstance();
        Box b = elem.computeBounds(width, height, client);
        int x = b.x();
        int y = b.y();
        int w = b.w();
        int h = b.h();
        int color = Tokens.Color.ACCENT;

        // Top-Left L-tick
        painter.fill(x - CORNER_THICK, y - CORNER_THICK, CORNER_LEN, CORNER_THICK, color);
        painter.fill(x - CORNER_THICK, y - CORNER_THICK, CORNER_THICK, CORNER_LEN, color);

        // Top-Right L-tick
        painter.fill(x + w + CORNER_THICK - CORNER_LEN, y - CORNER_THICK, CORNER_LEN, CORNER_THICK, color);
        painter.fill(x + w, y - CORNER_THICK, CORNER_THICK, CORNER_LEN, color);

        // Bottom-Left L-tick
        painter.fill(x - CORNER_THICK, y + h, CORNER_LEN, CORNER_THICK, color);
        painter.fill(x - CORNER_THICK, y + h + CORNER_THICK - CORNER_LEN, CORNER_THICK, CORNER_LEN, color);

        // Bottom-Right L-tick
        painter.fill(x + w + CORNER_THICK - CORNER_LEN, y + h, CORNER_LEN, CORNER_THICK, color);
        painter.fill(x + w, y + h + CORNER_THICK - CORNER_LEN, CORNER_THICK, CORNER_LEN, color);

        // Position Badge above element
        String badge = String.format("%s · (%d, %d)", elem.anchor().label(), elem.offsetX(), elem.offsetY());
        int badgeW = Typeset.width(Tokens.Type.MICRO, badge) + Tokens.Space.SNUG * 2;
        int badgeH = Tokens.Type.MICRO.leading() + 4;
        int badgeX = x;
        int badgeY = y - badgeH - Tokens.Space.TIGHT;

        if (badgeY >= 0) {
            painter.fill(badgeX, badgeY, badgeW, badgeH, Tokens.Radius.SM,
                Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.90f));
            painter.border(badgeX, badgeY, badgeW, badgeH, Tokens.Radius.SM, Tokens.Stroke.HAIR,
                Tokens.Color.LINE_HAIRLINE);
            Typeset.draw(painter, Tokens.Type.MICRO, badge, badgeX + Tokens.Space.SNUG,
                badgeY + 2, Tokens.Color.ACCENT);
        }
    }

    private void drawTopHeader(Painter painter) {
        int barY = 12;

        // 1. Left Title & Seal
        int capH = Typeset.capHeight(Tokens.Type.STRONG);
        int seal = capH + Tokens.Space.SNUG;
        int titleX = 24 + seal + Tokens.Space.COZY;
        int titleY = barY + (INSPECTOR_H - 9) / 2;
        Typeset.draw(painter, Tokens.Type.STRONG, "Fullmoon HUD Studio", titleX, titleY, Tokens.Color.INK_PRIMARY);

        int snapX = titleX + Typeset.width(Tokens.Type.STRONG, "Fullmoon HUD Studio") + Tokens.Space.SNUG;
        int snapY = barY + (INSPECTOR_H - 9) / 2;
        Typeset.draw(painter, Tokens.Type.MICRO, "· 4px 스냅", snapX, snapY, Tokens.Color.INK_TERTIARY);

        HudElement elem = selectedElement();
        if (elem != null) {
            int inspX = (width - INSPECTOR_W) / 2;
            glass(painter, inspX, barY, INSPECTOR_W, INSPECTOR_H);

            int modNameX = inspX + Tokens.Space.LOOSE;
            int modNameY = barY + (INSPECTOR_H - 9) / 2;
            Typeset.draw(painter, Tokens.Type.STRONG, elem.label(), modNameX, modNameY, Tokens.Color.INK_PRIMARY);

            int anchorBoxX = inspX + 135;
            int totalGridH = ANCHOR_CELL_SIZE * 3 + ANCHOR_CELL_GAP * 2;
            int anchorBoxY = barY + (INSPECTOR_H - totalGridH) / 2;

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int cx = anchorBoxX + c * (ANCHOR_CELL_SIZE + ANCHOR_CELL_GAP);
                    int cy = anchorBoxY + r * (ANCHOR_CELL_SIZE + ANCHOR_CELL_GAP);

                    boolean active = elem.anchor().col() == c && elem.anchor().row() == r;
                    int bg = active ? Tokens.Color.ACCENT : Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.8f);
                    int border = active ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.LINE_HAIRLINE;

                    painter.fill(cx, cy, ANCHOR_CELL_SIZE, ANCHOR_CELL_SIZE, Tokens.Radius.NONE, bg);
                    painter.border(cx, cy, ANCHOR_CELL_SIZE, ANCHOR_CELL_SIZE, Tokens.Radius.NONE,
                        Tokens.Stroke.HAIR, border);
                }
            }

            int anchorLabelX = anchorBoxX + ANCHOR_CELL_SIZE * 3 + ANCHOR_CELL_GAP * 2 + Tokens.Space.SNUG;
            int anchorLabelY = barY + (INSPECTOR_H - 9) / 2;
            Typeset.draw(painter, Tokens.Type.MICRO, elem.anchor().label(), anchorLabelX, anchorLabelY, Tokens.Color.INK_SECONDARY);
        }
    }

    /** The editor's own bars: the HUD chips' night glass and gilt hairline, ticked on two corners. */
    private static void glass(Painter painter, int x, int y, int w, int h) {
        painter.fill(x, y, w, h, Tokens.Radius.NONE, Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.90f));
        painter.border(x, y, w, h, Tokens.Radius.NONE, Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
    }

    private void drawModuleDock(Painter painter, int mx, int my) {
        int dockW = computeDockWidth();
        int dockX = (width - dockW) / 2;
        int dockY = height - DOCK_H - Tokens.Space.COZY;

        glass(painter, dockX, dockY, dockW, DOCK_H);

        int currX = dockX + Tokens.Space.LOOSE;
        int pillY = dockY + (DOCK_H - DOCK_PILL_H) / 2;

        for (HudElement elem : elements) {
            int pillW = measureDockPillWidth(elem);
            Box pillBox = new Box(currX, pillY, pillW, DOCK_PILL_H);
            boolean hovered = pillBox.holds(mx, my);
            boolean selected = elem.id().equals(selectedId);

            int bg = selected ? Rgb.alpha(Tokens.Color.SURFACE_RAISED, 0.95f)
                : (hovered ? Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.85f) : Tokens.Color.SURFACE_VOID);
            int border = selected ? Tokens.Color.ACCENT : Tokens.Color.LINE_HAIRLINE;

            painter.fill(pillBox.x(), pillBox.y(), pillBox.w(), pillBox.h(), Tokens.Radius.SM, bg);
            painter.border(pillBox.x(), pillBox.y(), pillBox.w(), pillBox.h(), Tokens.Radius.SM, Tokens.Stroke.HAIR, border);

            // Status dot with generous left margin
            int dotColor = elem.enabled() ? Tokens.Color.STATUS_LIVE : Tokens.Color.INK_TERTIARY;
            int dotX = pillBox.x() + 10;
            int dotY = pillBox.y() + pillBox.h() / 2;
            painter.dot(dotX, dotY, 2, dotColor);

            // Label text starting after dot with generous right margin
            int textX = dotX + 8;
            int textY = pillBox.y() + (DOCK_PILL_H - 9) / 2;
            int ink = elem.enabled() ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_TERTIARY;

            Typeset.draw(painter, Tokens.Type.MICRO, elem.label(), textX, textY, ink);

            currX += pillW + DOCK_PILL_GAP;
        }
    }

    private int measureDockPillWidth(HudElement elem) {
        return 10 + 4 + 8 + Typeset.width(Tokens.Type.MICRO, elem.label()) + 10;
    }

    private int computeDockWidth() {
        int total = Tokens.Space.LOOSE * 2;
        for (int i = 0; i < elements.size(); i++) {
            total += measureDockPillWidth(elements.get(i));
            if (i < elements.size() - 1) {
                total += DOCK_PILL_GAP;
            }
        }
        return total;
    }

    private HudElement selectedElement() {
        if (selectedId == null) return null;
        return HudElementRegistry.getInstance().get(selectedId);
    }

    private static String tr(String key, Object... args) {
        return I18n.get("fullmoon.hud.editor." + key, args);
    }
}
