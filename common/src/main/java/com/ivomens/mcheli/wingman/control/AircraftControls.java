package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.core.wingman.brain.Controls;
import com.ivomens.mcheli.core.wingman.brain.Point;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** {@link Controls} that act on the aircraft at once: how the scripted pilot applies the brain's work. */
final class AircraftControls implements Controls {

    private final AircraftEntity aircraft;

    AircraftControls(AircraftEntity aircraft) {
        this.aircraft = aircraft;
    }

    /**
     * The aircraft's pilot. MCHeli seats are their own entities, so the vanilla passenger list is often
     * empty; {@link AircraftEntity#getRiddenByEntity()} is asked first.
     */
    static @Nullable Entity pilotOf(Entity vehicle) {
        if (vehicle instanceof AircraftEntity ac) {
            Entity rider = ac.getRiddenByEntity();
            if (rider != null) {
                return rider;
            }
        }
        return vehicle.getPassengers().isEmpty() ? null : vehicle.getPassengers().get(0);
    }

    @Override
    public Point position() {
        return new Point(this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ());
    }

    @Override
    public Point motion() {
        Vec3 m = this.aircraft.getDeltaMovement();
        return new Point(m.x, m.y, m.z);
    }

    @Override
    public float yaw() {
        return this.aircraft.getYRot();
    }

    @Override
    public float pitch() {
        return this.aircraft.getXRot();
    }

    @Override
    public float roll() {
        return this.aircraft.roll();
    }

    @Override
    public double throttle() {
        return this.aircraft.engine().throttle();
    }

    @Override
    public boolean onGround() {
        return this.aircraft.onGround();
    }

    @Override
    public void setThrottle(double throttle) {
        this.aircraft.engine().setThrottle(throttle);
    }

    @Override
    public void setYaw(float yaw) {
        this.aircraft.setYRot(yaw);
    }

    @Override
    public void setPitch(float pitch) {
        this.aircraft.setXRot(pitch);
    }

    @Override
    public void setRoll(float roll) {
        this.aircraft.rollAngle().set(roll);
    }

    @Override
    public void setMotion(double x, double y, double z) {
        this.aircraft.setDeltaMovement(x, y, z);
    }

    @Override
    public void setMotionY(double y) {
        Vec3 m = this.aircraft.getDeltaMovement();
        this.aircraft.setDeltaMovement(m.x, y, m.z);
    }

    @Override
    public void setHorizontalMotion(double x, double z) {
        this.aircraft.setDeltaMovement(x, this.aircraft.getDeltaMovement().y, z);
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.aircraft.setPos(x, y, z);
    }

    @Override
    public void aimWeapons(float yaw) {
        this.aircraft.aim().setRiderYaw(yaw);
    }

    @Override
    public void aimWeaponsPitch(float pitch) {
        this.aircraft.aim().setRiderPitch(pitch);
    }

    @Override
    public void turnPilot(float yaw) {
        if (pilotOf(this.aircraft) instanceof Player player) {
            player.setYRot(yaw);
        }
    }

    /** Only from forward flight to hover and back: the switch toggles, and would undo itself. */
    @Override
    public void switchNozzle(boolean hover) {
        if (!(this.aircraft instanceof PlaneEntity plane) || !plane.nozzles().isFitted()) {
            return;
        }
        boolean forward = plane.nozzles().mode() == 0;
        if (hover == forward) {
            plane.nozzles().switchTo(hover);
        }
    }

    /**
     * Raises or lowers the gear under the rules the pilot's gear key follows: it goes up once fully
     * down with nothing below, and down once fully up.
     */
    @Override
    public void setGear(boolean folded) {
        PartsComponent parts = this.aircraft.parts();
        if (folded) {
            if (parts.canFoldLandingGear()) {
                parts.foldLandingGear();
            }
        } else if (parts.canUnfoldLandingGear()) {
            parts.unfoldLandingGear();
        }
    }
}
