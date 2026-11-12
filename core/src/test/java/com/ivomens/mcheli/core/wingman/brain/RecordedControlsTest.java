package com.ivomens.mcheli.core.wingman.brain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The brain decides on recorded controls and the scripted pilot replays them; both must agree with the aircraft. */
class RecordedControlsTest {

    @Test
    void readsSeeTheChangesMadeSoFar() {
        RecordedControls controls = ScriptedWorld.at(1, 2, 3, 10.0F);
        controls.setYaw(20.0F);
        controls.setMotionY(-0.25);
        controls.setHorizontalMotion(0.5, 0.0);
        assertEquals(20.0F, controls.yaw());
        assertEquals(new Point(0.5, -0.25, 0.0), controls.motion());
    }

    @Test
    void rotationsThatAreNotNumbersAreIgnoredAsTheEntityIgnoresThem() {
        RecordedControls controls = ScriptedWorld.at(0, 0, 0, 10.0F);
        controls.setYaw(Float.NaN);
        controls.setPitch(Float.POSITIVE_INFINITY);
        assertEquals(10.0F, controls.yaw());
        assertNull(controls.command().yaw());
        assertNull(controls.command().pitch());
    }

    @Test
    void aDamagedEngineNeverOpensFurtherButEverySettingIsKept() {
        RecordedControls controls = new RecordedControls(new Point(0, 0, 0), new Point(0, 0, 0), 0, 0, 0, 0.6,
                false, true);
        controls.setThrottle(1.0);
        assertEquals(0.6, controls.throttle());
        controls.setThrottle(0.2);
        controls.setThrottle(0.5);
        assertEquals(0.2, controls.throttle());
        assertEquals(List.of(1.0, 0.2, 0.5), controls.command().throttleSettings());
    }

    @Test
    void replayingTheCommandLeavesTheAircraftWhereTheBrainLeftTheRecord() {
        RecordedControls decided = ScriptedWorld.at(5, 64, 5, 0.0F);
        decided.setThrottle(1.0);
        decided.setThrottle(0.0);
        decided.setYaw(45.0F);
        decided.levelAttitude();
        decided.setMotionY(0.0);
        decided.aimWeapons(12.0F);
        decided.switchNozzle(true);

        RecordedControls aircraft = new RecordedControls(new Point(5, 64, 5), new Point(0.3, 0.1, 0.3), 0.0F, 7.0F,
                3.0F, 0.4, true, false);
        decided.command().replayOn(aircraft);

        assertEquals(0.0, aircraft.throttle());
        assertEquals(45.0F, aircraft.yaw());
        assertEquals(0.0F, aircraft.pitch());
        assertEquals(0.0F, aircraft.roll());
        assertEquals(new Point(0.3, 0.0, 0.3), aircraft.motion(), "untouched axes keep the aircraft's own motion");
        assertEquals(List.of(1.0, 0.0), aircraft.command().throttleSettings());
        assertEquals(12.0F, aircraft.command().weaponYaw());
        assertEquals(Boolean.TRUE, aircraft.command().nozzleHover());
    }

    @Test
    void aDecisionThatChangedNothingIsEmpty() {
        assertTrue(ScriptedWorld.at(1, 2, 3, 4.0F).command().isEmpty());
    }
}
