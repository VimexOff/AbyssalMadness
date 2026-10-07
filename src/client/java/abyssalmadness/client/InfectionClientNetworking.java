package abyssalmadness.client;

import abyssalmadness.network.InfectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Keeps {@link InfectionClientState} in sync with the server. */
public final class InfectionClientNetworking {
    private InfectionClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(InfectionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> InfectionClientState.set(payload.player(), payload.level(), payload.warm())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> InfectionClientState.clear());
    }
}
