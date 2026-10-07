package abyssalmadness.event;

import abyssalmadness.AbyssalMadness;
import abyssalmadness.infection.InfectionState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Rules for the Gods' Lands biome:
//   - eating anything here raises infection
//   - killing an animal here pins infection at 100%
public final class GodsLandsRules {
    private static final ResourceKey<Biome> GODS_LANDS = ResourceKey.create(
            Registries.BIOME, AbyssalMadness.id("gods_lands"));

    private static final int EATING_DOSE = 10;      // % per meal
    private static final int KILL_DOSE = InfectionState.MAX; // killing an animal = 100%

    // last seen food level per player, to detect eating
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();

    private GodsLandsRules() {
    }

    public static void register() {
        // eating raises infection
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int food = player.getFoodData().getFoodLevel();
                Integer previous = LAST_FOOD.put(player.getUUID(), food);
                if (previous != null && food > previous && inGodsLands(player)) {
                    infect(player, EATING_DOSE);
                }
            }
        });

        // killing an animal pins infection at max
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Animal
                    && source.getEntity() instanceof ServerPlayer killer
                    && inGodsLands(killer)) {
                infect(killer, KILL_DOSE);
            }
        });
    }

    private static boolean inGodsLands(ServerPlayer player) {
        return player.level().getBiome(player.blockPosition()).is(GODS_LANDS);
    }

    private static void infect(ServerPlayer player, int amount) {
        if (amount >= InfectionState.MAX) {
            InfectionState.set(player, InfectionState.MAX);
        } else {
            InfectionState.add(player, amount);
        }
    }
}
