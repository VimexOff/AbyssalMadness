package abyssalmadness.event;

import abyssalmadness.AbyssalMadness;
import abyssalmadness.infection.InfectionState;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.StainedGlassBlock;

// Breaking any stained-glass block inside the Sleeping Stars Valley raises the
// player's infection. Keyed on biome, since crystals are plain stained glass.
public final class CrystalGuard {
    private static final int CRYSTAL_INFECTION = 15; // % per broken crystal
    private static final ResourceKey<Biome> VALLEY = ResourceKey.create(
            Registries.BIOME, AbyssalMadness.id("sleeping_stars_valley"));

    private CrystalGuard() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer server
                    && state.getBlock() instanceof StainedGlassBlock
                    && inValley(world, pos)) {
                InfectionState.add(server, CRYSTAL_INFECTION);
            }
        });
    }

    private static boolean inValley(Level world, BlockPos pos) {
        return world.getBiome(pos).is(VALLEY);
    }
}
