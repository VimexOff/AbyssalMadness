package abyssalmadness.client.blackout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-only state for the blackout. Nothing here touches the world - the effect
 * lives inside the chunk mesh builder.
 *
 * {@link #override} runs on the chunk build worker threads, so the config is published
 * as one immutable snapshot behind a single volatile field. The mutable fields below
 * are client thread only.
 */
public final class BlackoutState {
    /** Read by chunk build workers. Null means "effect is off, do not touch anything". */
    private static volatile Snapshot active = null;

    // Client thread only.
    private static boolean enabled = false;
    private static boolean follow = true;
    private static int radius = 24;
    private static float noise = 0.30F;
    private static Palette palette = Palette.MONO;
    private static BlockPos origin = null;

    private BlackoutState() {
    }

    // --- the hot path -----------------------------------------------------

    /**
     * What to render here, or null to leave the real block alone. Once per block per
     * mesh build, so ints rather than a BlockPos.
     */
    public static BlockState override(int x, int y, int z, BlockState original) {
        Snapshot snapshot = active;

        // blocksMotion() leaves plants, torches and water alone, so the world keeps
        // its silhouette instead of inflating into solid cubes.
        if (snapshot == null || original.isAir() || !original.blocksMotion()) {
            return null;
        }

        double dx = (x + 0.5D) - snapshot.x;
        double dy = (y + 0.5D) - snapshot.y;
        double dz = (z + 0.5D) - snapshot.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq >= snapshot.radiusSq) {
            return null;
        }

        // 0 at the player, 1 at the edge of the sphere.
        float fade = (float) (Math.sqrt(distanceSq) * snapshot.invRadius);
        fade += (noiseAt(x, y, z) - 0.5F) * snapshot.noise;

        // Noise pushes edge blocks past 1, dissolving the border instead of leaving a
        // hard sphere outline.
        if (fade >= 1.0F) {
            return null;
        }
        if (fade < 0.0F) {
            fade = 0.0F;
        }

        BlockState[] steps = snapshot.steps;
        int step = (int) (fade * steps.length);
        return steps[Math.min(step, steps.length - 1)];
    }

    /** Two octaves: five-block cells for blotches, per-block grain on top. */
    private static float noiseAt(int x, int y, int z) {
        float grain = hash01(x, y, z, 0x5DEECE66DL);
        float blotch = hash01(Math.floorDiv(x, 5), Math.floorDiv(y, 5), Math.floorDiv(z, 5), 0x9E3779B9L);
        return grain * 0.4F + blotch * 0.6F;
    }

    /** splitmix64-flavoured hash of a block position, in [0, 1). */
    private static float hash01(int x, int y, int z, long salt) {
        long h = salt;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= z * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 32;
        return (float) ((h >>> 11) * 0x1.0p-53);
    }

    // --- config -----------------------------------------------------------

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isFollow() {
        return follow;
    }

    public static int getRadius() {
        return radius;
    }

    public static Palette getPalette() {
        return palette;
    }

    public static BlockPos getOrigin() {
        return origin;
    }

    /** Takes the whole config in one go: it only ever arrives from the server that way. */
    public static void configure(boolean on, int radiusValue, float noiseValue,
                                 int paletteIndex, boolean followValue) {
        enabled = on;
        radius = radiusValue;
        noise = noiseValue;
        follow = followValue;
        Palette[] all = Palette.values();
        palette = all[Math.clamp(paletteIndex, 0, all.length - 1)];
        publish();
    }

    public static void setOrigin(BlockPos value) {
        origin = value;
        publish();
    }

    /** Called on disconnect so the effect never leaks into the next world. */
    public static void reset() {
        enabled = false;
        origin = null;
        active = null;
    }

    private static void publish() {
        if (!enabled || origin == null) {
            active = null;
            return;
        }
        active = new Snapshot(
                origin.getX() + 0.5D,
                origin.getY() + 0.5D,
                origin.getZ() + 0.5D,
                (double) radius * radius,
                1.0D / radius,
                noise,
                palette.steps
        );
    }

    /** Immutable view of the config, swapped in one volatile write. */
    private record Snapshot(double x, double y, double z, double radiusSq, double invRadius,
                            float noise, BlockState[] steps) {
    }

    // --- palettes ---------------------------------------------------------

    /**
     * Dark to light: index 0 at the player, the last at the edge. Constant order is the
     * wire format - the server sends a palette as its index here.
     */
    public enum Palette {
        /** Black concrete fading through the vanilla greyscale ramp. */
        MONO("mono",
                Blocks.BLACK_CONCRETE,
                Blocks.BLACK_CONCRETE_POWDER,
                Blocks.BLACKSTONE,
                Blocks.GRAY_CONCRETE,
                Blocks.GRAY_CONCRETE_POWDER,
                Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.LIGHT_GRAY_CONCRETE_POWDER),

        /** Stays pitch black almost all the way out, then dissolves. */
        VOID("void",
                Blocks.BLACK_CONCRETE,
                Blocks.BLACK_CONCRETE,
                Blocks.BLACK_CONCRETE,
                Blocks.BLACK_CONCRETE_POWDER,
                Blocks.BLACKSTONE,
                Blocks.GRAY_CONCRETE),

        /** Heavier stone textures, more visible surface noise. */
        ROUGH("rough",
                Blocks.BLACK_CONCRETE,
                Blocks.BLACKSTONE,
                Blocks.DEEPSLATE,
                Blocks.SMOOTH_BASALT,
                Blocks.COBBLED_DEEPSLATE,
                Blocks.TUFF,
                Blocks.ANDESITE);

        private final String id;
        private final BlockState[] steps;

        Palette(String id, Block... blocks) {
            this.id = id;
            this.steps = new BlockState[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                this.steps[i] = blocks[i].defaultBlockState();
            }
        }

        public String id() {
            return id;
        }
    }
}
