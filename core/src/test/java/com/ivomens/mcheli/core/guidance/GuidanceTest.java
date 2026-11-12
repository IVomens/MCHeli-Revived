package com.ivomens.mcheli.core.guidance;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pursuit, seeker limits and the lock cone, without a world. */
class GuidanceTest {

    @Test
    void aFullTurnPointsAlongTheAimAtTheTargetSpeed() {
        Vector3d turned = Pursuit.turn(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, Double.MAX_VALUE, 1.0);
        assertEquals(0.0, turned.x, 1e-12);
        assertEquals(2.0, turned.z, 1e-12);
    }

    @Test
    void aPartialTurnKeepsTheSpeedWhileTurning() {
        Vector3d turned = Pursuit.turn(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, Double.MAX_VALUE, 0.5);
        assertEquals(1.0, turned.length(), 1e-12, "no speed is lost cutting the corner");
        assertEquals(turned.x, turned.z, 1e-12, "half way round");
    }

    @Test
    void theEasedSpeedIsCapped() {
        Vector3d turned = Pursuit.turn(1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 10.0, 3.0, 0.5);
        assertEquals(3.0, turned.x, 1e-12);
    }

    @Test
    void aMissileAtRestAcceleratesTowardsItsAim() {
        Vector3d turned = Pursuit.turn(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 4.0, Double.MAX_VALUE, 0.25);
        assertEquals(new Vector3d(0.0, 1.0, 0.0), turned);
    }

    @Test
    void leadMovesTheAimByTheTargetsMotionOverTheFlightTime() {
        Vector3d led = Pursuit.lead(new Vector3d(0.0, 0.0, 0.0), new Vector3d(2.0, 0.0, 0.0), 1.0,
                new Vector3d(100.0, 0.0, 0.0), new Vector3d(0.0, 0.0, 1.0));
        assertEquals(new Vector3d(100.0, 0.0, 50.0), led, "50 ticks at 2 blocks per tick");
        Vector3d resting = Pursuit.lead(new Vector3d(0.0, 0.0, 0.0), new Vector3d(), 4.0,
                new Vector3d(100.0, 0.0, 0.0), new Vector3d(0.0, 0.0, 1.0));
        assertEquals(25.0, resting.z, 1e-12, "a missile at rest assumes its nominal speed");
    }

    @Test
    void theFieldOfViewAndDopplerGateAreHalfAngles() {
        Vector3f flight = new Vector3f(0.0f, 0.0f, 1.0f);
        assertFalse(Seeker.outsideFieldOfView(flight, new Vector3f(0.5f, 0.0f, 1.0f), 30.0));
        assertTrue(Seeker.outsideFieldOfView(flight, new Vector3f(1.0f, 0.0f, 1.0f), 30.0));
        assertTrue(Seeker.outsideDopplerGate(flight, new Vector3f(1.0f, 0.0f, 0.0f), 80.0));
    }

    @Test
    void crossingTrafficNotchesAndHeadOnDoesNot() {
        Vector3f shooter = new Vector3f(0.0f, 0.0f, 1.0f);
        assertTrue(Seeker.isNotching(shooter, new Vector3f(1.0f, 0.0f, 0.0f), 60.0f));
        assertFalse(Seeker.isNotching(shooter, new Vector3f(0.0f, 0.0f, -1.0f), 60.0f), "head-on");
        assertFalse(Seeker.isNotching(shooter, new Vector3f(), 60.0f), "a hovering target");
    }

    @Test
    void theLockConeIsCentredOnTheAim() {
        // yaw 0 looks south (+z)
        assertTrue(LockCone.contains(0.0, 0.0, 10.0, 0.0F, 0.0F, 10.0F));
        assertFalse(LockCone.contains(10.0, 0.0, 0.0, 0.0F, 0.0F, 10.0F));
        assertTrue(LockCone.contains(10.0, 0.0, 0.0, -90.0F, 0.0F, 10.0F), "east is yaw -90");
        assertFalse(LockCone.contains(0.0, 10.0, 10.0, 0.0F, 0.0F, 10.0F), "45 degrees above");
        assertTrue(LockCone.contains(0.0, 10.0, 10.0, 0.0F, -45.0F, 10.0F), "aimed 45 degrees up");
        assertTrue(LockCone.contains(0.0, 0.0, 10.0, 359.0F, 0.0F, 10.0F), "across the wrap");
    }

    @Test
    void stealthNarrowsTheConeShortensTheReachAndSlowsTheLock() {
        assertEquals(20.0F, LockCone.searchAngle(20, 0.0F));
        assertEquals(10.0F, LockCone.searchAngle(20, 1.0F));
        assertEquals(50.0, LockCone.reach(100.0, 0.5F));
        assertEquals(20, LockCone.lockTicks(20, 0.0F));
        assertEquals(30, LockCone.lockTicks(20, 0.5F));
    }
}
