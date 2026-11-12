package com.ivomens.mcheli.core.wingman.brain;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A wingman's standing attack orders: players set them, and mission phases switch the attack mode on
 * and off as they fly through the target area.
 */
public final class AttackOrders {

    private AttackMode mode = AttackMode.NONE;
    private @Nullable UUID manualTarget;
    private @Nullable String weaponType;
    private @Nullable UUID autoTarget;

    public AttackMode mode() {
        return this.mode;
    }

    public boolean isAttacking() {
        return this.mode.isAttacking();
    }

    /** Sets the mode and the manual target together; the target only means something in manual mode. */
    public void set(AttackMode newMode, @Nullable UUID target) {
        this.mode = newMode;
        this.manualTarget = target;
    }

    /** Switches the mode and keeps the manual target, as mission phases do. */
    public void setMode(AttackMode newMode) {
        this.mode = newMode;
    }

    /** Stops attacking and forgets the manual target. */
    public void holdFire() {
        set(AttackMode.NONE, null);
    }

    public @Nullable UUID manualTarget() {
        return this.manualTarget;
    }

    /** MCHeli weapon type filter such as "gun" or "missile"; null tries every weapon. */
    public @Nullable String weaponType() {
        return this.weaponType;
    }

    public void setWeaponType(@Nullable String type) {
        this.weaponType = type;
    }

    /** The target auto attack picked, shared so wingmen of one leader spread over different targets. */
    public @Nullable UUID autoTarget() {
        return this.autoTarget;
    }

    public void setAutoTarget(@Nullable UUID target) {
        this.autoTarget = target;
    }

    /** The target this wingman is engaging, which siblings leave alone; null when it is not attacking. */
    public @Nullable UUID engagedTarget() {
        if (this.mode == AttackMode.MANUAL && this.manualTarget != null) {
            return this.manualTarget;
        }
        if (this.mode == AttackMode.AUTO && this.autoTarget != null) {
            return this.autoTarget;
        }
        return null;
    }
}
