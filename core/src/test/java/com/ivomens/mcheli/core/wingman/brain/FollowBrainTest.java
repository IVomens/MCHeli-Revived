package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.HelicopterModel;
import com.ivomens.mcheli.core.sim.model.RotorcraftHandling;
import com.ivomens.mcheli.core.sim.model.VerticalForces;
import com.ivomens.mcheli.core.sim.port.SimRandom;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.CruiseSpeed;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Followers flown by the brain alone. A plane flies a toy flight model (it moves along its heading at a
 * speed set by its throttle); a helicopter flies the real rotor law of {@link HelicopterModel}. The
 * leader flies straight down +Z, as in the formation scenario.
 */
class FollowBrainTest {

    private static final double LEADER_SPEED = 0.6;
    /** Toy top speed: at the leader's throttle (0.5 for a leader that is no aircraft) a follower keeps pace. */
    private static final double TOP_SPEED = 1.2;
    /** The UH-1C's speed limit: barely faster than the leader. */
    private static final float HELI_SPEED_LIMIT = 0.65F;

    private final ScriptedWorld world = new ScriptedWorld();
    private final Assignment follower = new Assignment(0, new AttackOrders(), new Autopilot());

    @Test
    void planeConvergesToItsSlot() {
        Point leader = new Point(0, 100, 60);
        Point self = new Point(-10, 100, 0);
        float yaw = 0.0F;
        double previous = slot(leader).distanceTo(self);
        int inSlotFrom = -1;
        for (int tick = 1; tick <= 600; tick++) {
            leader = new Point(leader.x(), leader.y(), leader.z() + LEADER_SPEED);
            RecordedControls controls = ScriptedWorld.at(self.x(), self.y(), self.z(), yaw);
            WingmanBrain.control(perceive(ScriptedWorld.plane(), leader), controls, this.follower);
            yaw = controls.yaw();
            double speed = controls.throttle() * TOP_SPEED;
            double rad = Math.toRadians(yaw);
            self = new Point(self.x() - Math.sin(rad) * speed, self.y(), self.z() + Math.cos(rad) * speed);

            RecordedControls after = ScriptedWorld.at(self.x(), self.y(), self.z(), yaw);
            WingmanBrain.afterMove(perceive(ScriptedWorld.plane(), leader), after, this.follower);
            self = after.position();
            double distance = slot(leader).distanceTo(self);
            if (tick % 50 == 0) {
                assertTrue(distance <= Math.max(previous, FollowBrain.SLOT_ARRIVAL_DIST),
                        "the follower fell back at tick " + tick);
                previous = distance;
            }
            if (distance >= FollowBrain.SLOT_ARRIVAL_DIST) {
                inSlotFrom = -1;
            } else if (inSlotFrom < 0) {
                inSlotFrom = tick;
            }
        }
        assertTrue(inSlotFrom > 0 && inSlotFrom <= 300, "the follower should stay in its slot from tick 300 on, "
                + "is " + previous + " away and in it from tick " + inSlotFrom);
        assertTrue(previous < 1.0, "the follower should sit in its slot, is " + previous + " away");
        assertEquals(0.0F, Angles.wrapDegrees(yaw), 2.0F, "the follower should fly the leader's heading");
    }

    @Test
    void helicopterClimbsToItsSlotAndStaysInIt() {
        RotorFlight heli = new RotorFlight(new Point(-30, 70, 0));
        Point leader = new Point(0, 100, 60);
        int inSlotFrom = -1;
        double worstLate = 0;
        for (int tick = 1; tick <= 600; tick++) {
            leader = new Point(leader.x(), leader.y(), leader.z() + LEADER_SPEED);
            double distance = heli.tick(perceive(ScriptedWorld.helicopter(), leader), this.follower, slot(leader));
            if (distance >= FollowBrain.SLOT_ARRIVAL_DIST) {
                inSlotFrom = -1;
            } else if (inSlotFrom < 0) {
                inSlotFrom = tick;
            }
            if (tick > 400) {
                worstLate = Math.max(worstLate, distance);
            }
        }
        assertTrue(inSlotFrom > 0 && inSlotFrom <= 400,
                "the helicopter should be in its slot by tick 400 and stay, is in it from tick " + inSlotFrom);
        assertTrue(worstLate < 2.0, "late in the flight it strays " + worstLate + " blocks from its slot");
    }

