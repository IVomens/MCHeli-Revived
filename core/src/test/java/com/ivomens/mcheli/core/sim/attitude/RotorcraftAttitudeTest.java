package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.EULER;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEFT;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEVEL;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.NO_KEYS;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.QUATERNION;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.STICK;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.flying;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.grounded;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.run;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.stick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotorcraftAttitudeTest {

    private static final RotorcraftAttitude MODEL = new RotorcraftAttitude();
    private static final RotorcraftAttitude.Setup FLYING = new RotorcraftAttitude.Setup(STICK, 0.0F, false, false);
    private static final RotorcraftAttitude.Setup HOVERING = new RotorcraftAttitude.Setup(STICK, 0.0F, true, false);

    private static AttitudeState<RotorcraftAttitude.Memory> state(Attitude attitude, float rollFactor) {
        return new AttitudeState<>(attitude, null, new RotorcraftAttitude.Memory(rollFactor));
    }

    @Test
    void rollLevelsOutByItselfInFlight() {
        AttitudeState<RotorcraftAttitude.Memory> banked = state(new Attitude(0.0F, 0.0F, 30.0F), 1.0F);
        AttitudeState<RotorcraftAttitude.Memory> after = run(MODEL, banked, AttitudeInput.CENTRED, NO_KEYS, FLYING,
                flying(EULER), 0.05F, 100);
        assertTrue(Math.abs(after.attitude().roll()) < 1.0F, "roll " + after.attitude().roll());
    }

    @Test
    void hoveringLevelsFasterThanFlying() {
        AttitudeState<RotorcraftAttitude.Memory> banked = state(new Attitude(0.0F, 0.0F, 30.0F), 1.0F);
        float flyingRoll = run(MODEL, banked, AttitudeInput.CENTRED, NO_KEYS, FLYING, flying(EULER), 0.05F, 10)
                .attitude().roll();
        float hoveringRoll = run(MODEL, banked, AttitudeInput.CENTRED, NO_KEYS, HOVERING, flying(EULER), 0.05F, 10)
                .attitude().roll();
        assertTrue(hoveringRoll < flyingRoll, hoveringRoll + " vs " + flyingRoll);
    }

    @Test
    void stickRaisesTheNoseAtItsRate() {
        // stick 10 up for one 0.05 s step: 10 degrees per second, half a degree nose up
        AttitudeState<RotorcraftAttitude.Memory> after = MODEL.integrate(state(LEVEL, 1.0F), stick(0.0F, 10.0F),
                NO_KEYS, FLYING, flying(EULER), 0.05F);
        assertEquals(-0.5F, after.attitude().pitch(), 1.0e-4F);
        assertEquals(0.0F, after.attitude().roll(), 1.0e-4F);
    }

    @Test
    void stickRateStopsAtTheDefinitionsRate() {
        // the stick asks for 100 degrees per second, the definition allows 40
        AttitudeState<RotorcraftAttitude.Memory> after = MODEL.integrate(state(LEVEL, 1.0F), stick(0.0F, 100.0F),
                NO_KEYS, FLYING, flying(EULER), 0.05F);
        assertEquals(-2.0F, after.attitude().pitch(), 1.0e-4F);
    }

    @Test
    void theVeryFirstStickRollIsHalved() {
        float first = MODEL.integrate(state(LEVEL, 0.0F), stick(10.0F, 0.0F), NO_KEYS, FLYING, flying(EULER), 0.05F)
                .attitude().roll();
        float later = MODEL.integrate(state(LEVEL, 1.0F), stick(10.0F, 0.0F), NO_KEYS, FLYING, flying(EULER), 0.05F)
                .attitude().roll();
        assertEquals(later / 2.0F, first, 1.0e-3F);
    }

    @Test
    void sideKeysRollInTheAir() {
        AttitudeState<RotorcraftAttitude.Memory> after = MODEL.integrate(state(LEVEL, 1.0F), AttitudeInput.CENTRED,
                LEFT, FLYING, flying(EULER), 0.05F);
        assertEquals(-24.0F * 0.05F, after.attitude().roll(), 1.0e-4F);
    }

    @Test
    void onTheGroundTheStickDoesNothingAndTheAirframeSettles() {
        AttitudeState<RotorcraftAttitude.Memory> tilted = state(new Attitude(10.0F, 5.0F, 5.0F), 1.0F);
        AttitudeState<RotorcraftAttitude.Memory> after = MODEL.integrate(tilted, stick(40.0F, 40.0F), NO_KEYS,
                FLYING, grounded(), 0.05F);
        assertEquals(10.0F, after.attitude().yaw(), 1.0e-4F);
        assertTrue(after.attitude().pitch() < 5.0F && after.attitude().roll() < 5.0F);
    }

    @Test
    void quaternionModeKeepsTheOrientation() {
        AttitudeState<RotorcraftAttitude.Memory> after = MODEL.integrate(state(LEVEL, 1.0F), stick(0.0F, 10.0F),
                NO_KEYS, FLYING, flying(QUATERNION), 0.05F);
        assertEquals(-0.5F, after.attitude().pitch(), 1.0e-4F);
        assertTrue(after.orientation() != null);
    }

    @Test
    void levellingIsTheSameAtSixtyStepsPerSecond() {
        AttitudeState<RotorcraftAttitude.Memory> banked = state(new Attitude(0.0F, 0.0F, 30.0F), 1.0F);
        float atTicks = run(MODEL, banked, AttitudeInput.CENTRED, NO_KEYS, FLYING, flying(EULER), 0.05F, 20)
                .attitude().roll();
        float atSubsteps = run(MODEL, banked, AttitudeInput.CENTRED, NO_KEYS, FLYING, flying(EULER),
                FixedStepClock.STEP_SECONDS, 60).attitude().roll();
        assertEquals(atTicks, atSubsteps, 1.0e-3F);
    }

    @Test
    void stickYawIsTheSameAtSixtyStepsPerSecond() {
        // hovering holds pitch and roll, so the stick only yaws: 20 degrees per second for a second
        AttitudeState<RotorcraftAttitude.Memory> level = state(LEVEL, 1.0F);
        float atTicks = run(MODEL, level, stick(20.0F, 0.0F), NO_KEYS, HOVERING, flying(QUATERNION), 0.05F, 20)
                .attitude().yaw();
        float atSubsteps = run(MODEL, level, stick(20.0F, 0.0F), NO_KEYS, HOVERING, flying(QUATERNION),
                FixedStepClock.STEP_SECONDS, 60).attitude().yaw();
        assertEquals(20.0F, atTicks, 0.01F);
        assertEquals(atTicks, atSubsteps, 0.01F);
    }
}
