package com.ivomens.mcheli.vehicle.component.countermeasure;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.combat.countermeasure.net.ChaffUseMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The chaff dispenser of a vehicle: while active it drops pairs of chaff clouds behind and below the
 * vehicle, ten over the burst, that radar guided missiles can lock instead.
 */
public final class ChaffDispenser implements Countermeasure {

    private static final int DROPS_PER_BURST = 10;
    private static final double DROP_DISTANCE_BEHIND = 20.0;
    private static final double DROP_DISTANCE_BELOW = 10.0;
    private static final double SIDE_OFFSET = 1.5;
    private static final double SIDE_SPEED = 0.2;

    private final Level level;
    private final AircraftEntity vehicle;
    private int cooldown;
    private int activeTicks;
    private int burstTicks;
    private int dropCountdown;

    public ChaffDispenser(AircraftEntity vehicle) {
        this.level = vehicle.level();
        this.vehicle = vehicle;
    }

    /**
     * Starts a burst of the given length, after which the dispenser needs {@code cooldown} ticks. The
     * server always obeys; a client only starts a burst when it thinks the dispenser is ready.
     */
    public boolean release(int burstTicks, int cooldown) {
        if (this.level.isClientSide && this.cooldown != 0) {
            return false;
        }
        this.burstTicks = burstTicks;
        this.cooldown = cooldown;
        this.activeTicks = burstTicks;
        this.dropCountdown = 0;
        if (!this.level.isClientSide) {
            // only players that can see the vehicle need to know
            Network.sendToTracking(this.vehicle, new ChaffUseMessage(this.vehicle.getId(), this.activeTicks));
            CountermeasureFlag.CHAFF_USING.set(this.vehicle, true);
        }
        return true;
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved()) {
            return;
        }
        if (this.cooldown > 0) this.cooldown--;
        if (this.activeTicks > 0) this.activeTicks--;
        if (this.activeTicks > 0) {
            this.dropOnSchedule();
        }
        if (!this.isActive()) {
            CountermeasureFlag.CHAFF_USING.clear(this.vehicle);
        }
    }

    @Override
    public boolean isActive() {
        return this.activeTicks > 0;
    }

    @Override
    public boolean isRecharging() {
        return this.cooldown != 0;
    }

    private void dropOnSchedule() {
        if (this.dropCountdown == 0) {
            this.dropCountdown = this.burstTicks / DROPS_PER_BURST;
            if (!this.level.isClientSide) {
                this.dropPair();
            }
        }
        if (this.dropCountdown > 0) {
            this.dropCountdown--;
        }
    }

    private void dropPair() {
        float yawRad = (float) Math.toRadians(this.vehicle.getYRot());
        Vec3 base = new Vec3(this.vehicle.xo + Mth.sin(yawRad) * DROP_DISTANCE_BEHIND,
                this.vehicle.yo - DROP_DISTANCE_BELOW, this.vehicle.zo - Mth.cos(yawRad) * DROP_DISTANCE_BEHIND);
        this.drop(base, yawRad + (float) Math.PI / 2F);
        this.drop(base, yawRad - (float) Math.PI / 2F);
    }

    /** One chaff cloud drifting sideways in the direction of the given yaw (radians). */
    private void drop(Vec3 base, float sideYawRad) {
        double sideX = -Mth.sin(sideYawRad);
        double sideZ = Mth.cos(sideYawRad);
        Vec3 motion = this.vehicle.getDeltaMovement();
        this.level.addFreshEntity(new ChaffEntity(this.level, base.x + sideX * SIDE_OFFSET, base.y,
                base.z + sideZ * SIDE_OFFSET, motion.x + sideX * SIDE_SPEED, motion.y, motion.z + sideZ * SIDE_SPEED));
    }
}
