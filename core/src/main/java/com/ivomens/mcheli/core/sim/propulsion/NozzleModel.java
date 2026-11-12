package com.ivomens.mcheli.core.sim.propulsion;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;

/** Where a fixed-wing engine pushes: along the nose, or down the VTOL nozzles. */
public final class NozzleModel {

    /** Above this angle the thrust counts as vectored. */
    private static final float DEFLECTED = 0.001F;
    /**
     * Below this angle thrust lifts at half strength, above it at an eighth. It is ten times
     * {@link #DEFLECTED}, so between the two the thrust is vectored but still lifts at half.
     */
    private static final float LIFT_SPLIT = 0.01F;
    private static final float VERTICAL = 90.0F;
    /** Unvectored thrust points this far above the nose, which is the only lift a plane has. */
    private static final float THRUST_ABOVE_NOSE = 10.0F;
    /** While vectoring the nose drops by this factor every tick. */
    private static final float VECTORING_NOSE_DROP = 0.95F;
    /** Fully vertical nozzles keep only this much of their horizontal push. */
    private static final float VERTICAL_HORIZONTAL_SHARE = 0.8F;

    private NozzleModel() {
    }

    /**
     * The thrust direction and the attitude it leaves the airframe in.
     *
     * @param direction unit thrust direction, shortened sideways when the nozzles point straight down
     * @param attitude  the attitude after vectoring pitched the nose down
     */
    public record Thrust(Vec3d direction, Attitude attitude) {
    }

    /**
     * Vectored thrust also pitches the nose down 5%. The legacy code did that inside a getter, which
     * ran once per tick; this is that same single nudge, made explicit and applied before the thrust
     * direction is taken from the new pitch.
     */
    public static Thrust thrust(Attitude attitude, float nozzleAngle) {
        if (nozzleAngle <= DEFLECTED) {
            return new Thrust(LegacyMath.lookVector(attitude.yaw(), attitude.pitch() - THRUST_ABOVE_NOSE), attitude);
        }
        Attitude noseDown = attitude.withPitch(attitude.pitch() * VECTORING_NOSE_DROP);
        Vec3d direction = LegacyMath.lookVector(noseDown.yaw(), noseDown.pitch() - nozzleAngle);
        if (nozzleAngle >= VERTICAL) {
            direction = new Vec3d(direction.x() * VERTICAL_HORIZONTAL_SHARE, direction.y(),
                    direction.z() * VERTICAL_HORIZONTAL_SHARE);
        }
        return new Thrust(direction, noseDown);
    }

    /** Divides the vertical thrust component; vectored nozzles lift far less per unit of thrust. */
    public static double liftDivisor(float nozzleAngle) {
        return nozzleAngle <= LIFT_SPLIT ? 2.0 : 8.0;
    }
}
