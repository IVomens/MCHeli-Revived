package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;

/**
 * Target drones fly circles on their own: high up they cruise level with the gear up; closer to
 * the ground they turn tighter and climb back up. The throttle is held full elsewhere, and a drone
 * that runs dry explodes.
 */
public final class TargetDroneAutopilot {

    /** With nothing below for this many blocks the drone cruises. */
    private static final int CRUISE_CLEARANCE = 40;
    /** With nothing below for this many blocks, but ground within cruise clearance, it climbs. */
    private static final int CLIMB_CLEARANCE = 5;
    private static final float CRUISE_PITCH_LEVELING = 0.95F;
    private static final float CLIMB_TURN_FACTOR = 2.0F;
    private static final float CLIMB_PITCH_STEP = 0.5F;
    /** Steepest climb pitch; negative pitch is nose up. */
    private static final float MAX_CLIMB_PITCH = -20.0F;

    private TargetDroneAutopilot() {
    }

    /**
     * @param attitude     the attitude after steering
     * @param holdAltitude whether the drone cruises at its altitude this tick
     * @param retractGear  whether the drone wants its landing gear up
     */
    public record Command(Attitude attitude, boolean holdAltitude, boolean retractGear) {
    }

    /** @param circleRate the definition's {@code autoPilotRot}, yaw degrees per tick */
    public static Command steer(Attitude attitude, float circleRate, TerrainProbe terrain) {
        if (terrain.isClearBelow(3, CRUISE_CLEARANCE)) {
            Attitude cruising = new Attitude(attitude.yaw() + circleRate, attitude.pitch() * CRUISE_PITCH_LEVELING,
                    attitude.roll());
            return new Command(cruising, true, true);
        }
        if (!terrain.isClearBelow(3, CLIMB_CLEARANCE)) {
            return new Command(attitude, false, false);
        }
        Attitude climbing = attitude.withYaw(attitude.yaw() + circleRate * CLIMB_TURN_FACTOR);
        if (climbing.pitch() > MAX_CLIMB_PITCH) {
            climbing = climbing.withPitch(climbing.pitch() - CLIMB_PITCH_STEP);
        }
        return new Command(climbing, false, false);
    }
}
