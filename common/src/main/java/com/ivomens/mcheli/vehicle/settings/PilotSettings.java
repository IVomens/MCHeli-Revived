package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.api.vehicle.VehicleKind;

/**
 * Settings from the pilot's own client that change how the vehicle behaves: whether leaving takes
 * the rest of the crew out too, and whether the throttle winds down by itself. The pilot's client
 * sends its {@link ClientPreferences#pilot()} when boarding; until then the defaults apply.
 */
public record PilotSettings(boolean dismountAll, boolean heliAutoThrottleDown, boolean planeAutoThrottleDown,
                            boolean tankAutoThrottleDown) {

    public static final PilotSettings DEFAULTS = new PilotSettings(false, true, false, false);

    /** Ships share the planes' setting, ground vehicles never wind down. */
    public boolean autoThrottleDown(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> this.heliAutoThrottleDown;
            case PLANE, SHIP -> this.planeAutoThrottleDown;
            case TANK -> this.tankAutoThrottleDown;
            case VEHICLE -> false;
        };
    }
}
