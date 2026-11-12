package com.ivomens.mcheli.aircraft.assist;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.core.sim.assist.AltitudeHold;
import com.ivomens.mcheli.core.sim.assist.AutoLevel;
import com.ivomens.mcheli.core.sim.assist.CoordinatedTurn;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ThrottleCommand;
import com.ivomens.mcheli.core.sim.model.RotorcraftHandling;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.vehicle.settings.AssistSettings;

/**
 * The opt-in handling assists of one pilot, applied to the pilot's input: the helicopter assist
 * (auto-level and altitude hold) and the planes' auto-rudder. With every assist off the input is
 * handed back untouched. The control laws are in {@code core.sim.assist}; this reads the vehicle for
 * them.
 * <p>
 * The pilot's client runs it, and the golden-trajectory harness runs it in place of that client. One
 * per pilot; it follows one vehicle at a time and starts over when the vehicle changes.
 */
public final class PilotAssist {

    private final AltitudeHold altitudeHold = new AltitudeHold();
    private int vehicleId = Integer.MIN_VALUE;
    private double lastAltitude = Double.NaN;

    /**
     * The throttle keys the altitude hold presses this tick. Call once per game tick.
     *
     * @param vehicle         the flown vehicle
     * @param assisted        whether the helicopter assist is on
     * @param pilotOnThrottle whether the pilot works the throttle (a key, a joystick throttle): the hold
     *                        lets go and takes the altitude again afterwards
     */
    public ThrottleCommand throttle(AircraftEntity vehicle, boolean assisted, boolean pilotOnThrottle) {
        double climb = follow(vehicle);
        if (!(vehicle instanceof HelicopterEntity heli) || heli.definition() == null) {
            return this.altitudeHold.update(new AltitudeHold.Reading(false, 0.0, 0.0, 0.0, 0.0F, 0.0F));
        }
        boolean engaged = assisted && !pilotOnThrottle && !heli.isDestroyed() && !heli.onGround()
                && !heli.isInWater() && !heli.modes().isHovering() && !heli.blades().isFolded()
                && heli.attitudeControl().terrain().airborne();
        float gravity = heli.physics().handling(RotorcraftHandling.class).gravity().air();
        return this.altitudeHold.update(new AltitudeHold.Reading(engaged, heli.getY(), climb, heli.engine().throttle(),
                gravity, heli.spec().file().features().throttleUpDown()));
    }

    /** The altitude the hold keeps, or NaN while it is not holding. */
    public double heldAltitude() {
        return this.altitudeHold.target();
    }

    /**
     * The stick for one attitude step with the attitude assists applied.
     *
     * @param vehicle  the flown vehicle
     * @param input    the pilot's stick
     * @param settings which assists are on
     */
    public static AttitudeInput stick(AircraftEntity vehicle, AttitudeInput input, AssistSettings settings) {
        if (settings.assistedHeli() && vehicle instanceof HelicopterEntity heli && !heli.modes().isHovering()
                && heli.attitudeControl().terrain().airborne()) {
            return AutoLevel.DEFAULT.apply(input, heli.body().attitude());
        }
        if (settings.autoRudderPlane() && vehicle instanceof AbstractPlaneEntity plane) {
            return CoordinatedTurn.withGain((float) settings.autoRudderGain()).apply(input, plane.body().attitude(),
                    plane.attitudeControl().terrain().airborne());
        }
        return input;
    }

    /** The climb over the last tick, starting over for a new vehicle. */
    private double follow(AircraftEntity vehicle) {
        if (vehicle.getId() != this.vehicleId) {
            this.vehicleId = vehicle.getId();
            this.lastAltitude = Double.NaN;
        }
        double climb = Double.isNaN(this.lastAltitude) ? 0.0 : vehicle.getY() - this.lastAltitude;
        this.lastAltitude = vehicle.getY();
        return climb;
    }
}
