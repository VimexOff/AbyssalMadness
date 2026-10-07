package abyssalmadness.hollow;

import abyssalmadness.infection.InfectionState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Figures that stand in the sand, and sometimes come for you. The AI stays off even
// while chasing: driven by hand they walk at the nearest player in a straight line and
// do nothing else - no swinging, no wandering after animals.
public final class Hollows {
    public enum Mode {
        /** Frozen at the angle they were placed. */
        STILL,
        /** Rooted, but the head follows. */
        WATCH,
        /** Shambling. About a quarter of walking pace. */
        SLOW,
        /** Just short of a sprint - outrunnable, barely. */
        FAST,
    }

    /** Degrees per tick while only watching. Slow on purpose: a snap to face you reads as a glitch. */
    private static final float TURN_RATE = 4.0F;
    private static final double LOOK_RANGE = 48.0;
    private static final double SLOW_SPEED = 0.05;
    private static final double FAST_SPEED = 0.24;
    /** Close enough. Without this they grind into the player and shove them around. */
    private static final double STOP_RANGE = 0.9;
    private static final double GRAVITY = 0.28;
    /** Saved with the entity, so a hollow is still a hollow after a reload. */
    private static final String TAG = "abyssalmadness_hollow";
    /** Same trick for the palette, so a warm one is still warm after a reload. */
    private static final String TAG_WARM = "abyssalmadness_hollow_warm";

    private static final Set<UUID> HOLLOWS = new LinkedHashSet<>();
    private static Mode mode = Mode.STILL;

    private Hollows() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Hollows::tick);
        // The level lives in memory only, so on reload a hollow would come back with
        // its skin showing. The tag survives and the level is restored from it.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof HollowEntity hollow && entity.getTags().contains(TAG)) {
                HOLLOWS.add(hollow.getUUID());
                InfectionState.setRaw(level.getServer(), hollow.getUUID(), InfectionState.MAX);
                // Restored from the tag the same way the level is.
                InfectionState.setWarm(level.getServer(), hollow.getUUID(),
                        entity.getTags().contains(TAG_WARM));
            }
        });
    }

    public static int count() {
        return HOLLOWS.size();
    }

    public static Mode mode() {
        return mode;
    }

    /** One switch for all of them. They keep their angle and position when it changes. */
    public static void setMode(Mode to) {
        mode = to;
    }

    /** Places one where the caller stands, facing the same way, already fully taken. */
    public static boolean spawn(ServerLevel level, Vec3 at, float yaw, boolean warm) {
        HollowEntity hollow = ModEntities.HOLLOW.spawn(level, BlockPos.containing(at), EntitySpawnReason.COMMAND);
        if (hollow == null) {
            return false;
        }
        hollow.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        hollow.setYBodyRot(yaw);
        hollow.setYHeadRot(yaw);
        hollow.addTag(TAG);
        if (warm) {
            hollow.addTag(TAG_WARM);
        }

        HOLLOWS.add(hollow.getUUID());
        // Fully taken, so the shader paints the whole body rather than a rising front.
        InfectionState.setRaw(level.getServer(), hollow.getUUID(), InfectionState.MAX);
        InfectionState.setWarm(level.getServer(), hollow.getUUID(), warm);
        return true;
    }

    public static int clear(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(TAG)) {
                    InfectionState.forget(server, entity.getUUID());
                    entity.discard();
                    removed++;
                }
            }
        }
        HOLLOWS.clear();
        return removed;
    }

    private static void tick(MinecraftServer server) {
        if (HOLLOWS.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : HOLLOWS) {
                Entity entity = level.getEntity(id);
                if (!(entity instanceof HollowEntity hollow) || !hollow.isAlive()) {
                    continue;
                }
                if (mode == Mode.STILL) {
                    continue;
                }

                Player target = level.getNearestPlayer(hollow, LOOK_RANGE);
                if (target == null) {
                    continue;
                }
                float want = wantedYaw(hollow, target);

                if (mode == Mode.WATCH) {
                    face(hollow, approach(hollow.getYRot(), want));
                    continue;
                }

                // Chasing: no easing on the turn, they are already coming.
                face(hollow, want);
                double dx = target.getX() - hollow.getX();
                double dz = target.getZ() - hollow.getZ();
                double flat = Math.sqrt(dx * dx + dz * dz);
                double speed = mode == Mode.FAST ? FAST_SPEED : SLOW_SPEED;
                double stepX = flat > STOP_RANGE ? dx / flat * speed : 0.0;
                double stepZ = flat > STOP_RANGE ? dz / flat * speed : 0.0;

                // Driven by hand rather than by pathfinding, so the downward pull has to
                // come from here too or they walk off a dune and keep walking on air.
                hollow.move(MoverType.SELF, new Vec3(stepX, -GRAVITY, stepZ));
            }
        }
    }

    private static void face(HollowEntity hollow, float yaw) {
        hollow.setYRot(yaw);
        hollow.setYBodyRot(yaw);
        hollow.setYHeadRot(yaw);
    }

    private static float wantedYaw(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    /** Steps current towards target the short way round, at most TURN_RATE per tick. */
    private static float approach(float current, float target) {
        float delta = wrap(target - current);
        return current + Math.max(-TURN_RATE, Math.min(TURN_RATE, delta));
    }

    private static float wrap(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) {
            degrees -= 360.0F;
        }
        if (degrees < -180.0F) {
            degrees += 360.0F;
        }
        return degrees;
    }
}
