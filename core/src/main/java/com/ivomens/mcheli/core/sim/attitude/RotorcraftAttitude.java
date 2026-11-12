package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Freedom;
import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Turn;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.model.GroundSettle;
import com.ivomens.mcheli.core.sim.port.SimSettings.TiltLimit;

/**
 * Helicopter attitude. The stick yaws and rolls together on its x axis and pitches on its y axis; the
 * side keys roll. In the air the airframe levels its roll by itself, faster while hovering; on the
 * ground it settles onto its gear, and with folded blades the side keys steer it.
 */
public final class RotorcraftAttitude implements AttitudeModel<RotorcraftAttitude.Setup, RotorcraftAttitude.Memory> {

    private static final float KEY_ROLL_RATE = 24.0F;
    private static final float FOLDED_TAXI_YAW_RATE = 10.0F;
    /** Share of the roll lost per tick in forward flight, and while hovering. */
    private static final float FLIGHT_LEVELLING = 0.04F;
    private static final float HOVER_LEVELLING = 0.07F;
    /** Rolled past this the helicopter is going over, not banking, and is left alone. */
    private static final float LEVELLING_LIMIT = 65.0F;
    private static final double LEVELLING_DEADBAND = 0.1;
    private static final float GROUND_SETTLE_LIMIT = 40.0F;
    private static final float GROUND_SETTLE = 0.97F;
    /** How fast pitch and roll are eased back past the server's tilt limit, per second and degree. */
    private static final float TILT_RETURN_NOSE_DOWN = 2.0F;
    private static final float TILT_RETURN_NOSE_UP = 4.0F;
    private static final float TILT_RETURN_ROLL = 0.6F;
    private static final float ROLL_FACTOR = 1.0F;

    /**
     * What a helicopter reads for one step.
     *
     * @param stick         stick rates and the definition's limits
     * @param onGroundPitch the pitch the airframe rests at on its gear
     * @param hovering      hover mode (or gunner mode) holds pitch and roll
     * @param bladesFolded  the blades are folded, so the side keys steer on the ground
     */
    public record Setup(StickHandling stick, float onGroundPitch, boolean hovering, boolean bladesFolded) {
    }

    /**
     * @param rollFactor the stick roll factor of the previous step. The stick roll uses the average of
     *                   it and the current factor, a smoothing for a factor that never changes: all it
     *                   does is halve the very first stick roll, since the memory starts at zero.
     */
    public record Memory(float rollFactor) {
    }

    @Override
    public Memory initialMemory() {
        return new Memory(0.0F);
    }

    @Override
    public AttitudeState<Memory> integrate(AttitudeState<Memory> state, AttitudeInput mouse, ControlInput keys,
                                           Setup setup, AttitudeContext context, float dtSeconds) {
        Freedom free = Freedom.of(context);
        StickHandling stick = setup.stick();
        boolean tiltFree = !setup.hovering();
        float yaw = free.yaw()
                ? StickIntegration.axis(mouse.yawRequest(mouse.effectiveX()), stick.yawRate(), 1.0F, dtSeconds) : 0.0F;
        float pitch = free.pitch() && tiltFree
                ? StickIntegration.axis(mouse.effectiveY(), stick.pitchRate(), -1.0F, dtSeconds) : 0.0F;
        float roll = 0.0F;
        Memory memory = state.memory();
        if (free.roll() && tiltFree) {
            float factor = (ROLL_FACTOR + memory.rollFactor()) / 2.0F;
            roll = StickIntegration.axis(mouse.effectiveX(), stick.rollRate(), factor, dtSeconds);
            memory = new Memory(ROLL_FACTOR);
        }
        return StickIntegration.integrate(state.withMemory(memory), new Turn(yaw, pitch, roll), stick.limits(),
                context.settings().quaternionAttitude(),
                turned -> turned.withAttitude(settle(turned.attitude(), keys, setup, context, dtSeconds)));
    }

    @Override
    public float maxTurnRate(Setup setup) {
        return setup.stick().fullStickRate() + KEY_ROLL_RATE + FOLDED_TAXI_YAW_RATE;
    }

    private static Attitude settle(Attitude attitude, ControlInput keys, Setup setup, AttitudeContext context,
                                   float dtSeconds) {
        if (context.wrecked()) {
            return attitude;
        }
        TiltLimit tiltLimit = context.settings().tiltLimit();
        Attitude settled = tiltLimit.enabled() ? easeIntoTiltLimit(attitude, tiltLimit, dtSeconds) : attitude;
        settled = levelRoll(settled, setup.hovering(), dtSeconds);
        if (context.terrain().airborne()) {
            return settled.withRoll(KeyTurn.apply(settled.roll(), keys, KEY_ROLL_RATE * dtSeconds));
        }
        if (Math.abs(settled.pitch()) < GROUND_SETTLE_LIMIT) {
            settled = GroundSettle.level(settled, setup.onGroundPitch(), GROUND_SETTLE);
        }
        if (setup.bladesFolded()) {
            settled = settled.withYaw(KeyTurn.apply(settled.yaw(), keys, FOLDED_TAXI_YAW_RATE * dtSeconds));
        }
        return settled;
    }

    private static Attitude levelRoll(Attitude attitude, boolean hovering, float dtSeconds) {
        float keep = PerTick.decay(1.0F - (hovering ? HOVER_LEVELLING : FLIGHT_LEVELLING), dtSeconds);
        float roll = attitude.roll();
        if (roll > LEVELLING_DEADBAND && roll < LEVELLING_LIMIT) {
            roll *= keep;
        }
        if (roll < -LEVELLING_DEADBAND && roll > -LEVELLING_LIMIT) {
            roll *= keep;
        }
        return attitude.withRoll(roll);
    }

    private static Attitude easeIntoTiltLimit(Attitude attitude, TiltLimit limit, float dtSeconds) {
        float pitch = attitude.pitch();
        if (pitch > limit.pitchMax()) {
            pitch -= Math.abs((pitch - limit.pitchMax()) * TILT_RETURN_NOSE_DOWN * dtSeconds);
        }
        if (pitch < limit.pitchMin()) {
            pitch += Math.abs((pitch - limit.pitchMin()) * TILT_RETURN_NOSE_UP * dtSeconds);
        }
        float roll = attitude.roll();
        if (roll > limit.roll()) {
            roll -= Math.abs((roll - limit.roll()) * TILT_RETURN_ROLL * dtSeconds);
        }
        if (roll < -limit.roll()) {
            roll += Math.abs((roll + limit.roll()) * TILT_RETURN_ROLL * dtSeconds);
        }
        return new Attitude(attitude.yaw(), pitch, roll);
    }
}
