package com.ivomens.mcheli.vehicle.client.input;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Where joystick input comes from. The game reads GLFW ({@link GlfwJoystickSource}); tests and the dev
 * scene hand in a scripted device instead, so everything behind this interface can be checked
 * without a real joystick.
 */
public interface JoystickSource {

    /** A connected device. */
    record Device(int id, String name, String guid, boolean gamepad) {

        /** Whether a {@code JoystickDevice} setting picks this device: empty picks any, else part of the name or the GUID. */
        public boolean matches(String wanted) {
            if (wanted.isEmpty()) {
                return true;
            }
            return this.guid.equalsIgnoreCase(wanted)
                    || this.name.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * One reading of a device. The arrays belong to the reading; nobody changes them afterwards.
     *
     * @param axes    each axis, -1 to 1
     * @param buttons each button, down or not
     */
    record Reading(float[] axes, boolean[] buttons) {

        public static final Reading EMPTY = new Reading(new float[0], new boolean[0]);

        public float axis(int index) {
            return index >= 0 && index < this.axes.length ? this.axes[index] : 0.0F;
        }

        public boolean button(int index) {
            return index >= 0 && index < this.buttons.length && this.buttons[index];
        }
    }

    /** The devices connected now, in id order. */
    List<Device> devices();

    /**
     * Reads a device.
     *
     * @param asGamepad read it in the standard gamepad layout; only for a device that is a gamepad
     * @return the reading, or null when the device is gone
     */
    @Nullable
    Reading read(Device device, boolean asGamepad);
}
