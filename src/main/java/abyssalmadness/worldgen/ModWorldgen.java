package abyssalmadness.worldgen;

import abyssalmadness.AbyssalMadness;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

// Registers the custom density-function and biome-source types so datapack JSON
// can reference them by id. Must run during mod init, before any datapack is parsed.
public final class ModWorldgen {
    private static boolean registered;

    private ModWorldgen() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
                AbyssalMadness.id("abyss_terrain"), AbyssTerrain.CODEC.codec());
        Registry.register(BuiltInRegistries.BIOME_SOURCE,
                AbyssalMadness.id("level_biomes"), LevelBiomeSource.CODEC);
        registered = true;
    }
}
