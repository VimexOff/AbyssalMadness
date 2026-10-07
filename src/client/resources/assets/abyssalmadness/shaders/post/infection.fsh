#version 330

// The infection, drawn entirely in the shader: rebuild each pixel's world position
// from depth, test it against an infected body, run ridged noise (|fbm| gives thin
// veins), raise the front from the feet up, add the glow.
//
// The pattern is computed RELATIVE to the player, so it does not swim with the camera
// and does not lose precision at large world coordinates.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;
// Outline buffer: the engine has already filled the infected in solid.
uniform sampler2D InOutlineSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 InDepthSize;
    vec2 InOutlineSize;
};

layout(std140) uniform InfectionConfig {
// Inverse view-projection, laid out in columns.
    vec4 InvVP0;
    vec4 InvVP1;
    vec4 InvVP2;
    vec4 InvVP3;
// xyz - position relative to the camera, |w| - infection 0..1. The SIGN of w picks
// the palette: positive cold, negative warm. It rides on the sign because the vec4 is
// full. An array rather than named slots: a loop cannot walk those.
    vec4 Players[100];
// x - time in seconds, y - how many infected were sent
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

const float BODY_HEIGHT = 1.95;
// The silhouette mask holds the body's edge, so the box is only a coarse filter and
// is taken wide: an arm mid-swing has to land inside it.
const float PICK_HALF = 2.2;
const float PICK_TOP = 3.0;
const float PICK_BOTTOM = -0.6;
// Cold and slightly blue, so it reads as alien light rather than a torch.
const vec3 INFECTION_COLOUR = vec3(0.72, 0.86, 1.0);
// The body is painted over: none of the real skin shows through.
const vec3 INFECTION_BASE = vec3(0.86, 0.91, 1.0);
// Warm palette. Green stays ABOVE blue or it comes out pink - mirroring the cold
// numbers does not work.
const vec3 WARM_COLOUR = vec3(1.0, 0.84, 0.60);
const vec3 WARM_BASE = vec3(1.0, 0.92, 0.76);

float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i + vec3(0, 0, 0)), hash13(i + vec3(1, 0, 0)), f.x),
            mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
            mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * noise3(p);
        p *= 2.03;
        amp *= 0.5;
    }
    return sum;
}

// The front climbs from the feet: below it the body is taken, above it is clean. Led
// past the crown (level * 1.2), or at 100% the head stays half transparent.
float taken(vec3 d, float level) {
    float heightFrac = clamp(d.y / BODY_HEIGHT, 0.0, 1.0);
    float reachTop = level * 1.2;
    return smoothstep(reachTop + 0.10, reachTop - 0.10, heightFrac);
}

// Ridged noise: thin glowing veins instead of blotches.
float veins(vec3 p, float t) {
    float n = fbm3(p * 1.7 + vec3(0.0, t * 0.18, 0.0));
    float ridge = 1.0 - abs(n * 2.0 - 1.0);
    return pow(clamp(ridge, 0.0, 1.0), 5.0);
}

// A claim on the pixel: horizontal distance to the body's axis, or -1 if not its own.
// A pixel on a body sits a third of a block from its own axis, metres from anyone
// else's.
float claim(vec3 pos, vec4 player) {
    float level = abs(player.w);
    if (level <= 0.002) {
        return -1.0;
    }

    vec3 d = pos - player.xyz;

    // Vanilla fills the outline buffer WITHOUT a depth test, or ordinary glowing
    // would not show through walls. Without this check the infected read through them
    // too: a pixel clearly nearer than the body's middle has something in front.
    float bodyDist = length(player.xyz + vec3(0.0, 1.0, 0.0));
    if (length(pos) < bodyDist - 1.1) {
        return -1.0;
    }
    // Coarse filter only. A tight box clipped a lowered head and an outstretched leg.
    if (d.y < PICK_BOTTOM || d.y > PICK_TOP
            || abs(d.x) > PICK_HALF || abs(d.z) > PICK_HALF) {
        return -1.0;
    }
    // Above the front the body is clean, so the pixel is not its own.
    if (taken(d, level) <= 0.0) {
        return -1.0;
    }
    return length(d.xz);
}

