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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * One tree of the wood over the far half of Level 2. The canopy is cover from the
 * Sleeping Stars, not decoration, so it has to be thick enough to block the sky.
 *
 * Built block by block: no vanilla placer makes a trunk fourteen blocks across.
 */
public class AncientTreeFeature extends Feature<NoneFeatureConfiguration> {
    // A feature may only write to the eight chunks around the one being generated;
    // past that the write is silently dropped and the crown comes out sliced flat.
    // A trunk at the chunk centre has 24 blocks of room, less AbyssTerrain's jitter.
    private static final int MAX_REACH = 19;

    private static final int MIN_GROUND_Y = 4;
    /** Steepest ground a tree will stand on, measured across its own footprint. */
    private static final int MAX_GROUND_SPREAD = 9;
    private static final int FLAT_PROBE = 7;

    // Grown to the canopy altitude rather than to a fixed length, so crowns come out
    // level with each other.
    private static final int TRUNK_MIN = 44;
    private static final int TRUNK_MAX = 150;
    // Carried below ground so a trunk across a dip does not hang. Deep enough for the
    // whole slope MAX_GROUND_SPREAD allows.
    private static final int TRUNK_BURY = MAX_GROUND_SPREAD + 3;
    private static final int TRUNK_MARGIN = 3;

    private static final double CORE_MIN = 3.1;   // half-width up the shaft
    private static final double CORE_RANGE = 1.1;
    private static final double TOP_MIN = 1.7;
    private static final double TOP_RANGE = 0.8;
    private static final double FLARE_MIN = 2.6;  // extra half-width at the very foot
    private static final double FLARE_RANGE = 1.4;
    private static final double FLARE_FALL = 9.0; // blocks over which the flare dies away
    private static final double LEAN_MAX = 3.0;

    private static final int ROOT_MIN = 5;
    private static final int ROOT_EXTRA = 4;      // 5..8 buttresses
    private static final int ROOT_LEN_MIN = 9;
    private static final int ROOT_LEN_EXTRA = 8;  // 9..16 out from the trunk
    private static final double ROOT_HALF_MIN = 1.5;
    private static final double ROOT_HALF_RANGE = 1.3;
    private static final double ROOT_RISE_MIN = 8.0;
    private static final double ROOT_RISE_RANGE = 7.0;
    private static final int ROOT_BURY = 3;

    private static final int BRANCH_MIN = 4;
    private static final int BRANCH_EXTRA = 3;    // 4..6
    private static final double BRANCH_START = 0.60;  // share of the trunk below the lowest branch
    private static final double BRANCH_SPAN = 0.34;
    private static final int BRANCH_OUT_MIN = 8;
    private static final int BRANCH_OUT_EXTRA = 7;
    private static final int BRANCH_RISE_MIN = 7;
    private static final int BRANCH_RISE_EXTRA = 11;
    private static final double BRANCH_R0 = 2.3;
    private static final double BRANCH_R1 = 0.9;

    // Covering the sky is the canopy layer's job; a crown only adds mass over its own
    // trunk.
    private static final double CROWN_R_MIN = 12.0;
    private static final double CROWN_R_RANGE = 4.0;
    private static final double CROWN_H_MIN = 6.0;
    private static final double CROWN_H_RANGE = 2.5;
    private static final int CROWN_LIFT = 0;          // dead level with the leaf layer
    private static final double LOBE_R_MIN = 7.5;
    private static final double LOBE_R_RANGE = 2.5;
    private static final double LOBE_H_MIN = 4.0;
    private static final double LOBE_H_RANGE = 1.5;
    /** Inside this share of a lobe the leaf is solid; beyond it the fill thins to the rim. */
    private static final double LOBE_CORE = 0.52;

    private static final int MOSS_STRANDS = 26;
    private static final int MOSS_LEN_MIN = 2;
    private static final int MOSS_LEN_EXTRA = 5;

