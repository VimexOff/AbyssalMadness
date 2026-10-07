package abyssalmadness.client.blackout;

import abyssalmadness.network.BlackoutPayload;
import abyssalmadness.network.DecayPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Applies the blackout and decay config the server sends, and rebuilds what it changed. */
public final class BlackoutClientNetworking {
    private BlackoutClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BlackoutPayload.TYPE, (payload, context) ->
                context.client().execute(() -> BlackoutController.apply(
                        payload.enabled(), payload.radius(), payload.noise(),
                        payload.palette(), payload.follow(), payload.reanchor())));

        ClientPlayNetworking.registerGlobalReceiver(DecayPayload.TYPE, (payload, context) ->
                context.client().execute(() -> applyDecay(payload)));
    }

    private static void applyDecay(DecayPayload payload) {
        boolean was = DecayTrail.isEnabled();
        int reach = DecayTrail.getReach();

        DecayTrail.setRadius(payload.radius());
        DecayTrail.setReach(payload.reach());
        DecayTrail.setEnabled(payload.enabled());

        if (payload.wipe()) {
            DecayTrail.clear();
        }

        // Every one of these changes how columns recorded anywhere render, not just the
        // ones near the player, so there is nothing to do but rebuild the lot. The
        // radius alone is exempt: it only decides what gets recorded from here on.
        if (payload.wipe() || was != payload.enabled() || reach != DecayTrail.getReach()) {
            reloadTerrain();
        }
    }

    private static void reloadTerrain() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            client.levelRenderer.allChanged();
        }
    }
}
