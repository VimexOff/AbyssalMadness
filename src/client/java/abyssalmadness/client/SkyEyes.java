package abyssalmadness.client;

import abyssalmadness.AbyssalMadness;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Eye-shaped skybox stars for the Sleeping Stars event. When it wakes they fade in
// one by one, when it sleeps they fade out the same way. Each star has a fixed
// camera-anchored position, only its brightness changes. Only drawn in the Valley.
public final class SkyEyes {
    private static final Identifier EYE_TEX = Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "textures/effect/eye.png");
    private static final ResourceKey<Biome> VALLEY = ResourceKey.create(
            Registries.BIOME, Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "sleeping_stars_valley"));

    private static final int STAR_COUNT = 64;
    private static final long STAGGER_MS = 90;   // gap between successive stars opening/closing
    private static final float FADE_MS = 500f;   // fade time of a single star

    private SkyEyes() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(SkyEyes::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        if (!level.getBiome(player.blockPosition()).is(VALLEY)) {
            return;
        }

        boolean awake = StarClientState.isAwake();
        long changed = StarClientState.changedAtMs();
        long now = System.currentTimeMillis();
        // fully closed and the fade-out is done, nothing to draw
        if (!awake && now - changed > STAR_COUNT * STAGGER_MS + FADE_MS) {
            return;
        }

        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) {
            return;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Quaternionf rot = cam.rotation();
        Vector3f right = rot.transform(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f());
        Vector3f up = rot.transform(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());

        VertexConsumer buffer = consumers.getBuffer(RenderTypes.entityTranslucent(EYE_TEX));
        PoseStack pose = ctx.matrices();

        for (int i = 0; i < STAR_COUNT; i++) {
            float progress = starAlpha(awake, now - changed, i);
            if (progress <= 0.01f) {
                continue;
            }

            // fixed position on the dome, no time term so the star never moves
            double az = frac(hash(i * 2654435761L + 1)) * Math.PI * 2.0;
            double el = Math.toRadians(6.0 + frac(hash(i * 40503L + 7)) * 80.0); // 6..86 deg
            double dist = 90.0 + frac(hash(i * 668265263L + 13)) * 70.0;         // 90..160
            float size = (float) dist * (0.05f + (float) frac(hash(i * 374761393L + 17)) * 0.06f);

            double hxz = Math.cos(el) * dist;
            double ox = Math.cos(az) * hxz;
            double oy = Math.sin(el) * dist;
            double oz = Math.sin(az) * hxz;

            int alpha = (int) (235 * progress);
            pose.pushPose();
            pose.translate(ox, oy, oz);
            emitEye(pose.last(), buffer, right, up, size, alpha);
            pose.popPose();
        }
    }

    // 0..1 brightness of a star, fading in or out staggered by index
    private static float starAlpha(boolean awake, long since, int i) {
        float t = (since - i * STAGGER_MS) / FADE_MS;
        return awake ? Mth.clamp(t, 0f, 1f) : Mth.clamp(1f - t, 0f, 1f);
    }

    private static void emitEye(PoseStack.Pose pose, VertexConsumer c, Vector3f right, Vector3f up, float s, int a) {
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        float rx = right.x() * s, ry = right.y() * s, rz = right.z() * s;
        float ux = up.x() * s, uy = up.y() * s, uz = up.z() * s;
        vert(c, pose, -rx - ux, -ry - uy, -rz - uz, 0.0f, 1.0f, a, light, overlay);
        vert(c, pose, -rx + ux, -ry + uy, -rz + uz, 0.0f, 0.0f, a, light, overlay);
        vert(c, pose, rx + ux, ry + uy, rz + uz, 1.0f, 0.0f, a, light, overlay);
        vert(c, pose, rx - ux, ry - uy, rz - uz, 1.0f, 1.0f, a, light, overlay);
    }

    private static void vert(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z,
                             float u, float v, int a, int light, int overlay) {
        c.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    private static double frac(long h) {
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static long hash(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x & Long.MAX_VALUE;
    }
}
