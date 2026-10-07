package abyssalmadness.client;

import abyssalmadness.AbyssalMadness;
import abyssalmadness.client.mixin.CameraAccessor;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Drives the infection pass. The pattern is drawn in screen space rather than baked
// into the skin, so it is smooth instead of a grid of 64x64 texels.
public final class InfectionShader {
    private static final Identifier EFFECT = AbyssalMadness.id("infection");
    private static final String BLOCK = "InfectionConfig";
    /** As many as the uniform block carries: 100 vec4 is 1.6 KB of the guaranteed 16. */
    private static final int MAX_PLAYERS = 100;
    private static final double MAX_DISTANCE = 96.0;

    /** Broken once, never retried, so a failed pass does not spam the log each frame. */
    private static boolean broken;

    private InfectionShader() {
    }

    public static void addToFrame(Minecraft client, FrameGraphBuilder frameGraph,
                                  LevelTargetBundle targets, Matrix4f modelView, Matrix4f projection) {
        if (broken || client.level == null || !InfectionClientState.any()) {
            return;
        }
        // No outline buffer means no mask, and the pass would paint across whole
        // blocks. Better to draw nothing.
        if (targets.entityOutline == null) {
            return;
        }
        Vec3 cam = ((CameraAccessor) client.gameRenderer.getMainCamera()).abyssalmadness$getPosition();
        List<Entity> infected = nearestInfected(client, cam);
        if (infected.isEmpty()) {
            return;
        }

        // OUTLINE_TARGETS, not MAIN_TARGETS: the chain has to declare the outline
        // buffer as an external target or it will not build with that sampler.
        PostChain chain = client.getShaderManager().getPostChain(EFFECT, LevelTargetBundle.OUTLINE_TARGETS);
        if (chain == null) {
            return;
        }

        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        try {
            Matrix4f invViewProj = PostUniforms.inverseViewProjection(modelView, projection);
            float time = (System.currentTimeMillis() % 600000L) / 1000.0F;

            // Camera-relative positions, one flat array. Slots past the end stay zero,
            // which the shader reads as no infection and skips.
            float[] rel = new float[MAX_PLAYERS * 4];
            for (int i = 0; i < infected.size(); i++) {
                Entity body = infected.get(i);
                // Interpolated so the pattern does not stutter between ticks.
                double px = Mth.lerp(partial, body.xo, body.getX());
                double py = Mth.lerp(partial, body.yo, body.getY());
                double pz = Mth.lerp(partial, body.zo, body.getZ());
                int at = i * 4;
                rel[at] = (float) (px - cam.x);
                rel[at + 1] = (float) (py - cam.y);
                rel[at + 2] = (float) (pz - cam.z);
                // Sign picks the palette: positive cold, negative warm. It rides on the
                // sign because the vec4 is full and a second array would cost 1.6 KB to
                // carry one bit. Level is never zero here, so the sign always reads.
                float level = InfectionClientState.get(body.getUUID()) / 100.0F;
                boolean warm = InfectionClientState.isWarm(body.getUUID());
                rel[at + 3] = warm ? -level : level;
            }

            boolean written = PostUniforms.write(chain, BLOCK, builder -> {
                PostUniforms.putMatrix(builder, invViewProj);
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    int at = i * 4;
                    builder.putVec4(rel[at], rel[at + 1], rel[at + 2], rel[at + 3]);
                }
                builder.putVec4(time, infected.size(), 0.0F, 1.0F);
            });
            if (!written) {
                return;
            }
            // addToFrame, not process: process builds a bundle holding only main, so a
            // chain naming any other target dies on "Missing target".
            RenderTarget main = client.getMainRenderTarget();
            chain.addToFrame(frameGraph, main.width, main.height, targets);
        } catch (Exception e) {
            broken = true;
            AbyssalMadness.LOGGER.error("infection shader disabled: pass failed", e);
        }
    }

    // Any entity, not just players: hollows are mobs carrying an infection level.
    private static List<Entity> nearestInfected(Minecraft client, Vec3 cam) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isInvisible() || InfectionClientState.get(entity.getUUID()) <= 0) {
                continue;
            }
            if (entity.position().distanceTo(cam) > MAX_DISTANCE) {
                continue;
            }
            found.add(entity);
        }
        // With a crowd somebody has to be dropped, and it has to be the far ones -
        // list order could drop the player standing right here.
        if (found.size() > MAX_PLAYERS) {
            found.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(cam)));
            return found.subList(0, MAX_PLAYERS);
        }
        return found;
    }
}
