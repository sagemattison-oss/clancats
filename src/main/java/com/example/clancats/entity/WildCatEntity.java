package com.example.clancats.entity;

import com.example.clancats.CampClaims;
import com.example.clancats.CampUpgrade;
import com.example.clancats.CatAbilities;
import com.example.clancats.CatEvents;
import com.example.clancats.CatNames;
import com.example.clancats.ClanCatsMod;
import com.example.clancats.OpenRecruitPayload;
import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.world.CampLayout;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
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
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A loner cat. Feed it fish or meat while you belong to a clan with a claimed camp and a screen lets you
 * name it and pick its job; it then joins your clan and works from your nearest camp. Recruits can be told
 * to follow you (they keep doing their job) and go back to camp when told to stop. Camps with a nursery
 * bear kits, which grow into apprentices and (with a training ground) warriors.
 */
public class WildCatEntity extends CatEntity {
    public enum Stage { KIT, APPRENTICE, WARRIOR }

    private static final Identifier BONUS_ID = ClanCatsMod.id("npc_bonus");

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
    private static final String[] FOLLOW_LINES = {
            "Lead the way.",
            "Right behind you.",
            "I'll watch your tail."
    };
    private static final String[] KIT_LINES = {
            "Mew! Are you a real warrior?",
            "Can I come hunting too? Please?",
            "I'm going to be the best hunter ever!"
    };

    private String catName = "";
    private String clanId = "";
    private CatJob job = CatJob.HUNTER;
    private Stage stage = Stage.WARRIOR;
    private int rankTicks = 0;
    private int jobCooldown = 0;
    private UUID followId = null;
    private int lostTicks = 0;

