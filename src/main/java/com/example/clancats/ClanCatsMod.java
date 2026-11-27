package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatData;
import com.example.clancats.entity.ModEntities;
import com.example.clancats.world.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

import java.util.List;

public class ClanCatsMod implements ModInitializer {
    public static final String MOD_ID = "clancats";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /** Per-player cat state. Survives death and restarts. */
    public static final AttachmentType<CatData> CAT_DATA = AttachmentRegistry.<CatData>builder()
            .initializer(() -> CatData.DEFAULT)
            .persistent(CatData.CODEC)
            .copyOnDeath()
            .buildAndRegister(id("cat_data"));

    /** Claimed wild camps, stored on the overworld. */
    public static final AttachmentType<List<CampClaim>> CLAIMS = AttachmentRegistry.<List<CampClaim>>builder()
            .initializer(() -> List.<CampClaim>of())
            .persistent(CampClaim.CODEC.listOf())
            .buildAndRegister(id("claims"));

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModStructures.register();
        CatPounce.register();
        CatEvents.register();
        CatCommands.register();
    }
}
