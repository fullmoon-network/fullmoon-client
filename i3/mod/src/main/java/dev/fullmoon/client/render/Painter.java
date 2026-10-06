package dev.fullmoon.client.render;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * The only way anything in this client puts a solid on screen.
 *
 * <p>Everything routes through {@link ShapeRenderState}, so a rule, a card and a focus ring
 * are the same draw with different numbers and land in one batch.
 *
 * <p>The clip stack is duplicated rather than read: each render state has to report its own
 * {@code scissorArea()}, and the game's stack is private. {@link #pushClip} therefore keeps a
 * parallel stack with the same arithmetic the game uses — intersect with the current top,
 * empty rect when disjoint — and also calls through to the public
 * {@code enableScissor}/{@code disableScissor} so vanilla text drawn inside the same clip
 * obeys it.
 *
 * <p>{@link #opacity} multiplies the alpha of every colour submitted after it, shapes and text
 * alike, which is how a whole panel fades in as one thing. Item icons are drawn by the game
 * and cannot take it; a screen that fades keeps them back until the fade is mostly through.
 */
public final class Painter {
    private final GuiGraphicsExtractor gfx;
    private final Deque<ScreenRectangle> clips = new ArrayDeque<>();
    private float opacity = 1.0f;

    public Painter(GuiGraphicsExtractor gfx) {
        this.gfx = gfx;
    }

    public GuiGraphicsExtractor gfx() {
        return gfx;
    }

    public int width() {
        return gfx.guiWidth();
    }

    public int height() {
        return gfx.guiHeight();
    }

    /** Multiplies every alpha submitted from here on; 1 draws colours as their tokens say. */
    public void opacity(float value) {
        opacity = Math.clamp(value, 0.0f, 1.0f);
    }

    public float opacity() {
        return opacity;
    }

    /** A colour with this painter's opacity applied, for the game's own text call. */
    public int tint(int color) {
        return opacity >= 1.0f ? color : Rgb.scaleAlpha(color, opacity);
    }

    /**
     * Whether a row of cells that begins at GUI x {@code left} and is {@code cell} wide has every
     * edge on a whole screen pixel under the current pose and GUI scale. A pose that turns or skews
     * is never on one.
     */
    public boolean onWholePixels(float left, float cell) {
        Matrix3x2fc pose = gfx.pose();
        if (pose.m01() != 0.0f || pose.m10() != 0.0f) {
            return false;
        }
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        return PixelArt.onWholePixels((left * pose.m00() + pose.m20()) * scale, cell * pose.m00() * scale);
    }

    /** Fills a rect. */
    public void fill(float x, float y, float w, float h, int color) {
        fill(x, y, w, h, 0, color);
    }

    /** Fills a rect with rounded corners; {@code radius} is clamped to the shorter side. */
    public void fill(float x, float y, float w, float h, float radius, int color) {
        shape(x, y, w, h, radius, 0.0f, color);
    }

    /**
     * Fills a rect whose colour runs from {@code top} to {@code bottom}. Moonlight falls from
     * above, so a raised surface is lit along its top edge and settles into its ground.
     */
    public void fillGradient(float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0.0f || h <= 0.0f) {
            return;
        }
        float hx = w * 0.5f;
        float hy = h * 0.5f;
        submit(x + hx, y + hy, hx, hy, 0.0f, 0.0f, top, bottom);
    }

    /**
     * Fills a rect whose colour runs from {@code left} to {@code right}: the vertical gradient
     * turned a quarter on, since a shape carries one colour per end and only along its height.
     */
    public void fillGradientAcross(float x, float y, float w, float h, int left, int right) {
        if (w <= 0.0f || h <= 0.0f) {
            return;
        }
        gfx.pose().pushMatrix();
        gfx.pose().translate(x + w, y);
        gfx.pose().rotate((float) (Math.PI / 2.0));
        fillGradient(0.0f, 0.0f, h, w, right, left);
        gfx.pose().popMatrix();
    }

    /** Strokes the inside edge of a rect. A 1px stroke on integer coords lands on one row. */
    public void border(float x, float y, float w, float h, float radius, float thickness, int color) {
        if (thickness > 0.0f) {
            shape(x, y, w, h, radius, thickness, color);
        }
    }

    /** A horizontal rule, one pixel tall whatever the GUI scale. */
    public void hRule(float x, float y, float w, int color) {
        fill(x, y, w, 1.0f, 0, color);
    }

    /** A vertical rule, one pixel wide whatever the GUI scale. */
    public void vRule(float x, float y, float h, int color) {
        fill(x, y, 1.0f, h, 0, color);
    }

    /** A filled circle of radius {@code r} about ({@code cx}, {@code cy}). */
    public void dot(float cx, float cy, float r, int color) {
        submit(cx, cy, r, r, r, 0.0f, color);
    }

    /** A circular outline, the stroke growing inwards from {@code r}. */
    public void ring(float cx, float cy, float r, float thickness, int color) {
        submit(cx, cy, r, r, r, thickness, color);
    }

    /**
     * A square turned a quarter-turn onto its point, {@code half} from centre to each vertex.
     * {@code thickness <= 0} fills it.
     */
    public void diamond(float cx, float cy, float half, float thickness, int color) {
        float side = half * (float) Math.sqrt(0.5);
        gfx.pose().pushMatrix();
        gfx.pose().translate(cx, cy);
        gfx.pose().rotate((float) (Math.PI / 4.0));
        submit(0.0f, 0.0f, side, side, 0.0f, thickness, color, color);
        gfx.pose().popMatrix();
    }

    /** A straight stroke between two points, {@code thickness} wide, with rounded ends. */
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0f) {
            dot((x1 + x2) / 2, (y1 + y2) / 2, thickness / 2, color);
            return;
        }
        gfx.pose().pushMatrix();
        gfx.pose().translate((x1 + x2) / 2, (y1 + y2) / 2);
        gfx.pose().rotate((float) Math.atan2(dy, dx));
        submit(0.0f, 0.0f, length / 2 + thickness / 2, thickness / 2, thickness / 2, 0.0f, color, color);
        gfx.pose().popMatrix();
    }

    /** ‹ or ›: a chevron {@code size} tall about ({@code cx}, {@code cy}), its point on the left when {@code left}. */
    public void chevron(float cx, float cy, float size, float thickness, int color, boolean left) {
        float half = size / 2;
        float reach = size * 0.36f;
        float tip = left ? cx - reach / 2 : cx + reach / 2;
        float back = left ? cx + reach / 2 : cx - reach / 2;
        line(back, cy - half, tip, cy, thickness, color);
        line(tip, cy, back, cy + half, thickness, color);
    }

    /** ✕: two diagonals across a {@code size} square about the centre. */
    public void cross(float cx, float cy, float size, float thickness, int color) {
        float half = size / 2;
        line(cx - half, cy - half, cx + half, cy + half, thickness, color);
        line(cx + half, cy - half, cx - half, cy + half, thickness, color);
    }

    /** ✔: a short stroke down to the foot, a long one up to the right, in a {@code size} square. */
    public void check(float cx, float cy, float size, float thickness, int color) {
        float half = size / 2;
        float footX = cx - half * 0.25f;
        float footY = cy + half * 0.7f;
        line(cx - half, cy + half * 0.05f, footX, footY, thickness, color);
        line(footX, footY, cx + half, cy - half * 0.75f, thickness, color);
    }

    /**
     * A moon of radius {@code r} with {@code lit} of its disc lit: the unlit face as a dot, the
     * lit face on top of it through the moon pipeline.
     */
    public void moon(float cx, float cy, float r, float lit, boolean waxing, int litColor, int shadowColor) {
        dot(cx, cy, r, shadowColor);
        if (lit <= 0.0f) {
            return;
        }
        gfx.guiRenderState.addGuiElement(new MoonRenderState(
            new Matrix3x2f(gfx.pose()), cx, cy, r, lit, waxing, tint(litColor), clips.peekLast()));
    }

    private void shape(float x, float y, float w, float h, float radius, float thickness, int color) {
        if (w <= 0.0f || h <= 0.0f) {
            return;
        }
        float hx = w * 0.5f;
        float hy = h * 0.5f;
        submit(x + hx, y + hy, hx, hy, Math.min(radius, Math.min(hx, hy)), thickness, color, color);
    }

    private void submit(float cx, float cy, float hx, float hy, float radius, float thickness, int color) {
        submit(cx, cy, hx, hy, radius, thickness, color, color);
    }

    private void submit(
        float cx, float cy, float hx, float hy, float radius, float thickness, int top, int bottom
    ) {
        if (tint(top) >>> 24 == 0 && tint(bottom) >>> 24 == 0) {
            return;
        }
        // The pose is a live stack; a render state outlives this call, so it gets a copy.
        gfx.guiRenderState.addGuiElement(new ShapeRenderState(
            new Matrix3x2f(gfx.pose()), cx, cy, hx, hy, radius, thickness, tint(top), tint(bottom),
            clips.peekLast()));
    }

    /** Clips subsequent draws — this painter's and the game's text — to a rect. */
    public void pushClip(int x, int y, int w, int h) {
        ScreenRectangle rect = new ScreenRectangle(x, y, w, h).transformAxisAligned(gfx.pose());
        ScreenRectangle current = clips.peekLast();
        if (current != null) {
            ScreenRectangle clipped = rect.intersection(current);
            rect = clipped != null ? clipped : ScreenRectangle.empty();
        }
        clips.addLast(rect);
        gfx.enableScissor(x, y, x + w, y + h);
    }

    public void popClip() {
        clips.pollLast();
        gfx.disableScissor();
    }

    /**
     * Starts a new stratum with the scene behind it blurred. This is the game's own backdrop
     * pass; a hand-rolled blur chain would duplicate it a frame later and out of sync.
     */
    public void blurredStratum() {
        gfx.nextStratum();
        gfx.blurBeforeThisStratum();
    }
}
