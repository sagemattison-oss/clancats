package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import com.example.clancats.world.CampSite;
import com.example.clancats.world.ModStructures;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
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
import java.util.List;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CatCommands {
    private CatCommands() {}

    private static final SuggestionProvider<ServerCommandSource> CLANS = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(CatClan.values()).map(c -> c.id), builder);

    private static final SuggestionProvider<ServerCommandSource> FURS = (ctx, builder) ->
            CommandSource.suggestMatching(CatVariants.namesWithDefault(), builder);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("cat")
                        .then(literal("become").executes(c -> become(c, true)))
                        .then(literal("revert").executes(c -> become(c, false)))
                        .then(literal("status").executes(CatCommands::status))
                        .then(literal("meow").executes(c -> sound(c, SoundEvents.ENTITY_CAT_AMBIENT)))
                        .then(literal("purr").executes(c -> sound(c, SoundEvents.ENTITY_CAT_PURR)))
                        .then(literal("hiss").executes(c -> sound(c, SoundEvents.ENTITY_CAT_HISS)))
                        .then(literal("fur").then(argument("variant", StringArgumentType.word())
                                .suggests(FURS).executes(CatCommands::setFur)))
                        .then(literal("clan")
                                .then(literal("join").then(argument("clan", StringArgumentType.word())
                                        .suggests(CLANS).executes(CatCommands::joinClan)))
                                .then(literal("leave").executes(CatCommands::leaveClan))
                                .then(literal("list").executes(CatCommands::listClans))
                                .then(literal("camp").executes(CatCommands::camp))
                                .then(literal("findcamp").executes(CatCommands::findCamp))
                                .then(literal("claim").executes(CatCommands::claimCamp))
                                .then(literal("unclaim").executes(CatCommands::unclaimCamp)))
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

    private static String clanName(String id) {
        return CatClan.byId(id).map(cl -> cl.display).orElse("another clan");
    }

    // ---- cat form ----
    private static int become(CommandContext<ServerCommandSource> c, boolean cat) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (d.cat() == cat) return fail(c, cat ? "You are already a cat." : "You are not a cat.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withCat(cat));
        CatAbilities.apply(p);
        say(c, Text.literal(cat
                ? "Your senses sharpen and the world grows huge. You are a cat. Try /cat clan findcamp."
                : "Your paws become hands once more.").formatted(Formatting.GOLD));
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
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

    private static int sound(CommandContext<ServerCommandSource> c, SoundEvent ev) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        if (!CatAbilities.data(p).cat()) return fail(c, "Only cats can do that.");
        p.getServerWorld().playSound(null, p.getBlockPos(), ev, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }

    private static int setFur(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (!d.cat()) return fail(c, "Become a cat first (/cat become).");
        String v = StringArgumentType.getString(c, "variant").toLowerCase(java.util.Locale.ROOT);
        boolean reset = v.equals("default");
        if (!reset && !CatVariants.names().contains(v)) return fail(c, "Unknown fur: " + v);
        p.setAttached(ClanCatsMod.CAT_DATA, d.withFur(reset ? "" : v));
        CatAbilities.apply(p);
        say(c, Text.literal(reset ? "Your coat returns to its natural pattern."
                : "Your coat is now " + CatVariants.pretty(v) + ".").formatted(Formatting.GOLD));
        return 1;
    }

    // ---- clans ----
    private static int joinClan(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
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

    private static int leaveClan(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        if (d.clanOpt().isEmpty()) return fail(c, "You are already a loner.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withClan(""));
        CatAbilities.apply(p);
        say(c, Text.literal("You leave your clan and walk alone.").formatted(Formatting.GRAY));
        return 1;
    }

    private static int listClans(CommandContext<ServerCommandSource> c) {
        MinecraftServer server = c.getSource().getServer();
        List<CampClaim> claims = CampClaims.all(server);
        for (CatClan clan : CatClan.values()) {
            long members = server.getPlayerManager().getPlayerList().stream()
                    .filter(pl -> CatAbilities.data(pl).clan().equals(clan.id)).count();
            long camps = claims.stream().filter(cl -> cl.clan().equals(clan.id)).count();
            say(c, Text.literal(clan.display + " - " + members + " online, " + camps + " camp(s)").formatted(clan.color));
        }
        return 1;
    }

    private static int camp(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatClan clan = CatAbilities.data(p).clanOpt().orElse(null);
        if (clan == null) return fail(c, "Loners have no camp. Join one with /cat clan join <clan>.");
        Optional<CampClaim> camp = CampClaims.nearest(c.getSource().getServer(), clan.id, p.getPos());
        if (camp.isEmpty()) return fail(c, clan.display + " has no camp yet. Find one with /cat clan findcamp, then /cat clan claim.");
        BlockPos pos = camp.get().center();
        int dist = (int) Math.sqrt(p.squaredDistanceTo(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5));
        say(c, Text.literal("Nearest " + clan.display + " camp: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " (" + dist + " blocks away)").formatted(clan.color));
        return 1;
    }

    private static int findCamp(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return fail(c, "Wild camps are only in the overworld.");
        BlockPos found = world.locateStructure(ModStructures.CAMP_TAG, p.getBlockPos(), 64, false);
        if (found == null) return fail(c, "No wild camp found nearby. Keep exploring.");
        int dist = (int) Math.sqrt(p.squaredDistanceTo(found.getX() + 0.5, p.getY(), found.getZ() + 0.5));
        say(c, Text.literal("Nearest wild camp: x " + found.getX() + ", z " + found.getZ()
                + " (" + dist + " blocks away). Look for a cave mouth in the ground.").formatted(Formatting.GOLD));
        return 1;
    }

    private static int claimCamp(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        CatClan clan = d.clanOpt().orElse(null);
        if (clan == null) return fail(c, "Join a clan first (/cat clan join <clan>).");
        boolean allowed = p.hasPermissionLevel(2) || d.rank().ordinal() >= CatRank.WARRIOR.ordinal();
        if (!allowed) return fail(c, "Only a Warrior or higher (or an operator) can claim a camp.");
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return fail(c, "Camps are only in the overworld.");

        CampSite piece = ModStructures.findCamp(world, p.getBlockPos());
        if (piece == null) return fail(c, "You need to be inside a wild camp. Try /cat clan findcamp.");

        MinecraftServer server = c.getSource().getServer();
        Optional<CampClaim> existing = CampClaims.at(server, piece.center());
        if (existing.isPresent()) return fail(c, "This camp already belongs to " + clanName(existing.get().clan()) + ".");

        CampClaims.add(server, new CampClaim(piece.center(), piece.rot(), clan.id, piece.markerY()));
        CampClaims.setMarker(world, piece.center(), piece.markerY(), clan.marker.getDefaultState());
        server.getPlayerManager().broadcast(
                Text.literal(clan.display + " has claimed a camp!").formatted(clan.color), false);
        return 1;
    }

    private static int unclaimCamp(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatData d = CatAbilities.data(p);
        ServerWorld world = p.getServerWorld();
        CampSite piece = ModStructures.findCamp(world, p.getBlockPos());
        if (piece == null) return fail(c, "You need to be inside the camp you want to give up.");

        MinecraftServer server = c.getSource().getServer();
        Optional<CampClaim> claim = CampClaims.at(server, piece.center());
        if (claim.isEmpty()) return fail(c, "This camp isn't claimed.");

        boolean op = p.hasPermissionLevel(2);
        boolean leadership = d.rank() == CatRank.LEADER || d.rank() == CatRank.DEPUTY;
        if (!op && !(leadership && claim.get().clan().equals(d.clan()))) {
            return fail(c, "Only an operator, or your own clan's Leader or Deputy, can release this camp.");
        }
        CampClaims.remove(server, claim.get());
        CampClaims.setMarker(world, piece.center(), claim.get().markerY(), Blocks.STONE.getDefaultState());
        say(c, Text.literal("The camp is wild again.").formatted(Formatting.GRAY));
        return 1;
    }

    // ---- admin ----
    private static int setPrey(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
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
