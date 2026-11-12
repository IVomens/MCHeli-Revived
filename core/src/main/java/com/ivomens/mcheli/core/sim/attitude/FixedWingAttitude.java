package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Freedom;
import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Turn;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.model.GroundSettle;

/**
 * Plane, ship and VTOL attitude. In the air the stick yaws, pitches and rolls and the side keys feed
 * a roll impulse; on the ground, afloat, in free look or in gunner mode the side keys yaw instead. A
 * deflected nozzle levels the airframe, which is how a VTOL hovers level.
 * <p>
 * In flight sim mode the mouse rolls and the side keys yaw through the same roll impulse.
 */
public final class FixedWingAttitude implements AttitudeModel<FixedWingAttitude.Setup, FixedWingAttitude.Memory> {

    /** Every stick axis of a plane answers at 80 % of the definition's mobility. */
    private static final float STICK_FACTOR = 0.8F;
    /** Outside flight sim mode, half the stick roll; VTOL modes roll at full stick. */
    private static final float WING_ROLL_SHARE = 0.5F;
    private static final float FLIGHT_SIM_ROLL_SHARE = 2.0F;
    /** In flight sim mode the roll impulse is read as a yaw rate, scaled by this. */
    private static final float FLIGHT_SIM_YAW_PER_IMPULSE = 20.0F;
    /** Roll impulse added per tick while a side key is held, per second of step. */
    private static final float KEY_ROLL_RATE = 4.0F;
    private static final float KEY_ROLL_PER_IMPULSE = 0.5F;
    private static final double KEY_ROLL_KEEP_PER_TICK = 0.9;
    private static final float KEY_YAW_RATE = 12.0F;
    private static final double GUNNER_LEVELLING_PER_TICK = 0.95;
    private static final float GUNNER_TURN_PER_AUTOPILOT = 4.0F;
    private static final float GUNNER_ROLL_LIMIT = 20.0F;
    private static final float GROUND_SETTLE_LIMIT = 40.0F;
    private static final float GROUND_SETTLE = 0.97F;
    private static final float NOZZLE_DEFLECTED = 0.001F;
    private static final double NOZZLE_PITCH_KEEP_PER_TICK = 0.97;
    private static final double NOZZLE_ROLL_KEEP_PER_TICK = 0.9;

    /**
     * What a plane reads for one step.
     *
     * @param stick         stick rates and the definition's limits
     * @param onGroundPitch the pitch the airframe rests at on its gear
     * @param groundTurning how the side keys yaw on the ground
     * @param mobilityRoll  the definition's roll mobility, which scales the key roll
     * @param autoPilotRot  the gentle turn gunner mode flies, degrees per tick divided by 4
     * @param floats        whether the hull floats, so the side keys yaw it on water
     * @param vtol          the nozzle state
     * @param gunnerMode    the autopilot flies while the pilot aims
     * @param hovering      hover or gunner mode, which hold the stick
     */
    public record Setup(StickHandling stick, float onGroundPitch, GroundTurning groundTurning, float mobilityRoll,
                        float autoPilotRot, boolean floats, Vtol vtol, boolean gunnerMode, boolean hovering) {
    }

    /**
     * @param mode        0 horizontal, 1 swivelling, 2 vertical
     * @param yawFactor   the definition's {@code vtolYaw}: yaw and roll rate in a VTOL mode
     * @param pitchFactor the definition's {@code vtolPitch}
     * @param nozzleAngle current nozzle angle, degrees
     */
    public record Vtol(int mode, float yawFactor, float pitchFactor, float nozzleAngle) {

        public static final Vtol NONE = new Vtol(0, 0.0F, 0.0F, 0.0F);

        boolean active() {
            return this.mode > 0;
        }
    }

    /**
     * @param keyRoll        the roll impulse the side keys build up; it decays by itself
     * @param keyRollApplied whether this tick's key input is in. The keys add once per game tick, not
     *                       per step, a kept quirk: at fixed substeps the keys roll as they did at
     *                       60 frames per second
     */
    public record Memory(float keyRoll, boolean keyRollApplied) {
    }

    @Override
    public Memory initialMemory() {
        return new Memory(0.0F, false);
    }

    @Override
    public Memory startTick(Memory memory) {
        return new Memory(memory.keyRoll(), false);
    }

    @Override
    public AttitudeState<Memory> integrate(AttitudeState<Memory> state, AttitudeInput mouse, ControlInput keys,
                                           Setup setup, AttitudeContext context, float dtSeconds) {
        boolean flightSim = context.settings().flightSimMode();
        Freedom free = Freedom.of(context);
        boolean stickFree = !setup.hovering();
        StickHandling stick = setup.stick();
        Vtol vtol = setup.vtol();
        Memory memory = state.memory();

        float yaw = 0.0F;
        if (free.yaw() && stickFree) {
            float requested = mouse.effectiveX();
            if (flightSim) {
                // the legacy code fed a whole second as the step here, so one tick of keys adds a full impulse
                memory = addKeyRoll(memory, keys, 1.0F, true, vtol);
                requested = memory.keyRoll() * FLIGHT_SIM_YAW_PER_IMPULSE;
            }
            yaw = StickIntegration.axis(mouse.yawRequest(requested), stick.yawRate(), yawFactor(vtol), dtSeconds);
        }
        float pitch = free.pitch() && stickFree
                ? StickIntegration.axis(mouse.effectiveY(), stick.pitchRate(), -pitchFactor(vtol), dtSeconds) : 0.0F;
        float roll = free.roll() && stickFree
                ? StickIntegration.axis(stickRoll(mouse.effectiveX(), flightSim, vtol), stick.rollRate(), rollFactor(vtol),
                dtSeconds) : 0.0F;

        return StickIntegration.integrate(state.withMemory(memory), new Turn(yaw, pitch, roll), stick.limits(),
                context.settings().quaternionAttitude(),
                turned -> settle(turned, mouse, keys, setup, context, dtSeconds));
    }

