package com.example.clancats;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: a menu button was pressed. {@code menu} is the menu to refresh with the result
 * (empty = send the result to chat instead).
 */
public record ActionPayload(String action, String arg, String menu) implements CustomPayload {
    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(ClanCatsMod.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ActionPayload::action,
            PacketCodecs.STRING, ActionPayload::arg,
            PacketCodecs.STRING, ActionPayload::menu,
            ActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
