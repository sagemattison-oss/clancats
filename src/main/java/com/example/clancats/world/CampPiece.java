package com.example.clancats.world;

import com.example.clancats.entity.ModEntities;
import com.example.clancats.entity.WildCatEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/** One wild camp: carves the cavern, rock, rooms and entrance, then spawns a few wild cats. */
public class CampPiece extends StructurePiece implements CampSite {
    private final long seed;
    private final int rot;
    private final int cx;
    private final int fy;
    private final int cz;
    private final CampLayout layout;

    public CampPiece(BlockBox box, long seed, int rot, int cx, int fy, int cz) {
        super(ModStructures.CAMP_PIECE, 0, box);
        this.seed = seed;
        this.rot = rot;
        this.cx = cx;
        this.fy = fy;
        this.cz = cz;
        this.layout = new CampLayout(seed);
    }

    public CampPiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.CAMP_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.rot = nbt.getInt("Rot");
        this.cx = nbt.getInt("Cx");
        this.fy = nbt.getInt("Fy");
        this.cz = nbt.getInt("Cz");
        this.layout = new CampLayout(seed);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putInt("Rot", rot);
        nbt.putInt("Cx", cx);
        nbt.putInt("Fy", fy);
        nbt.putInt("Cz", cz);
    }

    /** Cavern floor at the centre (directly under the Great Rock). */
    public BlockPos center() {
        return new BlockPos(cx, fy, cz);
    }

    public int rot() {
        return rot;
    }

    @Override
    public int markerY() {
        return CampLayout.ROCK_TOP;
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
        int[] col = new int[3];

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - cx;
                int dz = z - cz;
                int lx = CampLayout.localX(rot, dx, dz);
                int lz = CampLayout.localZ(rot, dx, dz);
                boolean has = layout.column(lx, lz, col);

                for (int y = minY; y <= maxY; y++) {
                    BlockState state = layout.blockAt(has, col, lx, y - fy, lz);
                    if (state != null) {
                        world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // A few wild cats live in an unclaimed camp.
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(40 + i * 120);
            int lx = (int) Math.round(7 * Math.cos(a));
            int lz = (int) Math.round(7 * Math.sin(a));
            int wx = cx + CampLayout.worldDX(rot, lx, lz);
            int wz = cz + CampLayout.worldDZ(rot, lx, lz);
            BlockPos at = new BlockPos(wx, fy, wz);
            if (!chunkBox.contains(at)) continue;

            WildCatEntity cat = new WildCatEntity(ModEntities.WILD_CAT, world.toServerWorld());
            cat.refreshPositionAndAngles(wx + 0.5, fy, wz + 0.5, random.nextFloat() * 360.0f, 0.0f);
            cat.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
            world.spawnEntityAndPassengers(cat);
        }
    }
}
