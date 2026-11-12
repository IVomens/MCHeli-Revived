package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;

/**
 * The attitude a pilot's client computed, sent to the server once per tick when it changed.
 *
 * @param vehicleId entity id of the vehicle
 * @param sequence  counts up with every report from that client, so a late one can be told apart
 * @param yaw       degrees
 * @param pitch     degrees
 * @param roll      degrees
 */
public record AttitudeReport(int vehicleId, int sequence, float yaw, float pitch, float roll) {

    public static AttitudeReport of(int vehicleId, int sequence, Attitude attitude) {
        return new AttitudeReport(vehicleId, sequence, attitude.yaw(), attitude.pitch(), attitude.roll());
    }

    public Attitude attitude() {
        return new Attitude(this.yaw, this.pitch, this.roll);
    }

    /** A client can send anything; infinite or NaN angles are never applied. */
    public boolean isFinite() {
        return Float.isFinite(this.yaw) && Float.isFinite(this.pitch) && Float.isFinite(this.roll);
    }
}
