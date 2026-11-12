package com.ivomens.mcheli.gametest.weapons;

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

/**
 * The firing range every weapon snapshot uses, built at fixed coordinates so positions round the
 * same way in every run. The shooter stands at the origin facing +z (yaw 0). The first
 * {@value #LAND_LENGTH} blocks are a stone floor; beyond them a shallow pool catches rounds that
 * fly further, so torpedoes and water explosions are covered too.
 * <pre>
 *  z: -16 ... 0 (shooter) ... 100 | water, 3 deep ... 520
 *  x: -16 ... 16
 * </pre>
 */
final class WeaponRange {

    static final BlockPos ORIGIN = new BlockPos(3072, 100, 3072);
    static final int LAND_LENGTH = 100;
    private static final int HALF_WIDTH = 16;
    private static final int BEHIND = 16;
    private static final int LENGTH = 520;
    private static final int POOL_DEPTH = 3;
    private static final int CLEAR_ABOVE = 24;
    /** Chunks kept loaded past the built area, so projectiles keep ticking a while longer. */
    private static final int LOADED_MARGIN = 32;
    private static final int LOADED_AHEAD = 640;

    private final ServerLevel level;
    private final ForcedArea loaded;

    WeaponRange(ServerLevel level) {
        this.level = level;
        this.loaded = new ForcedArea(level, new ChunkPos(ORIGIN.offset(-LOADED_MARGIN, 0, -LOADED_MARGIN)),
                new ChunkPos(ORIGIN.offset(LOADED_MARGIN, 0, LOADED_AHEAD)), ORIGIN.getY());
    }

    /** A position relative to the origin, in world coordinates. */
    static Vec3 at(double x, double y, double z) {
        return new Vec3(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z);
    }

    void forceChunks(boolean forced) {
        this.loaded.force(forced);
    }

    /** Holds the server thread until the forced chunks tick entities (see {@link ForcedArea}). */
    void awaitChunks() {
        this.loaded.awaitReady();
    }

    /** The whole loaded area, from below the pool to well above the floor. */
    AABB area() {
        return new AABB(Vec3.atLowerCornerOf(ORIGIN.offset(-LOADED_MARGIN, -64, -LOADED_MARGIN)),
                Vec3.atLowerCornerOf(ORIGIN.offset(LOADED_MARGIN + 16, 256, LOADED_AHEAD + 16)));
    }

    /** Removes every entity but players: wrecks, rounds, items and targets of the previous case. */
    void clearEntities() {
        for (Entity entity : this.level.getEntities((Entity) null, area(), entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    /** Puts every block back, setting only those that differ (explosions may have cratered the floor). */
    void build() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = -BEHIND; z <= LENGTH; z++) {
                for (int y = -POOL_DEPTH - 1; y <= CLEAR_ABOVE; y++) {
                    pos.set(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z);
                    BlockState wanted = blockAt(y, z);
                    if (this.level.getBlockState(pos) != wanted) {
                        this.level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static BlockState blockAt(int y, int z) {
        if (z < LAND_LENGTH) {
            return y >= -POOL_DEPTH - 1 && y <= 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (y == -POOL_DEPTH - 1) {
            return Blocks.STONE.defaultBlockState();
        }
        return y <= 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    static String describe() {
        return "{\"origin\":[" + ORIGIN.getX() + "," + ORIGIN.getY() + "," + ORIGIN.getZ() + "],"
                + "\"layout\":\"stone floor " + (2 * HALF_WIDTH + 1) + "x" + LAND_LENGTH + ", then water "
                + POOL_DEPTH + " deep to z=" + LENGTH + "\"}";
    }
}
