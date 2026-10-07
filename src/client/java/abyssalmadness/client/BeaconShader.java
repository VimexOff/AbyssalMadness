package abyssalmadness.client;

import abyssalmadness.AbyssalMadness;
import abyssalmadness.client.mixin.CameraAccessor;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

// Drives the beacon pass.
//
// Everything the shader needs is the beacon's foot relative to the camera plus the
// matrix to turn pixels back into view rays. Feeding it camera-relative coordinates
// rather than world ones keeps the maths accurate far from the origin, where float
// precision on raw block coordinates would make the shaft jitter.
public final class BeaconShader {
    private static final Identifier EFFECT = AbyssalMadness.id("beacon");
    private static final String BLOCK = "BeaconConfig";
    /** The lamp sits above the anchored block, not inside it. */
    private static final double LAMP_HEIGHT = 1.5;

    private static boolean broken;

    private BeaconShader() {
    }

    public static void addToFrame(Minecraft client, FrameGraphBuilder frameGraph,
                                  LevelTargetBundle targets, Matrix4f modelView, Matrix4f projection) {
        if (broken || client.level == null || !BeaconClientState.burning()) {
            return;
        }
        BlockPos site = BeaconClientState.pos();
        if (site == null) {
            return;
        }
        PostChain chain = client.getShaderManager().getPostChain(EFFECT, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }

        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        try {
            Vec3 cam = ((CameraAccessor) client.gameRenderer.getMainCamera()).abyssalmadness$getPosition();
            float time = (System.currentTimeMillis() % 600000L) / 1000.0F;
            float level = BeaconClientState.intensity(partial);

            // Camera-relative, because raw block coordinates this far from the origin
            // lose enough float precision to make the light jitter.
            float rx = (float) (site.getX() + 0.5 - cam.x);
            float ry = (float) (site.getY() + LAMP_HEIGHT - cam.y);
            float rz = (float) (site.getZ() + 0.5 - cam.z);

            Matrix4f viewProj = new Matrix4f(projection).mul(modelView);
            Vector4f clip = new Vector4f(rx, ry, rz, 1.0F).mul(viewProj);
            if (clip.w <= 0.0001F) {
                return;                     // behind the camera
            }
            float sx = (clip.x / clip.w) * 0.5F + 0.5F;
            float sy = (clip.y / clip.w) * 0.5F + 0.5F;
            // Same 0..1 range the depth buffer is sampled in, so the shader can compare
            // the two directly and skip reconstructing world positions altogether.
            float sz = (clip.z / clip.w) * 0.5F + 0.5F;
            float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);

            // A little slack past the edges: the halo should still spill in from just
            // off screen rather than popping the moment the lamp leaves the frame.
            boolean onScreen = sx > -0.4F && sx < 1.4F && sy > -0.4F && sy < 1.4F;

            boolean written = PostUniforms.write(chain, BLOCK, builder -> {
                builder.putVec4(sx, sy, sz, level);
                builder.putVec4(time, dist, onScreen ? 1.0F : 0.0F, 1.0F);
            });
            if (!written) {
                return;
            }
            RenderTarget main = client.getMainRenderTarget();
            chain.addToFrame(frameGraph, main.width, main.height, targets);
        } catch (Exception e) {
            broken = true;
            AbyssalMadness.LOGGER.error("beacon shader disabled: pass failed", e);
        }
    }
}
