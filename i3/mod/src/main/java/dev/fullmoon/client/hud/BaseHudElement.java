package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * Base class for HUD elements: anchor state, and the chip every readout is drawn as — sixteen
 * pixels of HUD glass with no border, the value large in the row face and its unit small in
 * tertiary ink after it. A label that shouts before the value is what the chips used to do; a
 * unit that follows it is what they do now.
 */
public abstract class BaseHudElement implements HudElement {
    protected static final int PADDING_H = Tokens.Space.BASE;
    protected static final int CHIP_HEIGHT = Tokens.Size.HUD_CHIP;
    /** Between a dot or a moon and the value, between a value and its unit, and around a separator. */
    protected static final int GAP = Tokens.Space.BASE;
    protected static final float DOT = Tokens.Space.TIGHT;

    /** One reading on a chip: a value that changes, and the unit it is in, which may be empty. */
    protected record Part(String value, String unit) {}

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

    /** The chip's ground: HUD glass, nothing around it. */
    protected void drawContainer(Painter painter, Box bounds) {
        painter.fill(bounds.x(), bounds.y(), bounds.w(), bounds.h(),
            Tokens.Radius.NONE, Tokens.Color.SURFACE_GLASS_HUD);
    }

    /**
     * Something drawn before the first value, such as the clock's moon. Returns the width it
     * took, including the gap after it; {@link #chipWidth} has to be given the same width.
     */
    protected int drawLeadingMark(Painter painter, int x, float cy) {
        return 0;
    }

    /** The width of a chip holding {@code parts}, after {@code lead} pixels of dot or moon. */
    protected static int chipWidth(int lead, List<Part> parts) {
        int w = PADDING_H * 2 + lead;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (i > 0) {
                w += GAP + Typeset.width(Tokens.Type.BODY, "·") + GAP;
            }
            w += Typeset.tabularWidth(Tokens.Type.ROW, part.value());
            if (!part.unit().isEmpty()) {
                w += GAP + Typeset.width(Tokens.Type.BODY, part.unit());
            }
        }
        return w;
    }

    /** The width a status dot takes before the value. */
    protected static int dotWidth() {
        return Math.round(DOT * 2) + GAP;
    }

    /** Draws the chip: a dot when {@code dotColor} is not zero, the leading mark, then each part. */
    protected void drawChip(Painter painter, Box bounds, int dotColor, List<Part> parts) {
        drawContainer(painter, bounds);
        int valueY = Typeset.centred(Tokens.Type.ROW, bounds.y(), bounds.h());
        int unitY = Typeset.centred(Tokens.Type.BODY, bounds.y(), bounds.h());
        int x = bounds.x() + PADDING_H;
        if (dotColor != 0) {
            painter.dot(x + DOT, bounds.y() + bounds.h() / 2.0f, DOT, dotColor);
            x += dotWidth();
        }
        x += drawLeadingMark(painter, x, bounds.y() + bounds.h() / 2.0f);
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (i > 0) {
                x += GAP;
                x += Typeset.draw(painter, Tokens.Type.BODY, "·", x, unitY, Tokens.Color.INK_DISABLED) + GAP;
            }
            x += Typeset.tabular(painter, Tokens.Type.ROW, part.value(), x, valueY, Tokens.Color.INK_PRIMARY);
            if (!part.unit().isEmpty()) {
                x += GAP;
                x += Typeset.draw(painter, Tokens.Type.BODY, part.unit(), x, unitY, Tokens.Color.INK_TERTIARY);
            }
        }
    }

    /** A one-reading chip: the value, then {@code unit} after it. */
    protected void drawChip(Painter painter, Box bounds, String unit, String value, int dotColor) {
        drawChip(painter, bounds, dotColor, List.of(new Part(value, unit == null ? "" : unit)));
    }
}
