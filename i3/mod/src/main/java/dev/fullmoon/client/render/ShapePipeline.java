package dev.fullmoon.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.resources.Identifier;

/**
 * The single pipeline behind every solid this client draws.
 *
 * <p>A rect, a rounded rect, a 1px rule, a ring and a circle are one signed-distance
 * function with different parameters, so they are one pipeline and therefore one batch —
 * {@code GuiRenderer} groups draws by pipeline, and a UI that reaches for a second one per
 * shape kind pays a state change per widget.
 *
 * <p>{@code VertexConsumer} can only write the standard elements, so the shape parameters
 * ride on them: UV0 carries the fragment's offset from the shape centre in GUI px, UV1 the
 * half extent and UV2 the corner radius and ring thickness. UV1 and UV2 are non-normalised
 * shorts, which arrive in GLSL as {@code ivec2}; at 1/16 px they cover ±2048 px, past the
 * largest GUI this game will lay out.
 */
public final class ShapePipeline {
    /** 32 B/vertex. Attribute names have to match the {@code in} declarations in shape.vsh. */
    public static final VertexFormat FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("UV2", VertexFormatElement.UV2)
        .build();

    /**
     * Built but never registered: {@code RenderPipelines.register} is private, and the
     * pipeline is compiled lazily the first time {@code GuiRenderer} draws with it, so
     * registration buys nothing a mod can use.
     */
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("fullmoon", "pipeline/shape"))
        .withVertexShader(Identifier.fromNamespaceAndPath("fullmoon", "core/shape"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("fullmoon", "core/shape"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
        .build();

    /**
     * The lit face of a moon phase. It shares the vertex stage and format with {@link #PIPELINE}
     * and only swaps the fragment's distance: a disc cut by the terminator ellipse instead of a
     * rounded box, so a phase is one quad on top of a {@link Painter#dot} for the unlit face.
     */
    public static final RenderPipeline MOON = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("fullmoon", "pipeline/moon"))
        .withVertexShader(Identifier.fromNamespaceAndPath("fullmoon", "core/shape"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("fullmoon", "core/moon"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
        .build();

    private ShapePipeline() {}
}
