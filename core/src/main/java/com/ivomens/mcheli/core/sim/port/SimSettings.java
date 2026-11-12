package com.ivomens.mcheli.core.sim.port;

/**
 * Settings that change how a vehicle handles but are not part of its definition. The throttle and
 * attitude flags come from the config of the player flying it, so the server reads them from the
 * vehicle, never from a global; the tilt limit comes from the server.
 *
 * @param autoThrottleDown   whether the throttle eases back by itself when no throttle key is held
 * @param quaternionAttitude the stick turns a persistent quaternion instead of Euler angles, so the
 *                           airframe can fly through the vertical
 * @param flightSimMode      planes yaw with the side keys and roll with the mouse
 * @param tiltLimit          the server's helicopter tilt limit
 */
public record SimSettings(boolean autoThrottleDown, boolean quaternionAttitude, boolean flightSimMode,
                          TiltLimit tiltLimit) {

    /** The same settings with another auto throttle preference; each vehicle type has its own. */
    public SimSettings withAutoThrottleDown(boolean newAutoThrottleDown) {
        if (newAutoThrottleDown == this.autoThrottleDown) {
            return this;
        }
        return new SimSettings(newAutoThrottleDown, this.quaternionAttitude, this.flightSimMode, this.tiltLimit);
    }

    /**
     * How far a server lets helicopters tilt. Past the limits the airframe is eased back instead of
     * stopped, so a pilot can still overshoot briefly.
     *
     * @param enabled  whether the server limits tilt at all
     * @param pitchMin most nose-up pitch (negative)
     * @param pitchMax most nose-down pitch
     * @param roll     most roll either way
     */
    public record TiltLimit(boolean enabled, float pitchMin, float pitchMax, float roll) {

        public static final TiltLimit NONE = new TiltLimit(false, 0.0F, 0.0F, 0.0F);
    }
}
