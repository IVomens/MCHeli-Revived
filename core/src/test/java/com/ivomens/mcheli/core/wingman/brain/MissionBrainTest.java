package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The task brains: mission orders and node missions moving from phase to phase. */
class MissionBrainTest {

    private final ScriptedWorld world = new ScriptedWorld();
    private final Assignment wingman = new Assignment(0, new AttackOrders(), new Autopilot());
    private final Autopilot autopilot = this.wingman.autopilot();

    @Test
    void anOrderWithoutParkingStartsInTheAirAndFliesToTheTarget() {
        this.autopilot.startOrder(capOrder().build());
        plan(0, 100, 0);
        assertEquals(FlightPhase.TRANSIT_TO, this.autopilot.phase());

        plan(0, 100, 0);
        assertEquals(1000.0, this.autopilot.targetX);
        assertEquals(120.0, this.autopilot.targetY, "at cruise altitude");
        plan(1000, 120, 980);
        assertEquals(FlightPhase.ON_STATION, this.autopilot.phase(), "within 30 blocks of the target");
    }

    @Test
    void onStationAttacksUntilTheTimeLimitThenHeadsHome() {
        this.autopilot.startOrder(capOrder().timeLimitSeconds(1).build());
        this.autopilot.phase = FlightPhase.ON_STATION;
        for (int tick = 1; tick < 20; tick++) {
            plan(1000, 120, 1000);
            assertEquals(AttackMode.AUTO, this.wingman.attack().mode(), "a patrol attacks on station");
        }
        plan(1000, 120, 1000);
        assertEquals(FlightPhase.RTB, this.autopilot.phase());
        assertEquals("time limit", this.autopilot.order().rtbReason());
        assertEquals(AttackMode.NONE, this.wingman.attack().mode());
    }

    @Test
    void reconReportsWhatItCountedOnTheWayHome() {
        this.autopilot.startOrder(MissionOrder.builder().type(MissionType.RECON).target(0, 0)
                .timeLimitSeconds(6).build());
        this.autopilot.phase = FlightPhase.ON_STATION;
        this.world.hostiles = 7;
        for (int tick = 1; tick <= 120; tick++) {
            plan(0, 80, 0);
        }
        assertEquals(FlightPhase.RTB, this.autopilot.phase());
        assertEquals(List.of("7 time limit"), this.world.reports);
    }

    @Test
    void aPlaneWithNoRunwayToReturnToStopsTheOrder() {
        this.autopilot.startOrder(capOrder().build());
        this.autopilot.phase = FlightPhase.RTB;
        plan(0, 120, 0);
        assertFalse(this.autopilot.hasOrder());
        assertEquals(FlightPhase.PARKED, this.autopilot.phase());
    }

    @Test
    void homeWithARunwayEntersTheCircuitWithin200Blocks() {
        this.world.place(MarkerType.RUNWAY_B, "rwb", "home", 0, 64, 300);
        this.autopilot.startOrder(capOrder().build());
        this.autopilot.phase = FlightPhase.RTB;
        plan(0, 120, 600);
        assertEquals(FlightPhase.RTB, this.autopilot.phase());
        plan(0, 120, 480);
        assertEquals(FlightPhase.DESCEND, this.autopilot.phase());
    }

    @Test
    void aTakeoffNodeStartsWithTheTaxi() {
        this.world.place(MarkerType.RUNWAY_A, "rw", "", 0, 64, 0);
        this.world.place(MarkerType.RUNWAY_B, "rw", "", 0, 64, 300);
        this.autopilot.startNodeMission(List.of(MissionNode.takeoff("rw"), MissionNode.flyTo(0, 60, 700)));
        assertEquals(FlightPhase.ENROUTE, this.autopilot.phase(), "a new node mission starts en route");
        plan(0.5, 64, 0.5);
        assertEquals(FlightPhase.TAXI_OUT, this.autopilot.phase(), "the takeoff node takes over");
    }

    @Test
    void flyToNodesFollowEachOtherAndTheMissionEndsParked() {
        this.autopilot.startNodeMission(List.of(MissionNode.flyTo(100, 10, 0), MissionNode.flyTo(200, 10, 0)));
        plan(0, 90, 0);
        assertEquals(80.0, this.autopilot.targetY, "never lower than the cruise floor");
        plan(80, 90, 0);
        assertEquals(1, this.autopilot.mission().index());
        plan(180, 90, 0);
        plan(180, 90, 0);
        assertEquals(FlightPhase.PARKED, this.autopilot.phase());
        assertNull(this.autopilot.mission());
        assertFalse(this.autopilot.isAutonomous());
    }

