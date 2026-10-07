#version 330

// The beacon flame: a bright point with a halo, computed in screen space.
//
// Not geometry and not a block. Either would be culled by draw distance and eaten by
// fog within a couple of hundred blocks. Here it lives in the frame for as long as the
// beacon is on screen, from anywhere in the level, because the halo has a floor on its
// size and the point never collapses to nothing.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 InDepthSize;
};

layout(std140) uniform BeaconConfig {
// xy - beacon on screen (0..1), z - its depth for the occlusion test, w - brightness
    vec4 Beacon;
// x - time in seconds, y - distance in blocks, z - whether it is on screen at all
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

// Apparent size is world size over distance, so the flame shrinks like a real one.
// The floor keeps it from vanishing: a kilometre out it should be a bright spark, not
// an empty patch of screen.
const float HALO_WORLD = 14.0;
const float CORE_WORLD = 2.5;
const float FOV_SPAN = 1.4;          // roughly how many radians fit across the screen height
const float MIN_HALO = 0.010;
const float MIN_CORE = 0.0025;
// Pure white: brighter than any tint at the same strength, and it argues with nothing
// on screen - the sand is warm, the crystals are cold, this is just white.
const vec3 LAMP = vec3(1.0, 1.0, 1.0);
// Depth samples around the centre. Occlusion is ONE number for the whole halo rather
// than per pixel: per pixel would draw a hard line across the floor wherever the depth
// changes, this way the light just fades as a whole.
const int TAPS = 8;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    return fract(p * (p + p));
}

// A real lamp never burns perfectly steady.
float flicker(float t) {
    float a = hash11(floor(t * 4.0));
    float b = hash11(floor(t * 4.0) + 1.0);
    float f = fract(t * 4.0);
    f = f * f * (3.0 - 2.0 * f);
    return 0.88 + 0.12 * mix(a, b, f);
}

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;
    float level = Beacon.w;
    if (level <= 0.001 || Params.z < 0.5) {
        fragColor = vec4(scene, 0.0);
        return;
    }

    float dist = max(Params.y, 1.0);
    float halo = max(HALO_WORLD / dist / FOV_SPAN, MIN_HALO);
    float core = max(CORE_WORLD / dist / FOV_SPAN, MIN_CORE);

    // A circle, not an oval: stretch the horizontal distance by the aspect ratio.
    vec2 d = texCoord - Beacon.xy;
    d.x *= OutSize.x / max(OutSize.y, 1.0);
    float r = length(d);

    // Is the flame occluded. Several samples in a ring around the centre, so an edge
    // crossing it fades the light out instead of switching it off for a frame.
    float open = 0.0;
    for (int i = 0; i < TAPS; i++) {
        float a = 6.2831853 * float(i) / float(TAPS);
        vec2 uv = clamp(Beacon.xy + vec2(cos(a), sin(a)) * core * 0.6, 0.0, 1.0);
        open += texture(InDepthSampler, uv).r >= Beacon.z ? 1.0 : 0.0;
    }
    open /= float(TAPS);
    if (open <= 0.0) {
        fragColor = vec4(scene, 0.0);
        return;
    }

    float lamp = exp(-r / core * 2.5);            // the lamp itself
    float glow = exp(-r / halo * 2.0) * 0.55;     // the air around it

    // The horizontal streak is what makes the shot read as filmed rather than
    // rendered: it is how a lens smears a bright light.
    vec2 s = vec2(d.x / (halo * 7.0), d.y / (halo * 0.30));
    float streak = exp(-length(s) * 2.0) * 0.30;

    // A slow breath on top of the fine flicker.
    float pulse = 0.85 + 0.15 * sin(Params.x * 1.1);
    float strength = (lamp * 1.7 + glow + streak) * level * open * pulse * flicker(Params.x);

    // Alpha feeds the blur pass, which is where the soft bloom comes from.
    vec3 colour = scene + LAMP * strength;
    fragColor = vec4(colour, clamp(strength, 0.0, 1.0));
}
