package abyssalmadness.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Scans the column top-to-bottom and lays moss wherever a stone floor has air above it.
public class CaveMossFeature extends Feature<NoneFeatureConfiguration> {
    public CaveMossFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        int x = ctx.origin().getX();
        int z = ctx.origin().getZ();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int bottom = level.getMinY() + 5;

        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int placed = 0;

        for (int y = top - 2; y > bottom; y--) {
            p.set(x, y, z);
            if (!isRock(level.getBlockState(p)) || !level.getBlockState(p.above()).isAir()) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 1) {
                        continue;
                    }
                    BlockPos f = new BlockPos(x + dx, y, z + dz);
                    if (isRock(level.getBlockState(f)) && level.getBlockState(f.above()).isAir()) {
                        level.setBlock(f, moss, 2);
                        if (rnd.nextInt(2) == 0) {
                            level.setBlock(f.above(), carpet, 2);
                        }
                    }
                }
            }
            placed++;
            y -= 2; // skip floors stacked right above this one
        }
        return placed > 0;
    }

    private static boolean isRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }
}
