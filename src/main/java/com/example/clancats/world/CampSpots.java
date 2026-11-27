package com.example.clancats.world;

import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Fixed places in a camp (banners, nursery), computed from the centre and rotation. */
public final class CampSpots {
    private CampSpots() {}

    public static final int CAVE = 0;
    public static final int SURFACE = 1;

    private static BlockPos at(BlockPos c, int rot, int lx, int lz) {
        return new BlockPos(c.getX() + CampLayout.worldDX(rot, lx, lz), c.getY(), c.getZ() + CampLayout.worldDZ(rot, lx, lz));
    }

    /** Two banners flanking the way in. */
    public static List<BlockPos> baseBanners(BlockPos c, int rot, int kind) {
        int x = kind == SURFACE ? 12 : 10;
        int z = kind == SURFACE ? 5 : 4;
        return List.of(at(c, rot, x, z), at(c, rot, x, -z));
    }

    /** Four more around the Great Rock, shown once the camp has grown. */
    public static List<BlockPos> ringBanners(BlockPos c, int rot) {
        return List.of(at(c, rot, 4, 4), at(c, rot, -4, 4), at(c, rot, 4, -4), at(c, rot, -4, -4));
    }

    /** Floor level at the middle of the nursery den. */
    public static BlockPos nursery(BlockPos c, int rot, int kind) {
        return kind == SURFACE ? at(c, rot, 4, 11) : at(c, rot, 8, 21);
    }

    /** Standing-banner rotation (0-15) so the flag faces out toward the camp entrance. */
    public static int bannerRotation(int rot) {
        int dx = CampLayout.worldDX(rot, 1, 0);
        int dz = CampLayout.worldDZ(rot, 1, 0);
        if (dx > 0) return 12;
        if (dx < 0) return 4;
        if (dz > 0) return 0;
        return 8;
    }
}
