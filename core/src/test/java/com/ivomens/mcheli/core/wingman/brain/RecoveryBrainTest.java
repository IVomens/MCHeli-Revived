package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The landing circuit on a runway from A (0, 64, 0) to B (0, 64, 300): the downwind leg runs 80 blocks
 * to the side, the final starts 250 blocks before A.
 */
class RecoveryBrainTest {

    private final ScriptedWorld world = new ScriptedWorld();
    private final Autopilot autopilot = new Autopilot();
    private final Runway runway = Runway.of(
            this.world.place(MarkerType.RUNWAY_A, "rw", "", 0, 64, 0),
            this.world.place(MarkerType.RUNWAY_B, "rw", "", 0, 64, 300));
    private final Perception perception = this.world.perceive(ScriptedWorld.plane(), null);

    @Test
    void theCircuitFliesItsLegsInOrder() {
        assertFalse(fly(500, 200, 500), "any phase starts the circuit");
        assertEquals(FlightPhase.DESCEND, this.autopilot.phase);

        fly(500, 200, 500);
        assertEquals(144.0, this.autopilot.targetY, "circuit height is 80 above the runway");
        fly(-79.5, 144, 300.5);
        assertEquals(FlightPhase.CIRCUIT_DOWNWIND, this.autopilot.phase, "abeam B");
        fly(-79.5, 144, -249.5);
        assertEquals(FlightPhase.CIRCUIT_BASE, this.autopilot.phase, "abeam the start of the final");
        fly(0.5, 144, -249.5);
        assertEquals(FlightPhase.CIRCUIT_FINAL, this.autopilot.phase, "at the start of the final");
        fly(0.5, 66, -2);
        assertEquals(FlightPhase.LANDING, this.autopilot.phase, "over the threshold");
        assertTrue(this.autopilot.landingRollSpeed >= 0.5, "the roll starts at touchdown speed at least");
        assertTrue(fly(0.5, 64, 290), "the roll ends near B");
    }

    @Test
    void theFinalFollowsTheGlideSlope() {
        this.autopilot.phase = FlightPhase.CIRCUIT_FINAL;
        RecordedControls high = ScriptedWorld.at(0.5, 140, -125, 0.0F);
        RecoveryBrain.fly(this.perception, high, this.autopilot, this.runway);
        // halfway down the final the slope is at half circuit height: 65 + 125.5 * 79 / 250
        assertEquals(65 + 125.5 * (79.0 / 250), this.autopilot.targetY, 1e-9);
        assertTrue(high.throttle() < 0.55, "above the slope the throttle comes back");
    }

    private boolean fly(double x, double y, double z) {
        return RecoveryBrain.fly(this.perception, ScriptedWorld.at(x, y, z, 0.0F), this.autopilot, this.runway);
    }
}
