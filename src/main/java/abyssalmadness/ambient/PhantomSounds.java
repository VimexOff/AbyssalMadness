package abyssalmadness.ambient;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Level 1 only: phantom players you can hear but never find. Density rises with depth
// into the level, from one sound every 15 s to an unbroken wall of them. A burst is
// scattered over the next few ticks rather than fired at once, so it smears instead of
// landing as a volley. Leaving the level kills it instantly, pending sounds included.
public final class PhantomSounds {
    private static final int LEVEL1_START = 1000;
    private static final int RAMP_START = 4250;   // where the build-up kicks in properly
    private static final int LEVEL1_END = 5000;

    // Ticks between bursts: a long sparse walk, then a short run where they crowd in.
    private static final int INTERVAL_FAR = 300;     // 15 s at the very start
    private static final int INTERVAL_MID = 200;     // 10 s just before the ramp
    private static final int INTERVAL_RAMP_IN = 100; // 5 s at RAMP_START
    private static final int INTERVAL_RAMP_OUT = 1;  // 0.05 s at the far edge

    // Sounds per burst at the far edge: the density knob.
    private static final int MAX_BURST = 12;
    // Widens with the build-up, so late sounds trip over each other.
    private static final int SPREAD_MIN = 2;
    private static final int SPREAD_MAX = 16;

    // SoundEvent#getRange is 16 blocks at volume <= 1, and anything further is dropped
    // before it reaches the client. Stay inside it or half the sounds go missing.
    private static final double MIN_RANGE = 6.0;
    private static final double MAX_RANGE = 15.0;

    // Only what exists on Level 1. No sand and no wood: there is neither in the gorges.
    private static final SoundEvent[] STEPS = {
            SoundEvents.STONE_STEP, SoundEvents.DEEPSLATE_STEP, SoundEvents.GRAVEL_STEP,
            SoundEvents.GRASS_STEP, SoundEvents.MOSS_STEP};
    // Someone mining: HIT is the pickaxe tapping away, BREAK is the block giving out.
    private static final SoundEvent[] MINING = {
            SoundEvents.STONE_HIT, SoundEvents.DEEPSLATE_HIT, SoundEvents.GRAVEL_HIT,
            SoundEvents.STONE_BREAK, SoundEvents.DEEPSLATE_BREAK, SoundEvents.GRASS_BREAK};
    private static final SoundEvent[] BLOCKS = {
            SoundEvents.STONE_PLACE, SoundEvents.DEEPSLATE_PLACE, SoundEvents.GRAVEL_PLACE,
            SoundEvents.GRASS_PLACE, SoundEvents.MOSS_PLACE};
    private static final SoundEvent[] HURTS = {
            SoundEvents.PLAYER_HURT};
    // Tells that give away a person, and nothing out of place in an empty gorge.
    private static final SoundEvent[] PRESENCE = {
            SoundEvents.ITEM_PICKUP, SoundEvents.PLAYER_SMALL_FALL};

    // A sound already decided on, waiting out its delay before it is played.
    private static final class Queued {
        private final UUID owner;
        private final ServerLevel level;
        private final double x;
        private final double y;
        private final double z;
        private final SoundEvent sound;
        private final float volume;
        private final float pitch;
        private int ticks;

        private Queued(UUID owner, ServerLevel level, double x, double y, double z,
                       SoundEvent sound, float volume, float pitch, int ticks) {
            this.owner = owner;
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.ticks = ticks;
        }
    }

    private static final List<Queued> queue = new ArrayList<>();
    private static final Map<UUID, Integer> countdown = new HashMap<>();
    private static boolean enabled = true;

