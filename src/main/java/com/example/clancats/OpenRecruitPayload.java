package com.example.clancats;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: open the recruit screen for this wild cat. */
public record OpenRecruitPayload(int entityId, String suggestedName) implements CustomPayload {
    public static final CustomPayload.Id<OpenRecruitPayload> ID = new CustomPayload.Id<>(ClanCatsMod.id("open_recruit"));
    public static final PacketCodec<RegistryByteBuf, OpenRecruitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, OpenRecruitPayload::entityId,
            PacketCodecs.STRING, OpenRecruitPayload::suggestedName,
            OpenRecruitPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
