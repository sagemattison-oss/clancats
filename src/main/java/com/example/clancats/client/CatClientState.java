package com.example.clancats.client;

import com.example.clancats.CatAbilities;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cat players are drawn by a hidden, client-only {@link CatEntity} that mirrors the player's
 * position, rotation, walking animation and pose. Nothing is spawned in the world.
 */
public final class CatClientState {
    private static final Map<UUID, CatEntity> CATS = new HashMap<>();
    /** Fur name last applied to each dummy cat ("" = default). */
    private static final Map<UUID, String> FUR = new HashMap<>();

    private CatClientState() {}

    public static boolean isCat(AbstractClientPlayerEntity p) {
        return CatAbilities.isCat(p);
    }

    public static CatEntity catFor(AbstractClientPlayerEntity p) {
        if (!CatAbilities.isCat(p)) return null;
        CatEntity cat = CATS.get(p.getUuid());
        if (cat == null || cat.getWorld() != p.getWorld()) {
            cat = new CatEntity(EntityType.CAT, p.getWorld());
            CATS.put(p.getUuid(), cat);
            FUR.remove(p.getUuid());
        }
        applyFur(p, cat);
        return cat;
    }

    /** Uses the player's chosen fur, or a pattern picked from their UUID if they have none. */
    private static void applyFur(AbstractClientPlayerEntity p, CatEntity cat) {
        String fur = CatAbilities.furOf(p);
        if (fur.equals(FUR.get(p.getUuid()))) return;
        FUR.put(p.getUuid(), fur);

        List<RegistryEntry.Reference<CatVariant>> variants = Registries.CAT_VARIANT.streamEntries().toList();
        RegistryEntry.Reference<CatVariant> pick = null;
        if (!fur.isEmpty()) {
            for (RegistryEntry.Reference<CatVariant> v : variants) {
                if (v.registryKey().getValue().getPath().equals(fur)) pick = v;
            }
        }
        if (pick == null && !variants.isEmpty()) {
            pick = variants.get(Math.floorMod(p.getUuid().hashCode(), variants.size()));
        }
        if (pick != null) cat.setVariant(pick);
    }

    public static void clear() {
        CATS.clear();
        FUR.clear();
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) {
            clear();
            return;
        }
        for (AbstractClientPlayerEntity p : world.getPlayers()) {
            CatEntity cat = catFor(p);
            if (cat == null) {
                CATS.remove(p.getUuid());
                FUR.remove(p.getUuid());
                continue;
            }
            sync(mc, p, cat);
        }
    }

    private static void sync(MinecraftClient mc, AbstractClientPlayerEntity p, CatEntity cat) {
        double dx = p.getX() - p.prevX;
        double dz = p.getZ() - p.prevZ;
        double distSq = dx * dx + dz * dz;
        boolean still = distSq < 1.0E-5;

        cat.limbAnimator.updateLimbs(Math.min((float) Math.sqrt(distSq) * 4.0f, 1.0f), 0.4f);
        cat.setPosition(p.getX(), p.getY(), p.getZ());
        cat.age = p.age;

        cat.prevBodyYaw = p.prevBodyYaw;
        cat.bodyYaw = p.bodyYaw;
        cat.prevHeadYaw = p.prevHeadYaw;
        cat.headYaw = p.headYaw;
        cat.prevYaw = p.prevYaw;
        cat.setYaw(p.getYaw());
        cat.prevPitch = p.prevPitch;
        cat.setPitch(p.getPitch());

        // Sneak while moving = stalking crouch. Sneak while still = sit.
        cat.setPose(p.isSneaking() && !still ? EntityPose.CROUCHING : EntityPose.STANDING);
        cat.setInSittingPose(p.isSneaking() && still);
        cat.setSprinting(p.isSprinting());
        cat.hurtTime = p.hurtTime;
        cat.deathTime = p.deathTime;

        if (p != mc.player) {
            cat.setCustomName(p.getDisplayName()); // keeps clan colours / rank suffix
            cat.setCustomNameVisible(true);
        } else {
            cat.setCustomNameVisible(false);
        }
    }

    /** Hold jump against logs, leaves or fences to scramble up them. */
    public static void tickClimbing(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        if (!CatAbilities.isCat(p) || p.isSpectator() || p.getAbilities().flying) return;
        if (!mc.options.jumpKey.isPressed() || !p.horizontalCollision) return;

        BlockPos base = p.getBlockPos();
        boolean nearClimbable = false;
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (isClimbable(mc.world.getBlockState(base.offset(d))) || isClimbable(mc.world.getBlockState(base.up().offset(d)))) {
                nearClimbable = true;
                break;
            }
        }
        if (!nearClimbable) return;

        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, 0.2, v.z);
        p.fallDistance = 0;
    }

    private static boolean isClimbable(BlockState s) {
        return s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FENCES);
    }
}
