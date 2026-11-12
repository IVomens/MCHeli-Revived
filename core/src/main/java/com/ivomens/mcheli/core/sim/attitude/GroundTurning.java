package com.ivomens.mcheli.core.sim.attitude;

/**
 * How well a vehicle turns while its wheels or hull touch the ground.
 *
 * @param mobility        the definition's {@code mobilityYawOnGround}: turn rate on the ground
 *                        relative to the air
 * @param turnsOnSolidGround the definition's {@code canRotOnGround}; without it the vehicle only
 *                        turns on water or in the air
 */
public record GroundTurning(float mobility, boolean turnsOnSolidGround) {

    /** The factor for key and steering turns: 1 in the air, the ground mobility otherwise. */
    public float factor(boolean airborne, boolean aboveWaterOrAir) {
        if (airborne) {
            return 1.0F;
        }
        return !this.turnsOnSolidGround && !aboveWaterOrAir ? 0.0F : this.mobility;
    }
}
