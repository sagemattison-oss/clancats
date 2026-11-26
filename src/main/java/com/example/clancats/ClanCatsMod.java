package com.example.clancats;

import com.example.clancats.data.CatData;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

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

    /** Clan camp locations (clan id -> position), stored on the overworld. */
    public static final AttachmentType<Map<String, BlockPos>> CAMPS = AttachmentRegistry.<Map<String, BlockPos>>builder()
            .initializer(() -> Map.<String, BlockPos>of())
            .persistent(Codec.unboundedMap(Codec.STRING, BlockPos.CODEC))
            .buildAndRegister(id("camps"));

    @Override
    public void onInitialize() {
        CatEvents.register();
        CatCommands.register();
    }
}
