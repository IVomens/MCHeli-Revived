package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.assist.PilotAssist;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.core.input.KeyRamp;
import com.ivomens.mcheli.core.input.StickFilter;
import com.ivomens.mcheli.core.input.ThrottleAxis;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ThrottleCommand;
import com.ivomens.mcheli.core.sim.control.ThrottleServo;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.vehicle.settings.VehicleSettingsHolder;

import javax.annotation.Nullable;

/**
 * Everything besides the mouse that flies the pilot's vehicle, combined into the two things the
 * vehicle takes from a pilot: the stick of each attitude step ({@link #stick}) and the throttle keys
 * of each tick ({@link #throttleUp}, {@link #throttleDown}). The sources are a joystick, the stick
 * keys, the stick smoothing and the handling assists; each is opt-in, and with all of them off the
 * mouse's stick is passed on exactly as it was.
 * <p>
 * A joystick axis that is bound and connected replaces the mouse on that axis. The joystick buttons
 * reach the vehicle through the tick handlers' keys ({@link #pressed}).
 * <p>
 * One per client, in {@code VehicleControlContext}; only the render thread touches it.
 */
public final class PilotControls {

    /** Full stick deflection in mouse units: what the mouse stick reaches at its stop. */
    static final float FULL_STICK = (float) MouseStick.MAX_LENGTH;
    private static final double SECONDS_PER_TICK = 0.05;

    private final JoystickInput joystick;
    private final PilotAssist assist = new PilotAssist();
    private final ThrottleAxis throttleAxis = new ThrottleAxis();
    private final StickFilter smoothing = new StickFilter();
    private final KeyRamp pitchKeys = new KeyRamp();
    private final KeyRamp rollKeys = new KeyRamp();
    private final KeyRamp yawKeys = new KeyRamp();
    private ThrottleCommand throttle = ThrottleCommand.NONE;

    public PilotControls(JoystickInput joystick) {
        this.joystick = joystick;
    }

    public JoystickInput joystick() {
        return this.joystick;
    }

    /** Reads the joystick; every frame, before the stick is used. */
    public void pollJoystick(JoystickSettings settings) {
        this.joystick.poll(settings);
    }

    /**
     * Works out this tick's throttle keys; once per client tick, before the vehicle handlers read
     * their keys.
     *
     * @param piloted           the vehicle the player flies, or null
     * @param pilotThrottleKeys whether the player holds a throttle key (or button) himself
     * @param handling          the handling options
     */
    public void tick(@Nullable AircraftEntity piloted, boolean pilotThrottleKeys, HandlingSettings handling) {
        if (piloted == null || piloted.spec() == null) {
            this.throttle = ThrottleCommand.NONE;
            this.throttleAxis.release();
            return;
        }
        boolean onThrottle = pilotThrottleKeys || pressed(JoystickAction.THROTTLE_UP)
                || pressed(JoystickAction.THROTTLE_DOWN);
        ThrottleCommand lever = onThrottle ? ThrottleCommand.NONE : joystickThrottle(piloted);
        if (onThrottle) {
            this.throttleAxis.release();
        }
        boolean leverHolds = !Double.isNaN(this.throttleAxis.target()) && this.joystick.drivesThrottle();
        ThrottleCommand hold = this.assist.throttle(piloted, handling.assistedHeli(), onThrottle || leverHolds);
        this.throttle = leverHolds ? lever : hold;
    }

    private ThrottleCommand joystickThrottle(AircraftEntity piloted) {
        if (!this.joystick.drivesThrottle()) {
            this.throttleAxis.release();
            return ThrottleCommand.NONE;
        }
        ThrottleAxis.Mode mode = this.joystick.throttleAbsolute() ? ThrottleAxis.Mode.ABSOLUTE
                : ThrottleAxis.Mode.INCREMENTAL;
        double current = piloted.engine().throttle();
        double target = this.throttleAxis.update(mode, this.joystick.throttle(), current, SECONDS_PER_TICK,
                this.joystick.throttleRate());
        if (Double.isNaN(target)) {
            return ThrottleCommand.NONE;
        }
        return ThrottleServo.toward(current, target,
                ThrottleServo.keyDeadBand(piloted.spec().file().features().throttleUpDown()));
    }

