package com.example.clancats.client;

import com.example.clancats.ActionPayload;
import com.example.clancats.MenuPayload;
import com.example.clancats.OpenRecruitPayload;
import com.example.clancats.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.CatEntityRenderer;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ClanCatsClient implements ClientModInitializer {
    private static KeyBinding furMenuKey;
    private static KeyBinding hubMenuKey;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenRecruitPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreen(new RecruitScreen(payload.entityId(), payload.suggestedName()))));

        // Menus are built from state the server sends.
        ClientPlayNetworking.registerGlobalReceiver(MenuPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        MenuScreen.receive(context.client(), payload.menu(), payload.json())));

        // Wild cats look like vanilla cats.
        EntityRendererRegistry.register(ModEntities.WILD_CAT, CatEntityRenderer::new);

        furMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clancats.fur_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.clancats"));
        hubMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clancats.hub_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.clancats"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CatClientState.tick(client);
            CatClientState.tickClimbing(client);
            CatClientState.tickPounce(client);
            while (furMenuKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new FurScreen());
                }
            }
            while (hubMenuKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    ClientPlayNetworking.send(new ActionPayload("open", "hub", ""));
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CatClientState.clear());
    }
}
