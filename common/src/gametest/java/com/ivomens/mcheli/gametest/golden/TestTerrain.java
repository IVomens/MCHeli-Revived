package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.gametest.snapshot.ForcedArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The course every golden script runs on, built in code at a fixed place in the world so that
 * absolute coordinates (and their rounding) are the same in every run.
 * <p>
 * Seen from above, with +z pointing away from the origin (yaw 0 faces +z):
 * <pre>
 *  x:   0        24     40      56                  104
 *       | runway | track | wall  |   pool            |
 *       |        | step  |       |   (beach at +z)   |
 *       |        | ramp  |       |                   |
 *       |        | ledge |       |                   |
 *  z: 320       192      64      72
 * </pre>
 * The floor is one layer of stone at {@code origin.y}; vehicles stand on {@code origin.y + 1}. The
 * course floats high above the superflat ground with open air above it.
 */
public final class TestTerrain {

    /** The usual course, at positive coordinates. */
    public static final BlockPos POSITIVE = new BlockPos(1024, 100, 1024);
    /** The same course at negative coordinates, where truncating and flooring disagree. */
    public static final BlockPos NEGATIVE = new BlockPos(-2048, 100, -2048);

    /** Places on the course, relative to the origin, at standing height. */
    public static final Vec3 RUNWAY_START = new Vec3(12.0, 1.0, 8.0);
    public static final Vec3 RUNWAY_MIDDLE = new Vec3(12.0, 1.0, 40.0);
    public static final Vec3 TRACK_START = new Vec3(32.0, 1.0, 8.0);
    public static final Vec3 RAMP_FOOT = new Vec3(32.0, 1.0, 52.0);
    public static final Vec3 PLATEAU = new Vec3(32.0, 5.0, 80.0);
    public static final Vec3 WALL_APPROACH = new Vec3(48.0, 1.0, 24.0);
    public static final Vec3 POOL_CENTRE = new Vec3(80.0, 1.0, 20.0);

    private static final int RUNWAY_END_X = 24;
    private static final int RUNWAY_LENGTH = 320;
    private static final int TRACK_END_X = 40;
    private static final int TRACK_LENGTH = 192;
    private static final int WALL_LANE_END_X = 56;
    private static final int WALL_LANE_LENGTH = 64;
    private static final int POOL_END_X = 104;
    private static final int POOL_LENGTH = 72;

    private static final int STEP_START = 32;
    private static final int STEP_END = 48;
    private static final int RAMP_START = 64;
    private static final int PLATEAU_START = 72;
    private static final int PLATEAU_HEIGHT = 4;
    private static final int LEDGE = 96;
    private static final int WALL_Z = 40;
    private static final int WALL_HEIGHT = 6;
    private static final int POOL_DEPTH = 4;
    private static final int BEACH_START = 52;

    /** The box rebuilt before every run: the course, a margin, and the air just above it. */
    private static final int MARGIN = 8;
    private static final int BELOW = POOL_DEPTH + 2;
    private static final int ABOVE = 16;
    /** Chunks kept loaded around the course, so flights do not leave the ticking area. */
    private static final int LOADED_BEFORE = -128;
    private static final int LOADED_X_AFTER = 256;
    private static final int LOADED_Z_AFTER = 768;

    private final ServerLevel level;
    private final BlockPos origin;
    private final ForcedArea loaded;

    public TestTerrain(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        this.loaded = new ForcedArea(level, new ChunkPos(origin.offset(LOADED_BEFORE, 0, LOADED_BEFORE)),
                new ChunkPos(origin.offset(LOADED_X_AFTER, 0, LOADED_Z_AFTER)), origin.getY());
    }

    public BlockPos origin() {
        return this.origin;
    }

    public Vec3 absolute(Vec3 relative) {
        return relative.add(this.origin.getX(), this.origin.getY(), this.origin.getZ());
    }

    /** Keeps every chunk a script can reach loaded and ticking entities. */
    public void forceChunks(boolean forced) {
        this.loaded.force(forced);
    }

    /** Holds the server thread until the forced chunks tick entities (see {@link ForcedArea}). */
    public void awaitChunks() {
        this.loaded.awaitReady();
    }