    public WildCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
    }

    public boolean isRecruited() {
        return !clanId.isEmpty();
    }

    public boolean isFollowing() {
        return followId != null;
    }

    public boolean isKit() {
        return stage == Stage.KIT;
    }

    public String getClanId() {
        return clanId;
    }

    // ------------------------------------------------------------------ setup
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.3, true));
        this.goalSelector.add(3, new HuntPreyGoal(this));
        this.goalSelector.add(4, new FollowCommanderGoal(this));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8) {
            @Override
            public boolean canStart() {
                return !isFollowing() && super.canStart();
            }
        });
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false,
                e -> isRecruited() && job == CatJob.GUARD && stage != Stage.KIT
                        && !(e instanceof CreeperEntity) && nearPost(e)));
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 EntityData entityData) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
        setBreedingAge(0); // adult unless becomeKit() says otherwise
        setPersistent();
        if (catName.isEmpty()) {
            catName = CatNames.random();
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
        nbt.putString("Stage", stage.name());
        nbt.putInt("RankTicks", rankTicks);
        nbt.putString("Follow", followId == null ? "" : followId.toString());
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
        try {
            stage = Stage.valueOf(nbt.getString("Stage"));
        } catch (IllegalArgumentException e) {
            stage = Stage.WARRIOR;
        }
        rankTicks = nbt.getInt("RankTicks");
        String f = nbt.getString("Follow");
        try {
            followId = f.isEmpty() ? null : UUID.fromString(f);
        } catch (IllegalArgumentException e) {
            followId = null;
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
            String role = switch (stage) {
                case KIT -> "Kit";
                case APPRENTICE -> "Apprentice " + job.title;
                case WARRIOR -> job.title;
            };
            MutableText tag = Text.literal("(" + role + (isFollowing() ? ", following" : "") + ")")
                    .formatted(Formatting.WHITE);
            name = Text.literal(catName + " ")
                    .append(Text.literal("[" + clan.display + "] ").formatted(clan.color))
                    .append(tag);
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }

    private static String clean(String s) {
        String t = s == null ? "" : s.replace("\u00a7", "").trim();
        return t.length() > 24 ? t.substring(0, 24) : t;
    }

    // ------------------------------------------------------------------ camp lookups
    private Optional<CampClaim> homeClaim() {
        if (!(getWorld() instanceof ServerWorld sw) || !isRecruited()) return Optional.empty();
        return CampClaims.nearest(sw.getServer(), clanId, getPos());
    }

    private boolean homeHas(CampUpgrade u) {
        return homeClaim().map(c -> c.has(u)).orElse(false);
    }

    private void tellClan(String message) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        CatClan clan = CatClan.byId(clanId).orElse(null);
        Formatting color = clan != null ? clan.color : Formatting.WHITE;
        for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
            if (CatAbilities.data(p).clan().equals(clanId)) {
                p.sendMessage(Text.literal(message).formatted(color), false);
            }
        }
    }

    // ------------------------------------------------------------------ kits & training
    /** Turns this cat into a clan kit born at the given nursery. */
    public void becomeKit(String clan, BlockPos nursery) {
        clanId = clan;
        stage = Stage.KIT;
        rankTicks = 0;
        CatJob[] jobs = CatJob.values();
        job = jobs[getRandom().nextInt(jobs.length)];
        catName = CatNames.kit();
        setBreedingAge(-6000); // five minutes until it grows up
        setPersistent();
        setPositionTarget(nursery, 6);
        refreshName();
    }

    private void setBonus(RegistryEntry<EntityAttribute> attr, double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(BONUS_ID);
        if (value != 0.0) inst.addPersistentModifier(new EntityAttributeModifier(BONUS_ID, value, op));
    }

    /** Warriors are tougher; border-patrol guards hit harder and move faster. */
    private void refreshBonuses() {
        if (!isRecruited()) return;
        boolean warrior = stage == Stage.WARRIOR;
        boolean patrol = job == CatJob.GUARD && stage != Stage.KIT && homeHas(CampUpgrade.BORDER_PATROL);
        setBonus(EntityAttributes.GENERIC_MAX_HEALTH, warrior ? 6.0 : 0.0, EntityAttributeModifier.Operation.ADD_VALUE);
        setBonus(EntityAttributes.GENERIC_ATTACK_DAMAGE, (warrior ? 1.5 : 0.0) + (patrol ? 2.0 : 0.0),
                EntityAttributeModifier.Operation.ADD_VALUE);
        setBonus(EntityAttributes.GENERIC_MOVEMENT_SPEED, patrol ? 0.15 : 0.0,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || !isRecruited()) return;

        if (jobCooldown > 0) jobCooldown--;

        if (stage == Stage.KIT && !isBaby()) {
            stage = Stage.APPRENTICE;
            rankTicks = 0;
            catName = CatNames.toApprentice(catName);
            refreshName();
            refreshBonuses();
            tellClan(catName + " has grown into an apprentice.");
        } else if (stage == Stage.APPRENTICE) {
            rankTicks++;
            if (rankTicks >= 6000 && age % 100 == 0 && homeHas(CampUpgrade.TRAINING)) {
                stage = Stage.WARRIOR;
                catName = CatNames.toWarrior(catName);
                refreshName();
                refreshBonuses();
                tellClan(catName + " finished training and is now a warrior!");
            }
        }

        if (age % 200 == 0) refreshBonuses();

        if (isFollowing()) {
            // If the player logs off or leaves the dimension, give up and go home.
            if (commander() == null) {
                if (++lostTicks > 200) stopFollowing();
            } else {
                lostTicks = 0;
            }
        } else if (age % 200 == 0 && !hasPositionTarget() && stage != Stage.KIT) {
            homeClaim().ifPresent(c -> setPositionTarget(c.center(), 14));
        }

        if (job == CatJob.HEALER && stage != Stage.KIT && age % 100 == 0) healNearbyClanmates();
    }

    private void healNearbyClanmates() {
        int amplifier = homeHas(CampUpgrade.HERB_STORE) ? 1 : 0;
        List<ServerPlayerEntity> players = getWorld().getEntitiesByClass(
                ServerPlayerEntity.class, getBoundingBox().expand(8.0), p -> true);
        for (ServerPlayerEntity p : players) {
            if (!CatAbilities.data(p).clan().equals(clanId)) continue;
            if (p.getHealth() < p.getMaxHealth()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, amplifier));
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        p.getX(), p.getBodyY(0.6), p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** The player this cat is following, or null if not following / not around. */
    private PlayerEntity commander() {
        if (followId == null) return null;
        PlayerEntity p = getWorld().getPlayerByUuid(followId);
        return (p != null && p.isAlive() && !p.isSpectator()) ? p : null;
    }

    /** Guards defend the camp, or the player they are following. */
    private boolean nearPost(LivingEntity e) {
        if (isFollowing()) {
            PlayerEntity boss = commander();
            return boss != null && e.squaredDistanceTo(boss) < 16.0 * 16.0;
        }
        if (!hasPositionTarget()) return false;
        BlockPos h = getPositionTarget();
        return e.squaredDistanceTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5) < 22.0 * 22.0;
    }

    // ------------------------------------------------------------------ following
    private void startFollowing(ServerPlayerEntity sp) {
        followId = sp.getUuid();
        lostTicks = 0;
        clearPositionTarget(); // free to leave the camp area
        getNavigation().stop();
        refreshName();
    }

    /** Stops following and teleports to the nearest camp the clan has claimed. */
    private void stopFollowing() {
        followId = null;
        lostTicks = 0;
        refreshName();
        homeClaim().ifPresent(this::sendHome);
    }

    private void teleportNear(PlayerEntity boss) {
        for (int i = 0; i < 10; i++) {
            double x = boss.getX() + (getRandom().nextDouble() - 0.5) * 6.0;
            double z = boss.getZ() + (getRandom().nextDouble() - 0.5) * 6.0;
            double y = boss.getY();
            if (getWorld().isSpaceEmpty(this, getBoundingBox().offset(x - getX(), y - getY(), z - getZ()))) {
                refreshPositionAndAngles(x, y, z, getYaw(), getPitch());
                getNavigation().stop();
                return;
            }
        }
        refreshPositionAndAngles(boss.getX(), boss.getY(), boss.getZ(), getYaw(), getPitch());
        getNavigation().stop();
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
                String problem = recruitProblem(sp, data);
                if (problem != null) {
                    say(sp, problem);
                } else {
                    ServerPlayNetworking.send(sp, new OpenRecruitPayload(getId(), catName));
                }
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

        // A named name tag renames the cat.
        if (held.isOf(Items.NAME_TAG) && held.contains(DataComponentTypes.CUSTOM_NAME)) {
            Text tagName = held.get(DataComponentTypes.CUSTOM_NAME);
            String renamed = tagName == null ? "" : clean(tagName.getString());
            if (!renamed.isEmpty()) {
                catName = renamed;
                if (!sp.isCreative()) held.decrement(1);
                refreshName();
                say(sp, "A new name... " + catName + ". I like it.");
                return ActionResult.SUCCESS;
            }
        }

        if (stage == Stage.KIT) {
            say(sp, pick(KIT_LINES));
            return ActionResult.SUCCESS;
        }

        if (sp.isSneaking() && held.isEmpty()) {
            job = job.next();
            jobCooldown = 0;
            refreshName();
            refreshBonuses();
            say(sp, "Understood. I'll be a " + job.title + " now. (" + job.title + " " + job.blurb + ")");
        } else if (held.isEmpty()) {
            if (isFollowing() && sp.getUuid().equals(followId)) {
                stopFollowing();
                say(sp, "Back to camp, then.");
            } else {
                startFollowing(sp);
                say(sp, pick(FOLLOW_LINES) + " I'll keep working as a " + job.title + " while we travel.");
            }
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

    /** Why this player can't recruit this cat right now, or null if they can. */
    private String recruitProblem(ServerPlayerEntity sp, CatData data) {
        if (!data.cat()) return "A two-leg wants me to join a clan? Come back as a cat. (/cat become)";
        CatClan clan = data.clanOpt().orElse(null);
        if (clan == null) return "You're a loner like me. Join a clan first. (/cat clan join <clan>)";
        if (getWorld().getRegistryKey() != World.OVERWORLD) return "This place doesn't feel like home. Find me in the overworld.";
        if (CampClaims.nearest(sp.getServer(), clan.id, getPos()).isEmpty()) {
            return clan.display + " has no camp. Claim a wild camp first. (/cat clan claim)";
        }
        return null;
    }

    /** Called when the player confirms the recruit screen. */
    public void completeRecruit(ServerPlayerEntity sp, String requestedName, CatJob chosenJob) {
        if (isRecruited()) return;
        CatData data = CatAbilities.data(sp);
        String problem = recruitProblem(sp, data);
        if (problem != null) {
            say(sp, problem);
            return;
        }
        ItemStack held = sp.getMainHandStack();
        if (!CatEvents.CAT_FOOD.contains(held.getItem())) {
            say(sp, "Hold out some fish or meat if you want me to join you.");
            return;
        }
        CatClan clan = data.clanOpt().orElseThrow();
        Optional<CampClaim> camp = CampClaims.nearest(sp.getServer(), clan.id, getPos());

        if (!sp.isCreative()) held.decrement(1);
        String cleaned = clean(requestedName);
        if (!cleaned.isEmpty()) catName = cleaned;
        clanId = clan.id;
        job = chosenJob;
        stage = Stage.WARRIOR;
        rankTicks = 0;
        jobCooldown = 0;
        followId = null;
        camp.ifPresent(this::sendHome);
        refreshName();
        refreshBonuses();

        ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.8), getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        say(sp, "Thank you. I'll join " + clan.display + " as a " + job.title + ". I'll meet you at camp.");
        sp.sendMessage(Text.literal(catName + " joined " + clan.display + " as a " + job.title
                + ". Right-click with an empty hand to make them follow or stay. Sneak + right-click changes their job; "
                + "a named name tag renames them.").formatted(clan.color), false);
    }

    private void sendHome(CampClaim claim) {
        BlockPos c = claim.center();
        double a = getRandom().nextDouble() * Math.PI * 2.0;
        double r = 5.5 + getRandom().nextDouble() * 1.3; // ring around the Great Rock, clear of dens and walls
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
        boolean party = homeHas(CampUpgrade.HUNTING_PARTY);
        ItemStack loot = new ItemStack(
                prey.getType() == EntityType.COD ? Items.COD
                : prey.getType() == EntityType.SALMON ? Items.SALMON
                : prey.getType() == EntityType.TROPICAL_FISH ? Items.TROPICAL_FISH
                : prey.getType() == EntityType.CHICKEN ? Items.CHICKEN
                : Items.RABBIT, party ? 2 : 1);
        prey.discard();
        jobCooldown = party ? 600 : 1200;

        // While following, the catch goes straight to the player.
        PlayerEntity boss = isFollowing() ? commander() : null;
        if (boss != null) {
            boss.giveItemStack(loot);
            boss.sendMessage(Text.literal(catName + " caught prey for you.").formatted(Formatting.YELLOW), true);
            return;
        }

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

    // ------------------------------------------------------------------ goals
    /** Hunters chase prey near the camp (or near the player they follow), then deliver it. */
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
            if (!cat.isRecruited() || cat.job != CatJob.HUNTER || cat.stage == Stage.KIT || cat.jobCooldown > 0) return false;
            PlayerEntity boss = cat.isFollowing() ? cat.commander() : null;
            if (cat.isFollowing() && boss == null) return false;
            if (boss == null && !cat.hasPositionTarget()) return false;
            if (cat.getRandom().nextInt(20) != 0) return false; // don't scan every tick

            Box area = boss != null
                    ? boss.getBoundingBox().expand(14.0)
                    : new Box(cat.getPositionTarget()).expand(24.0);
            List<LivingEntity> found = cat.getWorld().getEntitiesByClass(LivingEntity.class, area,
                    e -> e.isAlive() && CatEvents.PREY.contains(e.getType()));
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

    /** Trails the player; teleports to them if they get far away. Lower priority than hunting. */
    static class FollowCommanderGoal extends Goal {
        private final WildCatEntity cat;
        private int timer;

        FollowCommanderGoal(WildCatEntity cat) {
            this.cat = cat;
            setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            PlayerEntity boss = cat.commander();
            return cat.isFollowing() && boss != null && cat.squaredDistanceTo(boss) > 16.0;
        }

        @Override
        public boolean shouldContinue() {
            PlayerEntity boss = cat.commander();
            return cat.isFollowing() && boss != null && cat.squaredDistanceTo(boss) > 6.25;
        }

        @Override
        public void start() {
            timer = 0;
        }

        @Override
        public void tick() {
            PlayerEntity boss = cat.commander();
            if (boss == null) return;
            cat.getLookControl().lookAt(boss);
            if (--timer <= 0) {
                timer = 8;
                if (cat.squaredDistanceTo(boss) > 576.0) {
                    cat.teleportNear(boss);
                } else {
                    cat.getNavigation().startMovingTo(boss, 1.25);
                }
            }
        }

        @Override
        public void stop() {
            cat.getNavigation().stop();
        }
    }
}
