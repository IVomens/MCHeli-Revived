package com.ivomens.mcheli.vehicle.client.camera;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.camera.CameraStandIn;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Smoothed third person camera anchor for vehicles: eases toward a point above the vehicle (along
 * its rolled up vector) and looks where the player looks.
 */
public class ThirdPersonCamera extends CameraStandIn {

    /** Fraction of the remaining distance the anchor moves each tick. */
    private static final float LERP_AMOUNT = 0.3F;
    private static final double SPAWN_HEIGHT = 4.0;
    private static final float MAX_PITCH = 89.9F;
    private static final float PITCH_OFFSET = 2.0F;

    private AircraftEntity aircraft;

    private ThirdPersonCamera(Level level) {
        super(level);
    }

    /** A new anchor a few blocks above the aircraft. */
    static ThirdPersonCamera above(Level level, AircraftEntity ac) {
        ThirdPersonCamera anchor = new ThirdPersonCamera(level);
        anchor.setPos(ac.getX(), ac.getY() + SPAWN_HEIGHT, ac.getZ());
        anchor.xo = anchor.xOld = ac.getX();
        anchor.yo = anchor.yOld = ac.getY() + SPAWN_HEIGHT;
        anchor.zo = anchor.zOld = ac.getZ();
        return anchor;
    }

    /** Discards the anchor and lets go of its aircraft. */
    void release() {
        discard();
        this.aircraft = null;
    }

    /**
     * Eases toward the aircraft and looks where the player looks.
     *
     * @return false, discarding the anchor, when there is nothing left to follow
     */
    boolean follow(AircraftEntity aircraft, Player player) {
        if (aircraft == null || aircraft.isRemoved() || player == null) {
            discard();
            return false;
        }
        this.aircraft = aircraft;

        this.xo = this.xOld = this.getX();
        this.yo = this.yOld = this.getY();
        this.zo = this.zOld = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        Vec3 target = aircraft.position().add(rolledUpVector(aircraft).scale(anchorHeight(aircraft)));
        this.setPosRaw(
                this.getX() + (target.x - this.getX()) * LERP_AMOUNT,
                this.getY() + (target.y - this.getY()) * LERP_AMOUNT,
                this.getZ() + (target.z - this.getZ()) * LERP_AMOUNT);

        this.setYRot(player.getYRot());
        this.setXRot(Mth.clamp(player.getXRot() + PITCH_OFFSET, -MAX_PITCH, MAX_PITCH));
        // Keep the previous yaw on the same side of the wrap so interpolation takes the short way.
        if (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        } else if (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        return true;
    }

    private static double anchorHeight(AircraftEntity aircraft) {
        return switch (aircraft) {
            case PlaneEntity plane -> 10.0;
            case HelicopterEntity heli -> 5.0;
            case TankEntity tank -> 4.0;
            case GroundVehicleEntity vehicle -> 2.0;
            case ShipEntity ship -> 4.0;
            default -> 0.0;
        };
    }

    /** The aircraft's up vector, tilted by its pitch and roll. */
    private static Vec3 rolledUpVector(AircraftEntity aircraft) {
        double yaw = Math.toRadians(aircraft.getYRot());
        double pitch = Math.toRadians(aircraft.getXRot());
        double cosPitch = Math.cos(pitch);
        Vec3 forward = new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-12 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        return rotateAroundAxis(up, forward, Math.toRadians(aircraft.roll())).normalize();
    }

    /** Rodrigues' rotation of {@code vector} around {@code axis}. */
    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double angle) {
        Vec3 unitAxis = axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 cross = unitAxis.cross(vector);
        double along = unitAxis.dot(vector) * (1.0 - cos);
        return new Vec3(
                vector.x * cos + cross.x * sin + unitAxis.x * along,
                vector.y * cos + cross.y * sin + unitAxis.y * along,
                vector.z * cos + cross.z * sin + unitAxis.z * along);
    }

    @Override
    public void tick() {
        if (this.aircraft == null || this.aircraft.isRemoved()) {
            discard();
        }
    }
}
