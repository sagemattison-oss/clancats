package com.example.clancats.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * Pure geometry of a wild camp, in "local" coordinates:
 *  - lx, lz: horizontal offsets from the camp centre, entrance tunnel along +lx
 *  - ly: height above the cavern floor (floor blocks are at ly = -1, walkable air starts at ly = 0)
 * World coordinates are obtained by rotating (lx, lz) by a multiple of 90 degrees.
 */
public final class CampLayout {
    /** How far the cavern floor sits below the surface at the entrance. */
    public static final int FLOOR_TO_SURFACE = 16;
    public static final int MIN_LX = -31, MAX_LX = 40, MIN_LZ = -31, MAX_LZ = 31;
    public static final int ROCK_TOP = 7;
    /** Fresh-kill pile barrel, local position. */
    public static final int BARREL_LX = 8, BARREL_LZ = -6;

    private static final double ROOM_DIST = 22.0;

    private enum Kind { LEADER, WARRIORS, APPRENTICES, NURSERY, MEDICINE }

    private record Room(Kind kind, double angleDeg, double radius, int nests) {}

    private static final Room[] ROOMS = {
            new Room(Kind.LEADER, 180, 4.5, 1),
            new Room(Kind.WARRIORS, 130, 5.5, 6),
            new Room(Kind.APPRENTICES, 230, 4.5, 4),
            new Room(Kind.NURSERY, 70, 4.5, 3),
            new Room(Kind.MEDICINE, 290, 4.0, 0)
    };
    private static final int N = ROOMS.length;
    private static final int[] RX = new int[N];
    private static final int[] RZ = new int[N];
    private static final double[] COS = new double[N];
    private static final double[] SIN = new double[N];
    private static final int[] LANTERN_X = new int[N];
    private static final int[] LANTERN_Z = new int[N];
    private static final int[][][] NESTS = new int[N][][];

    static {
        for (int i = 0; i < N; i++) {
            Room r = ROOMS[i];
            double a = Math.toRadians(r.angleDeg());
            COS[i] = Math.cos(a);
            SIN[i] = Math.sin(a);
            RX[i] = (int) Math.round(ROOM_DIST * COS[i]);
            RZ[i] = (int) Math.round(ROOM_DIST * SIN[i]);
            LANTERN_X[i] = RX[i] + (int) Math.round(COS[i] * (r.radius() - 1.5));
            LANTERN_Z[i] = RZ[i] + (int) Math.round(SIN[i] * (r.radius() - 1.5));
            int n = r.nests();
            NESTS[i] = new int[n][2];
            for (int k = 0; k < n; k++) {
                if (n == 1) {
                    NESTS[i][k][0] = RX[i];
                    NESTS[i][k][1] = RZ[i];
                    continue;
                }
                double ang = Math.toRadians(r.angleDeg() + 180.0) + (k + 0.5) * 2.0 * Math.PI / n;
                double rr = r.radius() - 2.3;
                NESTS[i][k][0] = (int) Math.round(RX[i] + rr * Math.cos(ang));
                NESTS[i][k][1] = (int) Math.round(RZ[i] + rr * Math.sin(ang));
            }
        }
    }

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState[] HERBS = {
            Blocks.FERN.getDefaultState(), Blocks.AZURE_BLUET.getDefaultState(),
            Blocks.LILY_OF_THE_VALLEY.getDefaultState(), Blocks.CORNFLOWER.getDefaultState(),
            Blocks.ALLIUM.getDefaultState(), Blocks.DANDELION.getDefaultState()
    };

    private final long seed;
    private final double ph1;
    private final double ph2;

    public CampLayout(long seed) {
        this.seed = seed;
        this.ph1 = (hash(seed, 1, 2, 3) % 628) / 100.0;
        this.ph2 = (hash(seed, 4, 5, 6) % 628) / 100.0;
    }

    // ------------------------------------------------------------------ rotation helpers
    public static int localX(int rot, int dx, int dz) {
        return switch (rot) { case 1 -> dz; case 2 -> -dx; case 3 -> -dz; default -> dx; };
    }
    public static int localZ(int rot, int dx, int dz) {
        return switch (rot) { case 1 -> -dx; case 2 -> -dz; case 3 -> dx; default -> dz; };
    }
    public static int worldDX(int rot, int lx, int lz) {
        return switch (rot) { case 1 -> -lz; case 2 -> -lx; case 3 -> lz; default -> lx; };
    }
    public static int worldDZ(int rot, int lx, int lz) {
        return switch (rot) { case 1 -> lx; case 2 -> -lz; case 3 -> -lx; default -> lz; };
    }

