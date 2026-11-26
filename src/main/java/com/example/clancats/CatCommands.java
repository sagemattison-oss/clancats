package com.example.clancats;

import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CatCommands {
    private CatCommands() {}

    private static final SuggestionProvider<ServerCommandSource> CLANS = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(CatClan.values()).map(c -> c.id), builder);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("cat")
                        .then(literal("become").executes(c -> become(c, true)))
                        .then(literal("revert").executes(c -> become(c, false)))
                        .then(literal("status").executes(CatCommands::status))
                        .then(literal("meow").executes(c -> sound(c, SoundEvents.ENTITY_CAT_AMBIENT)))
                        .then(literal("purr").executes(c -> sound(c, SoundEvents.ENTITY_CAT_PURR)))
                        .then(literal("hiss").executes(c -> sound(c, SoundEvents.ENTITY_CAT_HISS)))
                        .then(literal("clan")
                                .then(literal("join").then(argument("clan", StringArgumentType.word())
                                        .suggests(CLANS).executes(CatCommands::joinClan)))
                                .then(literal("leave").executes(CatCommands::leaveClan))
                                .then(literal("list").executes(CatCommands::listClans))
                                .then(literal("camp").executes(CatCommands::camp))
                                .then(literal("setcamp").executes(CatCommands::setCamp)))
                        .then(literal("setprey").requires(s -> s.hasPermissionLevel(2))
                                .then(argument("targets", EntityArgumentType.players())
                                        .then(argument("amount", IntegerArgumentType.integer(0))
                                                .executes(CatCommands::setPrey))))
        ));
    }

    // ---- helpers ----
    private static void say(CommandContext<ServerCommandSource> c, Text t) {
        c.getSource().sendFeedback(() -> t, false);
    }

    private static int fail(CommandContext<ServerCommandSource> c, String msg) {
        c.getSource().sendError(Text.literal(msg));
        return 0;
    }

    // ---- commands ----
    private static int become(CommandContext<ServerCommandSource> c, boolean cat) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (d.cat() == cat) return fail(c, cat ? "You are already a cat." : "You are not a cat.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withCat(cat));
        CatAbilities.apply(p);
        say(c, Text.literal(cat
                ? "Your senses sharpen and the world grows huge. You are a cat. Try /cat clan list."
                : "Your paws become hands once more.").formatted(Formatting.GOLD));
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (!d.cat()) return fail(c, "You are not a cat. Use /cat become.");
        CatRank r = d.rank();
        CatRank next = r.next();
        StringBuilder sb = new StringBuilder();
        sb.append("Rank: ").append(r.title).append(" | Prey: ").append(d.prey());
        if (next != null) sb.append(" (").append(next.preyNeeded - d.prey()).append(" more for ").append(next.title).append(")");
        sb.append("\nClan: ").append(d.clanOpt().map(cl -> cl.display).orElse("Loner"));
        if (r == CatRank.LEADER) sb.append("\nLives: ").append(d.lives()).append("/9");
        say(c, Text.literal(sb.toString()).formatted(r.color));
        return 1;
    }

    private static int sound(CommandContext<ServerCommandSource> c, SoundEvent ev) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        if (!CatAbilities.data(p).cat()) return fail(c, "Only cats can do that.");
        p.getServerWorld().playSound(null, p.getBlockPos(), ev, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }

    private static int joinClan(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (!d.cat()) return fail(c, "Become a cat first (/cat become).");
        String id = StringArgumentType.getString(c, "clan");
        CatClan clan = CatClan.byId(id).orElse(null);
        if (clan == null) return fail(c, "Unknown clan: " + id);
        p.setAttached(ClanCatsMod.CAT_DATA, d.withClan(clan.id));
        CatAbilities.apply(p);
        p.getServer().getPlayerManager().broadcast(
                Text.literal(p.getName().getString() + " joined " + clan.display + ".").formatted(clan.color), false);
        return 1;
    }

    private static int leaveClan(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (d.clanOpt().isEmpty()) return fail(c, "You are already a loner.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withClan(""));
        CatAbilities.apply(p);
        say(c, Text.literal("You leave your clan and walk alone.").formatted(Formatting.GRAY));
        return 1;
    }

    private static int listClans(CommandContext<ServerCommandSource> c) {
        ServerCommandSource src = c.getSource();
        Map<String, BlockPos> camps = src.getServer().getOverworld().getAttachedOrElse(ClanCatsMod.CAMPS, Map.of());
        for (CatClan clan : CatClan.values()) {
            long members = src.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(pl -> CatAbilities.data(pl).clan().equals(clan.id)).count();
            BlockPos camp = camps.get(clan.id);
            String where = camp == null ? "no camp yet" : "camp at " + camp.getX() + " " + camp.getY() + " " + camp.getZ();
            say(c, Text.literal(clan.display + " - " + members + " online, " + where).formatted(clan.color));
        }
        return 1;
    }

    private static int camp(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        CatClan clan = d.clanOpt().orElse(null);
        if (clan == null) return fail(c, "Loners have no camp. Join one with /cat clan join <clan>.");
        BlockPos camp = c.getSource().getServer().getOverworld().getAttachedOrElse(ClanCatsMod.CAMPS, Map.of()).get(clan.id);
        if (camp == null) return fail(c, clan.display + " has no camp yet. A Deputy, Leader or operator can use /cat clan setcamp.");
        double dist = Math.sqrt(p.squaredDistanceTo(camp.getX() + 0.5, p.getY(), camp.getZ() + 0.5));
        say(c, Text.literal(clan.display + " camp: " + camp.getX() + " " + camp.getY() + " " + camp.getZ()
                + " (" + (int) dist + " blocks away, overworld)").formatted(clan.color));
        return 1;
    }

    private static int setCamp(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        CatClan clan = d.clanOpt().orElse(null);
        if (clan == null) return fail(c, "Join a clan first.");
        boolean allowed = p.hasPermissionLevel(2) || d.rank() == CatRank.LEADER || d.rank() == CatRank.DEPUTY;
        if (!allowed) return fail(c, "Only a Deputy, Leader or operator can set the camp.");
        if (p.getWorld().getRegistryKey() != World.OVERWORLD) return fail(c, "Camps must be in the overworld.");
        ServerWorld overworld = c.getSource().getServer().getOverworld();
        Map<String, BlockPos> camps = new HashMap<>(overworld.getAttachedOrElse(ClanCatsMod.CAMPS, Map.of()));
        camps.put(clan.id, p.getBlockPos());
        overworld.setAttached(ClanCatsMod.CAMPS, Map.copyOf(camps));
        say(c, Text.literal(clan.display + " camp set here.").formatted(clan.color));
        return 1;
    }

    private static int setPrey(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(c, "amount");
        int n = 0;
        for (ServerPlayerEntity p : EntityArgumentType.getPlayers(c, "targets")) {
            if (!CatAbilities.data(p).cat()) continue;
            CatEvents.setPrey(p, amount, true);
            n++;
        }
        final int count = n;
        say(c, Text.literal("Set prey to " + amount + " for " + count + " cat(s)."));
        return count;
    }
}
