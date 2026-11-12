package com.ivomens.mcheli.content.def.vehicle;

/**
 * YAML {@code PhysicalProperties}: how the vehicle moves. Values are as written; {@link VehicleRules} turns
 * {@code floatOffset} and {@code onGroundPitch} into the signs the game uses.
 *
 * @param rotationSnap   how hard the autopilot and a target drone snap back to level, from -5 to 5
 * @param onGroundPitch  the nose-up pitch while standing on the ground, in degrees
 * @param floatOffset    how deep the vehicle floats
 * @param canRotOnGround the vehicle can turn while standing
 */
public record Physics(float speed, boolean canFloat, float floatOffset, float motionFactor, float gravity,
                      float rotationSnap, float gravityInWater, float stepHeight, boolean canRotOnGround,
                      boolean canMoveOnGround, float onGroundPitch, float pivotTurnThrottle, Mobility mobility,
                      GroundTilt groundTilt, BodySize bodySize, RotationLimits rotationLimits) {

    /** YAML {@code Mobility}: turn rates, as factors. */
    public record Mobility(float yaw, float pitch, float roll, float yawOnGround) {
    }

    /** YAML {@code GroundPitchFactors}: how much the vehicle tilts with the ground under it. */
    public record GroundTilt(float pitch, float roll) {
    }

    /** YAML {@code BodySize}: the collision box. */
    public record BodySize(float height, float width) {
    }

    /**
     * YAML {@code RotationLimits}: limits on pitch and roll, in degrees, applied when the section is there.
     *
     * @param limited the vehicle limits its pitch and roll at all
     */
    public record RotationLimits(boolean limited, float minPitch, float maxPitch, float minRoll, float maxRoll) {
    }
}
