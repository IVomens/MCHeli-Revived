package com.ivomens.mcheli.api.vehicle.extension;

/**
 * The pilot's movement keys and the engine throttle of a vehicle, read-only. The keys are the vehicle's
 * throttle-up, throttle-down and the two side keys, which most vehicles use to drive forward, back and sideways.
 *
 * <p><b>Side and thread.</b> On the server, the keys the pilot's client last sent. On the pilot's own client, the
 * keys held right now; on other clients, what the server mirrors to them. On the vehicle level's thread.
 * Stability: since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface VehicleControls {

    /** Throttle up: forward, for a vehicle driven by keys. */
    boolean forward();

    /** Throttle down: back, for a vehicle driven by keys. */
    boolean back();

    /** The left key, and not the right one. */
    boolean left();

    /** The right key, and not the left one. */
    boolean right();

    /** The engine's throttle, from 0 (idle) to 1 (full). */
    double throttle();
}