    private static final float STRIPE_CHANCE = 0.07F;   // pale scars in the bark
    private static final float LIGHT_TREE_CHANCE = 0.4F;
    private static final int LIGHT_MAX = 2;
    private static final int LIGHT_LOW = 3;
    private static final int LIGHT_HIGH = 26;

    private static final double FLOOR_EXTRA = 4.0;      // reach of the litter past the roots

    private static final BlockState BARK = Blocks.PALE_OAK_WOOD.defaultBlockState();
    private static final BlockState SCAR = Blocks.STRIPPED_PALE_OAK_WOOD.defaultBlockState();
    private static final BlockState LEAF = Blocks.PALE_OAK_LEAVES.defaultBlockState()
            // Far past the seven blocks a leaf may sit from its log - without this the
            // crown decays on load.
            .setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    private static final BlockState MOSS_MID = Blocks.PALE_HANGING_MOSS.defaultBlockState()
            .setValue(HangingMossBlock.TIP, Boolean.FALSE);
    private static final BlockState MOSS_TIP = Blocks.PALE_HANGING_MOSS.defaultBlockState()
            .setValue(HangingMossBlock.TIP, Boolean.TRUE);
    private static final BlockState GLOW = Blocks.SHROOMLIGHT.defaultBlockState();

    private static final BlockState[] FLOOR = {
            Blocks.PALE_MOSS_BLOCK.defaultBlockState(),
            Blocks.PALE_MOSS_BLOCK.defaultBlockState(),
            Blocks.PALE_MOSS_BLOCK.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.PODZOL.defaultBlockState(),
    };

    private static final int MAP_SIDE = MAX_REACH * 2 + 1;

    public AncientTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos origin = ctx.origin();

        // Taken from the terrain, not from ctx.random(): the canopy layer has to work
        // out the same trunks from a chunk away. Being near the chunk centre is also
        // what leaves enough writable room for the crown.
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (!AbyssTerrain.treeInChunk(chunkX, chunkZ)) {
            return false;
        }
        int cx = AbyssTerrain.treeX(chunkX, chunkZ);
        int cz = AbyssTerrain.treeZ(chunkX, chunkZ);

