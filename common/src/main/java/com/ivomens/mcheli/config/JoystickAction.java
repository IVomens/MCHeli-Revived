package com.ivomens.mcheli.config;

import java.util.Locale;

/**
 * The vehicle actions a joystick or gamepad button can be bound to. A bound button works like the
 * action's key: pressing it sends the same command the key sends. The defaults are the buttons of a
 * gamepad in GLFW's standard layout (A 0, B 1, X 2, Y 3, LB 4, RB 5, back 6, start 7, guide 8,
 * left stick 9, right stick 10, d-pad up 11, right 12, down 13, left 14); -1 is unbound.
 */
public enum JoystickAction {
    FIRE("JoystickButtonFire", 5),
    NEXT_WEAPON("JoystickButtonNextWeapon", 3),
    WEAPON_MODE("JoystickButtonWeaponMode", 14),
    RELOAD("JoystickButtonReload", 11),
    FLARE("JoystickButtonFlare", 1),
    CHAFF("JoystickButtonChaff", 12),
    GEAR("JoystickButtonGear", 2),
    CAMERA_MODE("JoystickButtonCameraMode", 13),
    ZOOM("JoystickButtonZoom", 10),
    FREE_LOOK("JoystickButtonFreeLook", 9),
    HOVER("JoystickButtonHover", 0),
    EXTRA("JoystickButtonExtra", 4),
    THROTTLE_UP("JoystickButtonThrottleUp", -1),
    THROTTLE_DOWN("JoystickButtonThrottleDown", -1);

    /** The highest button number a binding accepts. */
    public static final int MAX_BUTTON = 127;
    /** The default of the first action, whose option carries the comment for all of them. */
    private static final int FIRST_DEFAULT = 5;

    private final IntOption option;

    JoystickAction(String key, int defaultButton) {
        IntOption bound = IntOption.of(key, defaultButton).range(-1, MAX_BUTTON);
        this.option = defaultButton == FIRST_DEFAULT ? bound.describe("JoystickButton<Action> = the button that"
                + " does what the action's key does, -1 for none. The defaults suit a gamepad.") : bound;
    }

    /** The config option holding the bound button. */
    public IntOption option() {
        return this.option;
    }

    /** The end of the action's translation key, {@code mcheli.joystick.action.<name>}. */
    public String translationName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
