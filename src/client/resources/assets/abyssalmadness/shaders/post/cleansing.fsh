#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform CleansingConfig {
    float Strength;
};

out vec4 fragColor;

// Screen-edge chromatic aberration with a slight barrel bulge, plus the light going
// out of the world, for the Cleansing. Everything is weighted by distance from the
// centre, so the middle of the screen stays readable while the edges come apart.
void main() {
    vec2 centered = texCoord - 0.5;

    // Aspect-correct the radius, or the falloff becomes an ellipse on wide monitors.
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 shaped = vec2(centered.x * aspect, centered.y);
    float radius = length(shaped) / length(vec2(0.5 * aspect, 0.5));

    // Nothing at all until well out from the centre, then a fast ramp to the corners.
    float edge = smoothstep(0.35, 1.05, radius);
    float amount = edge * Strength;

    // Fish eye: pull the sampling inwards so the picture bulges. Sampling inwards
    // (never outwards) keeps every tap inside the frame, so no smeared clamp at the
    // border.
    float bulge = 1.0 - 0.20 * Strength * radius * radius;
    vec2 warped = 0.5 + centered * bulge;

    // Split the channels along the radial direction: red flung out, blue pulled in.
    // That is what real lens aberration does, and it reads as pressure from outside.
    vec2 dir = radius > 0.0001 ? normalize(centered) : vec2(0.0);
    vec2 offset = dir * amount * 0.055;

    vec3 plain = texture(InSampler, warped).rgb;
    vec3 split = vec3(
        texture(InSampler, warped + offset).r,
        plain.g,
        texture(InSampler, warped - offset * 0.85).b
    );

    // The colour fringe on its own, before anything darkens it.
    vec3 fringe = split - plain;

    // The light goes out of the world as it closes in, leaving only what actually
    // emits: torches, lava, glowing crystals, the sky.
    //
    // This is a gamma-style curve rather than a brightness threshold. A threshold
    // splits the picture in two - a torch flame survives while the wall it lights
    // drops straight to black, which looks broken. Raising luminance to a power
    // scales every brightness continuously instead, so a torch keeps its pool of
    // light around it, just dimmer and tighter, and the falloff stays smooth.
    float luma = dot(split, vec3(0.2126, 0.7152, 0.0722));
    float exponent = 2.0 * Strength;                       // 0 at rest, so no effect
    float falloff = pow(clamp(luma, 0.0, 1.0), exponent);

    vec3 colour = split * max(falloff, 0.015);

    // Put the fringe back on top of the darkened image. Without this the aberration
    // is multiplied down along with everything else and vanishes exactly when the
    // world goes black - which is when it is supposed to be at its worst.
    colour += fringe * amount * 2.5;

    // A touch of darkening right at the rim, so the edges feel like they are being
    // crushed inwards rather than just recoloured.
    colour *= 1.0 - 0.30 * amount;

    fragColor = vec4(colour, 1.0);
}