    @Test
    void helicopterStopsAndHoversAtItsSlotHeightWithAStoppedLeader() {
        RotorFlight heli = new RotorFlight(new Point(15, 95, -40));
        heli.body = heli.body.withVelocity(new Vec3d(0.0, 0.0, 0.3));
        Point leader = new Point(0, 100, 0);
        double lowest = Double.MAX_VALUE;
        for (int tick = 1; tick <= 400; tick++) {
            heli.tick(this.world.perceive(ScriptedWorld.helicopter(), leaderAt(leader, 0.0)), this.follower,
                    slot(leader, 0.0));
            lowest = Math.min(lowest, heli.position.y());
        }
        assertTrue(lowest > 94.0, "it should not sink, went down to " + lowest);
        assertEquals(100.0, heli.position.y(), 0.5, "it should hover at the slot's height");
        assertEquals(0.0, heli.body.velocity().horizontalLength(), 0.01, "and stand still");
    }

    @Test
    void planeHoldsWithAStoppedLeader() {
        LeaderState stopped = leaderAt(new Point(0, 100, 60), 0.0);
        Point nearSlot = ScriptedWorld.FORMATION.slotPosition(stopped, 0);
        RecordedControls controls = ScriptedWorld.at(nearSlot.x() + 10, nearSlot.y(), nearSlot.z(), 30.0F);
        controls.setThrottle(0.7);

        WingmanBrain.control(this.world.perceive(ScriptedWorld.plane(), stopped), controls, this.follower);

        assertEquals(0.0, controls.throttle(), "a plane closes the throttle");
        assertEquals(26.0F, controls.yaw(), "and turns towards the leader's heading at the hover rate");
    }

    @Test
    void helicopterCollectiveHoldsTheSlotHeight() {
        Airframe heli = ScriptedWorld.helicopter();
        Point slot = new Point(0, 100, 0);
        double hover = HelicopterModel.throttleForClimb(heli.gravity(), 0.0);
        assertEquals(hover, FollowBrain.collective(heli, ScriptedWorld.at(0, 100, 0, 0.0F), slot), 1e-12,
                "level with the slot and not climbing: the hover throttle");
        assertTrue(FollowBrain.collective(heli, ScriptedWorld.at(0, 98, 0, 0.0F), slot) > hover, "low: more");
        assertTrue(FollowBrain.collective(heli, ScriptedWorld.at(0, 102, 0, 0.0F), slot) < hover, "high: less");
    }

    @Test
    void planeThrottleFollowsTheDistanceToGo() {
        LeaderState leader = new LeaderState(new Point(0, 0, 0), 0, 0, 0.4, new Point(0, 0, 1), true, false, true);
        assertEquals(1.0, FollowBrain.throttle(leader, 100, 100));
        assertEquals(0.4, FollowBrain.throttle(leader, 0, 3), "level with the slot: the leader's throttle");
        assertEquals(0.52, FollowBrain.throttle(leader, 12, 12), 1e-12, "trimmed up while behind");
        assertEquals(0.76, FollowBrain.throttle(leader, 46, 46), 1e-12, "blending to full further back");
        assertEquals(0.3, FollowBrain.throttle(leader, -10, 10), 1e-12, "trimmed down once past the slot");
        assertEquals(0.05, FollowBrain.throttle(new LeaderState(new Point(0, 0, 0), 0, 0, 0.0, new Point(0, 0, 1),
                true, false, true), 0, 3), "some throttle always remains");
    }

