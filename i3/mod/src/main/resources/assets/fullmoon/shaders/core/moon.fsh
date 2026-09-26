#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// The lit face of a moon phase, over shape.vsh. shapeHalf.x is the radius, shapeRadius carries
// the terminator coefficient k as k * TERMINATOR_SCALE / 16 (MoonRenderState, 1024), and the
// sign of shapeThickness says which limb is lit (positive: the right, a waxing moon).

in vec4 vertexColor;
in vec2 shapeLocal;
in vec2 shapeHalf;
in float shapeRadius;
in float shapeThickness;

out vec4 fragColor;

void main() {
    float r = shapeHalf.x;
    float k = shapeRadius * 16.0 / 1024.0;
    vec2 p = shapeLocal;
    if (shapeThickness < 0.0) {
        p.x = -p.x;
    }
    float disc = length(p) - r;
    // Past the terminator ellipse x = k * sqrt(r^2 - y^2) on the lit side. Not a true distance,
    // but its gradient is ~1 wherever the edge is visible, which is all the antialias needs.
    float terminator = k * sqrt(max(r * r - p.y * p.y, 0.0)) - p.x;
    float d = max(disc, terminator);
    float coverage = clamp(0.5 - d / max(fwidth(d), 1e-5), 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
