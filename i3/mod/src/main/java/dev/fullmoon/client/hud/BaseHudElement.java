package dev.fullmoon.client.hud;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

/** Base class for HUD elements providing common anchor state and chip styling. */
public abstract class BaseHudElement implements HudElement {
    protected static final int PADDING_H = Tokens.Space.COZY;
    protected static final int PADDING_V = Tokens.Space.SNUG;
    protected static final int CHIP_HEIGHT = 20;
    private static final int TICK = 4;

    private final String id;
    private final String label;
    private final String category;
    private boolean enabled;
    private Anchor anchor;
    private int offsetX;
    private int offsetY;
    private float scale = 1.0f;

    protected BaseHudElement(String id, String label, String category, boolean enabled,
            Anchor anchor, int offsetX, int offsetY) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.enabled = enabled;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String label() {
        return label;
    }

    @Override
    public final String category() {
        return category;
    }

    @Override
    public final boolean enabled() {
        return enabled;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public final Anchor anchor() {
        return anchor;
    }

    @Override
    public final void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    @Override
    public final int offsetX() {
        return offsetX;
    }

    @Override
    public final void setOffsetX(int offsetX) {
        this.offsetX = offsetX;
    }

    @Override
    public final int offsetY() {
        return offsetY;
    }

    @Override
    public final void setOffsetY(int offsetY) {
        this.offsetY = offsetY;
    }

    @Override
    public final float scale() {
        return scale;
    }

    @Override
    public final void setScale(float scale) {
        this.scale = scale;
    }

    /** Draws a chip: night glass inside a gilt hairline, with the frame's ticks on two corners. */
    protected void drawContainer(Painter painter, Box bounds) {
        painter.fill(bounds.x(), bounds.y(), bounds.w(), bounds.h(),
            Tokens.Radius.NONE, Tokens.Color.SURFACE_GLASS_HUD);
    }

    /**
     * Something drawn before the key, such as the clock's moon. Returns the width it took; the
     * element's {@link #measureWidth} has to count the same width.
     */
    protected int drawLeadingMark(Painter painter, int x, float cy) {
        return 0;
    }

    /** Draws a standard single-line key-value chip with shared baseline centering. */
    protected void drawChip(Painter painter, Box bounds, String key, String val, int dotColor) {
        drawContainer(painter, bounds);

        int textY = bounds.y() + (bounds.h() - 9) / 2;
        int currentX = bounds.x() + PADDING_H;

        if (dotColor != 0) {
            int dotCenterY = bounds.y() + bounds.h() / 2;
            painter.dot(currentX + Tokens.Space.TIGHT, dotCenterY, Tokens.Space.TIGHT, dotColor);
            currentX += Tokens.Space.COZY;
        }
        currentX += drawLeadingMark(painter, currentX, bounds.y() + bounds.h() / 2.0f);

        if (key != null && !key.isEmpty()) {
            Typeset.draw(painter, Tokens.Type.MICRO, key, currentX, textY, Tokens.Color.ACCENT);
            currentX += Typeset.width(Tokens.Type.MICRO, key) + Tokens.Space.SNUG;
        }

        Typeset.tabular(painter, Tokens.Type.STRONG, val, currentX, textY, Tokens.Color.INK_PRIMARY);
    }
}
