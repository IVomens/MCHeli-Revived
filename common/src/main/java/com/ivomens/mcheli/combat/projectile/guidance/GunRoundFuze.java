package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.Collisions;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Gun rounds fly straight; an explosive round with a proximity fuze bursts halfway to the first entity
 * that comes within one block more than the fuze distance.
 */
public final class GunRoundFuze implements Guidance {

    @Override
    public void steer(ProjectileEntity round) {
        if (round.level().isClientSide || round.age() <= 1 || round.warhead().explosionPower() <= 0) {
            return;
        }
        WeaponSpec info = round.spec();
        if (info.explosion().proximityFuseDist() <= 0.1F) {
            return;
        }
        float fuseDist = info.explosion().proximityFuseDist() + 1.0F;
        float searchRange = fuseDist + Mth.abs(info.ballistics().acceleration());
        float fuseDistSq = fuseDist * fuseDist;
        List<Entity> nearby = round.level().getEntities(round, round.getBoundingBox().inflate(searchRange),
                e -> Collisions.canHit(round, e) && e.distanceToSqr(round) < fuseDistSq);
        if (nearby.isEmpty()) {
            return;
        }
        Entity entity = nearby.getFirst();
        round.setPos((entity.getX() + round.getX()) / 2.0, (entity.getY() + round.getY()) / 2.0,
                (entity.getZ() + round.getZ()) / 2.0);
        round.impact(new BlockHitResult(entity.position(), Direction.DOWN,
                BlockPos.containing(round.getX(), round.getY(), round.getZ()), false));
    }
}
