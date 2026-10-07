#version 330

// Adds the blurred light back onto the frame the body was painted into.

uniform sampler2D InSampler;
uniform sampler2D InGlowSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 InGlowSize;
};

layout(std140) uniform GlowConfig {
// x - glow strength
    vec4 GlowParams;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Alpha is the body mask, written by the infection pass. Below it decides where
    // to save the hue and where to save the brightness.
    vec4 src = texture(InSampler, texCoord);
    vec3 glow = texture(InGlowSampler, texCoord).rgb;
    vec3 lit = src.rgb + glow * GlowParams.x;

    // Brought down to 1 by DIVIDING by the brightest channel rather than clipping
    // each one. Clipping eats the hue: the body is lit more than twice over, so all
    // three channels hit 1 and cold and warm came out identically white. Dividing
    // leaves the ratio between channels intact.
    //
    // Only on the body, though. The halo outside it has no colour of its own, and
    // dividing costs it dearly - every channel drops under the brightest one and the
    // white blaze around the figure turns into a dull smudge. Out there, clip.
    float peak = max(max(lit.r, lit.g), lit.b);
    if (peak > 1.0) {
        lit = mix(lit, lit / peak, src.a);
    }
    fragColor = vec4(lit, 1.0);
}
