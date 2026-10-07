package abyssalmadness.client.mixin;

import abyssalmadness.client.BeaconShader;
import abyssalmadness.client.InfectionClientState;
import abyssalmadness.client.InfectionShader;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelTargetBundle targets;

    // Straight after the main pass, and the position matters. The main pass fills the
    // outline buffer with solid silhouettes; vanilla's own outline chain then Sobels it
    // down to a ring. This is the one moment the buffer holds bodies, not outlines.
    // (At RETURN the frame graph has run and the bundle is already cleared.)
    //
    // Descriptor in full: loom-remap rewrites targets into the bytecode and can only
    // resolve an exact member. A bare "addMainPass" works in dev and crashes a built
    // jar, where it matches nothing.
    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
                    shift = At.Shift.AFTER))
    private void abyssalmadness$renderInfection(GraphicsResourceAllocator allocator,
                                                DeltaTracker deltaTracker,
                                                boolean renderBlockOutline,
                                                Camera camera,
                                                Matrix4f frustumMatrix,
                                                Matrix4f projectionMatrix,
                                                Matrix4f cullingMatrix,
                                                GpuBufferSlice fogBuffer,
                                                Vector4f fogColor,
                                                boolean skipSky,
                                                CallbackInfo ci,
                                                @Local FrameGraphBuilder frameGraphBuilder) {
        // The matrix order in this signature has moved between versions, so identify
        // the projection by shape instead: a perspective matrix has m33 == 0.
        boolean secondIsProjection = projectionMatrix.m33() == 0.0F;
        Matrix4f projection = secondIsProjection ? projectionMatrix : frustumMatrix;
        Matrix4f modelView = secondIsProjection ? frustumMatrix : projectionMatrix;

        Minecraft client = Minecraft.getInstance();
        // Beacon first, so a player in the shaft reads as a figure in the light.
        BeaconShader.addToFrame(client, frameGraphBuilder, this.targets, modelView, projection);
        InfectionShader.addToFrame(client, frameGraphBuilder, this.targets, modelView, projection);
    }

    // They are marked glowing only to fill the mask, so vanilla's outline has to be
    // swallowed or every infected player wears a coloured ring.
    @Inject(method = "doEntityOutline()V", at = @At("HEAD"), cancellable = true)
    private void abyssalmadness$hideOutline(CallbackInfo ci) {
        if (InfectionClientState.any()) {
            ci.cancel();
        }
    }
}
