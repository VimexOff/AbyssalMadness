package abyssalmadness.worldgen.feature;

import abyssalmadness.AbyssalMadness;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Registers the mod's custom worldgen feature types so datapack JSON can use them. */
public final class ModFeatures {
    public static Feature<NoneFeatureConfiguration> CRYSTAL_SPIKE;
    public static Feature<NoneFeatureConfiguration> CRYSTAL_SPIKE_TALL;
    public static Feature<NoneFeatureConfiguration> WILD_CROPS;
    public static Feature<NoneFeatureConfiguration> CAVE_MOSS;
    public static Feature<NoneFeatureConfiguration> BARRIER_WALL;
    public static Feature<NoneFeatureConfiguration> CRYSTAL_RING;
    public static Feature<NoneFeatureConfiguration> BURIED_RUIN;
    public static Feature<NoneFeatureConfiguration> CRYSTAL_FACE;
    public static Feature<NoneFeatureConfiguration> MONOLITH_SKIN;
    public static Feature<NoneFeatureConfiguration> ANCIENT_TREE;
    public static Feature<NoneFeatureConfiguration> FOREST_CANOPY;

    private ModFeatures() {
    }

    public static void register() {
        CRYSTAL_SPIKE = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("crystal_spike"), new CrystalSpikeFeature(NoneFeatureConfiguration.CODEC));
        CRYSTAL_SPIKE_TALL = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("crystal_spike_tall"), new CrystalSpikeFeature(NoneFeatureConfiguration.CODEC, true));
        WILD_CROPS = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("wild_crops"), new WildCropFeature(NoneFeatureConfiguration.CODEC));
        CAVE_MOSS = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("cave_moss"), new CaveMossFeature(NoneFeatureConfiguration.CODEC));
        BARRIER_WALL = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("barrier_wall"), new BarrierWallFeature(NoneFeatureConfiguration.CODEC));
        CRYSTAL_RING = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("crystal_ring"), new CrystalRingFeature(NoneFeatureConfiguration.CODEC));
        BURIED_RUIN = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("buried_ruin"), new BuriedRuinFeature(NoneFeatureConfiguration.CODEC));
        CRYSTAL_FACE = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("crystal_face"), new CrystalFaceFeature(NoneFeatureConfiguration.CODEC));
        MONOLITH_SKIN = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("monolith_skin"), new MonolithSkinFeature(NoneFeatureConfiguration.CODEC));
        ANCIENT_TREE = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("ancient_tree"), new AncientTreeFeature(NoneFeatureConfiguration.CODEC));
        FOREST_CANOPY = Registry.register(BuiltInRegistries.FEATURE,
                AbyssalMadness.id("forest_canopy"), new ForestCanopyFeature(NoneFeatureConfiguration.CODEC));
    }
}
