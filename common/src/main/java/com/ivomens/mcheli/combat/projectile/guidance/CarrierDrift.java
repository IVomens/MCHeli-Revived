package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.world.phys.Vec3;

/**
 * A dropped container: one without a launch speed of its own feels a little horizontal air drag, and
 * water slows every container down.
 */
public final class CarrierDrift implements Guidance {

    private static final double HORIZONTAL_AIR_DRAG = 0.999;
    private static final double NO_LAUNCH_SPEED = 1.0E-4;

    @Override
    public void steer(ProjectileEntity container) {
        if (container.level().isClientSide) {
            return;
        }
        WeaponSpec info = container.spec();
        Vec3 m = container.getDeltaMovement();
        if (container.speed() < NO_LAUNCH_SPEED) {
            m = new Vec3(m.x * HORIZONTAL_AIR_DRAG, m.y, m.z * HORIZONTAL_AIR_DRAG);
        }
        if (container.isInWater()) {
            m = m.scale(info.ballistics().velocityInWater());
        }
        container.setDeltaMovement(m);
    }
}
