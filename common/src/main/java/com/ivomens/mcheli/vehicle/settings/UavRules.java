package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.config.wingman.WingmanSettings;

/**
 * How far a UAV station reaches, from the wingman settings.
 *
 * @param controllerRange the control range of a UAV whose definition names none, in blocks
 */
public record UavRules(int controllerRange) {

    /** At or above this range (blocks) the control range is treated as unlimited. */
    public static final int UNLIMITED_THRESHOLD = WingmanSettings.UAV_UNLIMITED_THRESHOLD;

    /** Whether a range means the UAV can be flown at any distance. */
    public static boolean isUnlimited(int range) {
        return range < 0 || range >= UNLIMITED_THRESHOLD;
    }
}
