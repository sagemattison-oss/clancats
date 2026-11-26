package com.example.clancats.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ClanCatsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CatClientState.tick(client);
            CatClientState.tickClimbing(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CatClientState.clear());
    }
}
