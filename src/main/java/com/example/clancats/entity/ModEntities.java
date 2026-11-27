package com.example.clancats.entity;

import com.example.clancats.ClanCatsMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<WildCatEntity> WILD_CAT = Registry.register(
            Registries.ENTITY_TYPE, ClanCatsMod.id("wild_cat"),
            EntityType.Builder.create(WildCatEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 0.7f)
                    .maxTrackingRange(8)
                    .build("wild_cat"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(WILD_CAT, CatEntity.createCatAttributes());
        SpawnRestriction.register(WILD_CAT, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MobEntity::canMobSpawn);
        // Lone cats roam the overworld in small numbers.
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.CREATURE, WILD_CAT, 3, 1, 2);
    }
}
