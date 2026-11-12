package com.ivomens.mcheli.vehicle.settings;

/**
 * Settings that belong to this client's player rather than to the server: what the pilot's client
 * sends when boarding, and how this client turns the airframe. On a dedicated server they are the
 * server's own defaults and nothing reads them; the server learns a pilot's choices from the
 * {@code ClientSettingsMessage} the pilot's client sends.
 *
 * @param pilot             the pilot settings this client sends when it boards
 * @param shaderSupport     whether this client renders shaders, so the camera may use shader views
 * @param fixedStepAttitude the attitude is integrated at fixed 1/60 s substeps instead of once a frame
 * @param assists           the handling assists this client applies to its pilot's input
 */
public record ClientPreferences(PilotSettings pilot, boolean shaderSupport, boolean fixedStepAttitude,
                                AssistSettings assists) {
}
