package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;

import javax.annotation.Nullable;

/**
 * Charge-up kinetic weapons firing a fast round. The charge (spin-up sound and wait) only exists on the
 * client: holding the trigger for the definition's {@code LockTime} fires the shot, releasing it starts
 * over. The server fires as soon as the shot arrives.
 */
public final class RailgunFire implements FireMode {

    private static final float CHARGE_SOUND_VOLUME = 10.0F;

    private int chargeTicks;
    private int previousChargeTicks;
    @Nullable
    private LocalClient.PlayingSound chargeSound;

    @Override
    public boolean predict(Shot shot) {
        int chargeTime = shot.mount().spec().missile().lockOn().time();
        if (chargeTime > 0) {
            if (this.chargeTicks > chargeTime) {
                return false;
            }
            if (this.chargeTicks == 1) {
                this.startChargeSound(shot);
            }
            if (++this.chargeTicks < chargeTime) {
                return false;
            }
            this.chargeTicks = 0;
        }
        Shots.applyRecoil(shot);
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Vec2 rotation = Shots.rotation(shot, true, false);
        ProjectileEntity round = Shots.round(shot, ProjectileKind.BULLET, rotation.x, rotation.y);
        Shots.spawnHalfStepAhead(shot.level(), round);
        Shots.playFireSound(shot, shot.platform());
        return true;
    }

    /** Client: the trigger was released when the charge stopped advancing. */
    @Override
    public void tick(WeaponMount mount, int cooldown) {
        if (!mount.isClientSide()) {
            return;
        }
        if (this.chargeTicks != this.previousChargeTicks) {
            this.previousChargeTicks = this.chargeTicks;
        } else {
            this.chargeTicks = this.previousChargeTicks = 0;
            if (this.chargeSound != null) {
                this.chargeSound.stop();
                this.chargeSound = null;
            }
        }
    }

    private void startChargeSound(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        Entity source = shot.user() != null ? shot.user() : shot.platform();
        if (this.chargeSound == null && info.sound().locations().railgun() != null) {
            this.chargeSound = LocalClient.get().playSound(info.sound().locations().railgun(), CHARGE_SOUND_VOLUME,
                    1.0F,
                    source.position());
        }
    }
}
