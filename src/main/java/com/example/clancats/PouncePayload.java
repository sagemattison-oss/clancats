package com.example.clancats;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: "I just pounced". No data needed. */
public record PouncePayload() implements CustomPayload {
    public static final CustomPayload.Id<PouncePayload> ID = new CustomPayload.Id<>(ClanCatsMod.id("pounce"));
    public static final PacketCodec<RegistryByteBuf, PouncePayload> CODEC = PacketCodec.unit(new PouncePayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
