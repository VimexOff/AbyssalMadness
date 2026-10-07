package abyssalmadness.blackout;

import abyssalmadness.network.BlackoutPayload;
import abyssalmadness.network.DecayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The world going out, and the scar left behind by walking through it. Config lives
 * here and is pushed to clients; the painting itself happens in the chunk mesh builder,
 * so no block is ever changed.
 *
 * One set of settings for the whole server, not one per player - two people filming the
 * same take have to see the same darkness.
 */
public final class Blackout {
    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 96;
    public static final int DEFAULT_RADIUS = 24;
    public static final float DEFAULT_NOISE = 0.30F;

    public static final int DECAY_MIN_RADIUS = 0;
    public static final int DECAY_MAX_RADIUS = 8;
    public static final int DECAY_DEFAULT_RADIUS = 2;
    public static final int DECAY_MIN_REACH = 1;
    public static final int DECAY_MAX_REACH = 64;
    public static final int DECAY_DEFAULT_REACH = 16;

    /** Must stay in the same order as the client's palette enum: the packet sends an index. */
    public static final List<String> PALETTES = List.of("mono", "void", "rough");

    private static boolean enabled;
    private static int radius = DEFAULT_RADIUS;
    private static float noise = DEFAULT_NOISE;
    private static int palette;
    private static boolean follow = true;

    private static boolean decay;
    private static int decayRadius = DECAY_DEFAULT_RADIUS;
    private static int decayReach = DECAY_DEFAULT_REACH;

    private Blackout() {
    }

    public static void register() {
        // A joiner's client is clean and would be the only one still seeing daylight.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayNetworking.send(handler.getPlayer(), blackoutPacket(false));
            ServerPlayNetworking.send(handler.getPlayer(), decayPacket(false));
        });
    }

    // --- blackout ---------------------------------------------------------

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(MinecraftServer server, boolean on) {
        enabled = on;
        // Switching on re-centres on whoever is standing there now.
        push(server, on);
    }

    public static int radius() {
        return radius;
    }

    public static void setRadius(MinecraftServer server, int value) {
        radius = Math.clamp(value, MIN_RADIUS, MAX_RADIUS);
        push(server, false);
    }

    public static float noise() {
        return noise;
    }

    public static void setNoise(MinecraftServer server, float value) {
        noise = Math.clamp(value, 0.0F, 1.0F);
        push(server, false);
    }

    public static String palette() {
        return PALETTES.get(palette);
    }

    /** False when no palette goes by that name. */
    public static boolean setPalette(MinecraftServer server, String name) {
        int found = -1;
        for (int i = 0; i < PALETTES.size(); i++) {
            if (PALETTES.get(i).equalsIgnoreCase(name)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            return false;
        }
        palette = found;
        push(server, false);
        return true;
    }

    public static boolean follows() {
        return follow;
    }

    public static void setFollow(MinecraftServer server, boolean on) {
        follow = on;
        // Snap to the player rather than drifting there over the next few steps.
        push(server, on);
    }

    /** Re-centres the sphere on wherever each player is standing right now. */
    public static void anchor(MinecraftServer server) {
        push(server, true);
    }

    // --- decay ------------------------------------------------------------

    public static boolean decayEnabled() {
        return decay;
    }

    public static void setDecay(MinecraftServer server, boolean on) {
        decay = on;
        pushDecay(server, false);
    }

    public static int decayRadius() {
        return decayRadius;
    }

    public static void setDecayRadius(MinecraftServer server, int value) {
        decayRadius = Math.clamp(value, DECAY_MIN_RADIUS, DECAY_MAX_RADIUS);
        pushDecay(server, false);
    }

    public static int decayReach() {
        return decayReach;
    }

    public static void setDecayReach(MinecraftServer server, int value) {
        decayReach = Math.clamp(value, DECAY_MIN_REACH, DECAY_MAX_REACH);
        pushDecay(server, false);
    }

    /** The trail lives on the clients, so this is an order rather than a state change. */
    public static void wipeTrail(MinecraftServer server) {
        pushDecay(server, true);
    }

    // --- wire -------------------------------------------------------------

    private static BlackoutPayload blackoutPacket(boolean reanchor) {
        return new BlackoutPayload(enabled, radius, noise, palette, follow, reanchor);
    }

    private static DecayPayload decayPacket(boolean wipe) {
        return new DecayPayload(decay, decayRadius, decayReach, wipe);
    }

    private static void push(MinecraftServer server, boolean reanchor) {
        send(server, blackoutPacket(reanchor));
    }

    private static void pushDecay(MinecraftServer server, boolean wipe) {
        send(server, decayPacket(wipe));
    }

    private static void send(MinecraftServer server, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