// One infected body's contribution: cover paints the body, glow adds light on top.
// Called only for the pixel's owner, so claim's filters are not repeated here.
void addPlayer(vec3 pos, vec4 player, float t, inout float cover, inout vec3 coverColour, inout vec3 glow) {
    float level = abs(player.w);
    if (level <= 0.002) {
        return;
    }
    bool warm = player.w < 0.0;
    vec3 baseColour = warm ? WARM_BASE : INFECTION_BASE;
    vec3 veinColour = warm ? WARM_COLOUR : INFECTION_COLOUR;

    vec3 d = pos - player.xyz;          // relative to the feet
    float grip = taken(d, level);

    // Three-dimensional noise clouds, smooth at any distance. A model layer could
    // only give a flat wash.
    float grain = fbm3(d * 6.0 + vec3(0.0, t * 0.22, 0.0));
    float fine = fbm3(d * 16.0 - vec3(0.0, t * 0.35, 0.0));
    float lift = 0.42 + 0.62 * grain + 0.26 * fine;
    cover = grip;
    coverColour = baseColour * lift;

    // Veins on top, weak: any more and the body collapses into a flat white cutout.
    float v = veins(d * 4.5, t);
    glow += veinColour * grip * v * (0.18 + 0.22 * level);
}

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;
    float depth = texture(InDepthSampler, texCoord).r;

    // Sky: nothing to rebuild. Alpha 0 keeps these pixels out of the glow.
    if (depth >= 1.0) {
        fragColor = vec4(scene, 0.0);
        return;
    }

    mat4 invVP = mat4(InvVP0, InvVP1, InvVP2, InvVP3);
    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 restored = invVP * clip;
    vec3 pos = restored.xyz / restored.w;   // relative to the camera

    float t = Params.x;
    int count = int(Params.y + 0.5);

    // The infected are flagged as glowing and the engine has already filled them into
    // the outline buffer. Alpha is non-zero on exactly the model's pixels - no floor,
    // no wall behind the player.
    float mask = texture(InOutlineSampler, texCoord).a;
    if (mask <= 0.0) {
        fragColor = vec4(scene, 0.0);
        return;
    }

    vec3 bodyGlow = vec3(0.0);
    float cover = 0.0;
    vec3 coverColour = INFECTION_BASE;
    // The pixel goes to ONE body, the nearest to its own axis. The filter box is
    // wider than a person, so for two standing together a pixel falls inside both and
    // whoever came first in the array used to win: both were painted the same colour,
    // and which one changed as the camera turned.
    //
    // The loop is expensive but only runs on silhouette pixels - a fraction of a
    // percent of the frame.
    int owner = -1;
    float best = 1.0e9;
    for (int i = 0; i < count; i++) {
        float reachOf = claim(pos, Players[i]);
        if (reachOf >= 0.0 && reachOf < best) {
            best = reachOf;
            owner = i;
        }
    }
    if (owner >= 0) {
        addPlayer(pos, Players[owner], t, cover, coverColour, bodyGlow);
    }

    // The light has to obey fog rather than punch through it, so the contribution
    // falls off with distance. That works with FogTools or any other fog without
    // knowing anything about them.
    float dist = length(pos);
    float reach = exp(-dist * 0.022);

    // The body is painted with the noise clouds, the veins go on top.
    cover *= mask;
    vec3 colour = mix(scene, coverColour, cover);
    colour += bodyGlow * mask * reach;

    // Alpha carries how much infected light is here. The next passes blur exactly
    // these pixels and add the result back, and that is the halo.
    //
    // The halo is no longer a circle drawn around the projected point. That circle
    // needed a depth test to hide it, and the test drew a hard line across the floor:
    // equal distance to the camera on a plane is an ellipse, clearly visible as a dark
    // oval underfoot. A blur has no world position and no depth test, so it has no
    // edge - and it takes the shape of the silhouette for free.
    fragColor = vec4(colour, cover * reach);
}
