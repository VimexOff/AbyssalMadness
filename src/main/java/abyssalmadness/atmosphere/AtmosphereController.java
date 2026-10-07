package abyssalmadness.atmosphere;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Per-level fog (through FogTools) and sky, picked by distance from spawn. Entering a
// level sends its fog once, exactly like the /fogtools command, so FogTools runs its
// own fade. Time is held per client (tickDayTime=false) and eased, so it never snaps.
//
// Time is world-global, so in multiplayer the first overworld player drives it.
public final class AtmosphereController {
    private static final Logger LOGGER = LoggerFactory.getLogger("abyssalmadness/atmosphere");

    private static final int CYCLE = 0;   // natural day/night
    private static final int NIGHT = 1;   // hold at night
    private static final int DAY = 2;     // hold at day
    private static final long DAY_TIME = 6000L;
    private static final long NIGHT_TIME = 18000L;

    // Small on purpose: the client is not ticking time itself, so every packet is a
    // discrete jump and big steps stutter against the fixed fog colour.
    private static final long TIME_STEP = 25L;
    private static final int RESYNC_TICKS = 40; // idle resync rate, so we don't spam packets

    private record Zone(String name, boolean fog, float radius, float density, String color,
                        boolean sky, float opacity, int fadeIn, int fadeOut, int time) {
    }

    private record Band(int maxDistance, Zone zone) {
    }

    // ordered by ascending max distance; the first band the distance fits wins
    private static final Band[] BANDS = {
            // The hub is held at noon: the one place meant to read as safe.
            new Band(1500, new Zone("l0", false, 0, 0, "", false, 0, 0, 0, DAY)),
            new Band(4500, new Zone("l1", true, 200, 1, "SILVER", false, 1, 500, 300, NIGHT)),
            new Band(5200, new Zone("l1b", true, 33, 1, "SILVER", false, 1, 900, 1, NIGHT)),
            // Pale dust. Radius tied to the 330-block monolith spacing: one neighbour
            // is a silhouette at the limit, the next is not. Night, for the stars.
            new Band(8000, new Zone("l2", true, 500, 1, "BLACK", false, 1, 400, 1, NIGHT)),
            // The wood. Split at 8000, not at the treeline: up to there the trees are
            // still groves in sand, past it the roof is closed and the air is litter.
            new Band(10000, new Zone("l2wood", true, 320, 1, "4F3E22", false, 1, 150, 100, NIGHT)),
            new Band(15000, new Zone("l3", true, 160, 1, "LIGHT_GRAY", false, 1, 70, 80, NIGHT)),
            new Band(20000, new Zone("l4", true, 219, 1, "WHITE", false, 1, 50, 100, NIGHT)),
            new Band(Integer.MAX_VALUE, new Zone("l5", true, 141, 1, "GOLD", false, 1, 500, 133, DAY)),
    };

    private static final Map<UUID, String> lastZone = new HashMap<>();
    private static boolean timeInit;
    private static long managedTime;
    private static int lastMode = -1;
    private static int sinceSync;

    private AtmosphereController() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AtmosphereController::tick);
        // reset per world so nothing (time, zones) leaks between loads in one session
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            timeInit = false;
            lastMode = -1;
            lastZone.clear();
            LOGGER.info("atmosphere: fogtools available = {}", FogToolsBridge.available());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                lastZone.remove(handler.getPlayer().getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastZone.remove(handler.getPlayer().getUUID()));
    }

    private static Zone zoneFor(long dist) {
        for (Band b : BANDS) {
            if (dist <= b.maxDistance()) {
                return b.zone();
            }
        }
        return BANDS[BANDS.length - 1].zone();
    }

    private static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Zone driver = null;

        for (ServerPlayer player : players) {
            if (player.level() != overworld) {
                continue;
            }
            long dist = Math.max(Math.abs((long) player.getBlockX()), Math.abs((long) player.getBlockZ()));
            Zone zone = zoneFor(dist);
            if (driver == null) {
                driver = zone;
            }

            String prev = lastZone.get(player.getUUID());
            if (!zone.name().equals(prev)) {
                lastZone.put(player.getUUID(), zone.name());
                applyFog(player, zone, dist);
            }
        }

        if (driver != null) {
            driveTime(overworld, players, driver.time());
        }
    }

    private static void applyFog(ServerPlayer player, Zone zone, long dist) {
        if (!zone.fog()) {
            FogToolsBridge.sendClear(player, 200);
            LOGGER.info("atmosphere: zone {} at dist {} -> clear fog", zone.name(), dist);
        } else {
            int rgb = FogToolsBridge.color(zone.color());
            FogToolsBridge.sendSet(player, zone.radius(), zone.density(), rgb,
                    zone.sky(), zone.opacity(), zone.fadeIn(), zone.fadeOut());
            LOGGER.info("atmosphere: zone {} at dist {} -> fog radius={} color={} fadeIn={}",
                    zone.name(), dist, zone.radius(), zone.color(), zone.fadeIn());
        }
    }

    private static void driveTime(ServerLevel overworld, List<ServerPlayer> players, int mode) {
        if (!timeInit) {
            managedTime = overworld.getDayTime();
            timeInit = true;
        }
        boolean cycle = mode == CYCLE;
        long before = managedTime;
        if (cycle) {
            managedTime += 1;
        } else {
            managedTime = advanceToward(managedTime, mode == DAY ? DAY_TIME : NIGHT_TIME);
        }
        overworld.setDayTime(managedTime); // keep server logic (mob spawns, sleep) in sync

        // Every tick while easing so the steps read as motion, otherwise an occasional
        // resync. Spamming a frozen clock tears the sky at the horizon.
        boolean easing = !cycle && managedTime != before;
        boolean modeChanged = mode != lastMode;
        lastMode = mode;
        if (!easing && !modeChanged && ++sinceSync < RESYNC_TICKS) {
            return;
        }
        sinceSync = 0;

        ClientboundSetTimePacket packet =
                new ClientboundSetTimePacket(overworld.getGameTime(), managedTime, cycle);
        for (ServerPlayer player : players) {
            if (player.level() == overworld) {
                player.connection.send(packet);
            }
        }
    }

    // Move forward only, so the sun keeps its natural direction.
    private static long advanceToward(long time, long tod) {
        long target = time - Math.floorMod(time, 24000L) + tod;
        if (target < time) {
            target += 24000L;
        }
        if (target - time <= TIME_STEP) {
            return target;
        }
        return time + TIME_STEP;
    }
}