    /** Whether the position is inside the loaded area, so a vehicle there still ticks. */
    public boolean isLoaded(Vec3 position) {
        double x = position.x - this.origin.getX();
        double z = position.z - this.origin.getZ();
        return x >= LOADED_BEFORE && x < LOADED_X_AFTER + 16 && z >= LOADED_BEFORE && z < LOADED_Z_AFTER + 16;
    }

    /** Removes every entity but players from the loaded area: wrecks, items, seats of earlier runs. */
    public void clearEntities() {
        for (Entity entity : this.level.getEntities((Entity) null, area(), entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    /** The vehicles on the course. */
    public List<AircraftEntity> aircraft() {
        return this.level.getEntitiesOfClass(AircraftEntity.class, area());
    }

    /** The loaded course, from the bottom of the world to the top. */
    private AABB area() {
        BlockPos from = new BlockPos(this.origin.getX() + LOADED_BEFORE, this.level.getMinBuildHeight(),
                this.origin.getZ() + LOADED_BEFORE);
        BlockPos to = new BlockPos(this.origin.getX() + LOADED_X_AFTER + 16, this.level.getMaxBuildHeight(),
                this.origin.getZ() + LOADED_Z_AFTER + 16);
        return new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to));
    }

    /**
     * Puts every block of the course back the way it should be. Only differing blocks are set, so
     * a rebuild after an undisturbed run is quick. Neighbours are not updated, so water stays put.
     */
    public void build() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -MARGIN; x < POOL_END_X + MARGIN; x++) {
            for (int z = -MARGIN; z < RUNWAY_LENGTH + MARGIN; z++) {
                for (int y = -BELOW; y <= ABOVE; y++) {
                    pos.set(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
                    BlockState wanted = blockAt(x, y, z);
                    if (this.level.getBlockState(pos) != wanted) {
                        this.level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /** The block the course has at a position relative to the origin. */
    private static BlockState blockAt(int x, int y, int z) {
        if (x < 0 || z < 0) {
            return air();
        }
        if (x < RUNWAY_END_X) {
            return z < RUNWAY_LENGTH ? solidUpTo(y, 0) : air();
        }
        if (x < TRACK_END_X) {
            return z < TRACK_LENGTH ? solidUpTo(y, trackHeight(z)) : air();
        }
        if (x < WALL_LANE_END_X) {
            if (z >= WALL_LANE_LENGTH) {
                return air();
            }
            return solidUpTo(y, z >= WALL_Z && z < WALL_Z + 2 ? WALL_HEIGHT : 0);
        }
        if (x < POOL_END_X && z < POOL_LENGTH) {
            return poolBlock(x, y, z);
        }
        return air();
    }

    /** Flat, a one-block step, flat, a ramp up to a plateau, then a drop back to the floor. */
    private static int trackHeight(int z) {
        if (z >= STEP_START && z < STEP_END) {
            return 1;
        }
        if (z >= RAMP_START && z < PLATEAU_START) {
            return 1 + (z - RAMP_START) / 2;
        }
        if (z >= PLATEAU_START && z < LEDGE) {
            return PLATEAU_HEIGHT;
        }
        return 0;
    }

    /** A stone basin filled to floor level; its far end rises into a beach. */
    private static BlockState poolBlock(int x, int y, int z) {
        boolean rim = x == WALL_LANE_END_X || x == POOL_END_X - 1 || z == 0 || z == POOL_LENGTH - 1;
        if (y < -POOL_DEPTH || y > 0) {
            return air();
        }
        if (rim) {
            return Blocks.STONE.defaultBlockState();
        }
        int bottom = z < BEACH_START ? -POOL_DEPTH : Math.min(0, -POOL_DEPTH + 1 + (z - BEACH_START) / 4);
        return y <= bottom ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState();
    }

    private static BlockState solidUpTo(int y, int height) {
        return y >= 0 && y <= height ? Blocks.STONE.defaultBlockState() : air();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    /** A short description for the file header. */
    public String describe() {
        return "{\"origin\":[" + this.origin.getX() + "," + this.origin.getY() + "," + this.origin.getZ() + "],"
                + "\"layout\":\"runway 24x320, track step/ramp/plateau/ledge, wall, pool 48x72 with beach\"}";
    }
}