    /** Whether this tick's throttle keys include throttle-up from the joystick or the altitude hold. */
    public boolean throttleUp() {
        return this.throttle.up() || pressed(JoystickAction.THROTTLE_UP);
    }

    public boolean throttleDown() {
        return this.throttle.down() || pressed(JoystickAction.THROTTLE_DOWN);
    }

    /** Whether the joystick button bound to the action is down. */
    public boolean pressed(JoystickAction action) {
        return this.joystick.pressed(action);
    }

    /** The altitude the helicopter assist holds, or NaN. */
    public double heldAltitude() {
        return this.assist.heldAltitude();
    }

    /**
     * The stick for one frame of the piloted vehicle.
     *
     * @param vehicle  the flown vehicle
     * @param mouse    the mouse's stick, in mouse units
     * @param freeLook the vehicle's free look state
     * @param keys     the stick keys held this frame
     * @param dt       frame length, seconds
     * @param handling the handling options
     */
    public AttitudeInput stick(AircraftEntity vehicle, float[] mouse, boolean freeLook, StickKeys keys, float dt,
                               HandlingSettings handling) {
        float x = mouse[0];
        float y = mouse[1];
        double smoothTime = vehicle instanceof HelicopterEntity ? handling.smoothingHeli()
                : vehicle instanceof AbstractPlaneEntity ? handling.smoothingPlane() : 0.0;
        if (smoothTime > 0.0) {
            float[] smoothed = this.smoothing.smooth(x, y, dt, (float) smoothTime);
            x = smoothed[0];
            y = smoothed[1];
        } else {
            this.smoothing.reset();
        }
        StickSources sources = new StickSources(x, y, freeLook);
        sources.joystick(this.joystick);
        if (handling.hasStickKeys()) {
            sources.keys(this.pitchKeys.update(keys.pitchDown(), keys.pitchUp(), dt, (float) handling.keyRampSeconds())
                            * (float) handling.keyPitchRate(),
                    this.rollKeys.update(keys.rollLeft(), keys.rollRight(), dt, (float) handling.keyRampSeconds())
                            * (float) handling.keyRollRate(),
                    this.yawKeys.update(keys.yawLeft(), keys.yawRight(), dt, (float) handling.keyRampSeconds())
                            * (float) handling.keyYawRate());
        }
        return PilotAssist.stick(vehicle, sources.input(), VehicleSettingsHolder.current().client().assists());
    }

    /** Whether the joystick moves the stick on at least one axis this frame. */
    public boolean joystickSteers() {
        return this.joystick.drivesPitch() || this.joystick.drivesRoll() || this.joystick.drivesYaw();
    }

    /** The stick keys held in one frame. */
    public record StickKeys(boolean pitchUp, boolean pitchDown, boolean rollLeft, boolean rollRight,
                            boolean yawLeft, boolean yawRight) {

        public static final StickKeys NONE = new StickKeys(false, false, false, false, false, false);
    }

    /** Adds the sources one by one onto the mouse's stick. */
    private static final class StickSources {

        private float x;
        private float y;
        private float rudder;
        private boolean freeLook;

        StickSources(float x, float y, boolean freeLook) {
            this.x = x;
            this.y = y;
            this.freeLook = freeLook;
        }

        /** A bound axis replaces the mouse; the joystick flies on in free look, where the mouse only looks. */
        void joystick(JoystickInput joystick) {
            boolean steers = false;
            if (joystick.drivesRoll()) {
                this.x = joystick.roll() * FULL_STICK;
                steers = true;
            }
            if (joystick.drivesPitch()) {
                this.y = joystick.pitch() * FULL_STICK;
                steers = true;
            }
            if (joystick.drivesYaw()) {
                this.rudder = joystick.yaw() * FULL_STICK;
                steers = true;
            }
            if (steers) {
                this.freeLook = false;
            }
        }

        /** The roll keys roll without the yaw the stick's x axis brings along. */
        void keys(float pitch, float roll, float yaw) {
            this.y += pitch;
            this.x += roll;
            this.rudder += yaw - roll;
        }

        AttitudeInput input() {
            return new AttitudeInput(this.x, this.y, this.freeLook, this.rudder);
        }
    }
}
