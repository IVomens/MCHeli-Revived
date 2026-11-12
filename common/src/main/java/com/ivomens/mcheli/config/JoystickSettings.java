package com.ivomens.mcheli.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Joystick and gamepad input, client side. Off by default; while off no device is read at all. The
 * axis and button numbers are those GLFW reports, in the gamepad layout when the device has a
 * gamepad mapping and {@link #gamepadMapping} is on. docs/CONTROLS.md describes setting one up.
 *
 * @param enabled          read a joystick at all
 * @param device           which one: empty for the first connected, otherwise part of its name or its GUID
 * @param gamepadMapping   read devices GLFW knows as gamepads in the standard gamepad layout
 * @param pitch            the pitch axis
 * @param roll             the roll axis
 * @param yaw              the yaw (rudder) axis
 * @param throttle         the throttle axis
 * @param throttleAbsolute the throttle axis is a lever (its position is the throttle); otherwise it is a
 *                         spring-loaded stick that moves the throttle up and down
 * @param throttleRate     how fast a fully deflected incremental throttle axis moves the throttle, per second
 * @param deadZone         share of the travel around the centre of the stick axes that reads as zero
 * @param expo             0 linear to 1 cubic response of the stick axes
 * @param sensitivity      scale of the stick axes
 * @param buttons          the bound button of each action, -1 for none
 */
public record JoystickSettings(boolean enabled, String device, boolean gamepadMapping, Axis pitch, Axis roll,
                               Axis yaw, Axis throttle, boolean throttleAbsolute, double throttleRate, double deadZone,
                               double expo, double sensitivity, Map<JoystickAction, Integer> buttons) {

    private static final int MAX_AXIS = 31;

    public static final BoolOption ENABLED = BoolOption.of("JoystickEnabled", false)
            .describe("JoystickEnabled = true reads a joystick or gamepad for flying. While it is connected it drives"
                    + " the axes it is bound to instead of the mouse. false (default) = no joystick is read.");
    public static final StringOption DEVICE = StringOption.of("JoystickDevice", "")
            .describe("JoystickDevice = empty for the first connected device, otherwise part of its name or its GUID.");
    public static final BoolOption GAMEPAD_MAPPING = BoolOption.of("JoystickUseGamepadMapping", true)
            .describe("JoystickUseGamepadMapping = true reads known gamepads in the standard layout (axes: left x 0,"
                    + " left y 1, right x 2, right y 3, triggers 4 and 5).");
    public static final IntOption PITCH_AXIS = axis("JoystickPitchAxis", 1)
            .describe("Joystick<Pitch|Roll|Yaw|Throttle>Axis = the axis number, -1 for none;"
                    + " Joystick<...>Invert = true flips it.");
    public static final BoolOption PITCH_INVERT = BoolOption.of("JoystickPitchInvert", false);
    public static final IntOption ROLL_AXIS = axis("JoystickRollAxis", 0);
    public static final BoolOption ROLL_INVERT = BoolOption.of("JoystickRollInvert", false);
    public static final IntOption YAW_AXIS = axis("JoystickYawAxis", 2);
    public static final BoolOption YAW_INVERT = BoolOption.of("JoystickYawInvert", false);
    public static final IntOption THROTTLE_AXIS = axis("JoystickThrottleAxis", 3);
    public static final BoolOption THROTTLE_INVERT = BoolOption.of("JoystickThrottleInvert", false);
    public static final StringOption THROTTLE_MODE = StringOption.of("JoystickThrottleMode", "incremental")
            .describe("JoystickThrottleMode = incremental (a centring stick moves the throttle up and down) or"
                    + " absolute (a throttle lever; forward is full).");
    public static final DoubleOption THROTTLE_RATE = DoubleOption.of("JoystickThrottleRate", 0.5).range(0.05, 5.0)
            .describe("JoystickThrottleRate = throttle per second at full incremental deflection.");
    public static final DoubleOption DEAD_ZONE = DoubleOption.of("JoystickDeadZone", 0.1).range(0.0, 0.9)
            .describe("JoystickDeadZone, JoystickExpo, JoystickSensitivity = the response of the stick axes:"
                    + " dead zone 0 to 0.9, expo 0 (linear) to 1 (cubic), sensitivity 0.1 to 3.");
    public static final DoubleOption EXPO = DoubleOption.of("JoystickExpo", 0.3).range(0.0, 1.0);
    public static final DoubleOption SENSITIVITY = DoubleOption.of("JoystickSensitivity", 1.0).range(0.1, 3.0);

    /** Every option, in the order the file lists them. */
    public static final List<Option<?>> ALL = all();

    /** One axis binding. */
    public record Axis(int index, boolean invert) {

        public static final Axis NONE = new Axis(-1, false);

        public boolean bound() {
            return this.index >= 0;
        }
    }

    public JoystickSettings {
        buttons = Collections.unmodifiableMap(new EnumMap<>(buttons));
    }

    static JoystickSettings from(ConfigValues v) {
        Map<JoystickAction, Integer> buttons = new EnumMap<>(JoystickAction.class);
        for (JoystickAction action : JoystickAction.values()) {
            buttons.put(action, v.get(action.option()));
        }
        return new JoystickSettings(v.get(ENABLED), v.get(DEVICE).trim(), v.get(GAMEPAD_MAPPING),
                new Axis(v.get(PITCH_AXIS), v.get(PITCH_INVERT)), new Axis(v.get(ROLL_AXIS), v.get(ROLL_INVERT)),
                new Axis(v.get(YAW_AXIS), v.get(YAW_INVERT)), new Axis(v.get(THROTTLE_AXIS), v.get(THROTTLE_INVERT)),
                "absolute".equals(v.get(THROTTLE_MODE).trim().toLowerCase(Locale.ROOT)), v.get(THROTTLE_RATE),
                v.get(DEAD_ZONE), v.get(EXPO), v.get(SENSITIVITY), buttons);
    }

    /** The button bound to an action, or -1. */
    public int button(JoystickAction action) {
        return this.buttons.getOrDefault(action, -1);
    }

    private static List<Option<?>> all() {
        List<Option<?>> options = new ArrayList<>(List.of(ENABLED, DEVICE, GAMEPAD_MAPPING, PITCH_AXIS, PITCH_INVERT,
                ROLL_AXIS, ROLL_INVERT, YAW_AXIS, YAW_INVERT, THROTTLE_AXIS, THROTTLE_INVERT, THROTTLE_MODE,
                THROTTLE_RATE, DEAD_ZONE, EXPO, SENSITIVITY));
        for (JoystickAction action : JoystickAction.values()) {
            options.add(action.option());
        }
        return List.copyOf(options);
    }

    private static IntOption axis(String key, int defaultAxis) {
        return IntOption.of(key, defaultAxis).range(-1, MAX_AXIS);
    }
}
