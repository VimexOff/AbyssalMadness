package abyssalmadness.infection;

import abyssalmadness.network.InfectionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Per-player infection level, 0..100. Server-side source of truth; every change is
// broadcast so clients can render it on the skin.
public final class InfectionState {
    public static final int MAX = 100;

    private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();
    /** Nothing can raise the level of anyone in here. */
    private static final Set<UUID> IMMUNE = ConcurrentHashMap.newKeySet();
    /**
     * Who burns warm instead of cold. By UUID next to the level rather than on the
     * entity: a player is not ours, so there is nowhere on it to hang a synched field.
     */
    private static final Set<UUID> WARM = ConcurrentHashMap.newKeySet();

    private InfectionState() {
    }

    public static void register() {
        // send existing levels to the joiner, and the joiner's level to everyone
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            for (Map.Entry<UUID, Integer> e : LEVELS.entrySet()) {
                ServerPlayNetworking.send(joined,
                        new InfectionPayload(e.getKey(), e.getValue(), WARM.contains(e.getKey())));
            }
            broadcast(server, joined.getUUID(), get(joined));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LEVELS.remove(handler.getPlayer().getUUID()));
    }

    public static int get(ServerPlayer player) {
        return LEVELS.getOrDefault(player.getUUID(), 0);
    }

    /** True while nothing is allowed to infect this player. */
    public static boolean isImmune(UUID who) {
        return IMMUNE.contains(who);
    }

    /** Granting also clears what the player already carries. */
    public static void setImmune(ServerPlayer player, boolean on) {
        if (on) {
            IMMUNE.add(player.getUUID());
            set(player, 0);
        } else {
            IMMUNE.remove(player.getUUID());
        }
    }

    /** Sets the level (clamped) and tells every client. */
    public static void set(ServerPlayer player, int level) {
        int clamped = Math.max(0, Math.min(MAX, level));
        // Clearing always works, raising never does. Here rather than at every call
        // site, so no new source of infection can bypass it.
        if (clamped > 0 && IMMUNE.contains(player.getUUID())) {
            clamped = 0;
        }
        LEVELS.put(player.getUUID(), clamped);
        broadcast(player.level().getServer(), player.getUUID(), clamped);
    }

    public static void add(ServerPlayer player, int amount) {
        set(player, get(player) + amount);
    }

    // Keyed by UUID, so anything can carry a level - the hollows are plain mobs.

    public static void setRaw(MinecraftServer server, UUID who, int level) {
        int clamped = Math.max(0, Math.min(MAX, level));
        if (clamped > 0 && IMMUNE.contains(who)) {
            clamped = 0;
        }
        LEVELS.put(who, clamped);
        broadcast(server, who, clamped);
    }

    public static int raw(UUID who) {
        return LEVELS.getOrDefault(who, 0);
    }

    public static void forget(MinecraftServer server, UUID who) {
        LEVELS.remove(who);
        WARM.remove(who);
        broadcast(server, who, 0);
    }

    /** Switches which light this one burns with, and tells every client. */
    public static void setWarm(MinecraftServer server, UUID who, boolean warm) {
        if (warm) {
            WARM.add(who);
        } else {
            WARM.remove(who);
        }
        broadcast(server, who, raw(who));
    }

    public static boolean isWarm(UUID who) {
        return WARM.contains(who);
    }

    private static void broadcast(MinecraftServer server, UUID who, int level) {
        if (server == null) {
            return;
        }
        InfectionPayload payload = new InfectionPayload(who, level, WARM.contains(who));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
