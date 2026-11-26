package com.example.clancats;

import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Cat form is expressed purely through entity attribute modifiers. Attributes are synced to
 * every client, so clients can tell who is a cat (see {@link #isCat}) without custom networking.
 */
public final class CatAbilities {
    public static final Identifier FORM_ID = ClanCatsMod.id("cat_form");

    private CatAbilities() {}

    /** Works on both logical sides. */
    public static boolean isCat(PlayerEntity player) {
        EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        return scale != null && scale.hasModifier(FORM_ID);
    }

    public static CatData data(ServerPlayerEntity p) {
        return p.getAttachedOrElse(ClanCatsMod.CAT_DATA, CatData.DEFAULT);
    }

    /** Re-applies every cat modifier from the player's saved data. Call after any change. */
    public static void apply(ServerPlayerEntity p) {
        CatData d = data(p);
        CatRank rank = d.rank();
        boolean cat = d.cat();

        set(p, EntityAttributes.GENERIC_SCALE, -0.6, Operation.ADD_MULTIPLIED_TOTAL, cat);   // small body
        set(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.15, Operation.ADD_MULTIPLIED_BASE, cat);
        set(p, EntityAttributes.GENERIC_JUMP_STRENGTH, 0.2, Operation.ADD_VALUE, cat);
        set(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 8.0, Operation.ADD_VALUE, cat);  // lands on its feet
        set(p, EntityAttributes.GENERIC_STEP_HEIGHT, 0.4, Operation.ADD_VALUE, cat);         // hops up full blocks
        set(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, rank.attackBonus, Operation.ADD_VALUE, cat);
        set(p, EntityAttributes.GENERIC_MAX_HEALTH, rank.healthBonus, Operation.ADD_VALUE, cat);

        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        updateTeam(p, d);
    }

    private static void set(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr,
                            double value, Operation op, boolean active) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(FORM_ID);
        if (active) inst.addPersistentModifier(new EntityAttributeModifier(FORM_ID, value, op));
    }

    /** Gives each cat a personal scoreboard team so name tags / chat show clan + rank. */
    private static void updateTeam(ServerPlayerEntity p, CatData d) {
        Scoreboard sb = p.getServer().getScoreboard();
        String teamName = "cc_" + p.getUuid().toString().substring(0, 8);
        Team team = sb.getTeam(teamName);

        if (!d.cat()) {
            if (team != null) sb.removeTeam(team);
            return;
        }
        if (team == null) team = sb.addTeam(teamName);

        Formatting color = d.clanOpt().map(c -> c.color).orElse(Formatting.WHITE);
        String clanLabel = d.clanOpt().map(c -> c.display).orElse("Loner");
        team.setColor(color);
        team.setPrefix(Text.literal("[" + clanLabel + "] ").formatted(color));
        team.setSuffix(Text.literal(" (" + d.rank().title + ")").formatted(d.rank().color));
        if (p.getScoreboardTeam() != team) {
            sb.addScoreHolderToTeam(p.getNameForScoreboard(), team);
        }
    }

    /** Cat eyes: dim places become visible. Runs once per second. */
    public static void tickNightVision(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!data(p).cat()) continue;
            if (p.getWorld().getLightLevel(p.getBlockPos()) < 8) {
                // >200 ticks avoids the vanilla "fading" flicker
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 240, 0, true, false, false));
            }
        }
    }
}
