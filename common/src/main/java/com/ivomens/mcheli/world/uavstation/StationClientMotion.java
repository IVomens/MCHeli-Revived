package com.ivomens.mcheli.world.uavstation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A station on a client moves towards the position the server last sent, over a few more ticks than
 * the server asked for, and otherwise drifts with its motion.
 */
final class StationClientMotion {

    private final UavStationEntity station;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int stepsLeft;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetYaw;
    private double targetPitch;

    StationClientMotion(UavStationEntity station) {
        this.station = station;
    }

    void setVelocity(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
        this.station.setDeltaMovement(x, y, z);
    }

    void moveTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.stepsLeft = steps + 8;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetYaw = yRot;
        this.targetPitch = xRot;
        this.station.setDeltaMovement(this.velocityX, this.velocityY, this.velocityZ);
    }

    void tick() {
        UavStationEntity s = this.station;
        if (this.stepsLeft > 0) {
            double steps = this.stepsLeft;
            double yawChange = Mth.wrapDegrees(this.targetYaw - s.getYRot());
            float newYaw = (float) (s.getYRot() + yawChange / steps);
            float newPitch = (float) (s.getXRot() + (this.targetPitch - s.getXRot()) / steps);
            s.setPos(
                    s.getX() + (this.targetX - s.getX()) / steps,
                    s.getY() + (this.targetY - s.getY()) / steps,
                    s.getZ() + (this.targetZ - s.getZ()) / steps);
            s.setYRot(newYaw % 360.0F);
            s.setXRot(newPitch % 360.0F);
            this.stepsLeft--;
        } else {
            Vec3 m = s.getDeltaMovement();
            s.setPos(s.getX() + m.x, s.getY() + m.y, s.getZ() + m.z);
            s.setDeltaMovement(0.0, m.y * 0.96, 0.0);
        }
    }
}
