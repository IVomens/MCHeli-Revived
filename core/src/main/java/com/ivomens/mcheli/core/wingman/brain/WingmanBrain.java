package com.ivomens.mcheli.core.wingman.brain;

/**
 * A wingman's brain: decides from a {@link Perception} and works the aircraft through
 * {@link Controls}. It runs in four steps per tick, in this order over all wingmen of a level:
 * <ol>
 *   <li>{@link #control}, before the level's entities tick: attackers steer at their target, followers
 *       fly formation ({@link FollowBrain}), autonomous aircraft are flown in their current phase
 *       ({@link PhaseControl}).</li>
 *   <li>{@link #plan}, still before the entities tick: the task of an autonomous aircraft moves its
 *       phase and target on ({@link OrderBrain}, {@link NodeMissionBrain}).</li>
 *   <li>Once every entity has moved, the world releases weapons for attackers, then {@link #afterMove}
 *       applies the formation catch-up, the landing gear and the taxi push.</li>
 *   <li>{@link #hold}: the attitude and motion corrections of the runway and helipad phases, which
 *       MCHeli's physics would otherwise undo before the client sees them.</li>
 * </ol>
 * Steps 1 and 2 share one set of controls per aircraft, so the plan sees what the control did. The
 * brain keeps no state of its own; everything lives in the {@link Assignment}.
 */
public final class WingmanBrain {

    private WingmanBrain() {
    }

    /** Step 1: throttle, steering and the VTOL nozzle. */
    public static void control(Perception p, Controls c, Assignment a) {
        PhaseControl.driveNozzle(p, c, a.autopilot());
        if (a.attack().isAttacking()) {
            // an autonomous attack run is still the autopilot's; its rider keeps the last values shown
            if (a.autopilot().isAutonomous()) {
                p.senses().keepAutopilotShown();
            }
            c.setThrottle(1.0);
            Steering.steer(p, c, a);
        } else if (a.autopilot().isAutonomous()) {
            PhaseControl.fly(p, c, a);
        } else if (p.leader() != null) {
            FollowBrain.fly(p, c, a);
        }
    }

    /** Step 2: an autonomous aircraft's task picks its phase, target and, in some phases, throttle. */
    public static void plan(Perception p, Controls c, Assignment a) {
        if (!a.autopilot().isAutonomous()) {
            return;
        }
        if (a.autopilot().hasOrder()) {
            OrderBrain.fly(p, c, a);
        } else {
            NodeMissionBrain.fly(p, c, a);
        }
    }

    /** Step 3, after weapons: catch-up boost, landing gear, taxi push or the rider's heading. */
    public static void afterMove(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        LeaderState leader = p.leader();
        if (!a.attack().isAttacking() && !autopilot.isAutonomous() && leader != null && !leader.isStopped()) {
            FollowBrain.catchUp(p, c, a);
        }

        if (leader != null) {
            if (leader.aircraft()) {
                c.setGear(leader.gearFolded());
            }
        } else if (autopilot.isAutonomous()) {
            c.setGear(PhaseControl.gearUp(autopilot.phase));
        }

        if (autopilot.isAutonomous()) {
            PhaseControl.holdAfterPhysics(p, c, autopilot);
        }
    }

    /** Step 4: the holds of the runway and helipad phases. */
    public static void hold(Perception p, Controls c, Assignment a) {
        Autopilot autopilot = a.autopilot();
        if (!autopilot.isAutonomous() || VtolBrain.holdAfterPhysics(p, c, autopilot)) {
            return;
        }
        switch (autopilot.phase) {
            case PARKED -> {
                // ground torque can still turn a parked aircraft
                if (!Float.isNaN(autopilot.parkingHeadingYaw)) {
                    c.setYaw(autopilot.parkingHeadingYaw);
                }
            }
            // physics keeps the landing attitude from the velocity vector, which digs the tail into the ground
            case LANDING -> c.levelAttitude();
            case CIRCUIT_FINAL -> {
                OrderProgress progress = autopilot.order();
                Runway runway = progress != null ? HomeBase.findRunway(p, autopilot, progress.order()) : null;
                if (runway != null) {
                    RecoveryBrain.holdFinal(c, runway);
                }
            }
            case CLIMB -> DepartureBrain.holdClimbPitch(c, autopilot);
            case TAKEOFF_ROLL -> {
                OrderProgress progress = autopilot.order();
                Runway runway = progress != null ? HomeBase.findRunway(p, autopilot, progress.order())
                        : NodeMissionBrain.takeoffRunway(p, autopilot);
                if (runway != null) {
                    DepartureBrain.holdRoll(c, autopilot, runway);
                }
            }
            default -> {
            }
        }
    }
}
