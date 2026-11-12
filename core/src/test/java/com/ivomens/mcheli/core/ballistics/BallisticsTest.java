package com.ivomens.mcheli.core.ballistics;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The forces, steps and impact prediction of unguided flight, without a world. */
class BallisticsTest {

    @Test
    void theMotorBurnsStrictlyInsideItsWindow() {
        assertFalse(ProjectileForces.isBoosting(5, 5, 10));
        assertTrue(ProjectileForces.isBoosting(6, 5, 10));
        assertFalse(ProjectileForces.isBoosting(10, 5, 10));
    }

    @Test
    void boostPushesAlongTheFlightDirection() {
        Vector3d motion = new Vector3d(3.0, 0.0, 4.0);
        assertTrue(ProjectileForces.boost(motion, 0.5));
        assertEquals(3.3, motion.x, 1e-12);
        assertEquals(4.4, motion.z, 1e-12);
        Vector3d rest = new Vector3d();
        assertFalse(ProjectileForces.boost(rest, 0.5), "a projectile at rest has no direction");
        assertEquals(new Vector3d(), rest);
    }

    @Test
    void dragSlowsTheHorizontalMotionOnly() {
        Vector3d motion = new Vector3d(0.0, -1.0, 1.0);
        ProjectileForces.drag(motion, 0.1);
        assertEquals(-1.0, motion.y);
        assertEquals(1.0 - 0.1 / Math.sqrt(2.0), motion.z, 1e-12);
        Vector3d noDrag = new Vector3d(1.0, 2.0, 3.0);
        ProjectileForces.drag(noDrag, 0.0);
        assertEquals(new Vector3d(1.0, 2.0, 3.0), noDrag);
    }

    @Test
    void fastWeaponsMoveSeveralStepsPerTick() {
        assertEquals(1.0, ProjectileSteps.stepFactor(true, 4.0F));
        assertEquals(2.0, ProjectileSteps.stepFactor(true, 8.0F));
        assertEquals(1.0, ProjectileSteps.stepFactor(false, 8.0F), "bombs and missiles always move one step");
    }

    @Test
    void floorMatchesTheGameForNegativeAndWholeNumbers() {
        assertEquals(-1, ProjectileSteps.floor(-0.5));
        assertEquals(-1, ProjectileSteps.floor(-1.0));
        assertEquals(2, ProjectileSteps.floor(2.999));
        assertEquals(0, ProjectileSteps.floor(Double.NaN));
    }

    @Test
    void aSegmentIsLoadedOnlyIfEveryChunkAlongItIs() {
        Set<Long> loaded = new HashSet<>();
        for (int cx = 0; cx < 4; cx++) {
            loaded.add(key(cx, 0));
        }
        List<Long> asked = new ArrayList<>();
        ProjectileSteps.LoadedChunks chunks = (cx, cz) -> {
            asked.add(key(cx, cz));
            return loaded.contains(key(cx, cz));
        };
        assertTrue(ProjectileSteps.isSegmentLoaded(1.0, 1.0, 60.0, 1.0, chunks));
        assertEquals(key(3, 0), asked.get(0), "the end is looked at first");
        loaded.remove(key(2, 0));
        assertFalse(ProjectileSteps.isSegmentLoaded(1.0, 1.0, 60.0, 1.0, chunks), "a hole in the middle");
        assertTrue(ProjectileSteps.isSegmentLoaded(1.0, 1.0, 2.0, 2.0, chunks));
    }

    @Test
    void aDroppedBombFallsOntoTheGround() {
        Flat ground = new Flat(64.0);
        ImpactPrediction.Fall fall = new ImpactPrediction.Fall(-0.04, -0.02, 0.0);
        double[] hit = ImpactPrediction.impact(0.0, 100.0, 0.0, 1.0, 0.0, 0.0, fall, 1.0, 512, 0.0, ground);
        assertNotNull(hit);
        assertEquals(64.0, hit[1]);
        assertTrue(hit[0] > 40.0 && hit[0] < 45.0, "about sqrt(2 * 36 / 0.04) blocks downrange: " + hit[0]);
    }

    @Test
    void thePredictionGivesUpWhereTheWorldEnds() {
        ImpactPrediction.Fall fall = new ImpactPrediction.Fall(-0.04, -0.02, 0.0);
        Flat unloadedAhead = new Flat(64.0) {
            @Override
            public boolean isLoaded(double x, double y, double z) {
                return x < 10.0;
            }
        };
        assertNull(ImpactPrediction.impact(0.0, 100.0, 0.0, 1.0, 0.0, 0.0, fall, 1.0, 512, 0.0, unloadedAhead));
        assertNull(ImpactPrediction.impact(0.0, 100.0, 0.0, 1.0, 0.0, 0.0, fall, 1.0, 512, 90.0, new Flat(64.0)),
                "below the lowest height");
        assertNull(ImpactPrediction.impact(0.0, 100.0, 0.0, 1.0, 0.0, 0.0, fall, 1.0, 5, 0.0, new Flat(64.0)),
                "too long a flight");
    }

    @Test
    void anglesPointAlongTheMotion() {
        assertEquals(0.0F, FlightAngles.yaw(0.0, 1.0), "south");
        assertEquals(90.0F, FlightAngles.yaw(-1.0, 0.0), 1e-5, "west");
        assertEquals(-45.0F, FlightAngles.pitch(1.0, 1.0, 0.0), 1e-5, "climbing");
        assertFalse(FlightAngles.hasDirection(0.0, 0.0005, 0.0));
        assertTrue(FlightAngles.hasDirection(0.0, 0.01, 0.0));
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Dry, loaded land up to the given height. */
    private static class Flat implements ImpactPrediction.Surroundings<double[]> {

        private final double groundY;

        Flat(double groundY) {
            this.groundY = groundY;
        }

        @Override
        public boolean isLoaded(double x, double y, double z) {
            return true;
        }

        @Override
        public boolean isWater(double x, double y, double z) {
            return false;
        }

        @Override
        public double[] hit(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            if (toY > this.groundY) {
                return null;
            }
            double t = (fromY - this.groundY) / (fromY - toY);
            return new double[]{fromX + (toX - fromX) * t, this.groundY, fromZ + (toZ - fromZ) * t};
        }
    }
}
