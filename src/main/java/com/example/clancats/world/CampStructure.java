package com.example.clancats.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/** Decides where a wild camp may generate and how it is oriented. */
public class CampStructure extends Structure {
    public static final MapCodec<CampStructure> CODEC = createCodec(CampStructure::new);

    // Local points sampled to check the terrain is dry and not too steep.
    private static final int[][] SAMPLES = {
            {0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {24, 0}, {38, 0}
    };

    public CampStructure(Structure.Config config) {
        super(config);
    }

    @Override
    protected Optional<Structure.StructurePosition> getStructurePosition(Structure.Context context) {
        ChunkPos chunkPos = context.chunkPos();
        int cx = chunkPos.getCenterX();
        int cz = chunkPos.getCenterZ();
        ChunkRandom rng = context.random();
        int rot = rng.nextInt(4);
        long seed = rng.nextLong();

        ChunkGenerator gen = context.chunkGenerator();
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int[] s : SAMPLES) {
            int wx = cx + CampLayout.worldDX(rot, s[0], s[1]);
            int wz = cz + CampLayout.worldDZ(rot, s[0], s[1]);
            int ground = gen.getHeightOnGround(wx, wz, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
            int surface = gen.getHeightOnGround(wx, wz, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
            if (surface > ground) return Optional.empty(); // water
            minH = Math.min(minH, ground);
            maxH = Math.max(maxH, ground);
        }
        if (minH < gen.getSeaLevel() + 2 || maxH - minH > 14) return Optional.empty();

        int y0 = minH;
        int fy = y0 - CampLayout.FLOOR_TO_SURFACE;
        if (fy < context.world().getBottomY() + 8) return Optional.empty();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int[][] corners = {
                {CampLayout.MIN_LX, CampLayout.MIN_LZ}, {CampLayout.MIN_LX, CampLayout.MAX_LZ},
                {CampLayout.MAX_LX, CampLayout.MIN_LZ}, {CampLayout.MAX_LX, CampLayout.MAX_LZ}
        };
        for (int[] c : corners) {
            int wx = cx + CampLayout.worldDX(rot, c[0], c[1]);
            int wz = cz + CampLayout.worldDZ(rot, c[0], c[1]);
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        BlockBox box = new BlockBox(minX, fy - 2, minZ, maxX, fy + 36, maxZ);
        BlockPos start = new BlockPos(cx, fy, cz);
        final int fRot = rot;
        final long fSeed = seed;
        final int fFy = fy;

        return Optional.of(new Structure.StructurePosition(start,
                collector -> collector.addPiece(new CampPiece(box, fSeed, fRot, cx, fFy, cz))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.CAMP_TYPE;
    }
}
