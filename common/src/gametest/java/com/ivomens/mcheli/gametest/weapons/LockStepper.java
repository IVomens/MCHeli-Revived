package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds up a lock one {@link LockOnSeeker#track} per tick, the step the client's {@code lock} takes
 * while the trigger is held, without its seeker tones.
 */
final class LockStepper {

    /** How far the aim swings away in a "look away" progression, in degrees of yaw. */
    private static final float LOOK_AWAY_YAW = 45.0F;

    private LockStepper() {
    }

    /**
     * The lock count after each tick of holding the trigger with the shooter aimed at the target.
     *
     * @param lookAwayTick from this tick on the aim swings away; -1 to keep aiming
     */
    static String progression(String title, LockOnSeeker seeker, Entity shooter, Entity target, int ticks,
                              int lookAwayTick) {
        float[] aim = LockCone.aimAt(shooter, target);
        List<String> counts = new ArrayList<>();
        for (int tick = 1; tick <= ticks; tick++) {
            float yaw = lookAwayTick >= 0 && tick >= lookAwayTick ? aim[0] + LOOK_AWAY_YAW : aim[0];
            shooter.setYRot(yaw);
            shooter.setXRot(aim[1]);
            seeker.track(shooter);
            String locked = seeker.target() == null ? "-" : seeker.target() == target ? "" : "?";
            counts.add(seeker.lockCount() + locked);
        }
        return "lock count per tick, " + title + " (max " + seeker.lockTicks() + "): "
                + String.join(" ", counts) + "\n";
    }
}
