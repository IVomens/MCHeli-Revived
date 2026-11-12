package com.ivomens.mcheli.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Probes of the blocks around a position. Unloaded chunks read as air: a probe never loads terrain. */
public final class Terrain {

    /** The block itself and its six neighbours, checked by {@link #isNearWater}. */
    private static final int[][] WATER_CHECK_OFFSETS = new int[][] { { 0, -1, 0 }, { 0, 0, 0 }, { 0, 0, -1 },
            { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, 1, 0 } };
    /** The tolerance vanilla's block collision search rounds its box with. */
    private static final double COLLISION_EPSILON = 1.0E-7;

    private Terrain() {
    }

    /** Whether the block or one of its neighbours is water. Unloaded neighbours count as dry. */
    public static boolean isNearWater(Level level, int x, int y, int z) {
        if (y <= level.getMinBuildHeight()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] offset : WATER_CHECK_OFFSETS) {
            pos.set(x + offset[0], y + offset[1], z + offset[2]);
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNearWater(Level level, BlockPos pos) {
        return isNearWater(level, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Whether {@link #firstBlockY(Entity, int, int, boolean)} finds a block, counting liquids only as
     * source blocks.
     */
    public static boolean hasBlockY(Entity entity, int size, int lenY) {
        return firstBlockY(entity, size, lenY, true) != Blocks.AIR;
    }

    /**
     * Whether vanilla's block collision of {@code box} ({@code level.getBlockCollisions}) can find nothing
     * because every block it would look at is air: the chunk sections around the box, including the
     * one-block margin the collision search adds, hold only air or lie in chunks that are not loaded.
     * False whenever that cannot be told cheaply, which only means the search runs.
     */
    public static boolean isOnlyAirAround(Level level, AABB box) {
        if (level.isDebug()) {
            return false;
        }
        int minX = SectionPos.blockToSectionCoord(Mth.floor(box.minX - COLLISION_EPSILON) - 1);
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX + COLLISION_EPSILON) + 1);
        int minY = SectionPos.blockToSectionCoord(Mth.floor(box.minY - COLLISION_EPSILON) - 1);
        int maxY = SectionPos.blockToSectionCoord(Mth.floor(box.maxY + COLLISION_EPSILON) + 1);
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ - COLLISION_EPSILON) - 1);
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ + COLLISION_EPSILON) + 1);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // the collision search reads its blocks from what this hands it, and skips missing chunks
                BlockGetter chunk = level.getChunkForCollisions(x, z);
                if (chunk == null) {
                    continue;
                }
                if (!(chunk instanceof LevelChunk levelChunk) || !hasOnlyAir(levelChunk, minY, maxY)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Sections outside the chunk's height read as air. */
    private static boolean hasOnlyAir(LevelChunk chunk, int minSectionY, int maxSectionY) {
        for (int y = minSectionY; y <= maxSectionY; y++) {
            int index = chunk.getSectionIndexFromSectionY(y);
            if (index >= 0 && index < chunk.getSectionsCount() && !chunk.getSection(index).hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    public static Block firstBlockY(Entity entity, int size, int lenY, boolean collidableOnly) {
        return firstBlockY(entity.level(), entity.getX(), entity.getY(), entity.getZ(), size, lenY, collidableOnly);
    }

    /**
     * The first non-air block found scanning {@code lenY} blocks up (positive) or down (negative) from
     * the position, within a {@code size} x {@code size} column; air when there is none. With
     * {@code collidableOnly}, liquids only count as source blocks. The position is rounded half up
     * to a block (McHeli's {@code (int) (x + 0.5)}), with a floor so it rounds the same way at
     * negative coordinates.
     */
    public static Block firstBlockY(Level level, double posX, double posY, double posZ, int size, int lenY,
                                    boolean collidableOnly) {
        if (lenY != 0) {
            int px = Mth.floor(posX + 0.5);
            int py = Mth.floor(posY + 0.5);
            int pz = Mth.floor(posZ + 0.5);
            int cntY = lenY > 0 ? lenY : -lenY;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            // the scan goes over the same few columns layer by layer; each chunk is looked up once
            ChunkCursor chunks = new ChunkCursor(level);

            for (int y = 0; y < cntY; y++) {
                int by = py + (lenY > 0 ? y : -y);
                if (by < level.getMinBuildHeight() || by >= level.getMaxBuildHeight()) {
                    return Blocks.AIR;
                }

                for (int x = -size / 2; x <= size / 2; x++) {
                    for (int z = -size / 2; z <= size / 2; z++) {
                        pos.set(px + x, by, pz + z);
                        LevelChunk chunk = chunks.at(pos.getX(), pos.getZ());
                        if (chunk == null) {
                            continue;
                        }
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (!collidableOnly || state.getFluidState().isEmpty() || state.getFluidState().isSource()) {
                            return state.getBlock();
                        }
                    }
                }
            }
        }
        return Blocks.AIR;
    }

    /**
     * The loaded chunk of a block column, remembering the last one asked for. Answers what
     * {@code level.isLoaded(pos)} and {@code level.getBlockState(pos)} would for a position inside the
     * build height: no chunk where the level has none, else the chunk the level reads the block from.
     */
    private static final class ChunkCursor {

        private final Level level;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        @Nullable
        private LevelChunk chunk;

        ChunkCursor(Level level) {
            this.level = level;
        }

        @Nullable
        LevelChunk at(int blockX, int blockZ) {
            int x = SectionPos.blockToSectionCoord(blockX);
            int z = SectionPos.blockToSectionCoord(blockZ);
            if (x != this.chunkX || z != this.chunkZ) {
                this.chunkX = x;
                this.chunkZ = z;
                this.chunk = this.level.getChunkSource().hasChunk(x, z) ? this.level.getChunk(x, z) : null;
            }
            return this.chunk;
        }
    }
}
