package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Crystals standing in an exact circle, with a small paved slab at the centre.
//
// Everything else in this desert grows at random, which is the whole point: a shape
// this regular cannot be mistaken for nature, and a player will read it as a sign
// long before anything explains it.
public class CrystalRingFeature extends Feature<NoneFeatureConfiguration> {
    // Kept inside the reach a feature is allowed to write to: the far side of the ring
    // plus a shard's own width has to stay within CrystalSpikeFeature.MAX_REACH.
    private static final int MIN_RADIUS = 8;
    private static final int RADIUS_RANGE = 4;      // 8..11
    private static final int MIN_SHARDS = 10;
    private static final int SHARD_RANGE = 5;       // 10..14 around the circle
    private static final int SURFACE_SEARCH = 12;   // how far up or down to look for ground
    private static final BlockState PAVING = Blocks.CHISELED_SANDSTONE.defaultBlockState();
    private static final BlockState MARK = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();

    public CrystalRingFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource rnd = ctx.random();

        // Fades out as the wood comes in, like the rest of the crystals.
        if (rnd.nextFloat() < AbyssTerrain.woodGate(origin.getX(), origin.getZ())) {
            return false;
        }

        int radius = MIN_RADIUS + rnd.nextInt(RADIUS_RANGE);
        int shards = MIN_SHARDS + rnd.nextInt(SHARD_RANGE);

        // The dunes roll, so every shard is dropped onto its own ground level. Placing
        // them all at the origin's height would leave half the ring hanging in the air.
        int placed = 0;
        for (int i = 0; i < shards; i++) {
            double angle = (Math.PI * 2.0 / shards) * i;
            int x = origin.getX() + Mth.floor(Math.cos(angle) * radius + 0.5);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * radius + 0.5);
            BlockPos foot = groundAt(level, x, z, origin.getY());
            if (foot == null) {
                continue;
            }
            // Same height for every shard: the regularity is the message.
            if (CrystalSpikeFeature.buildShard(level, origin, foot, 9 + rnd.nextInt(3), 1, 0, rnd)) {
                placed++;
            }
        }
        if (placed == 0) {
            return false;
        }

        BlockPos centre = groundAt(level, origin.getX(), origin.getZ(), origin.getY());
        if (centre != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(centre.offset(dx, -1, dz), PAVING, 2);
                }
            }
            level.setBlock(centre.below(), MARK, 2);
        }
        return true;
    }

    /** First air block standing on solid ground near the given height, or null. */
    private static BlockPos groundAt(WorldGenLevel level, int x, int z, int nearY) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (Math.abs(top - nearY) > SURFACE_SEARCH) {
            return null;
        }
        BlockPos pos = new BlockPos(x, top, z);
        return CrystalSpikeFeature.canRootOn(level, pos) ? pos : null;
    }
}
