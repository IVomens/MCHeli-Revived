package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Free-fall bombs, released with the carrier's own velocity. A "destruct" bomb instead blows up the
 * small unmanned helicopter carrying it.
 */
public final class BombDrop implements FireMode {

    @Override
    public boolean predict(Shot shot) {
        if (shot.mount().spec().definition().selfDestruct()) {
            selfDestruct(shot, shot.mount().spec());
        }
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        if (info.definition().selfDestruct()) {
            selfDestruct(shot, info);
            return true;
        }
        Entity carrier = shot.platform();
        Shots.playFireSound(shot, carrier);
        Vec3 motion = carrier.getDeltaMovement();
        ProjectileEntity bomb = Shots.projectile(shot, ProjectileKind.BOMB, motion, carrier.getYRot(), 0.0F,
                shot.mount().stats().speed());
        // released with the carrier's velocity, not the normalized launch speed
        bomb.setDeltaMovement(motion);
        shot.level().addFreshEntity(bomb);
        return true;
    }

    private static void selfDestruct(Shot shot, WeaponSpec info) {
        ArmedVehicle vehicle = shot.shooter().vehicle();
        if (vehicle == null || vehicle.kind() != VehicleKind.HELICOPTER || !vehicle.isUAV()
                || vehicle.seats().count() != 0) {
            return;
        }
        if (!shot.isClient()) {
            Explosions.detonate(shot.level(), ExplosionSpec.at(vehicle.entity().position(), info.explosion().power())
                    .blockSize(info.explosionBlock()).fire(info.explosion().flaming()).destroyBlocks(true).build(),
                    null, shot.user());
            Shots.playFireSound(shot, vehicle.entity());
        }
        vehicle.destruct();
    }
}
