package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Crystals growing straight out of a monolith's flank.
//
// The placement scatters attempts through the air at every height; almost all of them
// find nothing and cost a handful of block reads, and the few that land against bare
// stone take hold. That is cheaper than working out where the towers are, and it puts
// the growths on the real faces including the ledges cut by the glyphs.
public class CrystalFaceFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState[] TIERS = {
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.CYAN_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
    };
    private static final BlockState CORE = Blocks.SEA_LANTERN.defaultBlockState();
    private static final int MIN_SPIKES = 3;
    private static final int SPIKE_RANGE = 4;       // 3..6 in a cluster
    private static final int SPREAD = 3;            // how far the cluster fans along the face
    /** Two thirds come out small, the rest medium. Nothing on a wall grows huge. */
    private static final int MEDIUM_ROLL = 66;

    public CrystalFaceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource rnd = ctx.random();

        if (!CrystalSpikeFeature.isReplaceable(level, origin)) {
            return false;
        }
        Direction wall = faceAt(level, origin);
        if (wall == null) {
            return false;
        }

        Direction out = wall.getOpposite();
        Direction along = wall.getClockWise();
        int spikes = MIN_SPIKES + rnd.nextInt(SPIKE_RANGE);
        boolean any = false;

        for (int i = 0; i < spikes; i++) {
            BlockPos root = origin
                    .relative(along, rnd.nextInt(SPREAD * 2 + 1) - SPREAD)
                    .above(rnd.nextInt(SPREAD * 2 + 1) - SPREAD);
            // Every spike needs its own bit of wall - the fan can easily reach past the
            // corner of a tower, and a crystal hanging in mid-air gives the trick away.
            if (level.getBlockState(root.relative(wall)).is(Blocks.STONE)
                    && CrystalSpikeFeature.isReplaceable(level, root)) {
                boolean medium = rnd.nextInt(100) >= MEDIUM_ROLL;
                int length = medium ? 7 + rnd.nextInt(7) : 3 + rnd.nextInt(4);
                any |= spike(level, root, out, length, medium ? 2.2F : 1.4F, rnd);
            }
        }
        return any;
    }

    /**
     * The direction of an adjacent monolith wall, or null if there is none.
     *
     * Stone alone is not enough to go on. The surface rule only paints horizontal tops,
     * so every vertical cut through a dune is left as bare stone too - and the terraced
     * slopes around a tower are full of them. Crystals took hold on a terrace step and
     * grew out sideways, leaving their far ends hanging over the step below.
     */
    private static Direction faceAt(WorldGenLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wall = pos.relative(dir);
            if (level.getBlockState(wall).is(Blocks.STONE)
                    && AbyssTerrain.isMonolith(wall.getX(), wall.getY(), wall.getZ())) {
                return dir;
            }
        }
        return null;
    }

    // One spike: away from the wall and drooping slightly, thick at the root and ending
    // in a point.
    private static boolean spike(WorldGenLevel level, BlockPos root, Direction out, int length,
                                 float thickness, RandomSource rnd) {
        int rise = rnd.nextInt(3) - 1;      // -1 down, 0 straight, 1 up
        int bendEvery = 2 + rnd.nextInt(3);
        BlockPos.MutableBlockPos head = root.mutable();
        int placed = 0;

        for (int i = 0; i < length; i++) {
            if (!CrystalSpikeFeature.isReplaceable(level, head)) {
                break;
            }
            float f = length <= 1 ? 1.0F : (float) i / (length - 1);
            int radius = Math.round((1.0F - f) * thickness);
            int tier = Mth.clamp(Math.round(f * (TIERS.length - 1)), 0, TIERS.length - 1);
            BlockState glass = TIERS[tier];

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > radius) {
                            continue;
                        }
                        BlockPos p = head.offset(dx, dy, dz);
                        if (CrystalSpikeFeature.isReplaceable(level, p)) {
                            level.setBlock(p, glass, 2);
                        }
                    }
                }
            }
            if (radius >= 1 && i == 0) {
                level.setBlock(head, CORE, 2);
            }
            placed++;

            head.move(out);
            if (rise != 0 && (i + 1) % bendEvery == 0) {
                head.move(0, rise, 0);
            }
        }
        return placed > 0;
    }
}
