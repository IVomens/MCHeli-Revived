package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponMount;

/**
 * Rockets launched along the platform's full attitude. A two-mode rocket pod fires plain rockets in
 * mode 0 and piercing ones in mode 1; a marker rocket marks where it lands for a bomb strike.
 */
public final class RocketLaunch implements FireMode {

    private final boolean marker;

    private RocketLaunch(boolean marker) {
        this.marker = marker;
    }

    public static RocketLaunch rockets() {
        return new RocketLaunch(false);
    }

    public static RocketLaunch markers() {
        return new RocketLaunch(true);
    }

    @Override
    public boolean predict(Shot shot) {
        WeaponMount mount = shot.mount();
        mount.setRequest(mount.request().withMode(mount.currentMode()));
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Shots.playFireSound(shot, shot.platform());
        ProjectileKind kind = this.marker ? ProjectileKind.MARKER_ROCKET : ProjectileKind.ROCKET;
        ProjectileEntity rocket = Shots.projectile(shot, kind, Shots.launchDirection(shot), shot.yaw(), shot.pitch(),
                shot.mount().stats().speed());
        if (this.marker) {
            rocket.startMarkerFlight();
        } else if (shot.request().mode() == 0 && shot.mount().stats().modes() > 1) {
            rocket.warhead().setPiercing(0);
        }
        shot.level().addFreshEntity(rocket);
        return true;
    }
}
