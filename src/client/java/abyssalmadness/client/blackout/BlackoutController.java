package abyssalmadness.client.blackout;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Keeps the sphere on the player and rebuilds the sections it covers. The fade is baked
 * into the mesh, so it only moves when sections are marked dirty - hence MOVE_STEP,
 * rather than rebuilding the same meshes every tick.
 */
public final class BlackoutController {
    /** How far the player must move before the sphere is re-centred (and meshes rebuilt). */
    private static final int MOVE_STEP = 2;

    private BlackoutController() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BlackoutController::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlackoutState.reset());
    }

    private static void tick(Minecraft client) {
        if (!BlackoutState.isEnabled() || !BlackoutState.isFollow()) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        BlockPos target = centreOn(player);
        BlockPos current = BlackoutState.getOrigin();
        if (current != null && current.distSqr(target) < (double) MOVE_STEP * MOVE_STEP) {
            return;
        }
        moveOrigin(target);
    }

    /** Applies a config that just arrived from the server, and rebuilds what it changed. */
    public static void apply(boolean enabled, int radius, float noise, int palette,
                             boolean follow, boolean reanchor) {
        Minecraft client = Minecraft.getInstance();
        BlockPos previous = BlackoutState.getOrigin();
        // Shrinking leaves the outer shell painted, so rebuild over the larger radius.
        int reach = Math.max(BlackoutState.getRadius(), radius);

        BlackoutState.configure(enabled, radius, noise, palette, follow);

        if (!enabled) {
            BlackoutState.setOrigin(null);
            markDirty(previous, reach);
            return;
        }

        LocalPlayer player = client.player;
        boolean needsOrigin = previous == null || reanchor;
        BlockPos target = needsOrigin && player != null ? centreOn(player) : previous;
        BlackoutState.setOrigin(target);

        markDirty(previous, reach);
        markDirty(target, reach);
    }

    /** Sphere centre: one block up from the feet, so it sits around the body, not under it. */
    public static BlockPos centreOn(LocalPlayer player) {
        return player.blockPosition().above();
    }

    /** Re-centres the sphere and rebuilds everything the old and new positions covered. */
    public static void moveOrigin(BlockPos target) {
        BlockPos previous = BlackoutState.getOrigin();
        int radius = BlackoutState.getRadius();

        BlackoutState.setOrigin(target);

        markDirty(previous, radius);
        markDirty(target, radius);
    }

    /**
     * Schedules a mesh rebuild for every section touching the sphere. The box is one
     * block wider than the radius so the sections on the boundary get re-lit too.
     */
    public static void markDirty(BlockPos centre, int radius) {
        Minecraft client = Minecraft.getInstance();
        if (centre == null || client.level == null) {
            return;
        }

        int reach = radius + 1;
        int minY = Math.max(client.level.getMinY(), centre.getY() - reach);
        int maxY = Math.min(client.level.getMaxY(), centre.getY() + reach);

        client.levelRenderer.setBlocksDirty(
                centre.getX() - reach, minY, centre.getZ() - reach,
                centre.getX() + reach, maxY, centre.getZ() + reach
        );
    }
}
