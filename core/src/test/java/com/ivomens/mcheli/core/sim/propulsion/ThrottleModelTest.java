package com.ivomens.mcheli.core.sim.propulsion;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.port.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleModelTest {

    private static final SimSettings MANUAL = new SimSettings(false, true, false, SimSettings.TiltLimit.NONE);
    private static final ControlInput NO_KEYS = new ControlInput(false, false, false, false, false);
    private static final ControlInput THROTTLE_UP = new ControlInput(true, false, false, false, false);
    private static final ControlInput THROTTLE_DOWN = new ControlInput(false, true, false, false, false);
    private static final ControlInput TURN_LEFT = new ControlInput(false, false, true, false, false);
    private static final ReversibleThrottle.Setup PLANE = new ReversibleThrottle.Setup(1.0F,
            ReversibleThrottle.CarrierScaling.NONE, true, 1.0F, 0.6F, 0.5F);
    private static final ReversibleThrottle.Setup TANK = new ReversibleThrottle.Setup(1.0F,
            ReversibleThrottle.CarrierScaling.NONE, true, 1.0F, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);

    private static ThrottleLever lever(double throttle) {
        return new ThrottleLever(throttle, 0.0F, () -> false);
    }

    @Test
    void aCrippledEngineCannotRiseEvenBackToZero() {
        ThrottleLever lever = new ThrottleLever(-0.001, 0.0F, () -> true);

        ThrottleModel.clampAtZero(lever);

        assertEquals(-0.001, lever.throttle());
    }

    @Test
    void everyPositiveSettingCountsForWakingAParkedVehicle() {
        ThrottleLever lever = lever(0.001);

        ThrottleModel.idleDown(lever, 0.0005);
        ThrottleModel.idleDown(lever, 0.01);
        ThrottleModel.clampAtZero(lever);

        assertEquals(0.0, lever.throttle());
        assertTrue(lever.wasPositive());
    }

    @Test
    void theSyncedThrottleTravelsInTwentiethsAndNeverRoundsAnEngineOff() {
        assertEquals(1, ThrottleModel.toSyncSteps(0.001));
        assertEquals(10, ThrottleModel.toSyncSteps(0.5));
        assertEquals(0, ThrottleModel.toSyncSteps(0.0));
        assertEquals(-1, ThrottleModel.toSyncSteps(-0.07));
        assertEquals(0.5, ThrottleModel.fromSyncSteps(10));
    }

    @Test
    void helicoptersSpoolUpAndHoverToFull() {
        ThrottleLever lever = lever(0.5);
        RotorThrottle.pilot(lever, THROTTLE_UP, 1.0F, MANUAL);
        assertEquals(0.5 + 0.02 * 1.0F, lever.throttle());

        ThrottleLever hovering = lever(0.99);
        RotorThrottle.hover(hovering);
        RotorThrottle.hover(hovering);
        assertEquals(1.0, hovering.throttle());
    }

    @Test
    void helicoptersSettleTowardsHalfThrottleWhenAsked() {
        ThrottleLever lever = lever(0.9);

        RotorThrottle.pilot(lever, NO_KEYS, 1.0F, MANUAL.withAutoThrottleDown(true));

        assertEquals(0.9 - 0.01, lever.throttle());
    }

    @Test
    void holdingThrottleDownAtZeroPullsTheReverseLeverUpToItsCap() {
        ThrottleLever lever = lever(0.0);
        for (int i = 0; i < 1000; i++) {
            ReversibleThrottle.pilot(lever, THROTTLE_DOWN, PLANE, MANUAL);
        }

        assertEquals(0.6F, lever.reverseThrust());
        assertTrue(lever.reverseEngaged());
    }

    @Test
    void tanksHaveNoReverseCap() {
        ThrottleLever lever = lever(0.0);
        for (int i = 0; i < 1000; i++) {
            ReversibleThrottle.pilot(lever, THROTTLE_DOWN, TANK, MANUAL);
        }

        assertTrue(lever.reverseThrust() > 2.0F);
    }

    @Test
    void throttleUpReleasesTheReverseLeverBeforeSpoolingUp() {
        ThrottleLever lever = new ThrottleLever(0.0, 0.5F, () -> false);

        ReversibleThrottle.pilot(lever, THROTTLE_UP, PLANE, MANUAL);

        assertEquals((float) (0.5F - 0.01 * 1.0F), lever.reverseThrust());
        assertEquals(0.0, lever.throttle());
    }

    @Test
    void planesSpoolUpByThemselvesToPivotOnTheGround() {
        ThrottleLever plane = lever(0.0);
        ReversibleThrottle.pilot(plane, TURN_LEFT, PLANE, MANUAL);
        assertEquals(0.01 * 2.0F, plane.throttle());

        ThrottleLever tank = lever(0.0);
        ReversibleThrottle.pilot(tank, TURN_LEFT, TANK, MANUAL);
        assertEquals(0.0, tank.throttle());
    }

    @Test
    void gunnerModeLeavesTheThrottleAlone() {
        ThrottleLever lever = lever(0.3);

        ReversibleThrottle.pilot(lever, new ControlInput(true, false, false, false, true), PLANE, MANUAL);

        assertEquals(0.3, lever.throttle());
        assertFalse(lever.wasPositive());
    }

    @Test
    void aWreckSpoolsDownFasterNearTheGround() {
        ThrottleLever lever = lever(1.0);

        ThrottleModel.spoolDownWreck(lever, true, true);

        assertEquals(1.0 * 0.8 * 0.98, lever.throttle());
    }
}
