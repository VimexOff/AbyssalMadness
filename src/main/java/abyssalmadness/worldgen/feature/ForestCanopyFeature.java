package abyssalmadness.worldgen.feature;

import abyssalmadness.worldgen.AbyssTerrain;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * One continuous sheet of leaf between the trees, instead of a crown per trunk.
 *
 * A tree cannot reach the next tree: a feature may only write to the eight chunks
 * around its own, which is less than the gap between trunks, so crowns built tree by
 * tree come out as separate lollipops. This runs once per chunk and fills only its own
 * 16x16 columns, which needs no reach at all - it asks the terrain which trunks are
 * near (their positions are a hash, not a roll) and reaches for them.
 *
 * Gaps are deliberate: clearings from the terrain, pinholes from the dapple noise.
 */
public class ForestCanopyFeature extends Feature<NoneFeatureConfiguration> {
    /** Chunks to look for trunks in, each way. Must cover REACH. */
    private static final int NEIGHBOURHOOD = 3;
    /** How far one tree's share of the sheet stretches. Wider than the gap between trunks. */
    private static final double REACH = 27.0;
    /** Coverage at or above this gets the full thickness; below it the sheet thins to a rim. */
    private static final double SOLID = 0.42;

    private static final int THICK_MIN = 2;
    private static final int THICK_MAX = 7;
    /** Sway of the sheet between neighbouring columns, so the underside is not a ceiling. */
    private static final double SWAY = 3.0;
    private static final double SWAY_SCALE = 0.35;

    /** Pinholes. Above this the column is left open. */
    private static final double DAPPLE_HOLE = 0.90;

    private static final float MOSS_CHANCE = 0.045F;
    private static final int MOSS_LEN_MIN = 2;
    private static final int MOSS_LEN_EXTRA = 6;

    private static final BlockState LEAF = Blocks.PALE_OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    private static final BlockState MOSS_MID = Blocks.PALE_HANGING_MOSS.defaultBlockState()
            .setValue(HangingMossBlock.TIP, Boolean.FALSE);
    private static final BlockState MOSS_TIP = Blocks.PALE_HANGING_MOSS.defaultBlockState()
            .setValue(HangingMossBlock.TIP, Boolean.TRUE);

    private static final int SIDE = NEIGHBOURHOOD * 2 + 1;

    public ForestCanopyFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        int chunkX = ctx.origin().getX() >> 4;
        int chunkZ = ctx.origin().getZ() >> 4;

        int[] treeX = new int[SIDE * SIDE];
        int[] treeZ = new int[SIDE * SIDE];
        int trees = 0;
        for (int ox = -NEIGHBOURHOOD; ox <= NEIGHBOURHOOD; ox++) {
            for (int oz = -NEIGHBOURHOOD; oz <= NEIGHBOURHOOD; oz++) {
                int tcx = chunkX + ox;
                int tcz = chunkZ + oz;
                if (!AbyssTerrain.treeInChunk(tcx, tcz)) {
                    continue;
                }
                treeX[trees] = AbyssTerrain.treeX(tcx, tcz);
                treeZ[trees] = AbyssTerrain.treeZ(tcx, tcz);
                trees++;
            }
        }
        if (trees == 0) {
            return false;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        boolean any = false;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = originX + lx;
                int z = originZ + lz;

                double cover = 0.0;
                for (int i = 0; i < trees; i++) {
                    double dx = x - treeX[i];
                    double dz = z - treeZ[i];
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < REACH) {
                        cover = Math.max(cover, 1.0 - d / REACH);
                    }
                }
                if (cover <= 0.0) {
                    continue;
                }
                if (AbyssTerrain.canopyDapple(x, z) > DAPPLE_HOLE) {
                    continue;
                }

                double fill = Mth.clamp(cover / SOLID, 0.0, 1.0);
                int thickness = THICK_MIN + Mth.floor((THICK_MAX - THICK_MIN) * fill);
                int mid = AbyssTerrain.canopyY(x, z)
                        + Mth.floor(Math.sin(x * SWAY_SCALE) * Math.cos(z * SWAY_SCALE) * SWAY);
                int bottom = mid - thickness / 2;
                int top = bottom + thickness;

                // Asked of the terrain, not the heightmap: the trees are already up, so
                // the heightmap here is the top of a trunk.
                if (AbyssTerrain.isMonolith(x, mid, z)) {
                    continue; // sky stays open over the towers
                }

                int lowest = Integer.MAX_VALUE;
                for (int y = bottom; y <= top; y++) {
                    if (level.isOutsideBuildHeight(y)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, LEAF, 2);
                    lowest = Math.min(lowest, y);
                    any = true;
                }

                if (lowest != Integer.MAX_VALUE && rnd.nextFloat() < MOSS_CHANCE) {
                    hang(level, pos, x, lowest, z, MOSS_LEN_MIN + rnd.nextInt(MOSS_LEN_EXTRA));
                }
            }
        }
        return any;
    }

    private static void hang(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                             int x, int from, int z, int len) {
        for (int s = 1; s <= len; s++) {
            int y = from - s;
            if (level.isOutsideBuildHeight(y)) {
                return;
            }
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
            level.setBlock(pos, s == len ? MOSS_TIP : MOSS_MID, 2);
        }
    }
}
