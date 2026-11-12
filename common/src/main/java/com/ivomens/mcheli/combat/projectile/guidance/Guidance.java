package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;

/**
 * What a projectile does after each move, on both sides: home on a target, fly to a point, drift
 * with its carrier's drag, or nothing. Implementations hold no state; what they remember lives on the
 * projectile.
 */
@FunctionalInterface
public interface Guidance {

    /** Flies straight on: rockets and marker rockets. */
    Guidance NONE = projectile -> {
    };

    /** Called once per tick after the move, unless the projectile is already gone. */
    void steer(ProjectileEntity projectile);
}
