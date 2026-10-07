package abyssalmadness.teleport;

import abyssalmadness.atmosphere.FogToolsBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Level 4 ("Бесконечность"): the fog moves you. It closes to arm's length, you are put
// somewhere else in the level, and it opens back out.
//
// The destination keeps your height: the level is an endless lattice with no floor or
// ceiling, and landing at the same altitude never shows you an edge.
public final class FogTeleport {
    // The Level 4 ring, kept off its own borders so a jump never lands you in the
    // neighbouring level.
    private static final int LEVEL4_MIN = 15000;
    private static final int LEVEL4_MAX = 20000;
    private static final int EDGE_MARGIN = 250;

    // Fog, exactly as the brief specifies it.
    private static final float NARROW_RADIUS = 1.0F; // FogTools' own minimum: total whiteout
    private static final float RESTORE_RADIUS = 219.0F;
    private static final int FADE_IN = 50;
    private static final int FADE_OUT = 100;
    private static final int HOLD_TICKS = 60;   // let it close (and sit shut a beat) first
    // Blind for a while after the jump too: the arm snaps and the terrain pops in as
    // chunks arrive, and the fog has to cover all of it.
    private static final int SETTLE_TICKS = 60;

    private static final int SEARCH_Y = 64;   // how far up/down to look for a girder
    private static final int ATTEMPTS = 6;    // candidate chunks before giving up

    // Two beats per player: close-and-jump, then settle-and-reopen.
    private static final class Step {
        private int ticks;
        private boolean teleported;

        private Step(int ticks) {
            this.ticks = ticks;
        }
    }

    private static final Map<UUID, Step> pending = new HashMap<>();

    private FogTeleport() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FogTeleport::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                pending.remove(handler.getPlayer().getUUID()));
    }

    /** Starts the sequence: the fog closes, then the player is moved. */
    public static void begin(ServerPlayer player) {
        FogToolsBridge.sendSet(player, NARROW_RADIUS, 1.0F, FogToolsBridge.color("WHITE"),
                false, 1.0F, FADE_IN, FADE_OUT);
        pending.put(player.getUUID(), new Step(HOLD_TICKS));
    }

    private static void tick(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Step>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Step> entry = it.next();
            Step step = entry.getValue();
            if (--step.ticks > 0) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (!step.teleported) {
                jump(player);
                step.teleported = true;
                step.ticks = SETTLE_TICKS; // hold the whiteout over the landing
            } else {
                FogToolsBridge.sendSet(player, RESTORE_RADIUS, 1.0F, FogToolsBridge.color("WHITE"),
                        false, 1.0F, FADE_IN, FADE_OUT);
                it.remove();
            }
        }
    }

    private static void jump(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos dest = findDestination(level, player.getBlockY());
        if (dest != null) {
            player.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        }
    }

    /** A random spot elsewhere in the Level 4 ring, at roughly {@code aroundY}. */
    private static BlockPos findDestination(ServerLevel level, int aroundY) {
        RandomSource rnd = level.getRandom();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int span = LEVEL4_MAX - LEVEL4_MIN - 2 * EDGE_MARGIN;
            int d = LEVEL4_MIN + EDGE_MARGIN + rnd.nextInt(span);
            int along = rnd.nextInt(2 * d + 1) - d;
            int x;
            int z;
            switch (rnd.nextInt(4)) {   // which edge of the square ring
                case 0 -> { x = d; z = along; }
                case 1 -> { x = -d; z = along; }
                case 2 -> { x = along; z = d; }
                default -> { x = along; z = -d; }
            }

            level.getChunk(x >> 4, z >> 4); // generate it so the blocks can be read
            BlockPos spot = findFooting(level, x, z, aroundY);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Somewhere to stand in the candidate chunk: solid, with headroom, as close to
     * {@code aroundY} as possible. Several columns, so one chunk gives many chances.
     */
    private static BlockPos findFooting(ServerLevel level, int x, int z, int aroundY) {
        int baseX = x & ~15;
        int baseZ = z & ~15;
        int minY = Math.max(level.getMinY() + 1, aroundY - SEARCH_Y);
        int maxY = Math.min(level.getMaxY() - 3, aroundY + SEARCH_Y);

        BlockPos best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int dx = 1; dx < 16; dx += 4) {
            for (int dz = 1; dz < 16; dz += 4) {
                int cx = baseX + dx;
                int cz = baseZ + dz;
                for (int y = minY; y <= maxY; y++) {
                    int gap = Math.abs(y - aroundY);
                    if (gap >= bestGap) {
                        continue;
                    }
                    BlockPos floor = new BlockPos(cx, y, cz);
                    if (!level.getBlockState(floor).blocksMotion()
                            || !level.getBlockState(floor.above()).isAir()
                            || !level.getBlockState(floor.above(2)).isAir()) {
                        continue;
                    }
                    best = floor.above();
                    bestGap = gap;
                }
            }
        }
        return best;
    }
}
