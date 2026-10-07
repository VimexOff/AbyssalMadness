package abyssalmadness.client;

import abyssalmadness.network.StarStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Receives the Sleeping Stars event state from the server and stores it for the sky renderer. */
public final class StarStateClientNetworking {
    private StarStateClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StarStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> StarClientState.set(payload.awake())));
    }
}
