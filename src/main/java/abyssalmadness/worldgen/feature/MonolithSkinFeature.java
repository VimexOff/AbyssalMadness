package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Turns the odd monolith black.
//
// The density function decides where stone is, never what it is made of, and a surface
// rule cannot tell one tower from another - so the shape alone left the odd one looking
// exactly like its neighbours. This walks the chunk it is given and repaints only the
// columns the terrain reports as belonging to that tower.
//
// Repainting per chunk is what keeps it legal: a feature may only write near the chunk
// being generated, and a tower spans several. Each chunk paints its own share, and the
// tower comes out whole.
public class MonolithSkinFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState DARK = Blocks.BLACKSTONE.defaultBlockState();
    /** Below this the tower is buried in the dunes and nobody will ever see it. */
    private static final int LOWEST = 60;

    public MonolithSkinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int baseX = ctx.origin().getX() & ~15;
        int baseZ = ctx.origin().getZ() & ~15;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean any = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                int top = AbyssTerrain.oddMonolithTop(x, z);
                if (top == Integer.MIN_VALUE) {
                    continue;   // ordinary ground, or an ordinary tower
                }
                for (int y = top; y >= LOWEST; y--) {
                    m.set(x, y, z);
                    if (level.getBlockState(m).is(Blocks.STONE)) {
                        level.setBlock(m, DARK, 2);
                        any = true;
                    }
                }
            }
        }
        return any;
    }
}
