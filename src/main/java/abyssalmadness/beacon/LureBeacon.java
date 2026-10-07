package abyssalmadness.beacon;

import abyssalmadness.infection.InfectionState;
import abyssalmadness.network.BeaconPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// A light in the desert worth walking towards, and it should not have been.
//
// No block is placed: the shaft is a post-process pass, which is the only way it
// survives kilometres of draw distance and fog. Anchoring and lighting are separate,
// so the spot can be marked while nobody is looking.
public final class LureBeacon {
    private static final double LURE_RADIUS = 22.0;
    private static final int STEP_TICKS = 10;          // twice a second
    private static final int NEAR_GAIN = 4;            // infection per step at the foot
    private static final int FAR_GAIN = 1;             // ...and at the edge of the pull

    private static ServerLevel level;
    private static BlockPos site;
    private static boolean active;
    private static int timer;

    private LureBeacon() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LureBeacon::tick);
        // A joiner has no idea a beacon is burning until told.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (site != null) {
                ServerPlayNetworking.send(handler.getPlayer(), new BeaconPayload(site, active));
            }
        });
    }

    public static boolean anchored() {
        return site != null;
    }

    public static boolean active() {
        return active;
    }

    public static BlockPos site() {
        return site;
    }

    /** Marks the spot without lighting anything. */
    public static void anchor(ServerLevel where, BlockPos at) {
        level = where;
        site = at;
        active = false;
        timer = 0;
        broadcast(where.getServer());
    }

    /** Turns the light on. Does nothing without an anchor. */
    public static boolean activate() {
        if (site == null) {
            return false;
        }
        active = true;
        timer = 0;
        broadcast(level.getServer());
        return true;
    }

    public static void extinguish() {
        if (site == null) {
            return;
        }
        active = false;
        broadcast(level.getServer());
    }

    private static void broadcast(MinecraftServer server) {
        if (server == null || site == null) {
            return;
        }
        BeaconPayload payload = new BeaconPayload(site, active);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        if (!active || level == null || site == null) {
            return;
        }
        if (++timer < STEP_TICKS) {
            return;
        }
        timer = 0;

        double centreX = site.getX() + 0.5;
        double centreY = site.getY() + 0.5;
        double centreZ = site.getZ() + 0.5;

        for (ServerPlayer player : level.players()) {
            double d = Math.sqrt(player.distanceToSqr(centreX, centreY, centreZ));
            if (d > LURE_RADIUS) {
                continue;
            }
            // Linear from the edge in, so it is felt building rather than switching on.
            double closeness = 1.0 - d / LURE_RADIUS;
            int gain = FAR_GAIN + (int) Math.round((NEAR_GAIN - FAR_GAIN) * closeness);
            InfectionState.add(player, gain);

            if (InfectionState.get(player) >= InfectionState.MAX) {
                // It got what it was for.
                extinguish();
                return;
            }
        }
    }
}
