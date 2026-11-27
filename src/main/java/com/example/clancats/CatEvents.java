package com.example.clancats;

import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.Set;

public final class CatEvents {
    private CatEvents() {}

    /** Animals a cat may hunt for fresh-kill. */
    public static final Set<EntityType<?>> PREY = Set.of(
            EntityType.RABBIT, EntityType.CHICKEN, EntityType.BAT, EntityType.COD,
            EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.SILVERFISH, EntityType.ENDERMITE);

    /** The only foods a cat will eat. */
    public static final Set<Item> CAT_FOOD = Set.of(
            Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
            Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON,
            Items.RABBIT, Items.COOKED_RABBIT, Items.COD, Items.COOKED_COD,
            Items.SALMON, Items.COOKED_SALMON, Items.TROPICAL_FISH, Items.ROTTEN_FLESH);

    public static void register() {
        // Claws can only break soft things (leaves, grass, flowers, vines, snow...).
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!CatAbilities.isCat(player) || player.isCreative()) return ActionResult.PASS;
            if (world.getBlockState(pos).getHardness(world, pos) <= 0.2f) return ActionResult.PASS;
            if (!world.isClient) player.sendMessage(Text.literal("Your paws can't break that.").formatted(Formatting.GRAY), true);
            return ActionResult.FAIL;
        });

        // No hands, no building.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!CatAbilities.isCat(player) || player.isCreative()) return ActionResult.PASS;
            if (player.getStackInHand(hand).getItem() instanceof BlockItem) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        // Cats are obligate carnivores.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (CatAbilities.isCat(player) && !player.isCreative()
                    && stack.contains(DataComponentTypes.FOOD) && !CAT_FOOD.contains(stack.getItem())) {
                if (!world.isClient) player.sendMessage(Text.literal("Cats only eat meat and fish.").formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Nine lives: a Leader survives lethal damage until the lives run out.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            CatData d = CatAbilities.data(p);
            if (!d.cat() || d.rank() != CatRank.LEADER || d.lives() <= 1) return true;
            if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true; // /kill, the void

            int left = d.lives() - 1;
            p.setAttached(ClanCatsMod.CAT_DATA, d.withLives(left));
            p.setHealth(p.getMaxHealth());
            p.clearStatusEffects();
            p.extinguish();
            p.setAir(p.getMaxAir());
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 2));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 0));
            p.getServerWorld().spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getBodyY(0.5), p.getZ(), 50, 0.4, 0.6, 0.4, 0.08);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.4f);
            p.sendMessage(Text.literal("A life slips away... " + left + " remain.").formatted(Formatting.LIGHT_PURPLE), false);
            return false;
        });

        // Hunting.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!PREY.contains(entity.getType())) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return;
            if (!CatAbilities.data(p).cat()) return;
            p.getHungerManager().add(4, 0.6f);
            addPrey(p, 1);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CatAbilities.apply(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> CatAbilities.apply(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) CatAbilities.tickNightVision(server);
        });
    }

    public static void addPrey(ServerPlayerEntity p, int amount) {
        setPrey(p, CatAbilities.data(p).prey() + amount, true);
    }

    /** Sets prey count, handles promotions and the nine-lives ceremony. */
    public static void setPrey(ServerPlayerEntity p, int prey, boolean announce) {
        CatData old = CatAbilities.data(p);
        CatRank before = old.rank();
        CatData now = old.withPrey(prey);
        CatRank after = now.rank();

        if (after == CatRank.LEADER && before != CatRank.LEADER) now = now.withLives(9);
        else if (after != CatRank.LEADER) now = now.withLives(0);

        p.setAttached(ClanCatsMod.CAT_DATA, now);
        CatAbilities.apply(p);

        if (!announce) return;
        p.sendMessage(Text.literal("Fresh-kill! Prey caught: " + prey).formatted(Formatting.GREEN), true);
        if (after.ordinal() > before.ordinal()) {
            p.getServer().getPlayerManager().broadcast(
                    Text.literal(p.getName().getString() + " has become a " + after.title + "!").formatted(after.color), false);
            if (after == CatRank.LEADER) {
                p.sendMessage(Text.literal("Your ancestors grant you nine lives.").formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
    }
}
