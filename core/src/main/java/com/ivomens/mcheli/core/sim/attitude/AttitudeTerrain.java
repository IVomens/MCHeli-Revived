package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.port.TerrainProbe;

/**
 * The ground below the vehicle as the attitude models see it. The vehicle only moves once per tick,
 * so one sample serves every attitude step of that tick.
 *
 * @param clearForStick   nothing solid within 2 blocks below; closer to the ground the stick cannot
 *                        turn the airframe, so it does not dig into the ground while taxiing
 * @param airborne        nothing solid within 3 blocks below; flying rather than taxiing
 * @param aboveWaterOrAir the block right below is water or air, not solid ground
 * @param waterDepth      submerged fraction of the hull, 0 to 1
 */
public record AttitudeTerrain(boolean clearForStick, boolean airborne, boolean aboveWaterOrAir, double waterDepth) {

    private static final int COLUMN_SIZE = 3;
    private static final int STICK_CLEARANCE = 2;
    private static final int AIRBORNE_CLEARANCE = 3;

    public static AttitudeTerrain sample(TerrainProbe probe) {
        return new AttitudeTerrain(probe.isClearBelow(COLUMN_SIZE, STICK_CLEARANCE),
                probe.isClearBelow(COLUMN_SIZE, AIRBORNE_CLEARANCE), probe.isAboveWaterOrAir(), probe.waterDepth());
    }

    public boolean afloat() {
        return this.waterDepth > 0.0;
    }
}
