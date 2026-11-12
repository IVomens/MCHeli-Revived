package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.BACK_LEFT;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEFT;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEVEL;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.NO_KEYS;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.ON_THE_GROUND;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.grounded;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundAttitudeTest {

    private static final GroundAttitude MODEL = new GroundAttitude();
    private static final GroundTurning TURNS = new GroundTurning(1.0F, true);
    private static final GroundAttitude.Motion STILL = new GroundAttitude.Motion(Vec3d.ZERO, 0.0, 0.0, 0.0F);
    private static final GroundAttitude.Slope FLAT = new GroundAttitude.Slope(0.0F, 0.0F);

    private static GroundAttitude.Setup tank(GroundAttitude.Motion motion, GroundAttitude.Slope slope) {
        return new GroundAttitude.Setup(false, TURNS, 0.0F, false, false, -0.6F, false, motion, slope,
                RecoilKick.NONE);
    }

    private static AttitudeState<FrameTimeAverage> state(Attitude attitude) {
        return new AttitudeState<>(attitude, null, MODEL.initialMemory());
    }

    @Test
    void hullSettlesOntoTheSlope() {
        GroundAttitude.Setup onRamp = tank(STILL, new GroundAttitude.Slope(-12.0F, 4.0F));
        AttitudeState<FrameTimeAverage> after = run(MODEL, state(LEVEL), AttitudeInput.CENTRED, NO_KEYS, onRamp,
                grounded(), 0.05F, 60);
        assertEquals(-12.0F, after.attitude().pitch(), 0.05F);
        assertEquals(4.0F, after.attitude().roll(), 0.05F);
    }

    @Test
    void theFirstStepsAfterBoardingAreShort() {
        // the step-time average starts at zero, so the first step only moves a tenth as far
        GroundAttitude.Setup onRamp = tank(STILL, new GroundAttitude.Slope(-10.0F, 0.0F));
        float first = MODEL.integrate(state(LEVEL), AttitudeInput.CENTRED, NO_KEYS, onRamp, grounded(), 0.05F)
                .attitude().pitch();
        assertEquals(-10.0F * 0.005F * 7.0F, first, 1.0e-4F);
    }

    @Test
    void tracksPivotWithTheSideKeys() {
        AttitudeState<FrameTimeAverage> warm = run(MODEL, state(LEVEL), AttitudeInput.CENTRED, NO_KEYS,
                tank(STILL, FLAT), grounded(), 0.05F, 10);
        float yaw = MODEL.integrate(warm, AttitudeInput.CENTRED, LEFT, tank(STILL, FLAT), grounded(), 0.05F)
                .attitude().yaw();
        assertEquals(-12.0F * 0.05F, yaw, 1.0e-4F);
    }

    @Test
    void theMouseNeverTurnsTheHull() {
        AttitudeState<FrameTimeAverage> after = run(MODEL, state(LEVEL), new AttitudeInput(40.0F, 40.0F, false),
                NO_KEYS, tank(STILL, FLAT), grounded(), 0.05F, 20);
        assertEquals(LEVEL.yaw(), after.attitude().yaw(), 0.0F);
        assertEquals(LEVEL.pitch(), after.attitude().pitch(), 0.0F);
    }

    @Test
    void carsOnlySteerWhileRolling() {
        GroundAttitude.Setup parkedCar = new GroundAttitude.Setup(true, TURNS, 0.0F, false, false, 0.0F, false, STILL,
                FLAT, RecoilKick.NONE);
        AttitudeState<FrameTimeAverage> after = run(MODEL, state(LEVEL), AttitudeInput.CENTRED, LEFT, parkedCar,
                grounded(), 0.05F, 20);
        assertEquals(0.0F, after.attitude().yaw(), 0.0F);
    }

    @Test
    void reversingMirrorsTheSideKeys() {
        GroundAttitude.Motion backingUp = new GroundAttitude.Motion(new Vec3d(0.0, 0.0, -0.2), 0.04, 0.0, 0.3F);
        GroundAttitude.Setup tracked = new GroundAttitude.Setup(false, TURNS, 0.2F, false, false, 0.0F, false,
                backingUp, FLAT, RecoilKick.NONE);
        float yaw = GroundAttitude.steerWhileReversing(0.0F, BACK_LEFT, tracked, ON_THE_GROUND);
        // left while backing slowly turns the hull right, scaled by the distance moved
        assertEquals(-0.6F * (float) Math.sqrt(0.04), yaw, 1.0e-5F);
    }

    @Test
    void anUnmannedTankSettlesOnTheServer() {
        Attitude settled = LEVEL;
        GroundAttitude.Setup onRamp = tank(STILL, new GroundAttitude.Slope(8.0F, 0.0F));
        for (int tick = 0; tick < 40; tick++) {
            settled = GroundAttitude.followTerrainUnmanned(settled, onRamp, ON_THE_GROUND, 0.05F);
        }
        assertEquals(8.0F, settled.pitch(), 0.01F);
    }

    @Test
    void terrainFollowIsCloseAtSixtyStepsPerSecond() {
        // following is a per-step share, so it only converges the same way, not to the same digit
        GroundAttitude.Setup onRamp = tank(STILL, new GroundAttitude.Slope(-10.0F, 0.0F));
        ControlInput keys = NO_KEYS;
        AttitudeState<FrameTimeAverage> start = run(MODEL, state(LEVEL), AttitudeInput.CENTRED, keys,
                tank(STILL, FLAT), grounded(), 0.05F, 10);
        float atTicks = run(MODEL, start, AttitudeInput.CENTRED, keys, onRamp, grounded(), 0.05F, 4)
                .attitude().pitch();
        float atSubsteps = run(MODEL, start, AttitudeInput.CENTRED, keys, onRamp, grounded(),
                FixedStepClock.STEP_SECONDS, 12).attitude().pitch();
        assertTrue(Math.abs(atTicks - atSubsteps) < 1.5F, atTicks + " vs " + atSubsteps);
    }
}
