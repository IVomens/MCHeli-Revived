package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.sound.client.UiSounds;

/**
 * The "locked on" tone the local player hears when a missile locks onto the vehicle they ride. The server
 * says so with a lock warning message; the tone then plays on the next tick, at most once a second.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class LockWarningTone {

    private static final int COOLDOWN_TICKS = 20;

    /** Set when a missile locks onto the player's vehicle; consumed by the tone. */
    private boolean locked;
    private int cooldown;

    /** A missile locked onto the player's vehicle. */
    public void onLockWarning() {
        this.locked = true;
    }

    /**
     * One client tick.
     *
     * @param inLiveVehicle whether the player rides a vehicle that is not destroyed; otherwise any warning
     *                      is dropped
     */
    public void tick(boolean inLiveVehicle) {
        if (inLiveVehicle) {
            if (this.locked && this.cooldown == 0) {
                this.locked = false;
                this.cooldown = COOLDOWN_TICKS;
                UiSounds.play("locked", 1.0F, 1.0F);
            }
        } else {
            this.locked = false;
            this.cooldown = 0;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
    }
}
