package com.ivomens.mcheli.combat.projectile;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The projectiles of one level that have a target. Missile warnings look for the missiles homing on a
 * vehicle several times a second; an entity search of the 120-block box around the vehicle would walk every
 * round of a firefight to find the few missiles, while only a projectile with a target can be homing.
 * <p>
 * {@link ProjectileEntity} keeps it current when its target changes; projectiles that left the level are
 * dropped as they are met.
 */
public final class HomingProjectiles {

    /** Implemented on {@link Level} by a mixin, so every level owns its own set. */
    public interface Holder {
        HomingProjectiles mcheli$homingProjectiles();
    }

    /** Removed projectiles are swept out after this many additions, even if nobody asks. */
    private static final int SWEEP_INTERVAL = 256;

    private final ReferenceLinkedOpenHashSet<ProjectileEntity> projectiles = new ReferenceLinkedOpenHashSet<>();
    private int additions;

    public static HomingProjectiles of(Level level) {
        return ((Holder) level).mcheli$homingProjectiles();
    }

    /** Called whenever the projectile's target changes. */
    void targetChanged(ProjectileEntity projectile) {
        if (projectile.target() == null) {
            this.projectiles.remove(projectile);
        } else if (this.projectiles.add(projectile) && ++this.additions % SWEEP_INTERVAL == 0) {
            this.projectiles.removeIf(ProjectileEntity::isRemoved);
        }
    }

    /**
     * What {@code level.getEntitiesOfClass(ProjectileEntity.class, area, filter)} finds among the projectiles
     * that have a target: those in the level whose box touches the area and that pass the filter. The order
     * may differ.
     */
    public List<ProjectileEntity> inArea(Level level, AABB area, Predicate<ProjectileEntity> filter) {
        List<ProjectileEntity> found = new ArrayList<>();
        this.projectiles.removeIf(ProjectileEntity::isRemoved);
        for (ProjectileEntity projectile : this.projectiles) {
            if (level.getEntity(projectile.getId()) == projectile && projectile.getBoundingBox().intersects(area)
                    && filter.test(projectile)) {
                found.add(projectile);
            }
        }
        return found;
    }
}
