package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// A cluster of leaning stained-glass shards, colour-graded from deep blue at the root
// to white at the tip, lit from inside.
public class CrystalSpikeFeature extends Feature<NoneFeatureConfiguration> {
    // Root-to-tip gradient, the same cold blue-white as the infection shader. Nothing
    // says the two are related; the colour is the only hint.
    private static final BlockState[] TIERS = {
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.CYAN_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
    };

    // Stained glass passes light, so a lantern buried inside lights the whole shard.
    private static final BlockState CORE = Blocks.SEA_LANTERN.defaultBlockState();
    private static final int CORE_EVERY = 4;

    // A feature may only write near the chunk being generated. A tall leaning shard
    // plus the scatter of the side ones reached two chunks out and the writes were
    // dropped, leaving holes. Everything is clipped to this box instead.
    private static final int MAX_REACH = 13;

    /** Survival odds far from any monolith; right at one it is always 1. */
    private static final float OPEN_CHANCE = 0.18F;

    // tall variant for the woods, above the treetops
    private final boolean tall;

    public CrystalSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
        this(codec, false);
    }

    public CrystalSpikeFeature(Codec<NoneFeatureConfiguration> codec, boolean tall) {
        super(codec);
        this.tall = tall;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource rnd = ctx.random();

        if (!canRootOn(level, origin)) {
            return false;
        }
        // Thin in the open, crowded at the towers. The placed feature sets the
        // ceiling; this decides how much of it survives.
        float keep = OPEN_CHANCE + (1.0F - OPEN_CHANCE)
                * (float) AbyssTerrain.monolithProximity(origin.getX(), origin.getZ());
        // Gone by the time the wood closes: they belong to the sand half.
        keep *= (float) (1.0 - AbyssTerrain.woodGate(origin.getX(), origin.getZ()));
        if (rnd.nextFloat() > keep) {
            return false;
        }

        int roll = rnd.nextInt(100);
        int mainHeight;
        int maxRadius;
        int shards;
        int spread;
        if (tall) {                            // woods
            mainHeight = 24 + rnd.nextInt(18); // 24..41
            maxRadius = 3;
            shards = 3 + rnd.nextInt(4);
            spread = 4;
        } else if (roll < 50) {                // small
            mainHeight = 7 + rnd.nextInt(6);   // 7..12
            maxRadius = 2;
            shards = 2 + rnd.nextInt(3);
            spread = 3;
        } else if (roll < 85) {                // medium
            mainHeight = 14 + rnd.nextInt(11); // 14..24
            maxRadius = 4;
            shards = 3 + rnd.nextInt(4);
            spread = 5;
        } else {                               // large
            mainHeight = 28 + rnd.nextInt(20); // 28..47
            maxRadius = 6;
            shards = 4 + rnd.nextInt(5);
            spread = 8;
        }

        boolean any = buildShard(level, origin, origin, mainHeight, maxRadius,
                tall ? 6 + rnd.nextInt(4) : 2 + rnd.nextInt(3), rnd);
        for (int i = 0; i < shards; i++) {
            int dx = rnd.nextInt(spread * 2 + 1) - spread;
            int dz = rnd.nextInt(spread * 2 + 1) - spread;
            if (dx == 0 && dz == 0) {
                continue;
            }
            BlockPos base = origin.offset(dx, 0, dz);
            if (canRootOn(level, base)) {
                any |= buildShard(level, origin, base, 1 + rnd.nextInt(Math.max(1, mainHeight - 2)),
                        Math.max(0, maxRadius - 1), tall ? 6 + rnd.nextInt(4) : 2 + rnd.nextInt(3), rnd);
            }
        }
        return any;
    }

    // One leaning shard: a diamond cross-section tapering to a point as it drifts
    // sideways. Shared with the ring feature, which stands the same shards in a circle.
    static boolean buildShard(WorldGenLevel level, BlockPos origin, BlockPos base, int height,
                              int maxRadius, int leanEvery, RandomSource rnd) {
        int leanX = rnd.nextInt(3) - 1;
        int leanZ = rnd.nextInt(3) - 1;

        BlockPos.MutableBlockPos center = base.mutable();
        int placed = 0;
        for (int i = 0; i < height; i++) {
            if (!isReplaceable(level, center)) {
                break;
            }
            float f = height <= 1 ? 1.0F : (float) i / (height - 1); // 0 at root, 1 at tip
            int radius = Math.round((1.0F - f) * maxRadius);
            int tier = Mth.clamp(Math.round(f * (TIERS.length - 1)), 0, TIERS.length - 1);
            BlockState glass = TIERS[tier];

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius) {
                        continue; // diamond cross-section
                    }
                    BlockPos p = center.offset(dx, 0, dz);
                    if (inReach(origin, p) && isReplaceable(level, p)) {
                        level.setBlock(p, glass, 2);
                    }
                }
            }
            // Only where the shard is thick enough to hide it; on a thin tip it would
            // stick out as a bare white cube.
            if (radius >= 1 && i % CORE_EVERY == 0 && inReach(origin, center)) {
                level.setBlock(center, CORE, 2);
            }
            placed++;

            if (leanEvery > 0 && (i + 1) % leanEvery == 0) {
                center.move(leanX, 1, leanZ);
            } else {
                center.move(0, 1, 0);
            }
        }
        return placed > 0;
    }

    static boolean inReach(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= MAX_REACH
                && Math.abs(pos.getZ() - origin.getZ()) <= MAX_REACH;
    }

    static boolean canRootOn(WorldGenLevel level, BlockPos airPos) {
        return isReplaceable(level, airPos) && level.getBlockState(airPos.below()).isSolidRender();
    }

    static boolean isReplaceable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