    private PhantomSounds() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PhantomSounds::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            countdown.clear();
            queue.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                forget(handler.getPlayer().getUUID()));
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            countdown.clear();
            queue.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 0 at the start of Level 1, 1 at its far edge; -1 when outside the level. */
    public static double progressAt(long dist) {
        if (dist < LEVEL1_START || dist > LEVEL1_END) {
            return -1.0;
        }
        return (dist - LEVEL1_START) / (double) (LEVEL1_END - LEVEL1_START);
    }

    /** Ticks between bursts at this distance. */
    public static int intervalFor(long dist) {
        if (dist < RAMP_START) {
            double t = clamp01((dist - LEVEL1_START) / (double) (RAMP_START - LEVEL1_START));
            return (int) Math.round(INTERVAL_FAR + (INTERVAL_MID - INTERVAL_FAR) * t);
        }
        double t = clamp01((dist - RAMP_START) / (double) (LEVEL1_END - RAMP_START));
        return (int) Math.round(INTERVAL_RAMP_IN + (INTERVAL_RAMP_OUT - INTERVAL_RAMP_IN) * t);
    }

    /** How many sounds a burst carries: single until the ramp, layered after. */
    public static int burstFor(long dist) {
        if (dist < RAMP_START) {
            return 1;
        }
        double t = clamp01((dist - RAMP_START) / (double) (LEVEL1_END - RAMP_START));
        // slight curve, so the pile-up is felt most in the last stretch
        return 1 + (int) Math.round(Math.pow(t, 1.3) * (MAX_BURST - 1));
    }

    public static int maxBurst() {
        return MAX_BURST;
    }

    /** Schedules one burst around the player, scattered over the next few ticks. */
    public static void burst(ServerPlayer player, double progress, int count) {
        ServerLevel level = player.level();
        RandomSource rnd = level.getRandom();
        int spread = SPREAD_MIN + (int) Math.round(progress * (SPREAD_MAX - SPREAD_MIN));
        for (int i = 0; i < count; i++) {
            schedule(player, level, rnd, progress, rnd.nextInt(Math.max(1, spread)));
        }
    }

    private static void tick(MinecraftServer server) {
        playDue();
        if (!enabled) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != overworld) {
                continue;
            }
            long dist = Math.max(Math.abs((long) player.getBlockX()), Math.abs((long) player.getBlockZ()));
            double progress = progressAt(dist);
            UUID id = player.getUUID();
            if (progress < 0.0) {
                forget(id); // left Level 1: cut, pending sounds and all
                continue;
            }

            int left = countdown.getOrDefault(id, 1) - 1;
            if (left > 0) {
                countdown.put(id, left);
                continue;
            }
            burst(player, progress, burstFor(dist));
            countdown.put(id, Math.max(1, intervalFor(dist)));
        }
    }

    private static void playDue() {
        if (queue.isEmpty()) {
            return;
        }
        Iterator<Queued> it = queue.iterator();
        while (it.hasNext()) {
            Queued q = it.next();
            if (--q.ticks > 0) {
                continue;
            }
            it.remove();
            q.level.playSound(null, q.x, q.y, q.z, q.sound, SoundSource.PLAYERS, q.volume, q.pitch);
        }
    }

    private static void forget(UUID id) {
        countdown.remove(id);
        queue.removeIf(q -> q.owner.equals(id));
    }

    private static void schedule(ServerPlayer player, ServerLevel level, RandomSource rnd,
                                 double progress, int delay) {
        // Footsteps take over as the crowd thickens, the rest thin out among them.
        int stepShare = 45 + (int) Math.round(progress * 25.0); // 45% -> 70%
        int rest = 100 - stepShare;
        SoundEvent[] pool;
        int roll = rnd.nextInt(100);
        if (roll < stepShare) {
            pool = STEPS;
        } else if (roll < stepShare + rest * 35 / 100) {
            pool = MINING;
        } else if (roll < stepShare + rest * 62 / 100) {
            pool = BLOCKS;
        } else if (roll < stepShare + rest * 82 / 100) {
            pool = PRESENCE;
        } else {
            pool = HURTS;
        }
        SoundEvent sound = pool[rnd.nextInt(pool.length)];

        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double range = MIN_RANGE + rnd.nextDouble() * (MAX_RANGE - MIN_RANGE);
        double x = player.getX() + Math.cos(angle) * range;
        double z = player.getZ() + Math.sin(angle) * range;
        double y = player.getY() + (rnd.nextDouble() - 0.5) * 4.0;

        // Detuned so it reads as someone out there, not a clean sound effect. The
        // spread widens with the build-up so the pile-up smears instead of looping.
        float volume = (float) (0.5 + progress * 0.5);
        float spread = (float) (0.15 + progress * 0.45);
        float pitch = 1.0F - spread + rnd.nextFloat() * spread * 2.0F;

        queue.add(new Queued(player.getUUID(), level, x, y, z, sound, volume, pitch,
                Math.max(1, delay)));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
