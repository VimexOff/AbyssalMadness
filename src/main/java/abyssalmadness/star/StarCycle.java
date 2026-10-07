package abyssalmadness.star;

import abyssalmadness.infection.InfectionState;
import abyssalmadness.network.StarStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// While the stars are awake, every player in the Sleeping Stars Valley
// (distance >= 5000) who can see the sky gains infection. Cover keeps them safe.
// Runs on a slow random cycle and can be forced with /abyss stars.
public final class StarCycle {
    private static final int VALLEY_MIN_DISTANCE = 5000;
    private static final int VALLEY_MAX_DISTANCE = 10000;

    // Auto-cycle timings (game ticks, 20/s).
    private static final int SLEEP_MIN = 6000;    // 5 min
    private static final int SLEEP_MAX = 18000;   // 15 min
    private static final int WAKE_MIN = 600;      // 30 s
    private static final int WAKE_MAX = 1800;     // 90 s

    private static final int APPLY_INTERVAL = 40; // re-apply the effect every 2 s
    private static final int EXPOSURE_INFECTION = 1;  // % per application while exposed

    private static boolean awake = false;
    private static long changeAtTick = -1L; // when the current phase ends
    private static boolean initialized = false;

    private StarCycle() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StarCycle::tick);
        // Tell joining players the current state so their sky matches.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.getPlayer(), new StarStatePayload(awake)));
    }

    private static void broadcastState(MinecraftServer server) {
        StarStatePayload payload = new StarStatePayload(awake);
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();

        if (!initialized) {
            changeAtTick = now + randomBetween(overworld, SLEEP_MIN, SLEEP_MAX);
            initialized = true;
        }

        if (awake) {
            if (now >= changeAtTick) {
                setAsleep(server, now, true);
            } else if (now % APPLY_INTERVAL == 0) {
                afflictExposedPlayers(server);
            }
        } else if (now >= changeAtTick) {
            setAwake(server, now, randomBetween(overworld, WAKE_MIN, WAKE_MAX), true);
        }
    }

    private static void afflictExposedPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            if (level != server.overworld()) {
                continue;
            }
            long distance = Math.max(Math.abs((long) player.getBlockX()), Math.abs((long) player.getBlockZ()));
            // Only inside the Valley itself. Without the upper bound this caught every
            // level past 5000, so players were being infected simply for standing on
            // the far levels under an open sky.
            if (distance < VALLEY_MIN_DISTANCE || distance > VALLEY_MAX_DISTANCE) {
                continue;
            }
            if (!level.canSeeSky(player.blockPosition())) {
                continue; // under cover, safe
            }
            InfectionState.add(player, EXPOSURE_INFECTION);
        }
    }

    // --- state changes (also used by the command) -------------------------

    public static void forceWake(MinecraftServer server, int seconds) {
        setAwake(server, server.overworld().getGameTime(), seconds * 20L, true);
    }

    public static void forceSleep(MinecraftServer server) {
        setAsleep(server, server.overworld().getGameTime(), true);
    }

    public static boolean isAwake() {
        return awake;
    }

    public static long ticksLeft(MinecraftServer server) {
        return Math.max(0L, changeAtTick - server.overworld().getGameTime());
    }

    private static void setAwake(MinecraftServer server, long now, long durationTicks, boolean announce) {
        awake = true;
        initialized = true;
        changeAtTick = now + durationTicks;
        broadcastState(server);
        if (announce) {
            playWakeCue(server);
        }
    }

    private static void setAsleep(MinecraftServer server, long now, boolean announce) {
        awake = false;
        initialized = true;
        changeAtTick = now + randomBetween(server.overworld(), SLEEP_MIN, SLEEP_MAX);
        broadcastState(server);
    }

    // low ambient cue when the stars wake, no chat message
    private static void playWakeCue(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.AMBIENT, 0.7F, 1.0F);
        }
    }

    private static int randomBetween(ServerLevel level, int min, int max) {
        return min + level.getRandom().nextInt(max - min + 1);
    }
}
