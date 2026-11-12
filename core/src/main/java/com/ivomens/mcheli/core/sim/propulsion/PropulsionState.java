package com.ivomens.mcheli.core.sim.propulsion;

/**
 * Engine and thrust state a motion step reads.
 *
 * @param throttle      0 to 1
 * @param reverseThrust reverse lever, 0 to 0.6 on planes; vehicles without reverse keep it at 0
 * @param cruiseSpeed   see {@link CruiseSpeed}
 * @param nozzleAngle   VTOL nozzle angle in degrees, 0 for horizontal thrust. The nozzle animates
 *                      after the physics step, so a step always sees the angle of the tick before:
 *                      thrust lags the nozzle by one tick, as it always has.
 */
public record PropulsionState(double throttle, float reverseThrust, double cruiseSpeed, float nozzleAngle) {

    public PropulsionState withCruiseSpeed(double newCruiseSpeed) {
        return new PropulsionState(this.throttle, this.reverseThrust, newCruiseSpeed, this.nozzleAngle);
    }
}
