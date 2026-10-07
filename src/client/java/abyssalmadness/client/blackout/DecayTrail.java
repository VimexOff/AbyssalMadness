package abyssalmadness.client.blackout;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The scar the player leaves behind: every column walked through renders as a decayed
 * version of itself until cleared. Like {@link BlackoutState} it never writes to the
 * world; the rot is applied while the chunk mesh is built.
 *
 * Stored per chunk as a {@code short[256]} of anchor Y values, so a column only rots
 * within {@link #getReach()} blocks of where the player actually walked. Arrays are
 * copy-on-write: build workers read them while the client thread writes.
 */
public final class DecayTrail {
    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 8;
    public static final int DEFAULT_RADIUS = 2;
    public static final int MIN_REACH = 1;
    public static final int MAX_REACH = 64;
    public static final int DEFAULT_REACH = 16;

    /** Column has never been walked. */
    private static final short ABSENT = Short.MIN_VALUE;
    /** Roughly 4 MB of trail before new ground stops being recorded. */
    private static final int MAX_CHUNKS = 8192;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState DEAD_BUSH = Blocks.DEAD_BUSH.defaultBlockState();
    private static final BlockState WITHER_ROSE = Blocks.WITHER_ROSE.defaultBlockState();
    private static final BlockState SNOWY_GRASS = Blocks.GRASS_BLOCK.defaultBlockState()
            .setValue(BlockStateProperties.SNOWY, true);

    private static final ConcurrentHashMap<Long, short[]> CHUNKS = new ConcurrentHashMap<>();

    // Read by chunk build workers.
    private static volatile boolean active = false;
    private static volatile int reach = DEFAULT_REACH;
    private static volatile int version = 0;

    // Client thread only.
    private static boolean enabled = false;
    private static int radius = DEFAULT_RADIUS;

    private DecayTrail() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DecayTrail::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    // ---------------------------------------------------------------- the hot path

    /** The rotted version of {@code state}, or {@code state} if this column is clean. */
    public static BlockState apply(int x, int y, int z, BlockState state) {
        if (!active) {
            return state;
        }

        short anchor = anchorAt(x, z);

        if (anchor == ABSENT) {
            return state;
        }

        int delta = y - anchor;
        int limit = reach;

        if (delta < -limit || delta > limit) {
            return state;
        }

        return rot(state);
    }

    /**
     * Mesh building walks a whole section, so consecutive lookups hit the same chunk.
     * One cached chunk per worker keeps the map and its key boxing off the hot path.
     */
    private static short anchorAt(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        Lookup lookup = LOOKUP.get();

        if (lookup.key != key || lookup.version != version) {
            lookup.key = key;
            lookup.version = version;
            lookup.cells = CHUNKS.get(key);
        }

        short[] cells = lookup.cells;
        return cells == null ? ABSENT : cells[((z & 15) << 4) | (x & 15)];
    }

    private static BlockState rot(BlockState state) {
        // Leaves first: flowering azalea is in both LEAVES and FLOWERS and should fall,
        // not turn into a rose.
        if (state.is(BlockTags.LEAVES)) {
            return AIR;
        }

        if (state.is(BlockTags.LOGS)) {
            return paleLog(state);
        }

        if (state.is(Blocks.GRASS_BLOCK)) {
            return SNOWY_GRASS;
        }

        if (state.is(Blocks.WATER) || isAquaticPlant(state)) {
            // Meshes read fluids off the block state, so dropping it dries the column.
            // Kelp and seagrass go too, or they hang in the empty basin.
            return AIR;
        }

        if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return state.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        if (state.is(BlockTags.FLOWERS)) {
            return isUpperHalf(state) ? AIR : WITHER_ROSE;
        }

        if (isGrassPlant(state)) {
            return isUpperHalf(state) ? AIR : DEAD_BUSH;
        }

        return state;
    }

    private static BlockState paleLog(BlockState state) {
        BlockState log = Blocks.PALE_OAK_LOG.defaultBlockState();

        if (state.hasProperty(BlockStateProperties.AXIS) && log.hasProperty(BlockStateProperties.AXIS)) {
            return log.setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
        }

        return log;
    }

    private static boolean isGrassPlant(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    /** The top block of a two-tall plant has nothing to stand on once the bottom rots. */
    private static boolean isUpperHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    private static final class Lookup {
        long key = Long.MIN_VALUE;
        int version = -1;
        short[] cells;
    }

    private static final ThreadLocal<Lookup> LOOKUP = ThreadLocal.withInitial(Lookup::new);

    // ---------------------------------------------------------------- recording

    private static void tick(Minecraft client) {
        if (!enabled) {
            return;
        }

        LocalPlayer player = client.player;

        if (player == null || client.level == null) {
            return;
        }

        BlockPos feet = player.blockPosition();
        int px = feet.getX();
        int pz = feet.getZ();
        short py = (short) Math.clamp(feet.getY(), Short.MIN_VALUE + 1, Short.MAX_VALUE);

        boolean changed = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }

                changed |= mark(px + dx, pz + dz, py);
            }
        }

        if (changed) {
            version++;
            markDirty(px, pz, py);
        }
    }

    /** Returns true if this column was not already recorded at this height. */
    private static boolean mark(int x, int z, short y) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        int index = ((z & 15) << 4) | (x & 15);
        short[] existing = CHUNKS.get(key);

        if (existing != null && existing[index] == y) {
            return false;
        }

        if (existing == null && CHUNKS.size() >= MAX_CHUNKS) {
            return false;
        }

        short[] copy;

        if (existing == null) {
            copy = new short[256];
            Arrays.fill(copy, ABSENT);
        } else {
            copy = existing.clone();
        }

        copy[index] = y;
        CHUNKS.put(key, copy);
        return true;
    }

    private static void markDirty(int x, int z, int y) {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null) {
            return;
        }

        int spread = radius + 1;
        int minY = Math.max(client.level.getMinY(), y - reach - 1);
        int maxY = Math.min(client.level.getMaxY(), y + reach + 1);

        client.levelRenderer.setBlocksDirty(x - spread, minY, z - spread, x + spread, maxY, z + spread);
    }

    // ---------------------------------------------------------------- config

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        active = value;
        version++;
    }

    public static int getRadius() {
        return radius;
    }

    public static void setRadius(int value) {
        radius = Math.clamp(value, MIN_RADIUS, MAX_RADIUS);
    }

    public static int getReach() {
        return reach;
    }

    public static void setReach(int value) {
        reach = Math.clamp(value, MIN_REACH, MAX_REACH);
        version++;
    }

    public static int getChunkCount() {
        return CHUNKS.size();
    }

    public static boolean isFull() {
        return CHUNKS.size() >= MAX_CHUNKS;
    }

    /** Wipes the trail. Caller is expected to force a renderer reload after. */
    public static void clear() {
        CHUNKS.clear();
        version++;
    }

    private static void reset() {
        enabled = false;
        active = false;
        CHUNKS.clear();
        version++;
    }
}
