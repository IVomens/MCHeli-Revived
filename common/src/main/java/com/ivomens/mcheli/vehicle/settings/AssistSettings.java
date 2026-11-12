package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.config.HandlingSettings;

/**
 * Which opt-in handling assists the pilot's client applies to the pilot's input (docs/CONTROLS.md).
 * Client side: the server sees an ordinary pilot.
 *
 * @param assistedHeli    helicopters level themselves and hold their altitude
 * @param autoRudderPlane planes in the air get rudder towards their bank
 * @param autoRudderGain  rudder at a 90 degree bank, as a share of full stick
 */
public record AssistSettings(boolean assistedHeli, boolean autoRudderPlane, double autoRudderGain) {

    /** The assists the handling section switches on. */
    public static AssistSettings of(HandlingSettings handling) {
        return new AssistSettings(handling.assistedHeli(), handling.autoRudderPlane(), handling.autoRudderGain());
    }
}
