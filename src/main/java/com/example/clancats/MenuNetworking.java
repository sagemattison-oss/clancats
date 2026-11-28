package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import com.example.clancats.world.CampLayout;
import com.example.clancats.world.CampSite;
import com.example.clancats.world.ModStructures;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Menu plumbing: button presses from the client, the state the screens draw, the "you entered an
 * unclaimed camp" pop-up, and right-clicking a clan's fresh-kill barrel to open the upgrade tree.
 * All rules live in {@link CatActions}; this class only routes and reports.
 */
public final class MenuNetworking {
    private MenuNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(MenuPayload.ID, MenuPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> handle(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(MenuNetworking::tick);

        // Right-click your own clan's fresh-kill barrel -> upgrade menu. Sneak + right-click opens the barrel itself.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || player.isSpectator() || player.isSneaking()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            String clan = CatAbilities.data(sp).clan();
            if (clan.isEmpty()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            for (CampClaim c : CampClaims.all(sw.getServer())) {
                if (c.clan().equals(clan) && CampLayout.barrelPos(c.center(), c.rot()).equals(pos)) {
                    sendMenu(sp, "upgrades", null, false, false);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ button presses
    private static void handle(ServerPlayerEntity p, ActionPayload a) {
        String arg = a.arg();
        String menu = a.menu();
        CatActions.Result r = null;
        switch (a.action()) {
            case "open" -> {
                sendMenu(p, arg, null, false, false);
                return;
            }
            case "become" -> r = CatActions.become(p, true);
            case "revert" -> r = CatActions.become(p, false);
            case "join" -> r = CatActions.joinClan(p, arg);
            case "leave" -> r = CatActions.leaveClan(p);
            case "camp" -> r = CatActions.camp(p);
            case "findcamp" -> r = CatActions.findCamp(p);
            case "claim" -> r = CatActions.claimCamp(p);
            case "unclaim" -> r = CatActions.unclaimCamp(p);
            case "upgrade" -> r = CatActions.buyUpgrade(p, arg);
            case "sound" -> r = CatActions.sound(p, arg);
            case "setprey" -> {
                String[] parts = arg.split(";", 2);
                try {
                    r = CatActions.setPrey(p, parts[0], parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0);
                } catch (NumberFormatException e) {
                    r = CatActions.Result.bad("Enter a whole number.");
                }
            }
            case "openbarrel" -> {
                CatActions.Own own = CatActions.ownCampNear(p);
                if (own.claim() == null) {
                    r = own.error();
                } else {
                    ServerWorld w = p.getServerWorld();
                    BlockPos bp = CampLayout.barrelPos(own.claim().center(), own.claim().rot());
                    BlockEntity be = w.isChunkLoaded(bp) ? w.getBlockEntity(bp) : null;
                    if (be instanceof NamedScreenHandlerFactory f && p.squaredDistanceTo(Vec3d.ofCenter(bp)) <= 36.0) {
                        p.openHandledScreen(f);
                        return;
                    }
                    r = CatActions.Result.bad("Stand next to the barrel to open it.");
                }
            }
            default -> {
                return;
            }
        }
        if (r == null) return;
        if (menu.isEmpty()) CatActions.send(p, r);
        else sendMenu(p, menu, r, true, false);
    }

    // ------------------------------------------------------------------ sending menus
    /**
     * @param refresh only update the screen if that menu is already open (never opens it)
     * @param auto    server-initiated pop-up: only shown if the player has no other screen open
     */
    public static void sendMenu(ServerPlayerEntity p, String menu, CatActions.Result r, boolean refresh, boolean auto) {
        JsonObject o = state(p, menu);
        if (o == null) return;
        if (r != null) {
            o.addProperty("msg", r.text().getString());
            o.addProperty("ok", r.ok());
        }
        if (refresh) o.addProperty("refresh", true);
        if (auto) o.addProperty("auto", true);
        ServerPlayNetworking.send(p, new MenuPayload(menu, o.toString()));
    }

    private static JsonObject state(ServerPlayerEntity p, String menu) {
        return switch (menu) {
            case "hub" -> hubState(p);
            case "clans" -> clansState(p);
            case "claim" -> claimState(p);
            case "upgrades" -> upgradesState(p);
            case "admin" -> p.hasPermissionLevel(2) ? adminState(p) : null;
            default -> null;
        };
    }

    private static int rgb(Formatting f) {
        Integer v = f.getColorValue();
        return v == null ? 0xFFFFFF : v;
    }

    private static JsonObject hubState(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        ServerWorld world = p.getServerWorld();
        CatData d = CatAbilities.data(p);
        JsonObject o = new JsonObject();
        o.addProperty("cat", d.cat());
        o.addProperty("op", p.hasPermissionLevel(2));
        if (!d.cat()) return o;

        CatRank r = d.rank();
        CatRank next = r.next();
        o.addProperty("rank", r.title);
        o.addProperty("rankColor", rgb(r.color));
        o.addProperty("prey", d.prey());
        o.addProperty("nextRank", next == null ? "" : next.title);
        o.addProperty("nextNeeded", next == null ? 0 : Math.max(0, next.preyNeeded - d.prey()));
        o.addProperty("leader", r == CatRank.LEADER);
        o.addProperty("lives", d.lives());

        CatClan clan = d.clanOpt().orElse(null);
        o.addProperty("clan", clan == null ? "" : clan.id);
        o.addProperty("clanName", clan == null ? "Loner" : clan.display);
        o.addProperty("clanColor", clan == null ? 0xAAAAAA : rgb(clan.color));

        boolean hasCamp = false;
        boolean nearCamp = false;
        if (clan != null) {
            Optional<CampClaim> camp = CampClaims.nearest(server, clan.id, p.getPos());
            if (camp.isPresent()) {
                hasCamp = true;
                BlockPos b = camp.get().center();
                double dist = Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ());
                nearCamp = dist <= 48.0;
                o.addProperty("campX", b.getX());
                o.addProperty("campY", b.getY());
                o.addProperty("campZ", b.getZ());
                o.addProperty("campDist", (int) dist);
                o.addProperty("campUpgrades", camp.get().upgrades().size());
                o.addProperty("campMax", CampUpgrade.values().length);
            }
        }
        o.addProperty("hasCamp", hasCamp);
        o.addProperty("nearCamp", nearCamp);

        boolean inCamp = false;
        String owner = "";
        boolean canClaim = false;
        boolean canRelease = false;
        if (world.getRegistryKey() == World.OVERWORLD) {
            CampSite site = ModStructures.findCamp(world, p.getBlockPos());
            if (site != null) {
                inCamp = true;
                Optional<CampClaim> claim = CampClaims.at(server, site.center());
                if (claim.isPresent()) {
                    owner = CatActions.clanName(claim.get().clan());
                    canRelease = CatActions.mayRelease(p, d, claim.get());
                } else {
                    canClaim = clan != null && CatActions.mayCommand(p, d);
                }
            }
        }
        o.addProperty("inCamp", inCamp);
        o.addProperty("campOwner", owner);
        o.addProperty("canClaim", canClaim);
        o.addProperty("canRelease", canRelease);
        o.addProperty("mayCommand", CatActions.mayCommand(p, d));
        return o;
    }

    private static JsonObject clansState(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        CatData d = CatAbilities.data(p);
        JsonObject o = new JsonObject();
        o.addProperty("cat", d.cat());
        o.addProperty("clan", d.clan());
        JsonArray arr = new JsonArray();
        for (CatClan c : CatClan.values()) {
            long members = server.getPlayerManager().getPlayerList().stream()
                    .filter(pl -> CatAbilities.data(pl).clan().equals(c.id)).count();
            long camps = CampClaims.all(server).stream().filter(cl -> cl.clan().equals(c.id)).count();
            JsonObject j = new JsonObject();
            j.addProperty("id", c.id);
            j.addProperty("name", c.display);
            j.addProperty("color", rgb(c.color));
            j.addProperty("members", members);
            j.addProperty("camps", camps);
            arr.add(j);
        }
        o.add("clans", arr);
        return o;
    }

    private static JsonObject claimState(ServerPlayerEntity p) {
        CatData d = CatAbilities.data(p);
        CatClan clan = d.clanOpt().orElse(null);
        JsonObject o = new JsonObject();
        o.addProperty("hasClan", clan != null);
        o.addProperty("clanName", clan == null ? "" : clan.display);
        o.addProperty("clanColor", clan == null ? 0xFFFFFF : rgb(clan.color));
        CampSite site = ModStructures.findCamp(p.getServerWorld(), p.getBlockPos());
        o.addProperty("kind", site == null ? 0 : site.kind());
        return o;
    }

    private static JsonObject upgradesState(ServerPlayerEntity p) {
        JsonObject o = new JsonObject();
        CatActions.Own own = CatActions.ownCampNear(p);
        if (own.claim() == null) {
            o.addProperty("available", false);
            o.addProperty("reason", own.error().text().getString());
            return o;
        }
        CampClaim claim = own.claim();
        CatData d = CatAbilities.data(p);
        CatClan clan = CatClan.byId(claim.clan()).orElse(null);
        Inventory barrel = CampManager.barrel(p.getServerWorld(), claim);
        Map<CampResource, Integer> stock = barrel == null ? null : CampManager.stock(barrel);

        o.addProperty("available", true);
        o.addProperty("clanName", clan == null ? "Clan" : clan.display);
        o.addProperty("clanColor", clan == null ? 0xFFFFFF : rgb(clan.color));
        o.addProperty("canSpend", CatActions.mayCommand(p, d));
        boolean near = false;
        if (barrel != null) {
            BlockPos bp = CampLayout.barrelPos(claim.center(), claim.rot());
            near = p.squaredDistanceTo(Vec3d.ofCenter(bp)) <= 36.0;
        }
        o.addProperty("nearBarrel", near);
        o.addProperty("stockKnown", stock != null);

        JsonArray stockArr = new JsonArray();
        if (stock != null) {
            for (CampResource res : CampResource.values()) {
                JsonObject s = new JsonObject();
                s.addProperty("label", res.label);
                s.addProperty("amount", stock.get(res));
                stockArr.add(s);
            }
        }
        o.add("stock", stockArr);

        JsonArray ups = new JsonArray();
        for (CampUpgrade u : CampUpgrade.values()) {
            String st;
            if (claim.has(u)) {
                st = "owned";
            } else if (!java.util.Arrays.stream(u.requires).allMatch(claim.upgrades()::contains)) {
                st = "locked";
            } else if (stock != null && CampManager.canAfford(stock, u)) {
                st = "ready";
            } else {
                st = "need";
            }
            JsonObject j = new JsonObject();
            j.addProperty("id", u.id);
            j.addProperty("title", u.title);
            j.addProperty("blurb", u.blurb);
            j.addProperty("cost", u.costString());
            j.addProperty("needs", CatActions.needsTitles(u));
            j.addProperty("state", st);
            ups.add(j);
        }
        o.add("upgrades", ups);
        return o;
    }

    private static JsonObject adminState(ServerPlayerEntity p) {
        JsonObject o = new JsonObject();
        JsonArray names = new JsonArray();
        for (ServerPlayerEntity pl : p.getServer().getPlayerManager().getPlayerList()) {
            if (CatAbilities.data(pl).cat()) names.add(pl.getName().getString());
        }
        o.add("players", names);
        return o;
    }

    // ------------------------------------------------------------------ "you entered an unclaimed camp"
    private static final class Presence {
        final BlockPos center;
        int missed;

        Presence(BlockPos center) {
            this.center = center;
        }
    }

    private static final Map<UUID, Presence> PRESENCE = new HashMap<>();

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if ((t + p.getId()) % 20 != 0) continue;
            checkEntry(server, p);
        }
    }

    private static void checkEntry(MinecraftServer server, ServerPlayerEntity p) {
        CatData d = CatAbilities.data(p);
        ServerWorld world = p.getServerWorld();
        if (!d.cat() || world.getRegistryKey() != World.OVERWORLD) {
            PRESENCE.remove(p.getUuid());
            return;
        }
        CampSite site = ModStructures.findCamp(world, p.getBlockPos());
        Presence pr = PRESENCE.get(p.getUuid());
        if (site == null) {
            // Leaving counts only after a few seconds outside, so wobbling at the edge doesn't re-trigger the pop-up.
            if (pr != null && ++pr.missed >= 3) PRESENCE.remove(p.getUuid());
            return;
        }
        if (pr != null && pr.center.equals(site.center())) {
            pr.missed = 0;
            return;
        }
        PRESENCE.put(p.getUuid(), new Presence(site.center()));

        Optional<CampClaim> claim = CampClaims.at(server, site.center());
        if (claim.isPresent()) {
            if (!claim.get().clan().equals(d.clan())) {
                Formatting color = CatClan.byId(claim.get().clan()).map(c -> c.color).orElse(Formatting.WHITE);
                p.sendMessage(Text.literal("You are in " + CatActions.clanName(claim.get().clan()) + " territory.")
                        .formatted(color), true);
            }
            return;
        }
        if (!CatActions.mayCommand(p, d)) {
            p.sendMessage(Text.literal("An unclaimed wild camp. Reach Warrior rank to claim one for your clan.")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        sendMenu(p, "claim", null, false, true);
    }
}
