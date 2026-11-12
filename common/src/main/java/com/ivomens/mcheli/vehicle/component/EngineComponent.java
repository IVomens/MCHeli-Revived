package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;

/**
 * The engine: the throttle the physics works with, the copy the server syncs to clients, and
 * whether the engine can run at all. A badly damaged engine (HP below the definition's
 * {@code engineShutdownThreshold} percent) only spools down.
 */
public final class EngineComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final ThrottleSync syncedThrottle;
    private final RestState rest;
    private final DamageComponent damage;
    private final FuelComponent fuel;
    private double throttle;
    private double previousThrottle;

    public EngineComponent(AircraftEntity vehicle, ThrottleSync syncedThrottle, RestState rest, DamageComponent damage,
            FuelComponent fuel) {
        this.vehicle = vehicle;
        this.syncedThrottle = syncedThrottle;
        this.rest = rest;
        this.damage = damage;
        this.fuel = fuel;
    }

    public double throttle() {
        return this.throttle;
    }

    /** The throttle at the start of this tick. */
    public double previousThrottle() {
        return this.previousThrottle;
    }

    /** Sets the throttle; a crippled engine cannot be throttled up, and any power wakes a parked vehicle. */
    public void setThrottle(double throttle) {
        if (throttle > this.throttle && isCrippled()) {
            throttle = this.throttle;
        }
        if (throttle > 0) {
            this.rest.wake();
        }
        this.throttle = throttle;
    }

    /** Takes a throttle the sim already limited for engine damage; see {@code AircraftPhysics#commit}. */
    public void commit(double throttle, boolean wake) {
        if (wake) {
            this.rest.wake();
        }
        this.throttle = throttle;
    }

    public void rememberThrottle() {
        this.previousThrottle = this.throttle;
    }

    /** The throttle as the server last published it, in its 0.05 sync steps. */
    public double syncedThrottle() {
        return this.syncedThrottle.get();
    }

    /** Server: tells clients the throttle. */
    public void publishThrottle(double throttle) {
        this.syncedThrottle.publish(throttle);
    }

    /**
     * True when the vehicle is damaged below {@code engineShutdownThreshold} percent of its HP: the
     * engine no longer produces power.
     */
    public boolean isCrippled() {
        VehicleSpec info = this.vehicle.spec();
        DamageComponent damage = this.damage;
        if (info == null || info.file().features().engineShutdownHp() <= 0 || this.vehicle.isDestroyed()
                || damage.maxHp() <= 0) {
            return false;
        }
        return damage.hp() * 100 < damage.maxHp() * info.file().features().engineShutdownHp();
    }

    /**
     * Whether the engine can run: fuel to burn (with {@code checkOtherSeats}, infinite fuel for anyone
     * aboard counts) and an engine that is not crippled. Every powered control depends on this.
     */
    public boolean canRun(boolean checkOtherSeats) {
        return this.fuel.hasUsable(checkOtherSeats) && !isCrippled();
    }

    public boolean canRun() {
        return canRun(false);
    }

    @Override
    public void describe(StateSink state) {
        state.put("throttle", this.throttle);
        state.put("previous throttle", this.previousThrottle);
        state.put("synced throttle", syncedThrottle());
        state.put("crippled", isCrippled());
        state.put("can run", canRun());
    }
}
