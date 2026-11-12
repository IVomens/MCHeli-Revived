package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.event.VehicleEnteredWater;
import com.ivomens.mcheli.api.event.VehicleLanded;
import com.ivomens.mcheli.api.event.VehicleLeftWater;
import com.ivomens.mcheli.api.event.VehicleTookOff;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.entity.Entity;

/**
 * Follows one vehicle's contact with the ground and with water for the API's takeoff, landing and water events,
 * and raises them once a change has lasted long enough that bouncing and rocking do not count. It only follows
 * while someone listens: otherwise each tick costs two listener lookups and nothing else, and the state is
 * forgotten, so the first state seen once someone listens is taken as the vehicle's own, without an event.
 * Ticked by the server at the end of the vehicle's tick.
 */
public final class VehicleContactWatch {

    /** An aircraft has to be off the ground this long to have taken off. */
    static final int TAKEOFF_TICKS = 20;
    /** And down this long to have landed. */
    static final int LANDING_TICKS = 10;
    /** Into or out of water this long to count. */
    static final int WATER_TICKS = 10;

    private final Vehicle vehicle;
    private final Debounced ground = new Debounced();
    private final Debounced water = new Debounced();

    public VehicleContactWatch(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public void tick() {
        ApiEventBus bus = ActivityEvents.bus();
        boolean groundWatched = bus.hasListeners(VehicleTookOff.class) || bus.hasListeners(VehicleLanded.class);
        boolean waterWatched = bus.hasListeners(VehicleEnteredWater.class) || bus.hasListeners(VehicleLeftWater.class);
        Entity entity = this.vehicle.entity();
        // a wreck or a vehicle carried by another entity is neither flying nor landed
        boolean followed = (groundWatched || waterWatched) && !this.vehicle.isDestroyed() && !entity.isPassenger();
        if (followed && groundWatched && isAircraft()) {
            boolean down = entity.onGround() || entity.isInWater();
            switch (this.ground.update(down, LANDING_TICKS, TAKEOFF_TICKS)) {
                case Debounced.BECAME_ON -> ActivityEvents.landed(this.vehicle, !entity.onGround());
                case Debounced.BECAME_OFF -> ActivityEvents.tookOff(this.vehicle);
                default -> {
                }
            }
        } else {
            this.ground.forget();
        }
        if (followed && waterWatched) {
            switch (this.water.update(entity.isInWater(), WATER_TICKS, WATER_TICKS)) {
                case Debounced.BECAME_ON -> ActivityEvents.enteredWater(this.vehicle);
                case Debounced.BECAME_OFF -> ActivityEvents.leftWater(this.vehicle);
                default -> {
                }
            }
        } else {
            this.water.forget();
        }
    }

    private boolean isAircraft() {
        VehicleKind kind = this.vehicle.kind();
        return kind == VehicleKind.HELICOPTER || kind == VehicleKind.PLANE;
    }

    /** A yes/no state that changes only after the new answer held for a number of ticks in a row. */
    static final class Debounced {

        static final int UNCHANGED = 0;
        static final int BECAME_ON = 1;
        static final int BECAME_OFF = 2;
        private static final int UNKNOWN = -1;

        /** The settled state: 1 on, 0 off, or unknown until the first answer has held. */
        private int state = UNKNOWN;
        /** The answer that differs from the settled state, and how many ticks in a row it held. */
        private boolean pending;
        private int pendingTicks;

        /**
         * Takes this tick's answer.
         *
         * @return {@link #BECAME_ON} or {@link #BECAME_OFF} when the settled state changed from a known one
         */
        int update(boolean now, int ticksToTurnOn, int ticksToTurnOff) {
            int asState = now ? 1 : 0;
            if (asState == this.state) {
                this.pendingTicks = 0;
                return UNCHANGED;
            }
            if (this.pendingTicks == 0 || this.pending != now) {
                this.pending = now;
                this.pendingTicks = 0;
            }
            this.pendingTicks++;
            boolean known = this.state != UNKNOWN;
            if (this.pendingTicks < (now ? ticksToTurnOn : ticksToTurnOff)) {
                return UNCHANGED;
            }
            this.state = asState;
            this.pendingTicks = 0;
            if (!known) {
                return UNCHANGED;
            }
            return now ? BECAME_ON : BECAME_OFF;
        }

        void forget() {
            this.state = UNKNOWN;
            this.pendingTicks = 0;
        }
    }
}
