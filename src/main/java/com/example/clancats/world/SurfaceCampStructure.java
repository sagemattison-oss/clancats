package com.example.clancats.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/** Picks flat-ish, dry ground for an above-ground camp. */
public class SurfaceCampStructure extends Structure {
    public static final MapCodec<SurfaceCampStructure> CODEC = createCodec(SurfaceCampStructure::new);

    private static final int[][] SAMPLES = {
            {0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}, {9, 9}, {-9, 9}, {9, -9}, {-9, -9},
            {17, 0}, {-17, 0}, {0, 17}, {0, -17}
    };

    public SurfaceCampStructure(Structure.Config config) {
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
        if (minH < gen.getSeaLevel() + 1 || maxH - minH > 6) return Optional.empty();

        int y0 = (minH + maxH) / 2;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int h = SurfaceCampLayout.HALF;
        int[][] corners = {{-h, -h}, {-h, h}, {h, -h}, {h, h}};
        for (int[] c : corners) {
            int wx = cx + CampLayout.worldDX(rot, c[0], c[1]);
            int wz = cz + CampLayout.worldDZ(rot, c[0], c[1]);
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        BlockBox box = new BlockBox(minX, y0 - 8, minZ, maxX, y0 + SurfaceCampLayout.HEIGHT, maxZ);
        BlockPos start = new BlockPos(cx, y0, cz);
        final int fRot = rot;
        final long fSeed = seed;
        final int fY0 = y0;

        return Optional.of(new Structure.StructurePosition(start,
                collector -> collector.addPiece(new SurfaceCampPiece(box, fSeed, fRot, cx, fY0, cz))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.SURFACE_CAMP_TYPE;
    }
}
