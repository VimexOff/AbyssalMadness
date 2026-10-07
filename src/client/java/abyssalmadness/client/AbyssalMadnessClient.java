package abyssalmadness.client;

import abyssalmadness.client.blackout.BlackoutClientNetworking;
import abyssalmadness.client.blackout.BlackoutController;
import abyssalmadness.client.blackout.DecayTrail;
import abyssalmadness.hollow.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class AbyssalMadnessClient implements ClientModInitializer {
    private static final double PLAYER_HEIGHT = 1.85;

    @Override
    public void onInitializeClient() {
        StarStateClientNetworking.register();
        InfectionClientNetworking.register();
        CleansingClientNetworking.register();
        CleansingClientState.register();
        BeaconClientState.register();
        BlackoutClientNetworking.register();
        BlackoutController.register();
        DecayTrail.register();
        EntityRendererRegistry.register(ModEntities.HOLLOW, HollowRenderer::new);
        SkyEyes.register();

        // dust drifting off infected players
        ClientTickEvents.END_CLIENT_TICK.register(AbyssalMadnessClient::spawnInfectionDust);
    }

    private static void spawnInfectionDust(Minecraft client) {
        if (client.level == null || !InfectionClientState.any()) {
            return;
        }
        var random = client.level.random;
        for (Player player : client.level.players()) {
            int level = InfectionClientState.get(player.getUUID());
            if (level <= 0 || player.isInvisible()) {
                continue;
            }
            // sparse: more motes the further infection has spread
            if (random.nextInt(100) >= level / 4 + 2) {
                continue;
            }
            double reach = PLAYER_HEIGHT * (level / 100.0);
            Vec3 pos = player.position();
            double px = pos.x + (random.nextDouble() - 0.5) * 0.6;
            double pz = pos.z + (random.nextDouble() - 0.5) * 0.6;
            double py = pos.y + random.nextDouble() * reach;
            // drift gently in every direction
            double speed = 0.012;
            client.level.addParticle(ParticleTypes.END_ROD, px, py, pz,
                    (random.nextDouble() - 0.5) * speed,
                    (random.nextDouble() - 0.5) * speed,
                    (random.nextDouble() - 0.5) * speed);
        }
    }
}
