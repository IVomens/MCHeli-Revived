package com.ivomens.mcheli.config;

import java.util.List;

/**
 * Opt-in handling aids, client side. Every one is off by default, and off it changes nothing: the
 * vehicles fly exactly as without the section. They all work on the pilot's input (the stick and the
 * throttle keys), never on the physics, so the server sees an ordinary pilot. docs/CONTROLS.md
 * describes each one.
 *
 * @param assistedHeli        helicopters level themselves when the stick is centred and hold their
 *                            altitude when the throttle is let go
 * @param autoRudderPlane     planes in the air get rudder towards their bank, so they turn the way they lean
 * @param autoRudderGain      rudder at a 90 degree bank, as a share of full stick
 * @param smoothingHeli       time constant of the helicopter stick smoothing, seconds; 0 is off
 * @param smoothingPlane      the same for planes
 * @param centringHeli        how fast a helicopter stick left alone returns to the centre, per second; 0 is off
 * @param centringPlane       the same for planes
 * @param keyPitchRate        turn rate of the stick pitch keys at full ramp, degrees per second
 * @param keyRollRate         the same for the stick roll keys
 * @param keyYawRate          the same for the stick yaw keys
 * @param keyRampSeconds      time a stick key takes from centre to its full rate
 * @param keyPitchUp          key that pulls the nose up (0 is unbound)
 * @param keyPitchDown        key that pushes the nose down
 * @param keyRollLeft         key that rolls left
 * @param keyRollRight        key that rolls right
 * @param keyYawLeft          key that yaws left
 * @param keyYawRight         key that yaws right
 */
public record HandlingSettings(boolean assistedHeli, boolean autoRudderPlane, double autoRudderGain,
                               double smoothingHeli, double smoothingPlane, double centringHeli, double centringPlane,
                               double keyPitchRate, double keyRollRate, double keyYawRate, double keyRampSeconds,
                               int keyPitchUp, int keyPitchDown, int keyRollLeft, int keyRollRight, int keyYawLeft,
                               int keyYawRight) {

    private static final double MAX_KEY_RATE = 120.0;

    public static final BoolOption ASSISTED_HELI = BoolOption.of("HandlingAssistedHeli", false)
            .describe("HandlingAssistedHeli = true: helicopters level pitch and roll by themselves while the stick is"
                    + " centred, and hold their altitude once the throttle keys are let go. false = as always.");
    public static final BoolOption AUTO_RUDDER_PLANE = BoolOption.of("HandlingAutoRudderPlane", false)
            .describe("HandlingAutoRudderPlane = true: planes in the air yaw towards their bank (coordinated turns).");
    public static final DoubleOption AUTO_RUDDER_GAIN = DoubleOption.of("HandlingAutoRudderGain", 0.5).range(0.0, 2.0)
            .describe("HandlingAutoRudderGain = rudder at a 90 degree bank, as a share of full stick, 0 to 2.");
    public static final DoubleOption SMOOTHING_HELI = DoubleOption.of("HandlingStickSmoothingHeli", 0.0)
            .range(0.0, 0.5)
            .describe("HandlingStickSmoothingHeli = seconds the helicopter stick takes to follow the mouse"
                    + " (time constant, 0 to 0.5). 0 = off.");
    public static final DoubleOption SMOOTHING_PLANE = DoubleOption.of("HandlingStickSmoothingPlane", 0.0)
            .range(0.0, 0.5)
            .describe("HandlingStickSmoothingPlane = the same for planes. 0 = off.");
    public static final DoubleOption CENTRING_HELI = DoubleOption.of("HandlingStickCentringHeli", 0.0)
            .range(0.0, 10.0)
            .describe("HandlingStickCentringHeli = how fast the helicopter mouse stick returns to the centre when the"
                    + " mouse rests, per second (0 to 10). 0 = off.");
    public static final DoubleOption CENTRING_PLANE = DoubleOption.of("HandlingStickCentringPlane", 0.0)
            .range(0.0, 10.0)
            .describe("HandlingStickCentringPlane = the same for planes. 0 = off.");
    public static final DoubleOption KEY_PITCH_RATE = DoubleOption.of("HandlingKeyPitchRate", 30.0)
            .range(0.0, MAX_KEY_RATE)
            .describe("HandlingKeyPitchRate, HandlingKeyRollRate, HandlingKeyYawRate = turn rate of the stick keys"
                    + " below, degrees per second at full ramp (limited by the vehicle's own rates).");
    public static final DoubleOption KEY_ROLL_RATE = DoubleOption.of("HandlingKeyRollRate", 40.0)
            .range(0.0, MAX_KEY_RATE);
    public static final DoubleOption KEY_YAW_RATE = DoubleOption.of("HandlingKeyYawRate", 20.0)
            .range(0.0, MAX_KEY_RATE);
    public static final DoubleOption KEY_RAMP = DoubleOption.of("HandlingKeyRampTime", 0.3).range(0.0, 2.0)
            .describe("HandlingKeyRampTime = seconds a stick key takes to reach its full rate (0 to 2).");
    public static final IntOption KEY_PITCH_UP = key("KeyStickPitchUp")
            .describe("KeyStickPitchUp ... KeyStickYawRight = keys that move the stick like the mouse does."
                    + " 0 = unbound (default).");
    public static final IntOption KEY_PITCH_DOWN = key("KeyStickPitchDown");
    public static final IntOption KEY_ROLL_LEFT = key("KeyStickRollLeft");
    public static final IntOption KEY_ROLL_RIGHT = key("KeyStickRollRight");
    public static final IntOption KEY_YAW_LEFT = key("KeyStickYawLeft");
    public static final IntOption KEY_YAW_RIGHT = key("KeyStickYawRight");

    /** Every option, in the order the file lists them. */
    public static final List<Option<?>> ALL = List.of(ASSISTED_HELI, AUTO_RUDDER_PLANE, AUTO_RUDDER_GAIN,
            SMOOTHING_HELI, SMOOTHING_PLANE, CENTRING_HELI, CENTRING_PLANE, KEY_PITCH_RATE, KEY_ROLL_RATE,
            KEY_YAW_RATE, KEY_RAMP, KEY_PITCH_UP, KEY_PITCH_DOWN, KEY_ROLL_LEFT, KEY_ROLL_RIGHT, KEY_YAW_LEFT,
            KEY_YAW_RIGHT);

    static HandlingSettings from(ConfigValues v) {
        return new HandlingSettings(v.get(ASSISTED_HELI), v.get(AUTO_RUDDER_PLANE), v.get(AUTO_RUDDER_GAIN),
                v.get(SMOOTHING_HELI), v.get(SMOOTHING_PLANE), v.get(CENTRING_HELI), v.get(CENTRING_PLANE),
                v.get(KEY_PITCH_RATE), v.get(KEY_ROLL_RATE), v.get(KEY_YAW_RATE), v.get(KEY_RAMP),
                v.get(KEY_PITCH_UP), v.get(KEY_PITCH_DOWN), v.get(KEY_ROLL_LEFT), v.get(KEY_ROLL_RIGHT),
                v.get(KEY_YAW_LEFT), v.get(KEY_YAW_RIGHT));
    }

    /** Whether any stick key is bound. */
    public boolean hasStickKeys() {
        return this.keyPitchUp != 0 || this.keyPitchDown != 0 || this.keyRollLeft != 0 || this.keyRollRight != 0
                || this.keyYawLeft != 0 || this.keyYawRight != 0;
    }

    private static IntOption key(String name) {
        return IntOption.of(name, 0);
    }
}
