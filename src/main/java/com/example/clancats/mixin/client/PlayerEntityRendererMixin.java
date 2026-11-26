package com.example.clancats.mixin.client;

import com.example.clancats.client.CatClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.CatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the player model for a cat model whenever the player is in cat form. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void clancats$renderAsCat(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      int light, CallbackInfo ci) {
        CatEntity cat = CatClientState.catFor(player);
        if (cat == null) return;
        EntityRenderer<? super CatEntity> renderer =
                MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(cat);
        renderer.render(cat, yaw, tickDelta, matrices, vertexConsumers, light);
        ci.cancel();
    }

    // Cats have no arms: hide the first-person hand.
    @Inject(method = "renderRightArm", at = @At("HEAD"), cancellable = true)
    private void clancats$hideRightArm(MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                       AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (CatClientState.isCat(player)) ci.cancel();
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"), cancellable = true)
    private void clancats$hideLeftArm(MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                      AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (CatClientState.isCat(player)) ci.cancel();
    }
}
