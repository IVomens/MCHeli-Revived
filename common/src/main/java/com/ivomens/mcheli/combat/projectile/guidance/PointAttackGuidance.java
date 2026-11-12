package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.Submunition;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fire-and-forget missile flying to the block its shooter looked at. Missiles without gravity climb for
 * their first ticks and then fly straight at it; ballistic ones only steer horizontally. Cluster versions
 * release their bomblets near the target, which drift slowly upwards.
 */
public final class PointAttackGuidance implements Guidance {

    private static final int CLIMB_TICKS = 10;
    private static final double CLIMB_AIM_OFFSET = 20.0;

    @Override
    public void steer(ProjectileEntity missile) {
        if (!missile.level().isClientSide && !missile.isBomblet()) {
            this.guide(missile, missile.spec());
            if (missile.isRemoved()) return;
        }
        FlightModel.faceMotion(missile);
    }

    private void guide(ProjectileEntity missile, WeaponSpec info) {
        Vec3 point = missile.targetPoint();
        if (point == null) {
            return;
        }
        BlockPos targetPos = BlockPos.containing(point);
        // never load the chunk of a far away target just to look at it
        if (!missile.level().hasChunkAt(targetPos) || !missile.level().getBlockState(targetPos).isSolid()) return;

        if (Math.sqrt(missile.distanceToSqr(point)) < info.explosion().proximityFuseDist()) {
            if (info.submunition().count() > 0) {
                Submunition.releaseAll(missile, info);
            } else {
                // not a block hit, so grass or leaves at the burst point can't swallow the explosion
                missile.impact(BlockHitResult.miss(missile.position(), Direction.DOWN, missile.blockPosition()));
            }
            missile.discard();
            return;
        }
        double dirX = point.x - missile.getX();
        double dirY = point.y - missile.getY();
        double dirZ = point.z - missile.getZ();
        if (FlightModel.gravity(missile, info) == 0.0) {
            if (missile.age() < CLIMB_TICKS) {
                dirY += CLIMB_AIM_OFFSET;
            }
            missile.setMotionAlong(dirX, dirY, dirZ);
        } else {
            double horizontalLength = Math.sqrt(dirX * dirX + dirZ * dirZ);
            missile.setDeltaMovement(dirX * missile.speed() / horizontalLength, missile.getDeltaMovement().y,
                    dirZ * missile.speed() / horizontalLength);
        }
        missile.hasImpulse = true;
    }
}
