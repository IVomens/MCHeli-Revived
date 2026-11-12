package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponRayUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Fire-and-forget missiles that fly to the block the shooter looks at when firing. */
public final class PointAttackLaunch implements FireMode {

    private static final double AIM_RANGE = 1500.0;

    @Override
    public boolean predict(Shot shot) {
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Entity aim = shot.user() != null ? shot.user() : shot.platform();
        Entity origin = shot.platform();
        float yaw = aim.getYRot();
        float pitch = aim.getXRot();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 src = origin.getEyePosition();
        Vec3 dst = src.add(look.scale(AIM_RANGE));
        Vec3 hit = WeaponRayUtil.clipLoaded(shot.level(), src, dst, origin, null);
        ProjectileEntity missile = Shots.projectile(shot, ProjectileKind.AS_MISSILE, look, yaw, pitch,
                shot.mount().stats().speed());
        missile.aimAt(hit != null ? hit : dst);
        shot.level().addFreshEntity(missile);
        Shots.playFireSound(shot, shot.platform());
        return true;
    }
}
