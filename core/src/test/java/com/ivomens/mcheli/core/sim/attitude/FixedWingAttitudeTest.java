package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.EULER;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEFT;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEVEL;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.NO_KEYS;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.RIGHT;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.STICK;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.flying;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.grounded;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.runTicks;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.stick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWingAttitudeTest {

    private static final FixedWingAttitude MODEL = new FixedWingAttitude();
    private static final GroundTurning TURNS_ON_GROUND = new GroundTurning(1.0F, true);

    private static FixedWingAttitude.Setup plane(FixedWingAttitude.Vtol vtol) {
        return new FixedWingAttitude.Setup(STICK, 0.0F, TURNS_ON_GROUND, 1.0F, -0.6F, false, vtol, false, false);
    }

    private static AttitudeState<FixedWingAttitude.Memory> state(Attitude attitude) {
        return new AttitudeState<>(attitude, null, MODEL.initialMemory());
    }

    @Test
    void stickRollsAtHalfRateOnWings() {
        // 20 units right for a 0.05 s step: half of it, times the plane's 0.8 stick factor
        AttitudeState<FixedWingAttitude.Memory> after = MODEL.integrate(state(LEVEL), stick(20.0F, 0.0F), NO_KEYS,
                plane(FixedWingAttitude.Vtol.NONE), flying(EULER), 0.05F);
        assertEquals(20.0F * 0.5F * 0.8F * 0.05F, after.attitude().roll(), 0.01F);
    }

    @Test
    void sideKeysYawOnTheGround() {
        AttitudeState<FixedWingAttitude.Memory> after = MODEL.integrate(state(LEVEL), AttitudeInput.CENTRED, RIGHT,
                plane(FixedWingAttitude.Vtol.NONE), grounded(), 0.05F);
        assertEquals(12.0F * 0.05F, after.attitude().yaw(), 1.0e-4F);
    }

    @Test
    void sideKeysBuildARollImpulseInTheAir() {
        AttitudeState<FixedWingAttitude.Memory> after = runTicks(MODEL, state(LEVEL), AttitudeInput.CENTRED, LEFT,
                plane(FixedWingAttitude.Vtol.NONE), flying(EULER), 10, 1);
        assertTrue(after.attitude().roll() < -0.5F, "roll " + after.attitude().roll());
        assertTrue(after.memory().keyRoll() < 0.0F);
    }

    @Test
    void keyRollIsAddedOncePerTick() {
        // the impulse counts per tick, so three substeps add a third of what one 0.05 s step adds
        FixedWingAttitude.Setup setup = plane(FixedWingAttitude.Vtol.NONE);
        float oneStep = runTicks(MODEL, state(LEVEL), AttitudeInput.CENTRED, LEFT, setup, flying(EULER), 1, 1)
                .memory().keyRoll();
        float threeSubsteps = runTicks(MODEL, state(LEVEL), AttitudeInput.CENTRED, LEFT, setup, flying(EULER), 1, 3)
                .memory().keyRoll();
        assertEquals(oneStep / 3.0F, threeSubsteps, 0.01F);
    }

    @Test
    void aDeflectedNozzleLevelsTheAirframe() {
        FixedWingAttitude.Setup hovering = plane(new FixedWingAttitude.Vtol(2, 0.3F, 0.2F, 90.0F));
        AttitudeState<FixedWingAttitude.Memory> after = runTicks(MODEL, state(new Attitude(0.0F, 10.0F, 10.0F)),
                AttitudeInput.CENTRED, NO_KEYS, hovering, flying(EULER), 20, 1);
        // a second keeps 0.97^20 of the pitch and 0.9^20 of the roll
        assertEquals(10.0F * (float) Math.pow(0.97, 20), after.attitude().pitch(), 0.01F);
        assertEquals(10.0F * (float) Math.pow(0.9, 20), after.attitude().roll(), 0.01F);
    }

    @Test
    void gunnerModeFliesASlowTurn() {
        FixedWingAttitude.Setup gunner = new FixedWingAttitude.Setup(STICK, 0.0F, TURNS_ON_GROUND, 1.0F, -0.6F, false,
                FixedWingAttitude.Vtol.NONE, true, true);
        AttitudeState<FixedWingAttitude.Memory> after = runTicks(MODEL, state(LEVEL), stick(40.0F, 40.0F), NO_KEYS,
                gunner, flying(EULER), 20, 1);
        // the stick is held, the autopilot turns 4 times autoPilotRot degrees per second
        assertEquals(-0.6F * 4.0F, after.attitude().yaw(), 0.01F);
    }
}
