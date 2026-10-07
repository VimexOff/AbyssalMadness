package abyssalmadness.client.mixin.sodium;

import abyssalmadness.client.blackout.RenderOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same repaint, for Sodium.
 *
 * Sodium does not use vanilla's chunk mesh builder, so with it installed the vanilla
 * mixin applies cleanly and does nothing at all. {@code LevelSlice} is its equivalent
 * snapshot. Targeted by name since Sodium is not a compile dependency, and kept in its
 * own mixin config so a setup without it skips rather than fails to boot.
 *
 * Both overloads are hooked; if one delegates to the other the override just runs twice
 * over its own output, which lands on the same block.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public class LevelSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void abyssalmadness$overrideByCoords(int x, int y, int z,
                                                 CallbackInfoReturnable<BlockState> cir) {
        BlockState replacement = RenderOverrides.blockState(x, y, z, cir.getReturnValue());

        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void abyssalmadness$overrideByPos(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState replacement = RenderOverrides.blockState(pos, cir.getReturnValue());

        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
