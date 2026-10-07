package abyssalmadness.hollow;

import abyssalmadness.AbyssalMadness;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<HollowEntity> HOLLOW;

    private ModEntities() {
    }

    public static void register() {
        ResourceKey<EntityType<?>> key =
                ResourceKey.create(Registries.ENTITY_TYPE, AbyssalMadness.id("hollow"));
        HOLLOW = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(HollowEntity::new, MobCategory.MISC)
                        // Player-sized, because the whole point is that it reads as one.
                        .sized(0.6F, 1.8F)
                        .eyeHeight(1.62F)
                        .fireImmune()
                        .noLootTable()
                        .clientTrackingRange(12)
                        .build(key));

        FabricDefaultAttributeRegistry.register(HOLLOW, HollowEntity.createAttributes());
    }
}
