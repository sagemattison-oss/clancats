package com.example.clancats.entity;

import com.example.clancats.CampClaims;
import com.example.clancats.CatAbilities;
import com.example.clancats.CatEvents;
import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.world.CampLayout;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * A loner cat. Feed it fish or meat while you belong to a clan with a claimed camp and it joins
 * your clan: it walks (teleports) to your nearest camp and works there as a Hunter, Guard or Healer.
 */
public class WildCatEntity extends CatEntity {
    private static final String[] NAMES = {
            "Fern", "Thistle", "Moth", "Sorrel", "Wren", "Pebble", "Marigold", "Cinder",
            "Dusk", "Sparrow", "Clover", "Flint", "Willow", "Rowan", "Juniper", "Gorse"
    };
    private static final String[] WILD_LINES = {
            "I've walked alone since the last leaf-fall. Why would I trust a clan?",
            "Hmm. A fresh bite of fish might make me listen.",
            "The wind smells of rain. Good hunting weather, if you've got a camp to hunt for.",
            "Clan cats always want something. What are you offering?",
            "Bring me prey and a place to sleep, and we can talk."
    };
    private static final String[] HUNTER_LINES = {
            "The prey's running well today.",
            "I'll keep the fresh-kill pile stocked.",
            "Rabbits near the camp are getting bolder. Not for long."
    };
    private static final String[] GUARD_LINES = {
            "Nothing gets past me.",
            "All quiet on the border. Stay sharp.",
            "I'll keep watch while you rest."
    };
    private static final String[] HEALER_LINES = {
            "Stay close if you're hurt. I know every herb in these caves.",
            "Rest, drink, and let the herbs do their work.",
            "Mind the cobwebs and the moss, they're good for wounds."
    };

    private String catName = "";
    private String clanId = "";
    private CatJob job = CatJob.HUNTER;
    private int jobCooldown = 0;

