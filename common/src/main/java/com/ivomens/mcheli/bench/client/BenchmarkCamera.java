package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.bench.BenchmarkLayout;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Flies the player along the orbit of a {@link BenchmarkLayout}, looking at the middle of the rows. It
 * moves the player once per client tick and lets the game interpolate the frames in between, the same
 * way the player's own movement is drawn; the yaw is kept continuous so the interpolation never spins.
 */
final class BenchmarkCamera {

    private float yaw;

    /** Puts the player at {@code turn} (0 to 1) of the orbit. */
    void place(LocalPlayer player, BenchmarkLayout layout, double turn) {
        Vec3 at = layout.orbitPosition(turn);
        Vec3 look = layout.centre().subtract(at);
        float rawYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        this.yaw += Mth.wrapDegrees(rawYaw - this.yaw);
        float pitch = (float) Math.toDegrees(Math.atan2(-look.y, look.horizontalDistance()));
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(at.x, at.y, at.z);
        player.setYRot(this.yaw);
        player.setYHeadRot(this.yaw);
        player.setXRot(pitch);
    }

    /** Yaw and pitch at the start of the orbit, for the server teleport that puts the player there. */
    static float[] startAngles(BenchmarkLayout layout) {
        Vec3 at = layout.orbitPosition(0);
        Vec3 look = layout.centre().subtract(at);
        return new float[]{(float) Math.toDegrees(Math.atan2(-look.x, look.z)),
                (float) Math.toDegrees(Math.atan2(-look.y, look.horizontalDistance()))};
    }
}
