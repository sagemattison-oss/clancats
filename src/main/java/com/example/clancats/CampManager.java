package com.example.clancats;

import com.example.clancats.data.CampClaim;
import com.example.clancats.data.CatClan;
import com.example.clancats.data.CatData;
import com.example.clancats.entity.ModEntities;
import com.example.clancats.entity.WildCatEntity;
import com.example.clancats.world.CampLayout;
import com.example.clancats.world.CampSpots;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Camp upgrades: paying for them, their effects, kits, and the clan banners. */
public final class CampManager {
    private CampManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CampManager::tick);
    }

    // ------------------------------------------------------------------ the stockpile
    /** The camp's fresh-kill barrel, or null if its chunk isn't loaded. */
    public static Inventory barrel(ServerWorld world, CampClaim claim) {
        BlockPos bp = CampLayout.barrelPos(claim.center(), claim.rot());
        if (!world.isChunkLoaded(bp)) return null;
        BlockEntity be = world.getBlockEntity(bp);
        return be instanceof Inventory inv ? inv : null;
    }

    public static Map<CampResource, Integer> stock(Inventory inv) {
        Map<CampResource, Integer> m = new EnumMap<>(CampResource.class);
        for (CampResource r : CampResource.values()) m.put(r, 0);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            for (CampResource r : CampResource.values()) {
                int w = r.worth(s);
                if (w > 0) m.merge(r, w * s.getCount(), Integer::sum);
            }
        }
        return m;
    }

    public static boolean canAfford(Map<CampResource, Integer> stock, CampUpgrade u) {
        for (CampUpgrade.Cost c : u.costs) {
            if (stock.getOrDefault(c.resource(), 0) < c.amount()) return false;
        }
        return true;
    }

    public static void pay(Inventory inv, CampUpgrade u) {
        for (CampUpgrade.Cost c : u.costs) {
            int remaining = c.amount();
            for (int i = 0; i < inv.size() && remaining > 0; i++) {
                ItemStack s = inv.getStack(i);
                int w = c.resource().worth(s);
                if (w <= 0) continue;
                int need = (remaining + w - 1) / w;
                int take = Math.min(need, s.getCount());
                s.decrement(take);
                remaining -= take * w;
            }
        }
        inv.markDirty();
    }

    // ------------------------------------------------------------------ banners
    /** Places the clan's banners: two by the entrance, plus four around the rock once the camp has 3+ upgrades. */
    public static void refreshBanners(ServerWorld world, CampClaim claim) {
        CatClan clan = CatClan.byId(claim.clan()).orElse(null);
        if (clan == null) return;
        BlockState banner = clan.banner.getDefaultState()
                .with(BannerBlock.ROTATION, CampSpots.bannerRotation(claim.rot()));

        for (BlockPos p : CampSpots.baseBanners(claim.center(), claim.rot(), claim.kind())) place(world, p, banner);
        boolean grown = claim.upgrades().size() >= 3;
        for (BlockPos p : CampSpots.ringBanners(claim.center(), claim.rot())) {
            if (grown) place(world, p, banner);
            else removeIfBanner(world, p);
        }
    }

    public static void removeBanners(ServerWorld world, CampClaim claim) {
        for (BlockPos p : CampSpots.baseBanners(claim.center(), claim.rot(), claim.kind())) removeIfBanner(world, p);
        for (BlockPos p : CampSpots.ringBanners(claim.center(), claim.rot())) removeIfBanner(world, p);
    }

    private static void place(ServerWorld world, BlockPos p, BlockState state) {
        if (world.isChunkLoaded(p)) world.setBlockState(p, state);
    }

    private static void removeIfBanner(ServerWorld world, BlockPos p) {
        if (world.isChunkLoaded(p) && world.getBlockState(p).getBlock() instanceof BannerBlock) {
            world.setBlockState(p, Blocks.AIR.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ effects & kits
    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (t % 40 != 0) return;
        List<CampClaim> claims = CampClaims.all(server);
        if (claims.isEmpty()) return;
        ServerWorld world = server.getOverworld();

        for (ServerPlayerEntity p : world.getPlayers()) {
            CatData d = CatAbilities.data(p);
            if (!d.cat() || d.clan().isEmpty()) continue;
            for (CampClaim c : claims) {
                if (!c.clan().equals(d.clan()) || c.upgrades().isEmpty()) continue;
                double dx = p.getX() - (c.center().getX() + 0.5);
                double dz = p.getZ() - (c.center().getZ() + 0.5);
                if (dx * dx + dz * dz > 900.0 || Math.abs(p.getY() - c.center().getY()) > 40.0) continue;

                if (c.has(CampUpgrade.BEDDING) && p.getHealth() < p.getMaxHealth()) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
                }
                if (c.has(CampUpgrade.GATHERING_ROCK)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 0, true, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, false, true));
                }
            }
        }

        if (t % 200 == 0) {
            for (CampClaim c : claims) {
                if (c.has(CampUpgrade.NURSERY)) maybeSpawnKit(server, world, c, t);
            }
        }
    }

    private static void maybeSpawnKit(MinecraftServer server, ServerWorld world, CampClaim c, int t) {
        int interval = c.has(CampUpgrade.GATHERING_ROCK) ? 3000 : 6000;
        int slots = interval / 200;
        if ((t / 200) % slots != Math.floorMod(c.center().hashCode(), slots)) return;

        BlockPos np = CampSpots.nursery(c.center(), c.rot(), c.kind());
        if (!world.isChunkLoaded(np)) return;
        int cap = c.has(CampUpgrade.GATHERING_ROCK) ? 5 : 3;
        List<WildCatEntity> kits = world.getEntitiesByClass(WildCatEntity.class, new Box(np).expand(32.0),
                e -> e.isRecruited() && e.isKit() && e.getClanId().equals(c.clan()));
        if (kits.size() >= cap) return;

        WildCatEntity kit = new WildCatEntity(ModEntities.WILD_CAT, world);
        kit.refreshPositionAndAngles(np.getX() + 0.5, np.getY(), np.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
        kit.initialize(world, world.getLocalDifficulty(np), SpawnReason.BREEDING, null);
        kit.becomeKit(c.clan(), np);
        world.spawnEntity(kit);

        CatClan clan = CatClan.byId(c.clan()).orElse(null);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (CatAbilities.data(p).clan().equals(c.clan())) {
                p.sendMessage(Text.literal("A kit was born in the " + (clan != null ? clan.display : "clan") + " nursery!")
                        .formatted(clan != null ? clan.color : net.minecraft.util.Formatting.WHITE), false);
            }
        }
    }
}