    private Perception perceive(Airframe airframe, Point leader) {
        return this.world.perceive(airframe, leaderAt(leader, LEADER_SPEED));
    }

    private static LeaderState leaderAt(Point position, double speed) {
        return new LeaderState(position, 0.0F, 0.0F, Double.NaN, new Point(0, 0, speed), false, false, true);
    }

    private static Point slot(Point leader) {
        return slot(leader, LEADER_SPEED);
    }

    private static Point slot(Point leader, double speed) {
        return ScriptedWorld.FORMATION.slotPosition(leaderAt(leader, speed), 0);
    }

    /** A helicopter in open sky, moved by the real rotor law. */
    private static final class RotorFlight {

        private static final RotorcraftHandling HANDLING = new RotorcraftHandling(
                new VerticalForces.Gravity(Airframe.DEFAULT_GRAVITY, Airframe.DEFAULT_GRAVITY), HELI_SPEED_LIMIT, 0.0F,
                false);
        private static final ControlInput NO_KEYS = new ControlInput(false, false, false, false, false);

        private final HelicopterModel model = new HelicopterModel();
        private final StepContext openSky = new StepContext(new OpenSky(), this::move, new NoDrift());
        private Vec3d lastMove = Vec3d.ZERO;
        private Point position;
        private BodyState body = new BodyState(Vec3d.ZERO, new Attitude(0.0F, 0.0F, 0.0F), false, false, false);
        private double cruiseSpeed = CruiseSpeed.MINIMUM;
        private double throttle;

        RotorFlight(Point position) {
            this.position = position;
        }

        /** One tick: the brain decides, the rotor moves the helicopter, the brain corrects; the slot distance. */
        double tick(Perception perception, Assignment assignment, Point slot) {
            Vec3d v = this.body.velocity();
            Attitude attitude = this.body.attitude();
            RecordedControls controls = new RecordedControls(this.position, new Point(v.x(), v.y(), v.z()),
                    attitude.yaw(), attitude.pitch(), attitude.roll(), this.throttle, false, false);
            WingmanBrain.control(perception, controls, assignment);
            this.throttle = controls.throttle();
            BodyState steered = this.body.withAttitude(new Attitude(controls.yaw(), controls.pitch(),
                    controls.roll()));
            MotionStep step = this.model.step(steered, new PropulsionState(this.throttle, 0.0F, this.cruiseSpeed,
                    0.0F), NO_KEYS, HANDLING, this.openSky);
            this.position = new Point(this.position.x() + this.lastMove.x(), this.position.y() + this.lastMove.y(),
                    this.position.z() + this.lastMove.z());
            this.body = step.body();
            this.cruiseSpeed = step.propulsion().cruiseSpeed();

            Vec3d after = this.body.velocity();
            RecordedControls corrections = new RecordedControls(this.position, new Point(after.x(), after.y(),
                    after.z()), controls.yaw(), controls.pitch(), controls.roll(), this.throttle, false, false);
            WingmanBrain.afterMove(perception, corrections, assignment);
            this.position = corrections.position();
            return slot.distanceTo(this.position);
        }

        private MoveResult move(BodyState moving) {
            this.lastMove = moving.velocity();
            return new MoveResult(moving.velocity(), false, false);
        }
    }

    private static final class OpenSky implements TerrainProbe {

        @Override
        public boolean isClearBelow(int size, int depth) {
            return true;
        }

        @Override
        public double waterDepth() {
            return 0.0;
        }

        @Override
        public boolean isAboveWaterOrAir() {
            return true;
        }
    }

    /** Never nudges: a drift draw only acts on a zero. */
    private static final class NoDrift implements SimRandom {

        @Override
        public int nextInt(int bound) {
            return 1;
        }

        @Override
        public double nextDouble() {
            return 0.5;
        }
    }
}
