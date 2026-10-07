package abyssalmadness.cleansing;

import abyssalmadness.network.CleansingPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// "Очистка": something you never see sweeps down the level and passes through you.
// No entity and no model - just a moving point and the noise it makes. You survive it
// by burying yourself: solid block overhead, sides sealed, wait out the silence.
public final class Cleansing {
    private static final double SPAWN_DISTANCE = 160.0; // where it starts, from the target
    private static final double OVERRUN = 90.0;         // how far past the target it carries on
    private static final double SPEED = 1.6;            // blocks per tick
    private static final double SWEEP_RADIUS = 12.0;    // the moment it is judged to pass you
    private static final double FEEL_RADIUS = 110.0;    // where the screen starts to react


    // Named in the command: exactly these walk away. Empty means cover decides.
    private static final Set<String> chosen = new HashSet<>();

    private static boolean active;
    private static ServerLevel level;
    private static Vec3 position = Vec3.ZERO;
    private static Vec3 heading = Vec3.ZERO;
    private static double travelled;
    private static double totalDistance;
    private static boolean swept;
    private static int age;

    private Cleansing() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Cleansing::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> stopQuietly());
    }

    public static boolean isActive() {
        return active;
    }

    /** Distance from the Cleansing to a point, or -1 when nothing is running. */
    public static double distanceTo(Vec3 point) {
        return active ? position.distanceTo(point) : -1.0;
    }

    /** Launches a run aimed through {@code target}, with a hand-picked survivor list. */
    public static void start(ServerPlayer target, Collection<String> survivorNames) {
        chosen.clear();
        for (String name : survivorNames) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                chosen.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        start(target);
    }

    /** True when the run has a hand-picked survivor list rather than using cover. */
    public static boolean isScripted() {
        return !chosen.isEmpty();
    }

    /** Whether this player lives through a pass: the named list wins if there is one. */
    public static boolean survives(ServerPlayer player) {
        if (!chosen.isEmpty()) {
            return chosen.contains(player.getName().getString().toLowerCase(Locale.ROOT));
        }
        return isBuried(player);
    }

    /** Launches a run aimed through {@code target}. */
    public static void start(ServerPlayer target) {
        level = target.level();
        RandomSource rnd = level.getRandom();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        Vec3 from = target.position().add(
                Math.cos(angle) * SPAWN_DISTANCE, 0.0, Math.sin(angle) * SPAWN_DISTANCE);

        position = from;
        heading = target.position().subtract(from).normalize();
        totalDistance = SPAWN_DISTANCE + OVERRUN;
        travelled = 0.0;
        swept = false;
        age = 0;
        active = true;

        // The only warning is the sound. Both live on the client and are triggered by
        // the payload below.
    }

    public static void stop() {
        if (!active) {
            return;
        }
        stopQuietly();
    }

    private static void stopQuietly() {
        active = false;
        swept = false;
        travelled = 0.0;
        age = 0;
        if (level != null) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, new CleansingPayload(false, 0.0F, 0.0F, 0.0F, 0.0F));
            }
        }
        level = null;
    }

    private static void tick(MinecraftServer server) {
        if (!active || level == null) {
            return;
        }
        age++;
        position = position.add(heading.scale(SPEED));
        travelled += SPEED;

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            if (player.level() != level) {
                continue;
            }
            double d = position.distanceTo(player.position());
            nearest = Math.min(nearest, d);
            // 0 far away, 1 right on top of them
            float intensity = (float) Math.max(0.0, Math.min(1.0, 1.0 - d / FEEL_RADIUS));
            ServerPlayNetworking.send(player, new CleansingPayload(true, intensity,
                    (float) position.x, (float) position.y, (float) position.z));
        }

        if (!swept && nearest <= SWEEP_RADIUS) {
            swept = true;
            judge(players);
        }

        if (travelled >= totalDistance) {
            stop();
        }
    }

    // Nothing is played from here: a server-side playSound is pinned to the spot it
    // was fired from and always arrives from one direction. The client steers both
    // sounds off the position in the payload instead, so the pass crosses the ears.

    /** The pass itself. Nothing is written to chat - whoever stood in the open dies. */
    private static void judge(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player.level() != level || survives(player)) {
                continue;
            }
            player.hurtServer(level, level.damageSources().magic(), Float.MAX_VALUE);
        }
    }

    /** Everyone who would live through a pass right now. */
    public static List<String> survivors(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (survives(player)) {
                names.add(player.getName().getString());
            }
        }
        return names;
    }

    /** Everyone who would not. */
    public static List<String> exposed(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!survives(player)) {
                names.add(player.getName().getString());
            }
        }
        return names;
    }

    /** Solid overhead and the sides closed in. A doorway does not count. */
    public static boolean isBuried(ServerPlayer player) {
        ServerLevel world = player.level();
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (!world.getBlockState(head.above()).isSolidRender()) {
            return false; // open sky above, or at least a gap
        }
        int walls = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(head.relative(dir)).isSolidRender()) {
                walls++;
            }
        }
        return walls >= 3;
    }

}
