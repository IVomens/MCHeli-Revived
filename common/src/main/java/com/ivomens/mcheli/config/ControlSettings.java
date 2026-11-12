package com.ivomens.mcheli.config;

/** How the player steers: mouse, stick mode, throttle and camera. Client side. */
public record ControlSettings(boolean invertMouse, double mouseSensitivity, boolean stickModeHeli,
                              boolean stickModePlane, boolean flightSimMode, boolean autoThrottleDownHeli,
                              boolean autoThrottleDownPlane, boolean autoThrottleDownTank,
                              boolean switchWeaponWithMouseWheel, boolean lightWeaponAutoFire,
                              boolean invertMousePitch, boolean disableCameraDistChange) {

    public static final BoolOption INVERT_MOUSE = BoolOption.of("InvertMouse", false)
            .describe("InvertMouse = invert the up/down axis of the virtual stick in stick control mode.");
    public static final DoubleOption MOUSE_SENSITIVITY = DoubleOption.of("MouseSensitivity", 10.0);
    public static final BoolOption STICK_MODE_HELI = BoolOption.of("MouseControlStickModeHeli", false);
    public static final BoolOption STICK_MODE_PLANE = BoolOption.of("MouseControlStickModePlane", false);
    public static final BoolOption FLIGHT_SIM_MODE = BoolOption.of("MouseControlFlightSimMode", false)
            .describe("MouseControlFlightSimMode = true ( Yaw:key, Roll=mouse )");
    public static final BoolOption AUTO_THROTTLE_DOWN_HELI = BoolOption.of("AutoThrottleDownHeli", true);
    public static final BoolOption AUTO_THROTTLE_DOWN_PLANE = BoolOption.of("AutoThrottleDownPlane", false);
    public static final BoolOption AUTO_THROTTLE_DOWN_TANK = BoolOption.of("AutoThrottleDownTank", false);
    public static final BoolOption SWITCH_WEAPON_WITH_MOUSE_WHEEL = BoolOption.of("SwitchWeaponWithMouseWheel", true);
    public static final BoolOption LIGHT_WEAPON_AUTO_FIRE = BoolOption.of("LWeaponAutoFire", false);
    public static final BoolOption INVERT_MOUSE_PITCH = BoolOption.of("InvertMousePitch", false)
            .describe("InvertMousePitch = true flips the mouse up/down axis while flying, so moving the mouse down"
                    + " raises the nose (flight-sim style).");
    /** Server side: stops players changing the third person camera distance. */
    public static final BoolOption DISABLE_CAMERA_DIST_CHANGE = BoolOption.of("DisableThirdPersonCameraDistChange",
            false);

    static ControlSettings from(ConfigValues v) {
        return new ControlSettings(v.get(INVERT_MOUSE), v.get(MOUSE_SENSITIVITY), v.get(STICK_MODE_HELI),
                v.get(STICK_MODE_PLANE), v.get(FLIGHT_SIM_MODE), v.get(AUTO_THROTTLE_DOWN_HELI),
                v.get(AUTO_THROTTLE_DOWN_PLANE), v.get(AUTO_THROTTLE_DOWN_TANK),
                v.get(SWITCH_WEAPON_WITH_MOUSE_WHEEL), v.get(LIGHT_WEAPON_AUTO_FIRE), v.get(INVERT_MOUSE_PITCH),
                v.get(DISABLE_CAMERA_DIST_CHANGE));
    }
}
