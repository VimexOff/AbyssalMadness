package abyssalmadness.client.mixin;

import abyssalmadness.AbyssalMadness;
import abyssalmadness.client.CleansingClientState;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Runs the Cleansing post-processing pass over the finished frame, right where
// vanilla runs its own screen effects.
//
// Post-chain uniforms in 1.21 are baked into GPU buffers when the chain is built, so
// there is no way to push a changing strength into one shader. Instead the effect
// ships as a set of chains at fixed strengths and the closest one is picked each
// frame - the value moves smoothly enough that the steps are invisible.
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    // Enough steps that the quantisation is invisible: at 8 the brightness visibly
    // jumped between levels, at 64 each step is ~1.5% and reads as a smooth ramp.
    private static final int LEVELS = 64;
    private static final Identifier[] CHAINS = new Identifier[LEVELS];

    static {
        for (int i = 0; i < LEVELS; i++) {
            CHAINS[i] = AbyssalMadness.id("cleansing_" + (i + 1));
        }
    }

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER
            )
    )
    private void abyssalmadness$cleansingPostEffect(net.minecraft.client.DeltaTracker delta,
                                                    boolean renderLevel, CallbackInfo ci) {
        float intensity = CleansingClientState.raw();
        if (intensity <= 0.02F) {
            return;
        }
        int step = Math.min(LEVELS, Math.max(1, Math.round(intensity * LEVELS)));
        Minecraft mc = Minecraft.getInstance();
        PostChain chain = mc.getShaderManager().getPostChain(CHAINS[step - 1], LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) {
            chain.process(mc.getMainRenderTarget(), this.resourcePool);
        }
    }
}
