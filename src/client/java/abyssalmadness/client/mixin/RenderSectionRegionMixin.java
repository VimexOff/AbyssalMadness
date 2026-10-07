package abyssalmadness.client.mixin;

import abyssalmadness.client.blackout.RenderOverrides;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where the blackout and the decay trail are painted.
 *
 * {@link RenderSectionRegion} is the world snapshot the chunk mesh builder reads from,
 * used only while baking geometry - never for collision or anything the server sees. So
 * the mesh is built from one block while the real one underneath is untouched, and the
 * fakes get vanilla culling, AO and lighting for free.
 *
 * Runs on the chunk build worker threads. Descriptor spelled out in full: loom-remap
 * rewrites targets into the bytecode and can only resolve an exact member.
 */
@Mixin(RenderSectionRegion.class)
public class RenderSectionRegionMixin {
    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true)
    private void abyssalmadness$overrideRenderedState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState replacement = RenderOverrides.blockState(pos, cir.getReturnValue());

        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