        // Read once up front: placing blocks moves the heightmap, and the roots would
        // then be built on top of the trunk.
        int[] ground = new int[MAP_SIDE * MAP_SIDE];
        for (int dx = -MAX_REACH; dx <= MAX_REACH; dx++) {
            for (int dz = -MAX_REACH; dz <= MAX_REACH; dz++) {
                ground[index(dx, dz)] =
                        level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + dx, cz + dz);
            }
        }

        int baseY = ground[index(0, 0)];
        if (baseY < MIN_GROUND_Y || !canRootOn(level, cx, baseY, cz)) {
            return false;
        }
        if (!flatEnough(ground, baseY)) {
            return false;
        }

        // Grown to the leaf layer, not to a length of its own.
        int height = AbyssTerrain.canopyY(cx, cz) - baseY;
        if (height < TRUNK_MIN || height > TRUNK_MAX
                || level.isOutsideBuildHeight(baseY + height + CROWN_LIFT + 16)) {
            return false;
        }

        double coreR = CORE_MIN + rnd.nextDouble() * CORE_RANGE;
        double topR = TOP_MIN + rnd.nextDouble() * TOP_RANGE;
        double flare = FLARE_MIN + rnd.nextDouble() * FLARE_RANGE;
        double leanAngle = rnd.nextDouble() * Math.PI * 2.0;
        double lean = rnd.nextDouble() * LEAN_MAX;
        double lobePhase = rnd.nextDouble() * Math.PI * 2.0;

        int rootReach = buildRoots(level, ground, cx, baseY, cz, coreR + flare, rnd);
        litterFloor(level, ground, cx, cz, rootReach + FLOOR_EXTRA, rnd);
        buildTrunk(level, ground, cx, baseY, cz, height,
                coreR, topR, flare, leanAngle, lean, lobePhase, rnd);
        buildCrown(level, cx, baseY, cz, height, leanAngle, lean, rnd);
        return true;
    }

    // --- trunk ------------------------------------------------------------

    private void buildTrunk(WorldGenLevel level, int[] ground, int cx, int baseY, int cz,
                            int height, double coreR, double topR, double flare,
                            double leanAngle, double lean, double lobePhase, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double leanX = Math.cos(leanAngle) * lean;
        double leanZ = Math.sin(leanAngle) * lean;
        int lights = rnd.nextFloat() < LIGHT_TREE_CHANCE ? 1 + rnd.nextInt(LIGHT_MAX) : 0;

        for (int yl = -TRUNK_BURY; yl <= height; yl++) {
            int y = baseY + yl;
            if (level.isOutsideBuildHeight(y)) {
                continue;
            }
            int shape = Math.max(0, yl);
            double radius = trunkRadius(shape, height, coreR, topR, flare);
            double t = shape / (double) height;
            double offX = leanX * t * t;
            double offZ = leanZ * t * t;
            // Has to cover the wobble (up to a fifth of the radius) and the lean, or
            // the fat side falls outside the loop.
            int span = Mth.ceil(radius * 1.2 + Math.max(Math.abs(offX), Math.abs(offZ))) + 1;

            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (Math.abs(dx) > MAX_REACH || Math.abs(dz) > MAX_REACH) {
                        continue;
                    }
                    double rx = dx - offX;
                    double rz = dz - offZ;
                    double dist = Math.sqrt(rx * rx + rz * rz);
                    // Three- and five-lobed wobble, so it is not a drainpipe.
                    double angle = Math.atan2(rz, rx);
                    double wobble = 1.0 + 0.13 * Math.sin(angle * 3.0 + lobePhase)
                            + 0.07 * Math.sin(angle * 5.0 - lobePhase);
                    if (dist > radius * wobble) {
                        continue;
                    }
                    // Only as far down as this column's own ground, or a slope leaves
                    // one side hanging and bores a shaft on the other.
                    if (yl < 0 && y < ground[index(dx, dz)] - TRUNK_MARGIN) {
                        continue;
                    }
                    pos.set(cx + dx, y, cz + dz);
                    level.setBlock(pos, rnd.nextFloat() < STRIPE_CHANCE ? SCAR : BARK, 2);
                }
            }

            if (lights > 0 && yl >= LIGHT_LOW && yl <= LIGHT_HIGH && rnd.nextInt(LIGHT_HIGH) == 0) {
                double angle = rnd.nextDouble() * Math.PI * 2.0;
                int gx = Mth.floor(offX + Math.cos(angle) * (radius - 0.6));
                int gz = Mth.floor(offZ + Math.sin(angle) * (radius - 0.6));
                if (Math.abs(gx) <= MAX_REACH && Math.abs(gz) <= MAX_REACH) {
                    pos.set(cx + gx, y, cz + gz);
                    level.setBlock(pos, GLOW, 2);
                    lights--;
                }
            }
        }
    }

    private static double trunkRadius(int yl, int height, double coreR, double topR, double flare) {
        double t = Mth.clamp(yl / (double) height, 0.0, 1.0);
        double taper = topR + (coreR - topR) * Math.pow(1.0 - t, 1.5);
        return taper + flare * Math.exp(-yl / FLARE_FALL);
    }

    // --- roots ------------------------------------------------------------

    /** Fins radiating from the foot. Returns how far the farthest one reached. */
    private int buildRoots(WorldGenLevel level, int[] ground, int cx, int baseY, int cz,
                           double footR, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = ROOT_MIN + rnd.nextInt(ROOT_EXTRA);
        double step = Math.PI * 2.0 / count;
        double start = rnd.nextDouble() * Math.PI * 2.0;
        int reach = 0;

        for (int i = 0; i < count; i++) {
            double angle = start + step * (i + (rnd.nextDouble() - 0.5) * 0.5);
            int len = ROOT_LEN_MIN + rnd.nextInt(ROOT_LEN_EXTRA);
            double halfW = ROOT_HALF_MIN + rnd.nextDouble() * ROOT_HALF_RANGE;
            double rise = ROOT_RISE_MIN + rnd.nextDouble() * ROOT_RISE_RANGE;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            reach = Math.max(reach, len);

            for (int d = 0; d <= len; d++) {
                double f = d / (double) len;
                double w = halfW * (1.0 - f * 0.7);
                // Squared, so it sweeps down to the ground instead of lying on it.
                double rideHeight = rise * (1.0 - f) * (1.0 - f);
                double alongX = footR * 0.6 * dirX + dirX * d;
                double alongZ = footR * 0.6 * dirZ + dirZ * d;
                int spread = Mth.ceil(w);

                for (int o = -spread; o <= spread; o++) {
                    double frac = w <= 0.0 ? 0.0 : Mth.clamp(o / w, -1.0, 1.0);
                    double crest = rideHeight * Math.sqrt(Math.max(0.0, 1.0 - frac * frac));
                    int bx = Mth.floor(alongX - dirZ * o);
                    int bz = Mth.floor(alongZ + dirX * o);
                    if (Math.abs(bx) > MAX_REACH || Math.abs(bz) > MAX_REACH) {
                        continue;
                    }
                    int gy = ground[index(bx, bz)];
                    int top = gy + Mth.floor(crest);
                    if (top < gy) {
                        top = gy;
                    }
                    for (int y = gy - ROOT_BURY; y <= top; y++) {
                        if (level.isOutsideBuildHeight(y) || y > baseY + rise + 2) {
                            continue;
                        }
                        pos.set(cx + bx, y, cz + bz);
                        level.setBlock(pos, rnd.nextFloat() < STRIPE_CHANCE ? SCAR : BARK, 2);
                    }
                }
            }
        }
        return reach;
    }

    // --- crown ------------------------------------------------------------

    private void buildCrown(WorldGenLevel level, int cx, int baseY, int cz, int height,
                            double leanAngle, double lean, RandomSource rnd) {
        double leanX = Math.cos(leanAngle) * lean;
        double leanZ = Math.sin(leanAngle) * lean;
        int topY = baseY + height;

        int branches = BRANCH_MIN + rnd.nextInt(BRANCH_EXTRA);
        double step = Math.PI * 2.0 / branches;
        double start = rnd.nextDouble() * Math.PI * 2.0;
        // A lobe goes on each branch end, so the crown sits on the wood.
        int[] tipX = new int[branches];
        int[] tipY = new int[branches];
        int[] tipZ = new int[branches];

        for (int i = 0; i < branches; i++) {
            double angle = start + step * (i + (rnd.nextDouble() - 0.5) * 0.5);
            double t = BRANCH_START + rnd.nextDouble() * BRANCH_SPAN;
            int fromY = baseY + (int) (height * t);
            int out = BRANCH_OUT_MIN + rnd.nextInt(BRANCH_OUT_EXTRA);
            // Every limb ends up in the crown whatever height it leaves at - a
            // candelabra rather than a pole with spokes.
            int rise = (int) (height * (1.0 - t)) + BRANCH_RISE_MIN + rnd.nextInt(BRANCH_RISE_EXTRA);
            double baseOffX = leanX * t * t;
            double baseOffZ = leanZ * t * t;
            // Fine enough that a steep limb is not a string of beads.
            int steps = Math.max(out * 3, rise * 2);

            for (int s = 0; s <= steps; s++) {
                double f = s / (double) steps;
                double dist = out * f;
                double bx = baseOffX + Math.cos(angle) * dist;
                double bz = baseOffZ + Math.sin(angle) * dist;
                // Curves up as it goes: straight spokes look like a diagram.
                int by = fromY + (int) (rise * f * f);
                stamp(level, cx, cz, bx, by, bz, BRANCH_R0 + (BRANCH_R1 - BRANCH_R0) * f, rnd);
            }

            tipX[i] = Mth.floor(baseOffX + Math.cos(angle) * out);
            tipZ[i] = Mth.floor(baseOffZ + Math.sin(angle) * out);
            tipY[i] = fromY + rise;
        }

        // Sized to what the lean leaves of the writable box: overrunning it shears the
        // crown against a flat plane.
        double crownRoom = MAX_REACH - 1 - Math.max(Math.abs(leanX), Math.abs(leanZ));
        double crownR = Math.min(CROWN_R_MIN + rnd.nextDouble() * CROWN_R_RANGE, crownRoom);
        double crownH = CROWN_H_MIN + rnd.nextDouble() * CROWN_H_RANGE;
        int crownY = topY + CROWN_LIFT;
        leafLobe(level, cx, cz, Mth.floor(leanX), crownY, Mth.floor(leanZ), crownR, crownH, rnd);

        for (int i = 0; i < branches; i++) {
            // A lump with no room left is dropped, not squared off.
            double room = MAX_REACH - 1 - Math.max(Math.abs(tipX[i]), Math.abs(tipZ[i]));
            if (room < LOBE_R_MIN * 0.6) {
                continue;
            }
            double r = Math.min(LOBE_R_MIN + rnd.nextDouble() * LOBE_R_RANGE, room);
            double h = LOBE_H_MIN + rnd.nextDouble() * LOBE_H_RANGE;
            leafLobe(level, cx, cz, tipX[i], tipY[i], tipZ[i], r, h, rnd);
        }

        hangMoss(level, cx, cz, Mth.floor(leanX), crownY, Mth.floor(leanZ), crownR, crownH, rnd);
    }

    /** One flattened blob of leaf: solid through the middle, ragged at the rim. */
    private void leafLobe(WorldGenLevel level, int cx, int cz,
                          int ox, int oy, int oz, double r, double h, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int span = Mth.ceil(r);
        int tall = Mth.ceil(h);

        for (int dy = -tall; dy <= tall; dy++) {
            int y = oy + dy;
            if (level.isOutsideBuildHeight(y)) {
                continue;
            }
            for (int dx = -span; dx <= span; dx++) {
                int gx = ox + dx;
                if (Math.abs(gx) > MAX_REACH) {
                    continue;
                }
                for (int dz = -span; dz <= span; dz++) {
                    int gz = oz + dz;
                    if (Math.abs(gz) > MAX_REACH) {
                        continue;
                    }
                    double v = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (h * h);
                    if (v > 1.0) {
                        continue;
                    }
                    if (v > LOBE_CORE
                            && rnd.nextFloat() < (v - LOBE_CORE) / (1.0 - LOBE_CORE)) {
                        continue;
                    }
                    pos.set(cx + gx, y, cz + gz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, LEAF, 2);
                    }
                }
            }
        }
    }

    /** A ball of bark, used to walk a branch outwards. */
    private void stamp(WorldGenLevel level, int cx, int cz,
                       double ox, int oy, double oz, double r, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int span = Mth.ceil(r);
        for (int dy = -span; dy <= span; dy++) {
            int y = oy + dy;
            if (level.isOutsideBuildHeight(y)) {
                continue;
            }
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    int gx = Mth.floor(ox) + dx;
                    int gz = Mth.floor(oz) + dz;
                    if (Math.abs(gx) > MAX_REACH || Math.abs(gz) > MAX_REACH) {
                        continue;
                    }
                    if (dx * dx + dy * dy + dz * dz > r * r) {
                        continue;
                    }
                    pos.set(cx + gx, y, cz + gz);
                    // Small steps, so most balls land on wood already there.
                    BlockState here = level.getBlockState(pos);
                    if (here.is(Blocks.PALE_OAK_WOOD) || here.is(Blocks.STRIPPED_PALE_OAK_WOOD)) {
                        continue;
                    }
                    level.setBlock(pos, rnd.nextFloat() < STRIPE_CHANCE ? SCAR : BARK, 2);
                }
            }
        }
    }

    /** Strands off the underside of the crown. */
    private void hangMoss(WorldGenLevel level, int cx, int cz,
                          int ox, int oy, int oz, double r, double h, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int span = Mth.ceil(r);

        for (int i = 0; i < MOSS_STRANDS; i++) {
            int gx = ox + rnd.nextInt(span * 2 + 1) - span;
            int gz = oz + rnd.nextInt(span * 2 + 1) - span;
            if (Math.abs(gx) > MAX_REACH || Math.abs(gz) > MAX_REACH) {
                continue;
            }
            // Walk up from under the lobe until leaf is found, then hang from there.
            int from = oy - Mth.ceil(h) - 2;
            int anchor = Integer.MIN_VALUE;
            for (int y = from; y <= oy; y++) {
                pos.set(cx + gx, y, cz + gz);
                if (level.getBlockState(pos).is(Blocks.PALE_OAK_LEAVES)) {
                    anchor = y;
                    break;
                }
            }
            if (anchor == Integer.MIN_VALUE) {
                continue;
            }

            int len = MOSS_LEN_MIN + rnd.nextInt(MOSS_LEN_EXTRA);
            for (int s = 1; s <= len; s++) {
                int y = anchor - s;
                if (level.isOutsideBuildHeight(y)) {
                    break;
                }
                pos.set(cx + gx, y, cz + gz);
                if (!level.getBlockState(pos).isAir()) {
                    break;
                }
                level.setBlock(pos, s == len ? MOSS_TIP : MOSS_MID, 2);
            }
        }
    }

    // --- ground -----------------------------------------------------------

    /** Turns the dune under the tree into forest floor. */
    private void litterFloor(WorldGenLevel level, int[] ground, int cx, int cz,
                             double radius, RandomSource rnd) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int span = Math.min(MAX_REACH, Mth.ceil(radius));

        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                if (rnd.nextFloat() < dist / radius) {
                    continue; // ragged, fading edge
                }
                int y = ground[index(dx, dz)] - 1;
                if (level.isOutsideBuildHeight(y)) {
                    continue;
                }
                pos.set(cx + dx, y, cz + dz);
                if (!isSoil(level.getBlockState(pos))) {
                    continue;
                }
                level.setBlock(pos, FLOOR[rnd.nextInt(FLOOR.length)], 2);
            }
        }
    }

    // --- checks -----------------------------------------------------------

    private static boolean canRootOn(WorldGenLevel level, int x, int baseY, int z) {
        if (AbyssTerrain.isMonolith(x, baseY - 1, z)) {
            return false;
        }
        return isSoil(level.getBlockState(new BlockPos(x, baseY - 1, z)));
    }

    private static boolean isSoil(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PALE_MOSS_BLOCK);
    }

    /**
     * The dunes terrace into cliffs around the monoliths and pits, and a trunk this wide
     * straddling one hangs in the air. Probe the footprint and leave steep spots bare.
     */
    private static boolean flatEnough(int[] ground, int baseY) {
        int low = baseY;
        int high = baseY;
        for (int dx = -FLAT_PROBE; dx <= FLAT_PROBE; dx += FLAT_PROBE) {
            for (int dz = -FLAT_PROBE; dz <= FLAT_PROBE; dz += FLAT_PROBE) {
                int y = ground[index(dx, dz)];
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        }
        return high - low <= MAX_GROUND_SPREAD;
    }

    private static int index(int dx, int dz) {
        return (dx + MAX_REACH) * MAP_SIDE + (dz + MAX_REACH);
    }
}
