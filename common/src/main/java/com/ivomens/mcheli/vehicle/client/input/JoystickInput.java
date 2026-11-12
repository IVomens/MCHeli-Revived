package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.core.input.AxisCurve;
import com.ivomens.mcheli.util.ModLogger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The joystick as the vehicle controls read it: each frame it picks the device the settings name,
 * reads it, and shapes the bound axes (dead zone, expo, sensitivity, invert). Stick axes come out
 * from -1 to 1; the throttle axis comes out as a lever position for an absolute throttle and past
 * its dead zone for an incremental one. Buttons are read as they are.
 * <p>
 * With joysticks disabled in the config nothing is read and every axis and button is idle. A device
 * that is unplugged simply stops being read; the axes go idle until it (or another match) comes
 * back. One per client; only the render thread touches it.
 */
public final class JoystickInput {

    private final JoystickSource source;
    @Nullable
    private JoystickSource.Device device;
    private JoystickSource.Reading reading = JoystickSource.Reading.EMPTY;
    @Nullable
    private JoystickSettings settings;
    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;

    public JoystickInput(JoystickSource source) {
        this.source = source;
    }

    /** Where the devices come from; the binding screen reads it directly. */
    public JoystickSource source() {
        return this.source;
    }

    /** Reads the joystick for this frame. */
    public void poll(JoystickSettings current) {
        this.settings = current;
        if (!current.enabled()) {
            clear();
            return;
        }
        JoystickSource.Device found = select(this.source.devices(), current.device());
        JoystickSource.Reading read = found != null ? this.source.read(found, current.gamepadMapping()) : null;
        if (read == null) {
            if (this.device != null) {
                ModLogger.info("Joystick {} is gone; flying with the mouse", this.device.name());
            }
            clear();
            return;
        }
        if (this.device == null || this.device.id() != found.id()) {
            ModLogger.info("Flying with joystick {}", found.name());
        }
        this.device = found;
        this.reading = read;
        AxisCurve stick = new AxisCurve(false, (float) current.deadZone(), (float) current.expo(),
                (float) current.sensitivity());
        this.pitch = shape(current.pitch(), stick);
        this.roll = shape(current.roll(), stick);
        this.yaw = shape(current.yaw(), stick);
        AxisCurve lever = current.throttleAbsolute() ? AxisCurve.LINEAR
                : new AxisCurve(false, (float) current.deadZone(), 0.0F, 1.0F);
        this.throttle = shape(current.throttle(), lever);
    }

    /** The first connected device the setting picks, or null. */
    @Nullable
    public static JoystickSource.Device select(List<JoystickSource.Device> devices, String wanted) {
        for (JoystickSource.Device candidate : devices) {
            if (candidate.matches(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    private float shape(JoystickSettings.Axis axis, AxisCurve curve) {
        return axis.bound() ? curve.inverted(axis.invert()).shape(this.reading.axis(axis.index())) : 0.0F;
    }

    private void clear() {
        this.device = null;
        this.reading = JoystickSource.Reading.EMPTY;
        this.pitch = 0.0F;
        this.roll = 0.0F;
        this.yaw = 0.0F;
        this.throttle = 0.0F;
    }

    /** Whether a device is being read. */
    public boolean active() {
        return this.device != null;
    }

    /** The device being read, or null. */
    @Nullable
    public JoystickSource.Device device() {
        return this.device;
    }

    /** The raw reading of this frame (empty when inactive). */
    public JoystickSource.Reading reading() {
        return this.reading;
    }

    public boolean drivesPitch() {
        return active() && this.settings.pitch().bound();
    }

    public boolean drivesRoll() {
        return active() && this.settings.roll().bound();
    }

    public boolean drivesYaw() {
        return active() && this.settings.yaw().bound();
    }

    public boolean drivesThrottle() {
        return active() && this.settings.throttle().bound();
    }

    /** Whether the throttle axis is a lever rather than a centring stick. */
    public boolean throttleAbsolute() {
        return this.settings != null && this.settings.throttleAbsolute();
    }

    /** Throttle per second of a fully deflected incremental throttle axis. */
    public double throttleRate() {
        return this.settings != null ? this.settings.throttleRate() : 0.0;
    }

    /** Stick pulled back (nose up) positive, -1 to 1. */
    public float pitch() {
        return this.pitch;
    }

    /** Stick to the right positive, -1 to 1. */
    public float roll() {
        return this.roll;
    }

    /** Rudder to the right positive, -1 to 1. */
    public float yaw() {
        return this.yaw;
    }

    /** The throttle axis: forward negative, -1 to 1. */
    public float throttle() {
        return this.throttle;
    }

    /** Whether the button bound to the action is down. */
    public boolean pressed(JoystickAction action) {
        return active() && this.reading.button(this.settings.button(action));
    }
}
