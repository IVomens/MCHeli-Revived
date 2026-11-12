package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import net.minecraft.world.phys.Vec3;

/**
 * Dispensers drop containers that use an item (water bucket, bone meal...) on the blocks around where
 * they land; a container leaves with the carrier's velocity plus half its own launch speed.
 */
public final class ContainerDrop implements FireMode {

    @Override
    public boolean predict(Shot shot) {
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Shots.playFireSound(shot, shot.platform());
        ProjectileEntity container = Shots.projectile(shot, ProjectileKind.DISPENSED_ITEM, Shots.launchDirection(shot),
                shot.yaw(), shot.pitch(), shot.mount().stats().speed());
        Vec3 motion = shot.platform().getDeltaMovement().add(container.getDeltaMovement().scale(0.5));
        container.setDeltaMovement(motion);
        container.setPos(container.getX() + motion.x * 0.5, container.getY() + motion.y * 0.5,
                container.getZ() + motion.z * 0.5);
        shot.level().addFreshEntity(container);
        return true;
    }
}
