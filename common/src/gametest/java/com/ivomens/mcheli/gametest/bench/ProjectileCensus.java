package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The projectiles of a tier while it is measured: how many are alive after each tick and how many of those
 * sit in chunks that do not tick entities (rounds that flew out of the simulated area and wait there,
 * frozen, as vanilla's arrows do), how many are fired (or split off) per tick, and how many ticks those
 * that ended in the window flew. Projectiles still in the air when the window closes are left out of the
 * lifetime, so it leans a little short.
 */
final class ProjectileCensus {

    private final ServerLevel level;
    private final List<ProjectileEntity> flying = new ArrayList<>();
    private long aliveSum;
    private long frozenSum;
    private int ticks;
    private int spawned;
    private long lifetimeSum;
    private int ended;

    ProjectileCensus(ServerLevel level) {
        this.level = level;
    }

    void joined(Entity entity) {
        if (entity instanceof ProjectileEntity projectile) {
            this.spawned++;
            this.flying.add(projectile);
        }
    }

    /** After a measured tick: counts what is alive and notes the projectiles that ended. */
    void afterTick() {
        for (Entity entity : this.level.getAllEntities()) {
            if (entity instanceof ProjectileEntity && !entity.isRemoved()) {
                this.aliveSum++;
                if (!this.level.isPositionEntityTicking(entity.blockPosition())) {
                    this.frozenSum++;
                }
            }
        }
        this.ticks++;
        for (Iterator<ProjectileEntity> it = this.flying.iterator(); it.hasNext(); ) {
            ProjectileEntity projectile = it.next();
            if (projectile.isRemoved()) {
                this.lifetimeSum += projectile.tickCount;
                this.ended++;
                it.remove();
            }
        }
    }

    /**
     * The projectiles of the level right now, for the log line written every {@value TierRun#CENSUS_INTERVAL}
     * ticks of a tier's warm-up: alive, frozen, and how many a save would write to the region files.
     */
    static String snapshot(ServerLevel level) {
        int alive = 0;
        int frozen = 0;
        int saved = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ProjectileEntity && !entity.isRemoved()) {
                alive++;
                if (!level.isPositionEntityTicking(entity.blockPosition())) {
                    frozen++;
                }
                if (entity.save(new CompoundTag())) {
                    saved++;
                }
            }
        }
        return "projectiles alive " + alive + ", frozen " + frozen + ", written by a save " + saved;
    }

    TierResult.Projectiles result() {
        int n = Math.max(1, this.ticks);
        return new TierResult.Projectiles(round(this.aliveSum / (double) n), round(this.frozenSum / (double) n),
                round(this.spawned / (double) n),
                round(this.ended == 0 ? 0 : this.lifetimeSum / (double) this.ended));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
