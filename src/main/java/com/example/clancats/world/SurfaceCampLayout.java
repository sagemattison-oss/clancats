package com.example.clancats.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;

/**
 * Geometry of the above-ground camp, in local coordinates (same conventions as {@link CampLayout}):
 * a flattened clearing with a Great Rock in the middle, a bramble hedge around the edge with a gap
 * on the +lx side, a fresh-kill barrel, and five leaf-and-moss dens built as hollow domes.
 * ly = 0 is the walkable air layer; floor blocks are at ly = -1.
 */
public final class SurfaceCampLayout {
    public static final int HALF = 19;
    public static final int ROCK_TOP = 6;
    /** Air is carved up to this height so hills inside the clearing are removed. */
    public static final int HEIGHT = 12;

    private static final double DEN_DIST = 11.5;

    private enum Kind { LEADER, WARRIORS, APPRENTICES, NURSERY, MEDICINE }

    private record Den(Kind kind, double angleDeg, double radius, int nests) {}

    private static final Den[] DENS = {
            new Den(Kind.LEADER, 180, 3.6, 1),
            new Den(Kind.WARRIORS, 130, 4.4, 4),
            new Den(Kind.APPRENTICES, 230, 3.6, 3),
            new Den(Kind.NURSERY, 70, 3.6, 3),
            new Den(Kind.MEDICINE, 290, 3.2, 0)
    };
    private static final int N = DENS.length;
    private static final int[] RX = new int[N];
    private static final int[] RZ = new int[N];
    private static final double[] COS = new double[N];
    private static final double[] SIN = new double[N];
    private static final int[] LANTERN_X = new int[N];
    private static final int[] LANTERN_Z = new int[N];
    private static final int[][][] NESTS = new int[N][][];

    static {
        for (int i = 0; i < N; i++) {
            Den d = DENS[i];
            double a = Math.toRadians(d.angleDeg());
            COS[i] = Math.cos(a);
            SIN[i] = Math.sin(a);
            RX[i] = (int) Math.round(DEN_DIST * COS[i]);
            RZ[i] = (int) Math.round(DEN_DIST * SIN[i]);
            LANTERN_X[i] = RX[i] + (int) Math.round(COS[i] * (d.radius() - 1.8));
            LANTERN_Z[i] = RZ[i] + (int) Math.round(SIN[i] * (d.radius() - 1.8));
            int n = d.nests();
            NESTS[i] = new int[n][2];
            for (int k = 0; k < n; k++) {
                if (n == 1) {
                    NESTS[i][k][0] = RX[i];
                    NESTS[i][k][1] = RZ[i];
                    continue;
                }
                double ang = Math.toRadians(d.angleDeg() + 180.0) + (k + 0.5) * 2.0 * Math.PI / n;
                double rr = d.radius() - 2.6;
                NESTS[i][k][0] = (int) Math.round(RX[i] + rr * Math.cos(ang));
                NESTS[i][k][1] = (int) Math.round(RZ[i] + rr * Math.sin(ang));
            }
        }
    }

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState LEAF_SPRUCE = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    private static final BlockState LEAF_OAK = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    private static final BlockState LEAF_AZALEA = Blocks.AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    private static final BlockState[] HERBS = {
            Blocks.FERN.getDefaultState(), Blocks.AZURE_BLUET.getDefaultState(),
            Blocks.LILY_OF_THE_VALLEY.getDefaultState(), Blocks.CORNFLOWER.getDefaultState(),
            Blocks.ALLIUM.getDefaultState(), Blocks.DANDELION.getDefaultState()
    };

    private final long seed;
    private final double ph1;

    public SurfaceCampLayout(long seed) {
        this.seed = seed;
        this.ph1 = (hash(seed, 7, 8, 9) % 628) / 100.0;
    }

