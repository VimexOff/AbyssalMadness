package abyssalmadness.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A plot of wheat someone tilled and never came back for. The only food the world
 * grows - nothing past Level 1 is edible, so a player's whole supply is cut here.
 *
 * Ragged rather than rectangular: a neat plot reads as a village, and there is none.
 */
public class WildCropFeature extends Feature<NoneFeatureConfiguration> {
    private static final int HALF_MIN = 2;
    private static final int HALF_RANGE = 3;      // plots 5x5 to 9x9
    /** Squeeze on one axis, so a plot is an oblong and not a disc. */
    private static final double SQUASH_MIN = 0.6;
    private static final double SQUASH_RANGE = 0.6;
    /** How much of the plot has gone back to bare ground. */
    private static final float GAP_CHANCE = 0.28F;
    /** Not everything ripened, so it does not look stamped out. */
    private static final float UNRIPE_CHANCE = 0.18F;
    /** Any more than this is a slope, and a field on a slope looks like an accident. */
    private static final int MAX_SPREAD = 3;
    private static final int MIN_STALKS = 6;

    private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState()
            .setValue(FarmBlock.MOISTURE, 7);

    public WildCropFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource rnd = ctx.random();

        int halfX = HALF_MIN + rnd.nextInt(HALF_RANGE);
        int halfZ = Math.max(HALF_MIN,
                Mth.floor(halfX * (SQUASH_MIN + rnd.nextDouble() * SQUASH_RANGE)));
        double angle = rnd.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        // Surveyed in full first: bailing out halfway would leave a stub of a field.
        List<BlockPos> plots = new ArrayList<>();
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos soil = new BlockPos.MutableBlockPos();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;

        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfX; dz <= halfX; dz++) {
                // Rotated ellipse, with a soft edge so the corners fray.
                double rx = (dx * cos + dz * sin) / halfX;
                double rz = (-dx * sin + dz * cos) / halfZ;
                double v = rx * rx + rz * rz;
                if (v > 1.0 || rnd.nextFloat() < v * 0.7F) {
                    continue;
                }
                if (rnd.nextFloat() < GAP_CHANCE) {
                    continue;
                }

                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
                top.set(wx, y, wz);
                soil.set(wx, y - 1, wz);
                if (!isTillable(level.getBlockState(soil)) || !level.getBlockState(top).isAir()) {
                    continue;
                }

                low = Math.min(low, y);
                high = Math.max(high, y);
                if (high - low > MAX_SPREAD) {
                    return false; // a slope: leave the whole plot unbuilt rather than draped
                }
                plots.add(top.immutable());
            }
        }

        if (plots.size() < MIN_STALKS) {
            return false;
        }
        for (BlockPos plot : plots) {
            int age = rnd.nextFloat() < UNRIPE_CHANCE ? 3 + rnd.nextInt(4) : CropBlock.MAX_AGE;
            level.setBlock(plot.below(), FARMLAND, 2);
            level.setBlock(plot, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age), 2);
        }
        return true;
    }

    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }
}
