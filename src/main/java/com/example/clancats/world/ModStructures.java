package com.example.clancats.world;

import com.example.clancats.ClanCatsMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

public final class ModStructures {
    private ModStructures() {}

    public static final RegistryKey<Structure> CAMP_KEY =
            RegistryKey.of(RegistryKeys.STRUCTURE, ClanCatsMod.id("wild_camp"));
    public static final TagKey<Structure> CAMP_TAG =
            TagKey.of(RegistryKeys.STRUCTURE, ClanCatsMod.id("wild_camp"));

    public static final StructureType<CampStructure> CAMP_TYPE =
            Registry.register(Registries.STRUCTURE_TYPE, ClanCatsMod.id("wild_camp"), () -> CampStructure.CODEC);

    public static final StructurePieceType CAMP_PIECE =
            Registry.register(Registries.STRUCTURE_PIECE, ClanCatsMod.id("wild_camp"), CampPiece::new);

    /** Call once from the mod initializer so the registrations above happen. */
    public static void register() {}

    /** The wild camp whose bounding box contains this position, or null. */
    public static CampPiece findCamp(ServerWorld world, BlockPos pos) {
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, CAMP_TAG);
        if (!start.hasChildren()) return null;
        for (StructurePiece piece : start.getChildren()) {
            if (piece instanceof CampPiece camp) return camp;
        }
        return null;
    }
}
