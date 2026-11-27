package com.example.clancats.world;

import com.example.clancats.entity.ModEntities;
import com.example.clancats.entity.WildCatEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/** The above-ground camp: levels a patch of land, then builds the clearing, hedge and dens on it. */
public class SurfaceCampPiece extends StructurePiece implements CampSite {
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();

    private final long seed;
    private final int rot;
    private final int cx;
    private final int y0;
    private final int cz;
    private final SurfaceCampLayout layout;

    public SurfaceCampPiece(BlockBox box, long seed, int rot, int cx, int y0, int cz) {
        super(ModStructures.SURFACE_CAMP_PIECE, 0, box);
        this.seed = seed;
        this.rot = rot;
        this.cx = cx;
        this.y0 = y0;
        this.cz = cz;
        this.layout = new SurfaceCampLayout(seed);
    }

    public SurfaceCampPiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.SURFACE_CAMP_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.rot = nbt.getInt("Rot");
        this.cx = nbt.getInt("Cx");
        this.y0 = nbt.getInt("Y0");
        this.cz = nbt.getInt("Cz");
        this.layout = new SurfaceCampLayout(seed);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putInt("Rot", rot);
        nbt.putInt("Cx", cx);
        nbt.putInt("Y0", y0);
        nbt.putInt("Cz", cz);
    }

    @Override
    public BlockPos center() {
        return new BlockPos(cx, y0, cz);
    }

    @Override
    public int rot() {
        return rot;
    }

    @Override
    public int markerY() {
        return SurfaceCampLayout.ROCK_TOP;
    }

    @Override
    public int kind() {
        return CampSpots.SURFACE;
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator,
                         Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockBox box = this.boundingBox;
        int minX = Math.max(box.getMinX(), chunkBox.getMinX());
        int maxX = Math.min(box.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(box.getMinZ(), chunkBox.getMinZ());
        int maxZ = Math.min(box.getMaxZ(), chunkBox.getMaxZ());
        int minY = Math.max(box.getMinY(), chunkBox.getMinY());
        int maxY = Math.min(box.getMaxY(), chunkBox.getMaxY());

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - cx;
                int dz = z - cz;
                int lx = CampLayout.localX(rot, dx, dz);
                int lz = CampLayout.localZ(rot, dx, dz);
                int zone = layout.zone(lx, lz);
                if (zone == 0) continue;

                // Natural ground height here (first free block above the terrain), read before we change anything.
                int gy = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);

                // Low ground gets filled up to the camp's floor level.
                for (int y = Math.max(minY, gy); y <= Math.min(maxY, y0 - 2); y++) {
                    world.setBlockState(pos.set(x, y, z), DIRT, Block.NOTIFY_LISTENERS);
                }
                // Floor, then everything above it (air carves away hills inside the clearing).
                for (int y = Math.max(minY, y0 - 1); y <= maxY; y++) {
                    BlockState state = layout.blockAt(zone, lx, y - y0, lz);
                    if (state != null) {
                        world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // A few wild cats loiter in an unclaimed camp.
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(40 + i * 120);
            int lx = (int) Math.round(7 * Math.cos(a));
            int lz = (int) Math.round(7 * Math.sin(a));
            int wx = cx + CampLayout.worldDX(rot, lx, lz);
            int wz = cz + CampLayout.worldDZ(rot, lx, lz);
            BlockPos at = new BlockPos(wx, y0, wz);
            if (!chunkBox.contains(at)) continue;

            WildCatEntity cat = new WildCatEntity(ModEntities.WILD_CAT, world.toServerWorld());
            cat.refreshPositionAndAngles(wx + 0.5, y0, wz + 0.5, random.nextFloat() * 360.0f, 0.0f);
            cat.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
            world.spawnEntityAndPassengers(cat);
        }
    }
}
