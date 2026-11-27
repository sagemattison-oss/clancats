package com.example.clancats;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: recruit this cat with the chosen name and job (index into CatJob.values()). */
public record RecruitPayload(int entityId, String name, int job) implements CustomPayload {
    public static final CustomPayload.Id<RecruitPayload> ID = new CustomPayload.Id<>(ClanCatsMod.id("recruit"));
    public static final PacketCodec<RegistryByteBuf, RecruitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RecruitPayload::entityId,
            PacketCodecs.STRING, RecruitPayload::name,
            PacketCodecs.VAR_INT, RecruitPayload::job,
            RecruitPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
