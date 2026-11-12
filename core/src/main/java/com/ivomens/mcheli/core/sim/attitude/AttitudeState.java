package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Orientation;

/**
 * A vehicle's attitude between two steps.
 *
 * @param attitude    yaw, pitch and roll
 * @param orientation the quaternion that the quaternion mode turns, or null until it first does. It
 *                    is not resynced when something outside the model changes the angles (ground
 *                    settling, a server correction), so such changes only last until the next stick
 *                    step. That quirk is kept: the handling depends on it.
 * @param memory      what the vehicle type remembers from step to step
 * @param <M>         the type's memory
 */
public record AttitudeState<M>(Attitude attitude, Orientation orientation, M memory) {

    public AttitudeState<M> withAttitude(Attitude newAttitude) {
        return new AttitudeState<>(newAttitude, this.orientation, this.memory);
    }

    public AttitudeState<M> withMemory(M newMemory) {
        return new AttitudeState<>(this.attitude, this.orientation, newMemory);
    }
}
