package abyssalmadness.client.blackout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything that lies to the chunk mesh builder. The decay trail runs first and the
 * blackout is layered on its result, so what the trail killed stays dead once the
 * sphere moves off it.
 */
public final class RenderOverrides {
    private RenderOverrides() {
    }

    /** What to render here, or null to leave the real block alone. */
    public static BlockState blockState(int x, int y, int z, BlockState original) {
        BlockState state = DecayTrail.apply(x, y, z, original);
        BlockState blackout = BlackoutState.override(x, y, z, state);

        if (blackout != null) {
            return blackout;
        }
        return state == original ? null : state;
    }

    public static BlockState blockState(BlockPos pos, BlockState original) {
        return blockState(pos.getX(), pos.getY(), pos.getZ(), original);
    }
}