    public static BlockPos barrelPos(BlockPos center, int rot) {
        return new BlockPos(center.getX() + worldDX(rot, BARREL_LX, BARREL_LZ), center.getY(),
                center.getZ() + worldDZ(rot, BARREL_LX, BARREL_LZ));
    }

    /** Ladder shafts: local ladder columns. Each has a log post one block further from the centre. */
    private static final int[][] SHAFTS = {{-2, 10}, {-2, -10}};

    /** 1 = ladder column, 2 = log post column, 0 = neither. */
    public static int shaft(int lx, int lz) {
        for (int[] s : SHAFTS) {
            if (lx != s[0]) continue;
            if (lz == s[1]) return 1;
            if (lz == s[1] + (s[1] > 0 ? 1 : -1)) return 2;
        }
        return 0;
    }

    /** The ladder faces toward the cavern centre (the post is behind it). */
    public static int shaftFacingZ(int lz) {
        return lz > 0 ? -1 : 1;
    }

    static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) (h & 0x7fffffffL);
    }

    // ------------------------------------------------------------------ columns
    /**
     * Describes the open space in one (lx, lz) column.
     * out[0] = floor ly, out[1] = ceiling ly (exclusive, 1000 = open sky), out[2] = 1 if inside the main cavern.
     * Returns false if the column is solid rock.
     */
    public boolean column(int lx, int lz, int[] out) {
        boolean any = false;
        int floor = 1000;
        int ceil = -1000;
        int main = 0;

        // Main cavern: a dome with a wobbly outline.
        double d = Math.sqrt((double) lx * lx + (double) lz * lz);
        double ang = Math.atan2(lz, lx);
        double r = 13.0 + 1.2 * Math.sin(3 * ang + ph1) + 0.8 * Math.sin(5 * ang + ph2);
        if (d < r) {
            double t = d / r;
            int h = (int) Math.floor(3.0 + 7.0 * Math.sqrt(Math.max(0.0, 1.0 - t * t)));
            if ((hash(seed, lx, 5, lz) & 3) == 0) h -= 1;
            h = Math.max(h, 3);
            any = true;
            floor = Math.min(floor, 0);
            ceil = Math.max(ceil, h);
            main = 1;
        }

        // Entrance: a ramp rising to the surface, open to the sky for its last stretch.
        if (lx >= 9 && lx <= MAX_LX) {
            int zc = (int) Math.round(1.5 * Math.sin(lx * 0.22 + ph1));
            if (Math.abs(lz - zc) <= 2) {
                int fl = lx <= 12 ? 0 : Math.min(FLOOR_TO_SURFACE, (int) ((lx - 12) * FLOOR_TO_SURFACE / 24.0));
                int ce = lx > 28 ? 1000 : fl + 4;
                any = true;
                floor = Math.min(floor, fl);
                ceil = Math.max(ceil, ce);
            }
        }

        // Side rooms and their connecting tunnels.
        for (int i = 0; i < N; i++) {
            double dd = Math.hypot(lx - RX[i], lz - RZ[i]);
            double rad = ROOMS[i].radius();
            if (dd < rad) {
                double t = dd / rad;
                int ce = 3 + (int) Math.floor(2.0 * Math.sqrt(Math.max(0.0, 1.0 - t * t)));
                any = true;
                floor = Math.min(floor, 0);
                ceil = Math.max(ceil, ce);
            }
            double t = lx * COS[i] + lz * SIN[i];
            double p = -lx * SIN[i] + lz * COS[i];
            if (t >= 8.0 && t <= ROOM_DIST && Math.abs(p) <= 1.5) {
                any = true;
                floor = Math.min(floor, 0);
                ceil = Math.max(ceil, 3);
            }
        }

        if (!any) return false;
        out[0] = floor;
        out[1] = ceil;
        out[2] = main;
        return true;
    }

    /** The block that should be at (lx, ly, lz), or null to leave the existing terrain alone. */
    public BlockState blockAt(boolean hasColumn, int[] col, int lx, int ly, int lz) {
        // The Great Rock in the middle of the cavern.
        if (ly >= 0 && ly <= ROCK_TOP) {
            double d = Math.sqrt((double) lx * lx + (double) lz * lz);
            if (d <= 5.0) {
                int h = hash(seed, lx, ly, lz);
                double r = 4.5 - 0.31 * ly + ((h & 7) - 3.5) * 0.07;
                if (d <= r) return rockBlock(h >> 3);
            }
        }
        if (!hasColumn) return null;

        int floor = col[0];
        int ceil = col[1];
        if (ly == floor - 1) {
            if (floor == 0) {
                BlockState s = decorFloor(lx, lz);
                if (s != null) return s;
            }
            return floorBlock(lx, lz);
        }
        if (ly >= floor && ly < ceil) {
            if (floor == 0 && ly == 0) {
                BlockState s = decorTop(lx, lz);
                if (s != null) return s;
            }
            return AIR;
        }
        if (ly == ceil && col[2] == 1 && (hash(seed, lx, ly, lz) % 26) == 0) {
            return Blocks.SHROOMLIGHT.getDefaultState();
        }
        return null;
    }

    // ------------------------------------------------------------------ materials & decoration
    private BlockState rockBlock(int h) {
        int v = h % 20;
        if (v < 10) return Blocks.STONE.getDefaultState();
        if (v < 14) return Blocks.ANDESITE.getDefaultState();
        if (v < 17) return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }

    private BlockState floorBlock(int lx, int lz) {
        int h = hash(seed, lx, -1, lz) % 10;
        if (h < 6) return Blocks.COARSE_DIRT.getDefaultState();
        if (h < 8) return Blocks.MOSS_BLOCK.getDefaultState();
        return Blocks.PODZOL.getDefaultState();
    }

    private static boolean inNest(int[] n, int lx, int lz) {
        return lx >= n[0] && lx <= n[0] + 1 && lz >= n[1] && lz <= n[1] + 1;
    }

    private BlockState decorFloor(int lx, int lz) {
        for (int i = 0; i < N; i++) {
            for (int[] n : NESTS[i]) {
                if (inNest(n, lx, lz)) return Blocks.HAY_BLOCK.getDefaultState();
            }
            if (ROOMS[i].kind() == Kind.MEDICINE && Math.hypot(lx - RX[i], lz - RZ[i]) < ROOMS[i].radius() - 0.5) {
                return Blocks.MOSS_BLOCK.getDefaultState();
            }
        }
        return null;
    }

    private BlockState decorTop(int lx, int lz) {
        // Fresh-kill pile: a barrel with hay around it.
        if (lx == BARREL_LX && lz == BARREL_LZ) return Blocks.BARREL.getDefaultState();
        if (lz == BARREL_LZ && (lx == BARREL_LX - 1 || lx == BARREL_LX + 1)) return Blocks.HAY_BLOCK.getDefaultState();
        if (lx == BARREL_LX && lz == BARREL_LZ - 1) return Blocks.HAY_BLOCK.getDefaultState();

        for (int i = 0; i < N; i++) {
            if (lx == LANTERN_X[i] && lz == LANTERN_Z[i]) return Blocks.LANTERN.getDefaultState();
            for (int[] n : NESTS[i]) {
                if (inNest(n, lx, lz)) return Blocks.MOSS_CARPET.getDefaultState();
            }
            if (ROOMS[i].kind() == Kind.MEDICINE && Math.hypot(lx - RX[i], lz - RZ[i]) < ROOMS[i].radius() - 1.0) {
                int h = hash(seed, lx, 0, lz);
                if ((h & 1) == 0) return HERBS[(h >> 1) % HERBS.length];
                return null;
            }
        }

        int h = hash(seed, lx, 0, lz);
        BlockState fb = floorBlock(lx, lz);
        if (fb.isOf(Blocks.MOSS_BLOCK) && h % 3 == 0) {
            return (h % 2 == 0 ? Blocks.MOSS_CARPET : Blocks.SHORT_GRASS).getDefaultState();
        }
        if (fb.isOf(Blocks.PODZOL) && h % 6 == 0) return Blocks.FERN.getDefaultState();
        return null;
    }
}
