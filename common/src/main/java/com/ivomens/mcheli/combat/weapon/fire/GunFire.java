package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import net.minecraft.world.phys.Vec2;

/**
 * Machine guns: one round per shot. A tracer gun with two modes fires high-explosive rounds in mode 1;
 * in mode 0 its rounds carry the explosion only as a sound (see {@code Warhead}: a negative explosion
 * power means "sound only").
 */
public final class GunFire implements FireMode {

    private final boolean explosiveMode;

    public GunFire(boolean explosiveMode) {
        this.explosiveMode = explosiveMode;
    }

    @Override
    public boolean predict(Shot shot) {
        WeaponMount mount = shot.mount();
        if (this.explosiveMode) {
            mount.setRequest(mount.request().withMode(mount.currentMode()));
        }
        Shots.applyRecoil(shot);
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Vec2 rotation = Shots.rotation(shot, false, false);
        ProjectileEntity round = Shots.round(shot, ProjectileKind.BULLET, rotation.x, rotation.y);
        if (this.explosiveMode && !this.firesHighExplosive(shot)) {
            int power = shot.mount().stats().explosionPower();
            round.warhead().setExplosionPower(-power, round.warhead().explosionPowerInWater());
        }
        Shots.spawnHalfStepAhead(shot.level(), round);
        Shots.playFireSound(shot, shot.platform());
        return true;
    }

    /** Single-mode tracer guns always do; two-mode ones in any mode but the first. */
    private boolean firesHighExplosive(Shot shot) {
        return shot.mount().spec().definition().modes() < 2 || shot.request().mode() != 0;
    }
}
