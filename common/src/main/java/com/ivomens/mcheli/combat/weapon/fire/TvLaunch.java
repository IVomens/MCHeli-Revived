package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.net.TvMissileMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * TV guided missiles. In mode 0 the shooter steers the missile through its camera, and the crew is
 * told to switch to the missile camera; in the other modes it flies half again as fast and follows the
 * laser designator (or flies straight).
 */
public final class TvLaunch implements FireMode {

    /** For this many ticks after a TV shot the crew is told (every third tick) to switch to the missile camera. */
    private static final int CAMERA_NOTIFY_TICKS = 9;
    private static final int CAMERA_NOTIFY_INTERVAL = 3;
    /** The vehicle shows the missile camera for this many ticks after the shot. */
    private static final int CAMERA_TICKS = 2;
    private static final float UNGUIDED_SPEED_FACTOR = 1.5F;

    @Nullable
    private ProjectileEntity lastMissile;
    @Nullable
    private Entity lastPlatform;
    @Nullable
    private ArmedVehicle lastVehicle;
    private boolean steeredByCamera;

    @Override
    public boolean predict(Shot shot) {
        WeaponMount mount = shot.mount();
        mount.setRequest(mount.request().withMode(mount.currentMode()));
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Shots.playFireSound(shot, shot.platform());
        Vec2 rot = Shots.rotation(shot, true, false);
        Vec3 motion = Vec3.directionFromRotation(rot.y, rot.x);
        this.steeredByCamera = shot.request().mode() == 0;
        float baseSpeed = shot.mount().stats().speed();
        float speed = this.steeredByCamera ? baseSpeed : baseSpeed * UNGUIDED_SPEED_FACTOR;
        ProjectileEntity missile = Shots.projectile(shot, ProjectileKind.TV_MISSILE, motion, rot.x, rot.y, speed);
        this.lastPlatform = shot.platform();
        this.lastVehicle = shot.shooter().vehicle();
        this.lastMissile = missile;
        shot.level().addFreshEntity(missile);
        return true;
    }

    @Override
    public void tick(WeaponMount mount, int cooldown) {
        if (mount.isClientSide()) {
            return;
        }
        int ticks = mount.ticksSinceShot();
        if (this.steeredByCamera && ticks <= CAMERA_NOTIFY_TICKS) {
            if (ticks % CAMERA_NOTIFY_INTERVAL == 0 && isAlive(this.lastMissile) && isAlive(this.lastPlatform)) {
                // only players that can see the vehicle (its crew included) need the camera switch
                Network.sendToTracking(this.lastPlatform, new TvMissileMessage(this.lastPlatform.getId(),
                        this.lastMissile.getId()));
            }
            if (ticks == CAMERA_NOTIFY_TICKS) {
                this.lastPlatform = null;
                this.lastVehicle = null;
                this.lastMissile = null;
            }
        }
        if (ticks <= CAMERA_TICKS && this.lastVehicle != null) {
            this.lastVehicle.cameras().watchTvMissile(this.lastMissile);
        }
    }

    private static boolean isAlive(@Nullable Entity entity) {
        return entity != null && !entity.isRemoved();
    }
}