    @Test
    void anAttackNodeEndsWhenNoHostileIsLeft() {
        this.autopilot.startNodeMission(List.of(MissionNode.attack(100), MissionNode.loiter(5)));
        this.world.hostileNearby = true;
        plan(0, 90, 0);
        assertEquals(AttackMode.AUTO, this.wingman.attack().mode());
        this.world.hostileNearby = false;
        for (int tick = 1; tick <= 10; tick++) {
            plan(0, 90, 0);
        }
        assertEquals(AttackMode.NONE, this.wingman.attack().mode());
        assertEquals(1, this.autopilot.mission().index(), "on to the loiter");
        assertTrue(this.autopilot.isAutonomous());
    }

    @Test
    void aTakeoffFromARunwayAboveTheCruiseAltitudeStillClimbs() {
        this.world.place(MarkerType.RUNWAY_A, "rw", "", 0, 120, 0);
        this.world.place(MarkerType.RUNWAY_B, "rw", "", 0, 120, 300);
        this.autopilot.startNodeMission(List.of(MissionNode.takeoff("rw"), MissionNode.flyTo(0, 60, 2000)));
        this.autopilot.phase = FlightPhase.CLIMB;
        plan(0.5, 125, 350);
        assertEquals(0, this.autopilot.mission().index(), "five blocks above the runway the climb goes on");
        assertEquals(FlightPhase.CLIMB, this.autopilot.phase());
        plan(0.5, 131, 450);
        assertEquals(1, this.autopilot.mission().index(), "16 blocks above the runway, less the tolerance");
    }

    @Test
    void theCruiseKeepsClearOfHighGround() {
        this.autopilot.startOrder(capOrder().build());
        this.autopilot.phase = FlightPhase.TRANSIT_TO;
        plan(0, 120, 0);
        assertEquals(120.0, this.autopilot.targetY, "the asked altitude over low ground");
        this.world.ground = 150;
        plan(0, 170, 0);
        assertEquals(166.0, this.autopilot.targetY, "16 blocks above the hills");
    }

    @Test
    void anAttackOnStationCarriesOnAroundThePatrolOrbit() {
        this.autopilot.startOrder(capOrder().build());
        this.autopilot.phase = FlightPhase.ON_STATION;
        for (int tick = 1; tick <= 300; tick++) {
            plan(1000, 120, 1000);
        }
        double patrolBearing = bearing(this.autopilot.target());

        // a missile is fired from the orbit: the attack keeps circling where the patrol was
        Point target = new Point(1000, 64, 1000);
        Point attack = CombatBrain.moveTarget(this.world.perceive(ScriptedWorld.plane(), null),
                ScriptedWorld.at(1000, 120, 1000, 0.0F), this.wingman, target, WeaponProfile.MISSILE);
        double step = Angles.wrapDegrees((float) (bearing(attack) - patrolBearing));
        assertTrue(step > 0 && step < 5, "the attack orbit moved on by " + step + " degrees from the patrol");
        assertEquals(300.0, Math.hypot(attack.x() - 1000, attack.z() - 1000), 1e-6, "on the order's orbit");

        // and the patrol picks up where the attack left off
        this.wingman.attack().setMode(AttackMode.NONE);
        plan(1000, 120, 1000);
        double back = Angles.wrapDegrees((float) (bearing(this.autopilot.target()) - bearing(attack)));
        assertTrue(back > 0 && back < 5, "the patrol moved on by " + back + " degrees from the attack");
    }

    /** Bearing in degrees of a point around the CAP target. */
    private static double bearing(Point point) {
        return Math.toDegrees(Math.atan2(point.z() - 1000, point.x() - 1000));
    }

    private static MissionOrder.Builder capOrder() {
        return MissionOrder.builder().type(MissionType.CAP).baseId("home").target(1000, 1000).cruiseAlt(120);
    }

    private void plan(double x, double y, double z) {
        RecordedControls controls = ScriptedWorld.at(x, y, z, 0.0F);
        WingmanBrain.plan(this.world.perceive(ScriptedWorld.plane(), null), controls, this.wingman);
    }
}
