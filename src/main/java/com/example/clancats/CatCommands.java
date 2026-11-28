package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * The /cat commands. Everything here is also available from the menus (press J, or /cat menu);
 * both routes call {@link CatActions}, so they always behave the same.
 */
public final class CatCommands {
    private CatCommands() {}

    private static final SuggestionProvider<ServerCommandSource> CLANS = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(CatClan.values()).map(c -> c.id), builder);

    private static final SuggestionProvider<ServerCommandSource> FURS = (ctx, builder) ->
            CommandSource.suggestMatching(CatVariants.namesWithDefault(), builder);

    private static final SuggestionProvider<ServerCommandSource> UPGRADES = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(CampUpgrade.values()).map(u -> u.id), builder);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("cat")
                        .then(literal("menu").executes(CatCommands::menu))
                        .then(literal("become").executes(c -> exec(c, p -> CatActions.become(p, true), true)))
                        .then(literal("revert").executes(c -> exec(c, p -> CatActions.become(p, false), true)))
                        .then(literal("status").executes(CatCommands::status))
                        .then(literal("meow").executes(c -> exec(c, p -> CatActions.sound(p, "meow"), false)))
                        .then(literal("purr").executes(c -> exec(c, p -> CatActions.sound(p, "purr"), false)))
                        .then(literal("hiss").executes(c -> exec(c, p -> CatActions.sound(p, "hiss"), false)))
                        .then(literal("fur").then(argument("variant", StringArgumentType.word())
                                .suggests(FURS).executes(CatCommands::setFur)))
                        .then(literal("clan")
                                .then(literal("join").then(argument("clan", StringArgumentType.word())
                                        .suggests(CLANS).executes(c -> {
                                            String id = StringArgumentType.getString(c, "clan");
                                            return exec(c, p -> CatActions.joinClan(p, id), false);
                                        })))
                                .then(literal("leave").executes(c -> exec(c, CatActions::leaveClan, true)))
                                .then(literal("list").executes(CatCommands::listClans))
                                .then(literal("camp").executes(c -> exec(c, CatActions::camp, true)))
                                .then(literal("findcamp").executes(c -> exec(c, CatActions::findCamp, true)))
                                .then(literal("claim").executes(c -> exec(c, CatActions::claimCamp, false)))
                                .then(literal("unclaim").executes(c -> exec(c, CatActions::unclaimCamp, true)))
                                .then(literal("upgrade").executes(CatCommands::upgradeInfo)
                                        .then(argument("id", StringArgumentType.word())
                                                .suggests(UPGRADES).executes(c -> {
                                                    String id = StringArgumentType.getString(c, "id");
                                                    return exec(c, p -> CatActions.buyUpgrade(p, id), false);
                                                }))))
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

    /** Runs a shared action for the command's player and reports its result. */
    private static int exec(CommandContext<ServerCommandSource> c, Function<ServerPlayerEntity, CatActions.Result> action,
                            boolean feedback) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatActions.Result r = action.apply(p);
        if (!r.ok()) {
            c.getSource().sendError(r.text());
            return 0;
        }
        if (feedback) c.getSource().sendFeedback(r::text, false);
        return 1;
    }

    private static int menu(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        MenuNetworking.sendMenu(c.getSource().getPlayerOrThrow(), "hub", null, false, false);
        return 1;
    }

    // ---- info and fur (kept as commands; menus have their own screens for these) ----
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

    private static int upgradeInfo(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CatActions.Own own = CatActions.ownCampNear(p);
        if (own.claim() == null) {
            c.getSource().sendError(own.error().text());
            return 0;
        }
        CampClaim claim = own.claim();
        CatClan clan = CatClan.byId(claim.clan()).orElse(null);

        Inventory barrel = CampManager.barrel(p.getServerWorld(), claim);
        Map<CampResource, Integer> stock = barrel == null ? null : CampManager.stock(barrel);

        StringBuilder sb = new StringBuilder();
        sb.append(clan != null ? clan.display : "Clan").append(" camp upgrades. Right-click the camp's fresh-kill barrel "
                + "for the upgrade menu, or run /cat clan upgrade <id>.\n");
        if (stock == null) {
            sb.append("(Can't see the barrel from here. Stand closer to camp.)\n");
        } else {
            sb.append("Stock: ");
            for (CampResource r : CampResource.values()) sb.append(stock.get(r)).append(' ').append(r.label).append("  ");
            sb.append('\n');
        }
        for (CampUpgrade u : CampUpgrade.values()) {
            String state;
            if (claim.has(u)) {
                state = "owned";
            } else if (!Arrays.stream(u.requires).allMatch(claim.upgrades()::contains)) {
                state = "locked";
            } else if (stock != null && CampManager.canAfford(stock, u)) {
                state = "ready";
            } else {
                state = "need more";
            }
            sb.append('[').append(state).append("] ").append(u.id).append(" - ").append(u.title).append(": ").append(u.blurb);
            if (!claim.has(u)) {
                sb.append(" (cost: ").append(u.costString());
                if (u.requires.length > 0) sb.append("; needs ").append(String.join(", ", u.requires));
                sb.append(')');
            }
            sb.append('\n');
        }
        say(c, Text.literal(sb.toString().trim()).formatted(clan != null ? clan.color : Formatting.WHITE));
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
