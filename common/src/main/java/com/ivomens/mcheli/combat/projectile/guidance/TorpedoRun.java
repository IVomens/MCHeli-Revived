package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.world.phys.Vec3;

/**
 * Torpedo: once in the water it settles at its underwater speed and runs straight, or, if guided, to
 * the water block its shooter aimed at.
 */
public final class TorpedoRun implements Guidance {

    private static final double SPEED_STEP = 0.1;
    /** Unguided torpedoes keep this share of their vertical speed every tick in the water. */
    private static final double VERTICAL_DAMPING = 0.8F;

    @Override
    public void steer(ProjectileEntity torpedo) {
        if (!torpedo.isInWater()) {
            return;
        }
        WeaponSpec info = torpedo.spec();
        if (info.missile().guidedTorpedo()) {
            if (!torpedo.level().isClientSide) {
                settleSpeed(torpedo, info);
                Vec3 point = torpedo.targetPoint();
                setMotionIfMoving(torpedo, point.x - torpedo.getX(), point.y - torpedo.getY(), point.z - torpedo.getZ());
            }
            FlightModel.faceMotion(torpedo);
            return;
        }
        if (!torpedo.level().isClientSide) {
            Vec3 m = torpedo.getDeltaMovement();
            settleSpeed(torpedo, info);
            setMotionIfMoving(torpedo, m.x, m.y * VERTICAL_DAMPING, m.z);
        }
        Vec3 m = torpedo.getDeltaMovement();
        torpedo.setYRot((float) (Math.atan2(m.z, m.x) * 180.0 / Math.PI) - 90.0F);
    }

    private static void settleSpeed(ProjectileEntity torpedo, WeaponSpec info) {
        double cruise = info.ballistics().accelerationInWater();
        if (torpedo.speed() < cruise) {
            torpedo.setSpeed(torpedo.speed() + SPEED_STEP);
        } else if (torpedo.speed() > cruise + 0.2F) {
            torpedo.setSpeed(torpedo.speed() - SPEED_STEP);
        }
    }

    private static void setMotionIfMoving(ProjectileEntity torpedo, double dirX, double dirY, double dirZ) {
        if (dirX * dirX + dirY * dirY + dirZ * dirZ > 0.0) {
            torpedo.setMotionAlong(dirX, dirY, dirZ);
            torpedo.hasImpulse = true;
        }
    }
}
