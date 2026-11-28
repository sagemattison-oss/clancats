package com.example.clancats;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: open (or refresh) a menu. {@code json} is the menu's state. */
public record MenuPayload(String menu, String json) implements CustomPayload {
    public static final CustomPayload.Id<MenuPayload> ID = new CustomPayload.Id<>(ClanCatsMod.id("menu"));
    public static final PacketCodec<RegistryByteBuf, MenuPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, MenuPayload::menu,
            PacketCodecs.STRING, MenuPayload::json,
            MenuPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
