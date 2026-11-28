package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.data.CatRank;
import com.example.clancats.world.CampSite;
import com.example.clancats.world.ModStructures;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
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

/**
 * Every player action that used to live only inside /cat commands. Commands and menus both call these,
 * so the rules (rank checks, range checks, costs) are identical no matter how the action was started.
 */
public final class CatActions {
    private CatActions() {}

    public record Result(boolean ok, Text text) {
        public static Result good(String msg, Formatting color) {
            return new Result(true, Text.literal(msg).formatted(color));
        }

        public static Result bad(String msg) {
            return new Result(false, Text.literal(msg));
        }
    }

    /** The player's clan camp, or the reason they can't use one right now. */
    public record Own(CampClaim claim, Result error) {}

    // ---- helpers ----
    public static void send(ServerPlayerEntity p, Result r) {
        p.sendMessage(r.ok() ? r.text() : r.text().copy().formatted(Formatting.RED), false);
    }

    public static String clanName(String id) {
        return CatClan.byId(id).map(cl -> cl.display).orElse("another clan");
    }

    public static void tellClan(MinecraftServer server, String clanId, String msg) {
        Formatting color = CatClan.byId(clanId).map(cl -> cl.color).orElse(Formatting.WHITE);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (CatAbilities.data(p).clan().equals(clanId)) {
                p.sendMessage(Text.literal(msg).formatted(color), false);
            }
        }
    }

    /** Warrior or higher (or an operator): may claim camps and spend their stores. */
    public static boolean mayCommand(ServerPlayerEntity p, CatData d) {
        return p.hasPermissionLevel(2) || d.rank().ordinal() >= CatRank.WARRIOR.ordinal();
    }

    /** Operator, or the owning clan's Leader or Deputy. */
    public static boolean mayRelease(ServerPlayerEntity p, CatData d, CampClaim claim) {
        boolean leadership = d.rank() == CatRank.LEADER || d.rank() == CatRank.DEPUTY;
        return p.hasPermissionLevel(2) || (leadership && claim.clan().equals(d.clan()));
    }

    // ---- cat form ----
    public static Result become(ServerPlayerEntity p, boolean cat) {
        CatData d = CatAbilities.data(p);
        if (d.cat() == cat) return Result.bad(cat ? "You are already a cat." : "You are not a cat.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withCat(cat));
        CatAbilities.apply(p);
        if (cat && d.clanOpt().isEmpty()) {
            // Fresh cat with no clan: offer the clan choice (or staying a loner).
            MenuNetworking.sendMenu(p, "clans", null, false, false);
        }
        return Result.good(cat
                ? "Your senses sharpen and the world grows huge. You are a cat. Press J for the clan menu."
                : "Your paws become hands once more.", Formatting.GOLD);
    }

    public static Result sound(ServerPlayerEntity p, String kind) {
        if (!CatAbilities.data(p).cat()) return Result.bad("Only cats can do that.");
        SoundEvent ev;
        String msg;
        switch (kind) {
            case "purr" -> { ev = SoundEvents.ENTITY_CAT_PURR; msg = "Purrrr..."; }
            case "hiss" -> { ev = SoundEvents.ENTITY_CAT_HISS; msg = "Hisss!"; }
            default -> { ev = SoundEvents.ENTITY_CAT_AMBIENT; msg = "Meow!"; }
        }
        p.getServerWorld().playSound(null, p.getBlockPos(), ev, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return Result.good(msg, Formatting.GRAY);
    }

    // ---- clans ----
    public static Result joinClan(ServerPlayerEntity p, String id) {
        CatData d = CatAbilities.data(p);
        if (!d.cat()) return Result.bad("Become a cat first.");
        CatClan clan = CatClan.byId(id).orElse(null);
        if (clan == null) return Result.bad("Unknown clan: " + id);
        p.setAttached(ClanCatsMod.CAT_DATA, d.withClan(clan.id));
        CatAbilities.apply(p);
        p.getServer().getPlayerManager().broadcast(
                Text.literal(p.getName().getString() + " joined " + clan.display + ".").formatted(clan.color), false);
        return Result.good("You joined " + clan.display + ".", clan.color);
    }

    public static Result leaveClan(ServerPlayerEntity p) {
        CatData d = CatAbilities.data(p);
        if (d.clanOpt().isEmpty()) return Result.bad("You are already a loner.");
        p.setAttached(ClanCatsMod.CAT_DATA, d.withClan(""));
        CatAbilities.apply(p);
        return Result.good("You leave your clan and walk alone.", Formatting.GRAY);
    }

    public static Result camp(ServerPlayerEntity p) {
        CatClan clan = CatAbilities.data(p).clanOpt().orElse(null);
        if (clan == null) return Result.bad("Loners have no camp. Join a clan first.");
        Optional<CampClaim> camp = CampClaims.nearest(p.getServer(), clan.id, p.getPos());
        if (camp.isEmpty()) return Result.bad(clan.display + " has no camp yet. Find a wild camp and claim it.");
        BlockPos pos = camp.get().center();
        int dist = (int) Math.sqrt(p.squaredDistanceTo(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5));
        return Result.good("Nearest " + clan.display + " camp: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " (" + dist + " blocks away, " + camp.get().upgrades().size() + "/" + CampUpgrade.values().length
                + " upgrades).", clan.color);
    }

    public static Result findCamp(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return Result.bad("Wild camps are only in the overworld.");
        BlockPos found = world.locateStructure(ModStructures.CAMP_TAG, p.getBlockPos(), 64, false);
        if (found == null) return Result.bad("No wild camp found nearby. Keep exploring.");
        int dist = (int) Math.sqrt(p.squaredDistanceTo(found.getX() + 0.5, p.getY(), found.getZ() + 0.5));
        return Result.good("Nearest wild camp: x " + found.getX() + ", z " + found.getZ() + " (" + dist + " blocks away).",
                Formatting.GOLD);
    }

    public static Result claimCamp(ServerPlayerEntity p) {
        CatData d = CatAbilities.data(p);
        CatClan clan = d.clanOpt().orElse(null);
        if (clan == null) return Result.bad("Join a clan first.");
        if (!mayCommand(p, d)) return Result.bad("Only a Warrior or higher (or an operator) can claim a camp.");
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return Result.bad("Camps are only in the overworld.");

        CampSite piece = ModStructures.findCamp(world, p.getBlockPos());
        if (piece == null) return Result.bad("You need to be inside a wild camp.");

        MinecraftServer server = p.getServer();
        Optional<CampClaim> existing = CampClaims.at(server, piece.center());
        if (existing.isPresent()) return Result.bad("This camp already belongs to " + clanName(existing.get().clan()) + ".");

        CampClaim claim = new CampClaim(piece.center(), piece.rot(), clan.id, piece.markerY(), piece.kind(), List.of());
        CampClaims.add(server, claim);
        CampClaims.setMarker(world, piece.center(), piece.markerY(), clan.marker.getDefaultState());
        CampManager.refreshBanners(world, claim);
        server.getPlayerManager().broadcast(
                Text.literal(clan.display + " has claimed a camp! Stock its fresh-kill barrel, then right-click it to upgrade the camp.")
                        .formatted(clan.color), false);
        return Result.good("You claimed the camp for " + clan.display + ".", clan.color);
    }

    public static Result unclaimCamp(ServerPlayerEntity p) {
        CatData d = CatAbilities.data(p);
        ServerWorld world = p.getServerWorld();
        CampSite piece = ModStructures.findCamp(world, p.getBlockPos());
        if (piece == null) return Result.bad("You need to be inside the camp you want to give up.");

        MinecraftServer server = p.getServer();
        Optional<CampClaim> claim = CampClaims.at(server, piece.center());
        if (claim.isEmpty()) return Result.bad("This camp isn't claimed.");
        if (!mayRelease(p, d, claim.get())) {
            return Result.bad("Only an operator, or your own clan's Leader or Deputy, can release this camp.");
        }
        CampClaims.remove(server, claim.get());
        CampClaims.setMarker(world, piece.center(), claim.get().markerY(), Blocks.STONE.getDefaultState());
        CampManager.removeBanners(world, claim.get());
        return Result.good("The camp is wild again.", Formatting.GRAY);
    }

    // ---- camp upgrades ----
    /** The player's clan camp nearest to them, if close enough to use it; otherwise the reason why not. */
    public static Own ownCampNear(ServerPlayerEntity p) {
        CatClan clan = CatAbilities.data(p).clanOpt().orElse(null);
        if (clan == null) return new Own(null, Result.bad("Join a clan first."));
        Optional<CampClaim> camp = CampClaims.nearest(p.getServer(), clan.id, p.getPos());
        if (camp.isEmpty()) return new Own(null, Result.bad(clan.display + " has no camp yet. Claim a wild camp first."));
        BlockPos b = camp.get().center();
        if (Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ()) > 48.0) {
            return new Own(null, Result.bad("Go to your camp first (x " + b.getX() + ", z " + b.getZ() + ")."));
        }
        return new Own(camp.get(), null);
    }

    public static Result buyUpgrade(ServerPlayerEntity p, String id) {
        CatData d = CatAbilities.data(p);
        CampUpgrade up = CampUpgrade.byId(id).orElse(null);
        if (up == null) return Result.bad("Unknown upgrade: " + id);
        if (!mayCommand(p, d)) return Result.bad("Only a Warrior or higher (or an operator) can spend the camp's stores.");

        Own own = ownCampNear(p);
        if (own.claim() == null) return own.error();
        CampClaim claim = own.claim();
        if (claim.has(up)) return Result.bad("Your camp already has " + up.title + ".");
        for (String req : up.requires) {
            if (!claim.upgrades().contains(req)) {
                String need = CampUpgrade.byId(req).map(u -> u.title).orElse(req);
                return Result.bad(up.title + " needs " + need + " first.");
            }
        }

        ServerWorld world = p.getServerWorld();
        Inventory barrel = CampManager.barrel(world, claim);
        if (barrel == null) return Result.bad("Can't reach the fresh-kill barrel. Stand closer to camp.");
        if (!CampManager.canAfford(CampManager.stock(barrel), up)) {
            return Result.bad("Not enough in the fresh-kill barrel. " + up.title + " costs " + up.costString() + ".");
        }

        MinecraftServer server = p.getServer();
        CampManager.pay(barrel, up);
        CampClaim updated = claim.withUpgrade(up);
        CampClaims.replace(server, claim, updated);
        CampManager.refreshBanners(world, updated);

        BlockPos b = claim.center();
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 3.0, b.getZ() + 0.5, 40, 3.0, 1.5, 3.0, 0.1);
        world.playSound(null, b, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        tellClan(server, claim.clan(), clanName(claim.clan()) + " camp upgraded: " + up.title + ". " + up.blurb);
        return Result.good("Upgraded: " + up.title + ".", Formatting.GREEN);
    }

    // ---- admin ----
    public static Result setPrey(ServerPlayerEntity op, String targetName, int amount) {
        if (!op.hasPermissionLevel(2)) return Result.bad("Operators only.");
        ServerPlayerEntity target = op.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) return Result.bad(targetName + " is not online.");
        if (!CatAbilities.data(target).cat()) return Result.bad(targetName + " is not a cat.");
        CatEvents.setPrey(target, Math.max(0, amount), true);
        return Result.good("Set " + targetName + "'s prey to " + Math.max(0, amount) + ".", Formatting.GREEN);
    }

    /** Titles of an upgrade's prerequisites, comma separated (empty if none). */
    public static String needsTitles(CampUpgrade u) {
        return String.join(", ", Arrays.stream(u.requires)
                .map(id -> CampUpgrade.byId(id).map(x -> x.title).orElse(id)).toList());
    }
}
