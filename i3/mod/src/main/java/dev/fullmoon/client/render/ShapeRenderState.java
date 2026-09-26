package dev.fullmoon.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import org.joml.Matrix3x2fc;

/**
 * One antialiased shape, queued into the GUI render state.
 *
 * <p>The quad is one pixel larger than the shape on every side. The distance field needs
 * that margin to fade out, and {@link #bounds()} reports the padded rect so the renderer's
 * own culling does not clip the fade.
 *
 * @param cx        shape centre, pre-pose GUI px
 * @param hx        shape half extent
 * @param radius    corner radius, clamped to the smaller half extent
 * @param thickness ring width, growing inwards from the edge; {@code <= 0} fills
 * @param color     packed 0xAARRGGBB along the top edge
 * @param colorBottom packed 0xAARRGGBB along the bottom edge; equal to {@code color} for a flat fill
 */
public record ShapeRenderState(
    Matrix3x2fc pose,
    float cx, float cy,
    float hx, float hy,
    float radius, float thickness,
    int color, int colorBottom,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements GuiElementRenderState {
    private static final float PAD = 1.0f;
    private static final float FIXED = 16.0f;

    public ShapeRenderState(
        Matrix3x2fc pose,
        float cx, float cy,
        float hx, float hy,
        float radius, float thickness,
        int color, int colorBottom,
        ScreenRectangle scissorArea
    ) {
        this(pose, cx, cy, hx, hy, radius, thickness, color, colorBottom, scissorArea,
            paddedBounds(cx, cy, hx, hy, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        // Winding matches ColoredRectangleRenderState: top-left, bottom-left, bottom-right,
        // top-right. QUADS mode indexes on that order.
        emit(consumer, -1.0f, -1.0f);
        emit(consumer, -1.0f, 1.0f);
        emit(consumer, 1.0f, 1.0f);
        emit(consumer, 1.0f, -1.0f);
    }

    private void emit(VertexConsumer consumer, float sx, float sy) {
        float ox = sx * (hx + PAD);
        float oy = sy * (hy + PAD);
        consumer.addVertexWith2DPose(pose, cx + ox, cy + oy)
            .setColor(sy < 0.0f ? color : colorBottom)
            .setUv(ox, oy)
            .setUv1(Math.round(hx * FIXED), Math.round(hy * FIXED))
            .setUv2(Math.round(radius * FIXED), Math.round(thickness * FIXED));
    }

    @Override
    public RenderPipeline pipeline() {
        return ShapePipeline.PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    static ScreenRectangle paddedBounds(
        float cx, float cy, float hx, float hy, Matrix3x2fc pose, ScreenRectangle scissor
    ) {
        int x0 = (int) Math.floor(cx - hx - PAD);
        int y0 = (int) Math.floor(cy - hy - PAD);
        int x1 = (int) Math.ceil(cx + hx + PAD);
        int y1 = (int) Math.ceil(cy + hy + PAD);
        ScreenRectangle rect = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(rect) : rect;
    }
}
