package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Orientation;
import com.ivomens.mcheli.core.sim.math.ZxyRotation;

import java.util.function.UnaryOperator;

/**
 * The stick step shared by helicopters, planes and ground vehicles. The type turns the stick into a
 * {@link Turn}; this composes it with the attitude, lets the type settle the result (self-levelling,
 * key turns), then applies the definition's limits.
 */
public final class StickIntegration {

    private static final float HALF_TURN = 180.0F;
    private static final float FULL_TURN = 360.0F;

    private StickIntegration() {
    }

    /**
     * Which axes the stick may turn this step.
     *
     * @param yaw   heading
     * @param pitch nose up and down
     * @param roll  bank
     */
    public record Freedom(boolean yaw, boolean pitch, boolean roll) {

        /** Settled in and clear of the ground; riding a carrier also holds yaw and roll. */
        public static Freedom of(AttitudeContext context) {
            boolean free = context.settledIn() && context.terrain().clearForStick();
            return new Freedom(free && !context.onCarrier(), free, free && !context.onCarrier());
        }
    }

    /** One step's turn in degrees, in the pilot's frame. */
    public record Turn(float yaw, float pitch, float roll) {

        public static final Turn NONE = new Turn(0.0F, 0.0F, 0.0F);
    }

    /**
     * The legacy axis response: the requested rate is clamped to the axis rate in double, then
     * scaled by the axis factor and the step, in that order.
     */
    public static float axis(float requested, float rateLimit, float factor, float dtSeconds) {
        double limit = rateLimit;
        float rate = (float) LegacyMath.clamp(requested, -limit, limit);
        return rate * (factor * dtSeconds);
    }

    public static <M> AttitudeState<M> integrate(AttitudeState<M> state, Turn turn, RotationLimits limits,
                                                 boolean quaternion, UnaryOperator<AttitudeState<M>> settle) {
        AttitudeState<M> turned = quaternion ? throughOrientation(state, turn, limits, settle)
                : throughEuler(state, turn, limits, settle);
        return turned.withAttitude(turned.attitude().withRoll(wrapRoll(turned.attitude().roll())));
    }

    /** Roll stays within +/-180; one wrap is enough since a step never turns a full circle. */
    static float wrapRoll(float roll) {
        float wrapped = roll;
        if (wrapped > HALF_TURN) {
            wrapped -= FULL_TURN;
        }
        if (wrapped < -HALF_TURN) {
            wrapped += FULL_TURN;
        }
        return wrapped;
    }

    /**
     * Quaternion mode: the turn rotates the kept orientation in the body frame, so the airframe can
     * loop without an Euler clamp. Settling still works in angles; whatever it changed is folded back
     * in as a small body-frame turn.
     */
    private static <M> AttitudeState<M> throughOrientation(AttitudeState<M> state, Turn turn, RotationLimits limits,
                                                           UnaryOperator<AttitudeState<M>> settle) {
        Orientation start = state.orientation() != null ? state.orientation() : Orientation.of(state.attitude());
        Orientation orientation = start.turnedBy(Orientation.of(turn.yaw(), turn.pitch(), turn.roll()));
        Attitude turned = orientation.toAttitude();
        AttitudeState<M> settled = settle.apply(new AttitudeState<>(turned, orientation, state.memory()));
        Attitude attitude = settled.attitude();

        float settleYaw = LegacyMath.wrapDegrees(attitude.yaw() - turned.yaw());
        float settlePitch = LegacyMath.wrapDegrees(attitude.pitch() - turned.pitch());
        float settleRoll = LegacyMath.wrapDegrees(attitude.roll() - turned.roll());
        if (settlePitch != 0.0F || settleYaw != 0.0F || settleRoll != 0.0F) {
            orientation = orientation.turnedBy(Orientation.of(settleYaw, settlePitch, settleRoll));
            attitude = orientation.toAttitude();
        }

        // definition limits stay clear of +/-90 pitch, so clamping the angles and rebuilding the
        // quaternion from them is safe; aerobatic definitions leave the limits off
        if (limits.enabled()) {
            Attitude limited = limits.apply(attitude);
            // a float comparison, so a limit that only flips the sign of a zero changes nothing
            if (limited.pitch() != attitude.pitch() || limited.roll() != attitude.roll()) {
                attitude = limited;
                orientation = Orientation.of(limited);
            }
        }
        return new AttitudeState<>(attitude, orientation, settled.memory());
    }

    /**
     * Euler mode: the turn is composed in front of the attitude in Z, X, Y order, which reads the
     * angles back with the legacy fallback at +/-90 pitch. The limits apply before and after settling.
     */
    private static <M> AttitudeState<M> throughEuler(AttitudeState<M> state, Turn turn, RotationLimits limits,
                                                     UnaryOperator<AttitudeState<M>> settle) {
        Attitude composed = ZxyRotation.turn(state.attitude(), turn.yaw(), turn.pitch(), turn.roll());
        Attitude limited = limits.apply(composed);
        Attitude turned = limited.withRoll(wrapRoll(limited.roll()));
        AttitudeState<M> settled = settle.apply(state.withAttitude(turned));
        return settled.withAttitude(limits.apply(settled.attitude()));
    }
}
