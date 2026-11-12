package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.model.SimFakes.GroundBelow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetDroneAutopilotTest {

    private static final float CIRCLE_RATE = -0.6F;
    private static final Attitude NOSE_DOWN = new Attitude(90.0F, 10.0F, 0.0F);

    @Test
    void highUpTheDroneCruisesLevelWithItsGearUp() {
        TargetDroneAutopilot.Command command = TargetDroneAutopilot.steer(NOSE_DOWN, CIRCLE_RATE, new GroundBelow(100));

        assertTrue(command.holdAltitude());
        assertTrue(command.retractGear());
        assertEquals(new Attitude(90.0F + CIRCLE_RATE, 10.0F * 0.95F, 0.0F), command.attitude());
    }

    @Test
    void lowDownTheDroneTurnsTighterAndClimbs() {
        TargetDroneAutopilot.Command command = TargetDroneAutopilot.steer(NOSE_DOWN, CIRCLE_RATE, new GroundBelow(20));

        assertFalse(command.holdAltitude());
        assertEquals(new Attitude(90.0F + CIRCLE_RATE * 2.0F, 10.0F - 0.5F, 0.0F), command.attitude());
    }

    @Test
    void justAboveTheGroundItLeavesTheControlsAlone() {
        TargetDroneAutopilot.Command command = TargetDroneAutopilot.steer(NOSE_DOWN, CIRCLE_RATE, new GroundBelow(2));

        assertFalse(command.holdAltitude());
        assertEquals(NOSE_DOWN, command.attitude());
    }
}
