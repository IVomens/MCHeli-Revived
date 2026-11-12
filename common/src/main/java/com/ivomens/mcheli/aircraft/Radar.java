package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.math.Vec2d;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple client-side entity radar (top-down XZ scope). The scan only runs when the radar sweep
 * completes a revolution, not every tick.
 */
public class Radar {

    /** Mobs under this many solid blocks are hidden from the radar. */
    private static final int MAX_BLOCKS_ABOVE = 5;
    private static final int SCAN_CEILING = 200;

    private final Level level;
    private final ArrayList<Vec2d> entityList = new ArrayList<>();
    private final ArrayList<Vec2d> enemyList = new ArrayList<>();

    public Radar(Level level) {
        this.level = level;
    }

    public ArrayList<Vec2d> getEntityList() {
        return this.entityList;
    }

    public ArrayList<Vec2d> getEnemyList() {
        return this.enemyList;
    }

    public void clear() {
        this.entityList.clear();
        this.enemyList.clear();
    }

    /** Collects the mobs within {@code range} (horizontally) as offsets from the center entity. */
    public void updateXZ(Entity center, int range) {
        if (!this.level.isClientSide) {
            return;
        }
        this.clear();
        List<Mob> mobs = center.level().getEntitiesOfClass(Mob.class, center.getBoundingBox().inflate(range, range, range),
                entity -> entity != center);
        double rangeSqr = (double) range * range;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Mob mob : mobs) {
            double x = mob.getX() - center.getX();
            double z = mob.getZ() - center.getZ();
            if (x * x + z * z >= rangeSqr || isUnderCover(mob, pos)) {
                continue;
            }
            (mob instanceof Enemy ? this.enemyList : this.entityList).add(new Vec2d(x, z));
        }
    }

    private boolean isUnderCover(Mob mob, BlockPos.MutableBlockPos pos) {
        int ceiling = Math.min(SCAN_CEILING, this.level.getMaxBuildHeight());
        int y = 1 + Mth.floor(mob.getY());
        if (y < this.level.getMinBuildHeight()) {
            y = this.level.getMinBuildHeight() + 1;
        }
        int solidBlocks = 0;
        for (; y < ceiling; y++) {
            pos.set(Mth.floor(mob.getX()), y, Mth.floor(mob.getZ()));
            if (!this.level.getBlockState(pos).isAir() && ++solidBlocks >= MAX_BLOCKS_ABOVE) {
                return true;
            }
        }
        return false;
    }
}