    private static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) (h & 0x7fffffffL);
    }

    /** 0 = outside the camp, 1 = clearing, 2 = hedge. */
    public int zone(int lx, int lz) {
        double d = Math.sqrt((double) lx * lx + (double) lz * lz);
        double ang = Math.atan2(lz, lx);
        double r = 17.0 + 0.8 * Math.sin(3 * ang + ph1);
        if (d >= r) return 0;
        if (d >= r - 1.8) return (lx > 0 && Math.abs(lz) <= 2) ? 1 : 2; // gap in the hedge = entrance
        return 1;
    }

    /** The block for (lx, ly, lz) with ly in [-1, HEIGHT]; the caller passes the column's zone (not 0). */
    public BlockState blockAt(int zone, int lx, int ly, int lz) {
        if (ly == -1) {
            BlockState s = decorFloor(lx, lz);
            return s != null ? s : floorBlock(lx, lz);
        }

        // The Great Rock.
        if (ly <= ROCK_TOP) {
            double d = Math.sqrt((double) lx * lx + (double) lz * lz);
            if (d <= 5.0) {
                int h = hash(seed, lx, ly, lz);
                double r = 4.4 - 0.36 * ly + ((h & 7) - 3.5) * 0.07;
                if (d <= r) return rockBlock(h >> 3);
            }
        }

        // Dens: hollow domes of leaves and moss, open on the side facing the Great Rock.
        for (int i = 0; i < N; i++) {
            double dd = Math.hypot(lx - RX[i], lz - RZ[i]);
            double rho = Math.sqrt(dd * dd + (double) ly * ly);
            double rad = DENS[i].radius();
            if (rho > rad) continue;
            if (rho > rad - 1.0) {
                double t = lx * COS[i] + lz * SIN[i];
                double p = -lx * SIN[i] + lz * COS[i];
                if (t < DEN_DIST - 1.0 && Math.abs(p) <= 1.2 && ly <= 2) return AIR;
                return shellBlock(hash(seed, lx, ly, lz));
            }
            if (ly == 0) {
                BlockState s = denTop(i, lx, lz);
                return s != null ? s : AIR;
            }
            return AIR;
        }

        // Bramble hedge.
        if (zone == 2) {
            int hh = 2 + (hash(seed, lx, 0, lz) & 1);
            return ly <= hh ? hedgeBlock(hash(seed, lx, ly, lz)) : AIR;
        }

        // Open clearing.
        if (ly == 0) {
            BlockState s = clearingTop(lx, lz);
            return s != null ? s : AIR;
        }
        if (ly == 1 && isPost(lx, lz)) return Blocks.LANTERN.getDefaultState();
        return AIR;
    }

    // ------------------------------------------------------------------ materials & decoration
    private BlockState rockBlock(int h) {
        int v = h % 20;
        if (v < 10) return Blocks.STONE.getDefaultState();
        if (v < 14) return Blocks.ANDESITE.getDefaultState();
        if (v < 17) return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }

    private BlockState shellBlock(int h) {
        int v = h % 10;
        if (v < 5) return LEAF_SPRUCE;
        if (v < 8) return LEAF_OAK;
        return Blocks.MOSS_BLOCK.getDefaultState();
    }

    private BlockState hedgeBlock(int h) {
        int v = h % 10;
        if (v < 6) return LEAF_SPRUCE;
        if (v < 9) return LEAF_OAK;
        return LEAF_AZALEA;
    }

    private BlockState floorBlock(int lx, int lz) {
        if (lx > 0 && Math.abs(lz) <= 1) return Blocks.DIRT_PATH.getDefaultState(); // worn entrance path
        int h = hash(seed, lx, -1, lz) % 10;
        if (h < 3) return Blocks.COARSE_DIRT.getDefaultState();
        if (h < 5) return Blocks.PODZOL.getDefaultState();
        return Blocks.GRASS_BLOCK.getDefaultState();
    }

    private static boolean inNest(int[] n, int lx, int lz) {
        return lx >= n[0] && lx <= n[0] + 1 && lz >= n[1] && lz <= n[1] + 1;
    }

    private BlockState decorFloor(int lx, int lz) {
        for (int i = 0; i < N; i++) {
            for (int[] n : NESTS[i]) {
                if (inNest(n, lx, lz)) return Blocks.HAY_BLOCK.getDefaultState();
            }
            if (DENS[i].kind() == Kind.MEDICINE && Math.hypot(lx - RX[i], lz - RZ[i]) < DENS[i].radius() - 0.8) {
                return Blocks.MOSS_BLOCK.getDefaultState();
            }
        }
        return null;
    }

    private BlockState denTop(int i, int lx, int lz) {
        if (lx == LANTERN_X[i] && lz == LANTERN_Z[i]) return Blocks.LANTERN.getDefaultState();
        for (int[] n : NESTS[i]) {
            if (inNest(n, lx, lz)) return Blocks.MOSS_CARPET.getDefaultState();
        }
        if (DENS[i].kind() == Kind.MEDICINE && Math.hypot(lx - RX[i], lz - RZ[i]) < DENS[i].radius() - 1.2) {
            int h = hash(seed, lx, 0, lz);
            if ((h & 1) == 0) return HERBS[(h >> 1) % HERBS.length];
        }
        return null;
    }

    private static boolean isPost(int lx, int lz) {
        return lx == 13 && (lz == 3 || lz == -3);
    }

    private BlockState clearingTop(int lx, int lz) {
        // Fresh-kill pile (same local spot as the cave camp, so the barrel lookup is shared).
        if (lx == CampLayout.BARREL_LX && lz == CampLayout.BARREL_LZ) return Blocks.BARREL.getDefaultState();
        if (lz == CampLayout.BARREL_LZ && (lx == CampLayout.BARREL_LX - 1 || lx == CampLayout.BARREL_LX + 1)) {
            return Blocks.HAY_BLOCK.getDefaultState();
        }
        if (lx == CampLayout.BARREL_LX && lz == CampLayout.BARREL_LZ - 1) return Blocks.HAY_BLOCK.getDefaultState();
        if (isPost(lx, lz)) return Blocks.COBBLESTONE_WALL.getDefaultState();

        int h = hash(seed, lx, 0, lz);
        BlockState fb = floorBlock(lx, lz);
        if (fb.isOf(Blocks.GRASS_BLOCK) && h % 3 == 0) return Blocks.SHORT_GRASS.getDefaultState();
        if (fb.isOf(Blocks.PODZOL) && h % 6 == 0) return Blocks.FERN.getDefaultState();
        return null;
    }
}
