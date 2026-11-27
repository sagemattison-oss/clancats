package com.example.clancats;

import com.example.clancats.entity.WildCatEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pounce: the client launches the cat forward when it jumps while sneaking and tells the server,
 * which then checks for hostile mobs and prey the cat crashes into for the next ~0.7 seconds.
 */
public final class CatPounce {
    private CatPounce() {}

    private static final int WINDOW_TICKS = 14;
    private static final int COOLDOWN_TICKS = 30;

    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();   // uuid -> last tick of the pounce
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>(); // uuid -> first tick a new pounce is allowed
    private static final Map<UUID, Set<Integer>> HIT = new HashMap<>(); // entity ids already struck

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PouncePayload.ID, PouncePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PouncePayload.ID, (payload, context) -> start(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(CatPounce::tick);
    }

    private static void start(ServerPlayerEntity p) {
        if (!CatAbilities.data(p).cat() || p.isSpectator()) return;
        int now = p.getServer().getTicks();
        if (now < COOLDOWN.getOrDefault(p.getUuid(), 0)) return;

        ACTIVE.put(p.getUuid(), now + WINDOW_TICKS);
        COOLDOWN.put(p.getUuid(), now + COOLDOWN_TICKS);
        HIT.remove(p.getUuid());
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        int now = server.getTicks();
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || now > e.getValue()) {
                HIT.remove(e.getKey());
                it.remove();
                continue;
            }
            strike(p);
        }
    }

    private static void strike(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        Box box = p.getBoundingBox().expand(0.9, 0.4, 0.9);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e ->
                e != p && e.isAlive() && !(e instanceof WildCatEntity)
                        && (e instanceof Monster || CatEvents.PREY.contains(e.getType())));
        if (targets.isEmpty()) return;

        Set<Integer> hit = HIT.computeIfAbsent(p.getUuid(), k -> new HashSet<>());
        float damage = (float) (p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 1.5 + 1.0);
        for (LivingEntity t : targets) {
            if (!hit.add(t.getId())) continue;
            t.damage(p.getDamageSources().playerAttack(p), damage);
            t.takeKnockback(0.6, -(t.getX() - p.getX()), -(t.getZ() - p.getZ()));
            world.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
    }
}
