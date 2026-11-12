package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.port.SimSettings;

/**
 * Everything an attitude step reads besides its vehicle type's own setup.
 *
 * @param terrain   the ground below
 * @param settings  config and server settings
 * @param onCarrier the vehicle rides another entity (a lift, a minecart), which holds its yaw and roll
 * @param settledIn the vehicle has existed long enough for the stick to work, so a fresh spawn does
 *                  not twitch while it lands on its wheels
 * @param wrecked   destroyed vehicles neither level themselves nor steer
 */
public record AttitudeContext(AttitudeTerrain terrain, SimSettings settings, boolean onCarrier, boolean settledIn,
                              boolean wrecked) {
}
