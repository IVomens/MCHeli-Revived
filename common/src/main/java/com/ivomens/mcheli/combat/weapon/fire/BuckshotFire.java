package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.def.weapon.BuckshotPellet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Several pellets (rounds or rockets) per shot, each scattered inside the accuracy cone. */
public final class BuckshotFire implements FireMode {

    @Override
    public boolean predict(Shot shot) {
        Shots.applyRecoil(shot);
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        Vec2 base = Shots.rotation(shot, false, false);
        ProjectileKind kind = info.buckshotPayload() == BuckshotPellet.ROCKET ? ProjectileKind.ROCKET
                : ProjectileKind.BULLET;
        int count = Math.max(1, info.definition().buckshot().count());
        for (int i = 0; i < count; i++) {
            Vec2 rot = Shots.scatter(CombatRandom.of(shot.level()).aim(), base.x, base.y, info.definition().accuracy());
            float yaw = Mth.wrapDegrees(rot.x);
            float pitch = Mth.wrapDegrees(rot.y);
            Vec3 motion = Vec3.directionFromRotation(pitch, yaw);
            ProjectileEntity pellet = Shots.projectile(shot, kind, motion, yaw, pitch, shot.mount().stats().speed());
            Shots.spawnHalfStepAhead(shot.level(), pellet);
        }
        Shots.playFireSound(shot, shot.platform());
        return true;
    }
}
