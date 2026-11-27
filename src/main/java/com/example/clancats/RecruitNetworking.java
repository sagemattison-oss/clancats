package com.example.clancats;

import com.example.clancats.entity.CatJob;
import com.example.clancats.entity.WildCatEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RecruitNetworking {
    private RecruitNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenRecruitPayload.ID, OpenRecruitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RecruitPayload.ID, RecruitPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RecruitPayload.ID, (payload, context) -> handle(context.player(), payload));
    }

    private static void handle(ServerPlayerEntity sp, RecruitPayload payload) {
        Entity e = sp.getServerWorld().getEntityById(payload.entityId());
        if (!(e instanceof WildCatEntity cat) || cat.isRecruited() || cat.squaredDistanceTo(sp) > 64.0) return;
        CatJob[] jobs = CatJob.values();
        int index = Math.max(0, Math.min(jobs.length - 1, payload.job()));
        cat.completeRecruit(sp, payload.name(), jobs[index]);
    }
}
