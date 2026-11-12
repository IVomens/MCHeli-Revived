package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.Collisions;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Free-fall bomb: a little horizontal air drag, water slowing it down, and a proximity fuze that goes
 * off near creatures, checked every ten ticks.
 */
public final class BombRun implements Guidance {

    private static final double HORIZONTAL_AIR_DRAG = 0.999;
    private static final int PROXIMITY_FUZE_INTERVAL = 10;

    @Override
    public void steer(ProjectileEntity bomb) {
        if (bomb.level().isClientSide) {
            return;
        }
        WeaponSpec info = bomb.spec();
        Vec3 m = bomb.getDeltaMovement();
        m = new Vec3(m.x * HORIZONTAL_AIR_DRAG, m.y, m.z * HORIZONTAL_AIR_DRAG);
        if (bomb.isInWater()) {
            m = m.scale(info.ballistics().velocityInWater());
        }
        bomb.setDeltaMovement(m);
        checkProximity(bomb, info);
    }

    private static void checkProximity(ProjectileEntity bomb, WeaponSpec info) {
        float dist = info.explosion().proximityFuseDist();
        if (dist <= 0.1F || bomb.age() % PROXIMITY_FUZE_INTERVAL != 0) {
            return;
        }
        List<Entity> nearby = bomb.level().getEntities(bomb, bomb.getBoundingBox().inflate(dist),
                e -> e instanceof LivingEntity && Collisions.canHit(bomb, e));
        if (!nearby.isEmpty()) {
            bomb.impact(new BlockHitResult(bomb.position(), Direction.DOWN,
                    BlockPos.containing(bomb.getX() + 0.5, bomb.getY() + 0.5, bomb.getZ() + 0.5), false));
        }
    }
}
