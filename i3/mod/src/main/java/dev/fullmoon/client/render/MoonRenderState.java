package dev.fullmoon.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import org.joml.Matrix3x2fc;

/**
 * The lit face of a moon, queued into the GUI render state on {@link ShapePipeline#MOON}.
 *
 * <p>The shape parameters ride on the same slots {@link ShapeRenderState} uses: UV1 is the
 * radius as a half extent, UV2.x the terminator coefficient and UV2.y which limb is lit. The
 * coefficient needs finer steps than the 1/16 px the shape slots carry, so it is written at
 * {@link #TERMINATOR_SCALE} per unit and moon.fsh divides it back out.
 *
 * @param lit       fraction of the disc that is lit, 0 new to 1 full
 * @param waxing    true when the lit limb is on the right, as a waxing moon seen from the north
 */
public record MoonRenderState(
    Matrix3x2fc pose,
    float cx, float cy,
    float r,
    float lit, boolean waxing,
    int color,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements GuiElementRenderState {
    static final float TERMINATOR_SCALE = 1024.0f;
    private static final float PAD = 1.0f;
    private static final float FIXED = 16.0f;

    public MoonRenderState(
        Matrix3x2fc pose, float cx, float cy, float r, float lit, boolean waxing, int color,
        ScreenRectangle scissorArea
    ) {
        this(pose, cx, cy, r, lit, waxing, color, scissorArea,
            ShapeRenderState.paddedBounds(cx, cy, r, r, pose, scissorArea));
    }

    /**
     * The terminator is the ellipse {@code x = k·sqrt(r² − y²)} and the lit face is everything
     * past it on the lit limb's side, which covers exactly {@code (1 − k) / 2} of the disc — so
     * the coefficient is linear in the lit fraction.
     */
    static float terminator(float lit) {
        return 1.0f - 2.0f * Math.clamp(lit, 0.0f, 1.0f);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        emit(consumer, -1.0f, -1.0f);
        emit(consumer, -1.0f, 1.0f);
        emit(consumer, 1.0f, 1.0f);
        emit(consumer, 1.0f, -1.0f);
    }

    private void emit(VertexConsumer consumer, float sx, float sy) {
        float ox = sx * (r + PAD);
        float oy = sy * (r + PAD);
        consumer.addVertexWith2DPose(pose, cx + ox, cy + oy)
            .setColor(color)
            .setUv(ox, oy)
            .setUv1(Math.round(r * FIXED), Math.round(r * FIXED))
            .setUv2(Math.round(terminator(lit) * TERMINATOR_SCALE), waxing ? 1 : -1);
    }

    @Override
    public RenderPipeline pipeline() {
        return ShapePipeline.MOON;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
