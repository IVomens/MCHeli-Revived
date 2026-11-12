package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The takeoff state machine, ALIGN to TAKEOFF_ROLL to CLIMB, on a runway running 300 blocks north to south. */
class DepartureBrainTest {

    private static final double RUNWAY_Y = 64;
    private static final double CRUISE = 120;

    private final ScriptedWorld world = new ScriptedWorld();
    private final Autopilot autopilot = new Autopilot();
    private final Runway runway = Runway.of(
            this.world.place(MarkerType.RUNWAY_A, "rw", "", 0, (int) RUNWAY_Y, 0),
            this.world.place(MarkerType.RUNWAY_B, "rw", "", 0, (int) RUNWAY_Y, 300));
    private final Perception perception = this.world.perceive(ScriptedWorld.plane(), null);

    @Test
    void alignedAndStoppedRollsAfterSettling() {
        this.autopilot.phase = FlightPhase.ALIGN;
        for (int tick = 1; tick < 20; tick++) {
            fly(0.5, RUNWAY_Y, 0.5, 0.0F);
            assertEquals(FlightPhase.ALIGN, this.autopilot.phase, "still settling at tick " + tick);
        }
        RecordedControls controls = fly(0.5, RUNWAY_Y, 0.5, 2.0F);
        assertEquals(FlightPhase.TAKEOFF_ROLL, this.autopilot.phase);
        assertEquals(0, this.autopilot.phaseTimer, "the roll starts its own timer");
        assertEquals(RUNWAY_Y, this.autopilot.groundRollY, "and is held at the runway height");
        assertEquals(this.runway.yaw(), controls.yaw(), "the nose snaps onto the runway heading");
        assertEquals(0.0, controls.throttle());
    }

    @Test
    void misalignedRollsAfterTheTimeout() {
        this.autopilot.phase = FlightPhase.ALIGN;
        for (int tick = 1; tick <= 80; tick++) {
            fly(0.5, RUNWAY_Y, 0.5, 90.0F);
            assertEquals(FlightPhase.ALIGN, this.autopilot.phase, "waiting for the nose at tick " + tick);
        }
        fly(0.5, RUNWAY_Y, 0.5, 90.0F);
        assertEquals(FlightPhase.TAKEOFF_ROLL, this.autopilot.phase);
    }

    @Test
    void anAircraftStillInTheAirIsPushedDownBeforeTheTimerStarts() {
        this.autopilot.phase = FlightPhase.ALIGN;
        RecordedControls controls = fly(0.5, RUNWAY_Y + 10, 0.5, 0.0F);
        assertEquals(-0.25, controls.motion().y());
        assertEquals(0, this.autopilot.phaseTimer);
    }

    @Test
    void rollClimbsOnlyOnceAirborneInTheRotationZoneAfterTheGroundRoll() {
        this.autopilot.phase = FlightPhase.TAKEOFF_ROLL;
        // airborne near the far end, but still inside the minimum ground roll
        for (int tick = 1; tick < 60; tick++) {
            fly(0.5, RUNWAY_Y + 5, 280, 0.0F);
            assertEquals(FlightPhase.TAKEOFF_ROLL, this.autopilot.phase, "held on the ground at tick " + tick);
        }
        RecordedControls controls = fly(0.5, RUNWAY_Y + 5, 280, 0.0F);
        assertEquals(FlightPhase.CLIMB, this.autopilot.phase);
        assertEquals(1.0, controls.throttle(), "full throttle down the runway");
    }

    @Test
    void rollOnAShortRunwayClimbsAfterTheTimeout() {
        this.autopilot.phase = FlightPhase.TAKEOFF_ROLL;
        for (int tick = 1; tick <= 200; tick++) {
            fly(0.5, RUNWAY_Y, 10, 0.0F);
        }
        assertEquals(FlightPhase.TAKEOFF_ROLL, this.autopilot.phase);
        fly(0.5, RUNWAY_Y, 10, 0.0F);
        assertEquals(FlightPhase.CLIMB, this.autopilot.phase);
    }

    @Test
    void theRollSteersAlongTheCentrelineOnTheGround() {
        this.autopilot.phase = FlightPhase.TAKEOFF_ROLL;
        fly(3.5, RUNWAY_Y, 100.5, 0.0F);
        assertEquals(0.5, this.autopilot.targetX, 1e-9, "back onto the centreline");
        assertEquals(140.5, this.autopilot.targetZ, 1e-9, "40 blocks ahead");
        assertEquals(RUNWAY_Y, this.autopilot.targetY, "on the runway surface");
    }

    @Test
    void climbEndsNearCruiseAltitude() {
        this.autopilot.phase = FlightPhase.CLIMB;
        assertFalse(DepartureBrain.fly(this.perception, ScriptedWorld.at(0.5, CRUISE - 6, 400, 0.0F), this.autopilot,
                this.runway, CRUISE));
        assertTrue(DepartureBrain.fly(this.perception, ScriptedWorld.at(0.5, CRUISE - 5, 400, 0.0F), this.autopilot,
                this.runway, CRUISE));
    }

    @Test
    void aNodeMissionTaxisToTheThresholdFirst() {
        this.autopilot.phase = FlightPhase.ENROUTE;
        DepartureBrain.flyFromThreshold(this.perception, ScriptedWorld.at(50, RUNWAY_Y, 50, 0.0F), this.autopilot,
                this.runway, CRUISE);
        assertEquals(FlightPhase.TAXI_OUT, this.autopilot.phase);
        DepartureBrain.flyFromThreshold(this.perception, ScriptedWorld.at(10, RUNWAY_Y, 10, 0.0F), this.autopilot,
                this.runway, CRUISE);
        assertEquals(FlightPhase.ALIGN, this.autopilot.phase, "within 30 blocks of the threshold");
    }

    private RecordedControls fly(double x, double y, double z, float yaw) {
        RecordedControls controls = ScriptedWorld.at(x, y, z, yaw);
        DepartureBrain.fly(this.perception, controls, this.autopilot, this.runway, CRUISE);
        return controls;
    }
}
