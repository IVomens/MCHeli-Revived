package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import net.minecraft.world.entity.Entity;

/**
 * Draws {@link LockOnSeeker#inLockAngle} as a grid: one row per pitch offset, one column per
 * yaw offset from the direction of the target, '#' where the target is inside a 10 degree cone.
 */
final class LockCone {

    private static final float LOCK_ANGLE = 10.0F;

    private LockCone() {
    }

    static String grid(Entity shooter, Entity target, int halfSpan, int step) {
        float[] aim = aimAt(shooter, target);
        StringBuilder text = new StringBuilder("lock cone of " + LOCK_ANGLE + " degrees around the aim at "
                + target.getType().toShortString() + " (yaw " + aim[0] + ", pitch " + aim[1] + "); columns are yaw "
                + -halfSpan + ".." + halfSpan + ", rows pitch " + -halfSpan + ".." + halfSpan + ", step " + step
                + "\n");
        for (int pitch = -halfSpan; pitch <= halfSpan; pitch += step) {
            text.append(String.format("  %+4d ", pitch));
            for (int yaw = -halfSpan; yaw <= halfSpan; yaw += step) {
                boolean inside = LockOnSeeker.inLockAngle(shooter, aim[0] + yaw, aim[1] + pitch, target,
                        LOCK_ANGLE);
                text.append(inside ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** The (yaw, pitch) that points the shooter at the target's centre, in the seeker's convention. */
    static float[] aimAt(Entity shooter, Entity target) {
        double dx = target.getX() - shooter.getX();
        double dy = target.getY() + target.getBbHeight() / 2.0F - shooter.getY();
        double dz = target.getZ() - shooter.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = -(float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[]{yaw, pitch};
    }
}
