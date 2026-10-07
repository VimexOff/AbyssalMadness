package abyssalmadness.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// A bedrock wall ringing the last level. Fills every column in the wall band from
// FLOOR_Y up past the build ceiling.
public class BarrierWallFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CENTER = 25000;
    private static final int HALF = 12;      // 24-block thick
    private static final int FLOOR_Y = -52;

    public BarrierWallFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int originX = ctx.origin().getX();
        int originZ = ctx.origin().getZ();
        int top = level.getMaxY();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int wx = originX + dx;
                int wz = originZ + dz;
                int dist = Math.max(Math.abs(wx), Math.abs(wz));
                if (dist < CENTER - HALF || dist > CENTER + HALF) {
                    continue;
                }
                for (int y = FLOOR_Y; y <= top; y++) {
                    p.set(wx, y, wz);
                    level.setBlock(p, bedrock, 2);
                }
                any = true;
            }
        }
        return any;
    }
}
