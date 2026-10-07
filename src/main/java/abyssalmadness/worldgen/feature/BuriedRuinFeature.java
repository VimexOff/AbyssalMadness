package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// What the sand did not finish swallowing: a doorway with no house behind it, or a
// fence walking into a dune and never coming out.
//
// Both say the same thing without a word of text - people were here first, and the
// desert came afterwards.
public class BuriedRuinFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState POST =
            Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    private static final BlockState FENCE = Blocks.DARK_OAK_FENCE.defaultBlockState();

    public BuriedRuinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos origin = ctx.origin();

        // Nothing built stands in the wood. The doorways and fences are the sand half's
        // story - whoever left them was crossing a desert, not living under trees.
        if (rnd.nextFloat() < AbyssTerrain.woodGate(origin.getX(), origin.getZ())) {
            return false;
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(rnd);
        return rnd.nextBoolean()
                ? doorway(level, origin, facing, rnd)
                : sinkingFence(level, origin, facing, rnd);
    }

    // A frame two blocks wide with a lintel across the top, sunk to varying depth.
    private static boolean doorway(WorldGenLevel level, BlockPos origin, Direction facing, RandomSource rnd) {
        Direction across = facing.getClockWise();
        BlockPos left = ground(level, origin);
        if (left == null) {
            return false;
        }
        BlockPos right = ground(level, origin.relative(across, 2));
        if (right == null) {
            return false;
        }

        int height = 3 + rnd.nextInt(2);
        // The two sides sit on their own ground, so on a slope the frame leans the way
        // a real one would after decades of sand.
        raise(level, left, height);
        raise(level, right, height);

        int lintelY = Math.max(left.getY(), right.getY()) + height;
        BlockPos lintel = new BlockPos(left.getX(), lintelY, left.getZ());
        for (int i = 0; i <= 2; i++) {
            BlockPos p = lintel.relative(across, i);
            if (CrystalSpikeFeature.isReplaceable(level, p)) {
                level.setBlock(p, POST.setValue(BlockStateProperties.AXIS, across.getAxis()), 2);
            }
        }
        return true;
    }

    // Posts marching into a dune, each one shorter than the last.
    private static boolean sinkingFence(WorldGenLevel level, BlockPos origin, Direction facing, RandomSource rnd) {
        int count = 5 + rnd.nextInt(4);
        int height = 3;
        boolean any = false;

        for (int i = 0; i < count; i++) {
            BlockPos foot = ground(level, origin.relative(facing, i * 2));
            if (foot == null) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                BlockPos p = foot.above(y);
                if (CrystalSpikeFeature.isReplaceable(level, p)) {
                    level.setBlock(p, FENCE, 2);
                    any = true;
                }
            }
            // Shrinks towards the far end rather than stopping: it reads as sinking,
            // not as an unfinished build.
            if (i >= count / 3 && height > 0) {
                height--;
            }
        }
        return any;
    }

    private static void raise(WorldGenLevel level, BlockPos foot, int height) {
        for (int y = 0; y < height; y++) {
            BlockPos p = foot.above(y);
            if (CrystalSpikeFeature.isReplaceable(level, p)) {
                level.setBlock(p, POST, 2);
            }
        }
    }

    private static BlockPos ground(WorldGenLevel level, BlockPos near) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, near.getX(), near.getZ());
        BlockPos pos = new BlockPos(near.getX(), top, near.getZ());
        return CrystalSpikeFeature.canRootOn(level, pos) ? pos : null;
    }
}
