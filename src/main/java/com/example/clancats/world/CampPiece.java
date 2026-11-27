package com.example.clancats.world;

import com.example.clancats.entity.ModEntities;
import com.example.clancats.entity.WildCatEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
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
    public int kind() {
        return CampSpots.CAVE;
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

                // Ladder shafts: a ladder column from the cavern floor up to the surface, carried by a log
                // post that also sticks up out of the ground so the shaft is easy to spot.
                int shaft = CampLayout.shaft(lx, lz);
                if (shaft != 0) {
                    int gy = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
                    if (shaft == 2) {
                        BlockState log = Blocks.OAK_LOG.getDefaultState();
                        for (int y = Math.max(minY, fy); y <= Math.min(maxY, gy + 1); y++) {
                            world.setBlockState(pos.set(x, y, z), log, Block.NOTIFY_LISTENERS);
                        }
                    } else {
                        int fz = CampLayout.shaftFacingZ(lz);
                        int fdx = CampLayout.worldDX(rot, 0, fz);
                        int fdz = CampLayout.worldDZ(rot, 0, fz);
                        Direction face = fdx > 0 ? Direction.EAST : fdx < 0 ? Direction.WEST
                                : fdz > 0 ? Direction.SOUTH : Direction.NORTH;
                        BlockState ladder = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, face);
                        for (int y = Math.max(minY, fy); y <= Math.min(maxY, gy - 1); y++) {
                            world.setBlockState(pos.set(x, y, z), ladder, Block.NOTIFY_LISTENERS);
                        }
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
