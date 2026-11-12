package com.ivomens.mcheli.vehicle.component.countermeasure;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.combat.countermeasure.net.IronCurtainUseMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.world.level.Level;

/**
 * The iron curtain: while it runs the vehicle ignores damage and its hull shimmers. Vehicle files
 * declare it as an APS with the range 100 (see {@code AircraftFeatures.Aps}).
 * <p>
 * Using it starts a run on the side that used it; the server tells the tracking clients, which only
 * show the run ({@link #showRun}). The run and the cooldown are counted by whoever used it, the shimmer
 * everywhere.
 */
public final class IronCurtain implements Countermeasure {

    private static final float RESTING_FACTOR = 0.5F;

    private final Level level;
    private final AircraftEntity vehicle;
    private int cooldown;
    private int activeTicks;
    private int runningTicks;
    private int waveTimer;
    private float lastFactor = RESTING_FACTOR;
    private float currentFactor = RESTING_FACTOR;

    public IronCurtain(AircraftEntity vehicle) {
        this.level = vehicle.level();
        this.vehicle = vehicle;
    }

    /** Raises the curtain for {@code activeTicks}; false while it is still recharging. */
    public boolean activate(int activeTicks, int cooldown) {
        if (this.cooldown != 0) {
            return false;
        }
        this.cooldown = cooldown;
        this.activeTicks = activeTicks;
        boolean server = !this.level.isClientSide;
        if (server) {
            CountermeasureFlag.APS_USING.set(this.vehicle, true);
        }
        this.playSound("iron_curtain", server ? 10.0F : 3.0F);
        this.showRun(activeTicks);
        if (server) {
            Network.sendToTracking(this.vehicle, new IronCurtainUseMessage(this.vehicle.getId(), activeTicks));
        }
        return true;
    }

    /** Shows a run of the given length that someone else started. */
    public void showRun(int ticks) {
        this.runningTicks = ticks;
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved()) {
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.activeTicks > 0) {
            this.activeTicks--;
            if (this.activeTicks == 0) {
                this.playSound("aps_deactivate", 3.0F);
                this.lower();
            }
        }
        if (!this.isActive()) {
            CountermeasureFlag.APS_USING.clear(this.vehicle);
        }
    }

    /** Advances the shimmer of a run, used or shown; runs every tick whatever the vehicle carries. */
    public void tickShimmer() {
        if (this.runningTicks > 0) {
            this.runningTicks--;
            this.waveTimer++;
            this.lastFactor = this.currentFactor;
            this.currentFactor = 0.75F + 0.25F * (float) Math.sin(this.waveTimer * 0.15);
        } else {
            this.waveTimer = 0;
            this.currentFactor = RESTING_FACTOR;
            this.lastFactor = RESTING_FACTOR;
        }
    }

    private void lower() {
        this.runningTicks = 0;
        this.waveTimer = 0;
        this.currentFactor = RESTING_FACTOR;
        this.lastFactor = RESTING_FACTOR;
    }

    @Override
    public boolean isActive() {
        return this.activeTicks > 0;
    }

    @Override
    public boolean isRecharging() {
        return this.cooldown != 0;
    }

    /** Whether hits are ignored now, on either side. */
    public boolean isRunning() {
        return this.runningTicks > 0;
    }

    public int runningTicks() {
        return this.runningTicks;
    }

    /** Shimmer strength for rendering, {@code t} of the way from the last tick's value to this one's. */
    public float shimmer(float t) {
        return this.lastFactor + (this.currentFactor - this.lastFactor) * t;
    }

    private void playSound(String name, float volume) {
        ModSounds.playSound(this.level, this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ(), name, volume, 1.0F);
    }
}
