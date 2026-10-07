package abyssalmadness.client;

import abyssalmadness.network.CleansingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.phys.Vec3;

public final class CleansingClientNetworking {
    private CleansingClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CleansingPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CleansingClientState.set(
                        payload.running(), payload.intensity(),
                        new Vec3(payload.x(), payload.y(), payload.z()))));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CleansingClientState.clear());
    }
}