    public WildCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
    }

    public boolean isRecruited() {
        return !clanId.isEmpty();
    }

    // ------------------------------------------------------------------ setup
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.3, true));
        this.goalSelector.add(3, new HuntPreyGoal(this));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false,
                e -> isRecruited() && job == CatJob.GUARD && !(e instanceof CreeperEntity) && nearHome(e)));
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 EntityData entityData) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
        setBreedingAge(0); // always an adult
        setPersistent();
        if (catName.isEmpty()) {
            catName = NAMES[world.getRandom().nextInt(NAMES.length)];
            refreshName();
        }
        return data;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("CatName", catName);
        nbt.putString("ClanId", clanId);
        nbt.putString("Job", job.name());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        catName = nbt.getString("CatName");
        clanId = nbt.getString("ClanId");
        try {
            job = CatJob.valueOf(nbt.getString("Job"));
        } catch (IllegalArgumentException e) {
            job = CatJob.HUNTER;
        }
        refreshName();
    }

    private void refreshName() {
        if (catName.isEmpty()) return;
        Text name;
        CatClan clan = CatClan.byId(clanId).orElse(null);
        if (clan == null) {
            name = Text.literal(catName + " (Loner)").formatted(Formatting.GRAY);
        } else {
            name = Text.literal(catName + " ")
                    .append(Text.literal("[" + clan.display + "] ").formatted(clan.color))
                    .append(Text.literal("(" + job.title + ")").formatted(Formatting.WHITE));
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || !isRecruited()) return;

        if (jobCooldown > 0) jobCooldown--;

        if (age % 200 == 0 && !hasPositionTarget() && getWorld() instanceof ServerWorld sw) {
            CampClaims.nearest(sw.getServer(), clanId, getPos()).ifPresent(c -> setPositionTarget(c.center(), 14));
        }
        if (job == CatJob.HEALER && age % 100 == 0) healNearbyClanmates();
    }

    private void healNearbyClanmates() {
        List<ServerPlayerEntity> players = getWorld().getEntitiesByClass(
                ServerPlayerEntity.class, getBoundingBox().expand(8.0), p -> true);
        for (ServerPlayerEntity p : players) {
            if (!CatAbilities.data(p).clan().equals(clanId)) continue;
            if (p.getHealth() < p.getMaxHealth()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0));
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        p.getX(), p.getBodyY(0.6), p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    private boolean nearHome(LivingEntity e) {
        if (!hasPositionTarget()) return false;
        BlockPos h = getPositionTarget();
        return e.squaredDistanceTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5) < 22.0 * 22.0;
    }

    // ------------------------------------------------------------------ talking & recruiting
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (getWorld().isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;

        CatData data = CatAbilities.data(sp);
        ItemStack held = sp.getMainHandStack();

        if (!isRecruited()) {
            if (CatEvents.CAT_FOOD.contains(held.getItem())) {
                tryRecruit(sp, data, held);
            } else {
                say(sp, pick(WILD_LINES));
            }
            return ActionResult.SUCCESS;
        }

        if (!clanId.equals(data.clan())) {
            String other = CatClan.byId(clanId).map(c -> c.display).orElse("another clan");
            say(sp, "I serve " + other + ". Walk your own path.");
            return ActionResult.SUCCESS;
        }

        if (sp.isSneaking() && held.isEmpty()) {
            job = job.next();
            jobCooldown = 0;
            refreshName();
            say(sp, "Understood. I'll be a " + job.title + " now. (" + job.title + " " + job.blurb + ")");
        } else {
            String[] lines = switch (job) {
                case HUNTER -> HUNTER_LINES;
                case GUARD -> GUARD_LINES;
                case HEALER -> HEALER_LINES;
            };
            say(sp, pick(lines));
        }
        return ActionResult.SUCCESS;
    }

    private void tryRecruit(ServerPlayerEntity sp, CatData data, ItemStack held) {
        if (!data.cat()) {
            say(sp, "A two-leg wants me to join a clan? Come back as a cat. (/cat become)");
            return;
        }
        CatClan clan = data.clanOpt().orElse(null);
        if (clan == null) {
            say(sp, "You're a loner like me. Join a clan first. (/cat clan join <clan>)");
            return;
        }
        if (getWorld().getRegistryKey() != World.OVERWORLD) {
            say(sp, "This place doesn't feel like home. Find me in the overworld.");
            return;
        }
        Optional<CampClaim> camp = CampClaims.nearest(sp.getServer(), clan.id, getPos());
        if (camp.isEmpty()) {
            say(sp, clan.display + " has no camp. Claim a wild camp first. (/cat clan claim)");
            return;
        }

        if (!sp.isCreative()) held.decrement(1);
        clanId = clan.id;
        job = CatJob.HUNTER;
        jobCooldown = 0;
        sendHome(camp.get());
        refreshName();

        ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.8), getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        say(sp, "Thank you. I'll join " + clan.display + ". I'll meet you at camp.");
        sp.sendMessage(Text.literal(catName + " joined " + clan.display + " as a Hunter. Sneak + right-click with an empty hand to change their job.")
                .formatted(clan.color), false);
    }

    private void sendHome(CampClaim claim) {
        BlockPos c = claim.center();
        double a = getRandom().nextDouble() * Math.PI * 2.0;
        double r = 6.0 + getRandom().nextDouble() * 2.0; // ring around the Great Rock
        refreshPositionAndAngles(c.getX() + 0.5 + Math.cos(a) * r, c.getY(), c.getZ() + 0.5 + Math.sin(a) * r,
                getRandom().nextFloat() * 360.0f, 0.0f);
        getNavigation().stop();
        setPositionTarget(c, 14);
    }

    private String pick(String[] lines) {
        return lines[getRandom().nextInt(lines.length)];
    }

    private void say(ServerPlayerEntity sp, String line) {
        sp.sendMessage(Text.literal(catName + ": ").formatted(Formatting.YELLOW)
                .append(Text.literal(line).formatted(Formatting.WHITE)), false);
    }

    // ------------------------------------------------------------------ hunting
    private void completeHunt(LivingEntity prey) {
        ItemStack loot = new ItemStack(
                prey.getType() == net.minecraft.entity.EntityType.COD ? Items.COD
                : prey.getType() == net.minecraft.entity.EntityType.SALMON ? Items.SALMON
                : prey.getType() == net.minecraft.entity.EntityType.TROPICAL_FISH ? Items.TROPICAL_FISH
                : prey.getType() == net.minecraft.entity.EntityType.CHICKEN ? Items.CHICKEN
                : Items.RABBIT, 1);
        prey.discard();
        jobCooldown = 1200; // a minute between hunts

        if (!(getWorld() instanceof ServerWorld sw)) return;
        Optional<CampClaim> camp = CampClaims.nearest(sw.getServer(), clanId, getPos());
        boolean stored = false;
        if (camp.isPresent()) {
            BlockPos barrel = CampLayout.barrelPos(camp.get().center(), camp.get().rot());
            if (sw.isChunkLoaded(barrel)) {
                BlockEntity be = sw.getBlockEntity(barrel);
                if (be instanceof Inventory inv) stored = insert(inv, loot);
            }
        }
        if (!stored) dropStack(loot);
    }

    private static boolean insert(Inventory inv, ItemStack stack) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, stack) && s.getCount() < s.getMaxCount()) {
                int move = Math.min(stack.getCount(), s.getMaxCount() - s.getCount());
                s.increment(move);
                stack.decrement(move);
                if (stack.isEmpty()) {
                    inv.markDirty();
                    return true;
                }
            }
        }
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) {
                inv.setStack(i, stack.copy());
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    /** Hunters chase prey near the camp, then add it to the fresh-kill barrel. */
    static class HuntPreyGoal extends Goal {
        private final WildCatEntity cat;
        private LivingEntity prey;
        private int giveUp;

        HuntPreyGoal(WildCatEntity cat) {
            this.cat = cat;
            setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!cat.isRecruited() || cat.job != CatJob.HUNTER || cat.jobCooldown > 0 || !cat.hasPositionTarget()) return false;
            if (cat.getRandom().nextInt(20) != 0) return false; // don't scan every tick
            BlockPos home = cat.getPositionTarget();
            List<LivingEntity> found = cat.getWorld().getEntitiesByClass(LivingEntity.class,
                    new Box(home).expand(24.0), e -> e.isAlive() && CatEvents.PREY.contains(e.getType()));
            prey = found.stream().min(Comparator.comparingDouble(cat::squaredDistanceTo)).orElse(null);
            return prey != null;
        }

        @Override
        public void start() {
            giveUp = 400;
            cat.getNavigation().startMovingTo(prey, 1.3);
        }

        @Override
        public boolean shouldContinue() {
            return prey != null && prey.isAlive() && giveUp > 0 && cat.job == CatJob.HUNTER;
        }

        @Override
        public void tick() {
            giveUp--;
            cat.getLookControl().lookAt(prey);
            if (cat.age % 10 == 0) cat.getNavigation().startMovingTo(prey, 1.3);
            if (cat.squaredDistanceTo(prey) < 2.5) {
                cat.completeHunt(prey);
                prey = null;
            }
        }

        @Override
        public void stop() {
            prey = null;
            cat.getNavigation().stop();
        }
    }
}
