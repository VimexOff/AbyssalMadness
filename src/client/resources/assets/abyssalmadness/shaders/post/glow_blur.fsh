#version 330

// Half a gaussian blur. Run four times - horizontal, vertical, then both again
// wider: two passes give a soft long tail one cannot, at a quarter of the cost of a
// real circular kernel.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
// xy - direction, z - take alpha as the brightness mask, w - step in screen fractions
    vec4 BlurDir;
};

in vec2 texCoord;
out vec4 fragColor;

const float WEIGHTS[5] = float[5](0.227027, 0.194594, 0.121621, 0.054054, 0.016216);

// The first pass pulls the light out of alpha; the rest work on the light itself.
vec3 fetch(vec2 uv, float useAlpha) {
    vec4 c = texture(InSampler, uv);
    return useAlpha > 0.5 ? c.rgb * c.a : c.rgb;
}

void main() {
    // Step is in fractions of the SCREEN, not pixels, so the halo is the same at any
    // resolution. Squeezed horizontally by the aspect ratio, or it comes out an oval
    // on a wide monitor.
    vec2 stride = BlurDir.xy * vec2(InSize.y / max(InSize.x, 1.0), 1.0) * BlurDir.w;

    vec3 sum = fetch(texCoord, BlurDir.z) * WEIGHTS[0];
    for (int i = 1; i < 5; i++) {
        vec2 o = stride * float(i);
        sum += (fetch(texCoord + o, BlurDir.z) + fetch(texCoord - o, BlurDir.z)) * WEIGHTS[i];
    }

    fragColor = vec4(sum, 1.0);
}