    @Override
    public float maxTurnRate(Setup setup) {
        float keyRoll = 2.0F * KEY_ROLL_PER_IMPULSE * PerTick.TICKS_PER_SECOND * Math.abs(setup.mobilityRoll());
        float gunnerTurn = Math.abs(setup.autoPilotRot()) * GUNNER_TURN_PER_AUTOPILOT;
        return setup.stick().fullStickRate() + KEY_YAW_RATE + keyRoll + gunnerTurn;
    }

    private static float yawFactor(Vtol vtol) {
        return (vtol.active() ? vtol.yawFactor() : 1.0F) * STICK_FACTOR;
    }

    private static float pitchFactor(Vtol vtol) {
        return (vtol.active() ? vtol.pitchFactor() : 1.0F) * STICK_FACTOR;
    }

    /** VTOL roll uses the yaw factor on purpose: definitions have no separate VTOL roll value. */
    private static float rollFactor(Vtol vtol) {
        return (vtol.active() ? vtol.yawFactor() : 1.0F) * STICK_FACTOR;
    }

    private static float stickRoll(float stickX, boolean flightSim, Vtol vtol) {
        if (flightSim) {
            return stickX * FLIGHT_SIM_ROLL_SHARE;
        }
        return vtol.mode() == 0 ? stickX * WING_ROLL_SHARE : stickX;
    }

    /** Adds this tick's key roll once; hovering on the nozzles the keys do nothing outside flight sim mode. */
    private static Memory addKeyRoll(Memory memory, ControlInput keys, float dtSeconds, boolean flightSim, Vtol vtol) {
        if (memory.keyRollApplied()) {
            return memory;
        }
        float rate = !flightSim && vtol.mode() != 0 ? 0.0F : KEY_ROLL_RATE;
        return new Memory(KeyTurn.apply(memory.keyRoll(), keys, rate * dtSeconds), true);
    }

    private static AttitudeState<Memory> settle(AttitudeState<Memory> state, AttitudeInput mouse, ControlInput keys,
                                                Setup setup, AttitudeContext context, float dtSeconds) {
        if (context.wrecked()) {
            return state;
        }
        Attitude attitude = setup.gunnerMode() ? gunnerAutopilot(state.attitude(), setup, dtSeconds) : state.attitude();
        Memory memory = state.memory();
        AttitudeTerrain terrain = context.terrain();
        boolean airborne = terrain.airborne();
        // in free look the mouse only moves the head, so the side keys yaw the airframe instead
        if (!airborne || mouse.freeLook() || setup.gunnerMode() || setup.floats() && terrain.afloat()) {
            float mobility = setup.groundTurning().factor(airborne, terrain.aboveWaterOrAir());
            attitude = attitude.withYaw(KeyTurn.apply(attitude.yaw(), keys, KEY_YAW_RATE * mobility * dtSeconds));
        } else if (!context.settings().flightSimMode()) {
            memory = addKeyRoll(memory, keys, dtSeconds, false, setup.vtol());
            attitude = attitude.withRoll(attitude.roll() + memory.keyRoll() * KEY_ROLL_PER_IMPULSE * setup.mobilityRoll());
        }
        // the impulse fades per step, so a frame-rate client used to lose it faster than it gained it
        float keyRoll = (float) (memory.keyRoll() * Math.pow(KEY_ROLL_KEEP_PER_TICK, dtSeconds * PerTick.TICKS_PER_SECOND));
        memory = new Memory(keyRoll, memory.keyRollApplied());

        if (!airborne && Math.abs(attitude.pitch()) < GROUND_SETTLE_LIMIT) {
            attitude = GroundSettle.level(attitude, setup.onGroundPitch(), GROUND_SETTLE);
        }
        if (setup.vtol().nozzleAngle() > NOZZLE_DEFLECTED) {
            attitude = new Attitude(attitude.yaw(),
                    attitude.pitch() * PerTick.decay(NOZZLE_PITCH_KEEP_PER_TICK, dtSeconds),
                    attitude.roll() * PerTick.decay(NOZZLE_ROLL_KEEP_PER_TICK, dtSeconds));
        }
        return new AttitudeState<>(attitude, state.orientation(), memory);
    }

    /** Gunner mode levels the nose, eases a steep bank and flies a slow turn. */
    private static Attitude gunnerAutopilot(Attitude attitude, Setup setup, float dtSeconds) {
        float keep = PerTick.decay(GUNNER_LEVELLING_PER_TICK, dtSeconds);
        float roll = attitude.roll();
        if (Math.abs(roll) > GUNNER_ROLL_LIMIT) {
            roll *= keep;
        }
        return new Attitude(attitude.yaw() + setup.autoPilotRot() * GUNNER_TURN_PER_AUTOPILOT * dtSeconds,
                attitude.pitch() * keep, roll);
    }
}
