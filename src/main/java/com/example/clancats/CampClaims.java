package com.example.clancats;

import com.example.clancats.data.CampClaim;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Helpers for the list of claimed camps. */
public final class CampClaims {
    private CampClaims() {}

    public static List<CampClaim> all(MinecraftServer server) {
        return server.getOverworld().getAttachedOrElse(ClanCatsMod.CLAIMS, List.of());
    }

    private static void save(MinecraftServer server, List<CampClaim> list) {
        server.getOverworld().setAttached(ClanCatsMod.CLAIMS, List.copyOf(list));
    }

    public static void add(MinecraftServer server, CampClaim claim) {
        List<CampClaim> list = new ArrayList<>(all(server));
        list.add(claim);
        save(server, list);
    }

    public static void remove(MinecraftServer server, CampClaim claim) {
        List<CampClaim> list = new ArrayList<>(all(server));
        list.remove(claim);
        save(server, list);
    }

    public static Optional<CampClaim> at(MinecraftServer server, BlockPos center) {
        return all(server).stream().filter(c -> c.center().equals(center)).findFirst();
    }

    public static Optional<CampClaim> nearest(MinecraftServer server, String clan, Vec3d from) {
        return all(server).stream()
                .filter(c -> c.clan().equals(clan))
                .min(Comparator.comparingDouble(c ->
                        c.center().getSquaredDistance(from.x, from.y, from.z)));
    }

    /** Fills the 3x3 on top of the Great Rock (clan wool when claimed, stone when released). */
    public static void setMarker(ServerWorld world, BlockPos center, int markerY, BlockState state) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(center.getX() + dx, center.getY() + markerY, center.getZ() + dz), state);
            }
        }
    }
}
