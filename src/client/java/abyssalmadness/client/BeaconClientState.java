package abyssalmadness.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import abyssalmadness.network.BeaconPayload;
import net.minecraft.core.BlockPos;

/**
 * Where the beacon stands and how far up it has come.
 *
 * <p>The intensity is eased on the client rather than sent by the server: a light this
 * big snapping on would read as a bug, and igniting over a couple of seconds is most of
 * what makes it look real.
 */
public final class BeaconClientState {
    private static final float RISE_PER_TICK = 0.010F;   // ~5 s to full
    private static final float FALL_PER_TICK = 0.020F;

    private static BlockPos pos;
    private static boolean active;
    private static float intensity;
    private static float previous;

    private BeaconClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BeaconPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    pos = payload.pos();
                    active = payload.active();
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            previous = intensity;
            float target = active ? 1.0F : 0.0F;
            if (intensity < target) {
                intensity = Math.min(target, intensity + RISE_PER_TICK);
            } else if (intensity > target) {
                intensity = Math.max(target, intensity - FALL_PER_TICK);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pos = null;
            active = false;
            intensity = 0.0F;
            previous = 0.0F;
        });
    }

    public static boolean burning() {
        return pos != null && (intensity > 0.001F || previous > 0.001F);
    }

    public static BlockPos pos() {
        return pos;
    }

    /** Interpolated so the rise is smooth at any frame rate. */
    public static float intensity(float partial) {
        return previous + (intensity - previous) * partial;
    }
}
