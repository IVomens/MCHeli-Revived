package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * How long a projectile lasts outside its flight: in chunks that tick no entities, and across saves.
 * <p>
 * A projectile that flies into a chunk that is loaded but ticks no entities (no player near) stops there,
 * as vanilla's arrows do, and flies on when the chunk ticks again. In a long fight thousands of rounds
 * pile up that way. The server therefore notes the game time of each projectile's last tick, and one
 * that has not ticked for longer than {@code FrozenProjectileLifetime} seconds is removed without
 * exploding: when it would tick next, or by the sweep that looks at the level once a second, whichever
 * comes first. A projectile in flight ticks every game tick, so the rule never touches one that flies.
 * Game time stands still while the server is stopped (and under {@code /tick freeze}), so neither counts.
 * <p>
 * Projectiles are not written to the world's saves: one that is in a chunk when the chunk unloads ends
 * there. Laid mines are the exception ({@link LandMine}): they are saved, and the lifetime never removes
 * them. A projectile that an older version saved is removed as soon as it was loaded.
 */
public final class ProjectileLifetime {

    /** Game ticks between the sweeps of a level. */
    static final int SWEEP_INTERVAL = 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final long NEVER = Long.MIN_VALUE;

    private final ProjectileEntity projectile;
    private final RoleBehaviour role;
    /** Server: the game time of the projectile's last tick, or of the sweep that first met it. */
    private long lastTick = NEVER;
    /** Read from a save without being restored: removed at once. */
    private boolean loadedFromSave;

    ProjectileLifetime(ProjectileEntity projectile, RoleBehaviour role) {
        this.projectile = projectile;
        this.role = role;
    }

    /** Whether the projectile is kept beyond its flight: written to saves, never removed by the lifetime. */
    boolean persists() {
        return this.role.persists();
    }

    boolean isLoadedFromSave() {
        return this.loadedFromSave;
    }

    /**
     * At the start of the projectile's tick: on the server, removes it if it waited longer than the
     * lifetime since its last tick, and notes the tick otherwise.
     *
     * @return true if the projectile is gone
     */
    boolean endsBeforeTick() {
        if (!(this.projectile.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        boolean expired = this.lastTick != NEVER && isExpired(now - this.lastTick) && !this.persists();
        this.lastTick = now;
        if (expired) {
            this.projectile.discard();
        }
        return expired;
    }

    /** Whether the projectile goes in a sweep at {@code now}; starts its clock if it never ticked. */
    private boolean expiresInSweep(long now) {
        if (this.persists()) {
            return false;
        }
        if (this.loadedFromSave) {
            return true;
        }
        if (this.lastTick == NEVER) {
            this.lastTick = now;
            return false;
        }
        return isExpired(now - this.lastTick);
    }

    /** Whether a projectile that has not ticked for this many game ticks is past the lifetime. */
    static boolean isExpired(long ticksSinceLastTick) {
        int seconds = McheliConfig.network().frozenProjectileLifetime();
        return seconds > 0 && ticksSinceLastTick > (long) seconds * TICKS_PER_SECOND;
    }

    /**
     * Server, at the end of every level tick: once every {@value #SWEEP_INTERVAL} ticks, removes the
     * projectiles of the level that are past the lifetime, wherever they are. One in a chunk that ticks no
     * entities would otherwise wait until someone comes near.
     */
    public static void sweep(ServerLevel level) {
        long now = level.getGameTime();
        if (now % SWEEP_INTERVAL != 0) {
            return;
        }
        List<ProjectileEntity> expired = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ProjectileEntity projectile && !projectile.isRemoved()
                    && projectile.lifetime.expiresInSweep(now)) {
                expired.add(projectile);
            }
        }
        expired.forEach(Entity::discard);
    }

    /** The projectile's own part of its save data (what {@code /data get entity} shows, and a saved mine). */
    void save(CompoundTag tag) {
        Vec3 motion = this.projectile.getDeltaMovement();
        ListTag direction = new ListTag();
        direction.add(DoubleTag.valueOf(motion.x));
        direction.add(DoubleTag.valueOf(motion.y));
        direction.add(DoubleTag.valueOf(motion.z));
        tag.put("direction", direction);
        tag.putString("WeaponName", this.projectile.weaponName());
        this.role.save(tag);
    }

    /** A projectile loaded from a save is removed straight away, unless its role restores it. */
    void load(CompoundTag tag) {
        if (!this.role.load(tag)) {
            this.loadedFromSave = true;
        }
    }
}
